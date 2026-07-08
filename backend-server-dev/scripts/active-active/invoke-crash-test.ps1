param(
    [Parameter(Mandatory = $true)]
    [string]$JwtToken,

    [ValidateSet('T1', 'T4', 'T5')]
    [string]$Scenario = 'T1',

    [string]$NodeABaseUrl = 'http://localhost:8080',

    [string]$NodeBBaseUrl = 'http://localhost:8081',

    [string]$LoadBalancerBaseUrl = 'http://localhost:8090',

    [string]$Topic = '/topic/option-trade-aggregates',

    [int]$ObserverDurationSeconds = 30,

    [switch]$SkipObserver,

    [string]$ReportPath = $(Join-Path $env:TEMP ("marketmono-crash-test-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + ".md"))
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$observerScript = Join-Path $scriptRoot 'observe-stomp-topic.ps1'
$instanceAScript = Join-Path $scriptRoot 'run-instance-a.ps1'
$instanceBScript = Join-Path $scriptRoot 'run-instance-b.ps1'

function New-AuthHeaders {
    return @{ Authorization = "Bearer $JwtToken" }
}

function Get-NodeStatus {
    param([string]$BaseUrl)
    Invoke-RestMethod -Uri ($BaseUrl.TrimEnd('/') + '/api/v1/node/status') -Headers (New-AuthHeaders) -TimeoutSec 10
}

function Test-LoadBalancerHealth {
    Invoke-RestMethod -Uri ($LoadBalancerBaseUrl.TrimEnd('/') + '/actuator/health') -TimeoutSec 10
}

function Get-ListeningProcessId {
    param([int]$Port)

    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1

    if ($null -eq $connection) {
        return $null
    }

    return $connection.OwningProcess
}

function Stop-BackendProcessByPort {
    param([int]$Port)

    $processId = Get-ListeningProcessId -Port $Port
    if ($null -eq $processId) {
        throw "No backend process is listening on port $Port"
    }

    Stop-Process -Id $processId -Force
    return $processId
}

function Start-BackendInstance {
    param([ValidateSet('A', 'B')][string]$Node)

    $scriptPath = if ($Node -eq 'A') { $instanceAScript } else { $instanceBScript }
    Start-Process pwsh -ArgumentList @('-NoExit', '-File', $scriptPath) -WorkingDirectory (Split-Path $scriptPath -Parent) | Out-Null
}

function Wait-ForNodeStatus {
    param(
        [string]$BaseUrl,
        [int]$TimeoutSeconds = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            return Get-NodeStatus -BaseUrl $BaseUrl
        } catch {
            Start-Sleep -Seconds 2
        }
    }

    throw "Timed out waiting for node status at $BaseUrl"
}

function Start-ObserverJob {
    if ($SkipObserver) {
        return $null
    }

    $outputPath = Join-Path $env:TEMP ("marketmono-crash-observer-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + ".jsonl")
    return Start-Job -ScriptBlock {
        param($ObserverScript, $WebSocketUrl, $Token, $ObservedTopic, $Duration, $Path)
        & $ObserverScript -WebSocketUrl $WebSocketUrl -JwtToken $Token -Topic $ObservedTopic -DurationSeconds $Duration -OutputPath $Path
    } -ArgumentList $observerScript, (($LoadBalancerBaseUrl -replace '^http', 'ws').TrimEnd('/') + '/ws'), $JwtToken, $Topic, $ObserverDurationSeconds, $outputPath
}

function Complete-ObserverJob {
    param($Job)

    if ($null -eq $Job) {
        return $null
    }

    Wait-Job -Job $Job | Out-Null
    $output = Receive-Job -Job $Job
    Remove-Job -Job $Job
    return $output
}

$reportLines = New-Object System.Collections.Generic.List[string]
$reportLines.Add('# Active-Active Crash Test Report')
$reportLines.Add("")
$reportLines.Add("- Scenario: $Scenario")
$reportLines.Add("- Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
$reportLines.Add("- Node A: $NodeABaseUrl")
$reportLines.Add("- Node B: $NodeBBaseUrl")
$reportLines.Add("- Load Balancer: $LoadBalancerBaseUrl")
$reportLines.Add("")

$observerJob = Start-ObserverJob

try {
    $baselineA = Get-NodeStatus -BaseUrl $NodeABaseUrl
    $baselineB = Get-NodeStatus -BaseUrl $NodeBBaseUrl
    $lbHealthBefore = Test-LoadBalancerHealth

    $reportLines.Add('## Baseline')
    $reportLines.Add("")
    $reportLines.Add("- Node A profiles: $($baselineA.profiles -join ', ')")
    $reportLines.Add("- Node B profiles: $($baselineB.profiles -join ', ')")
    $reportLines.Add("- Node A Kafka listeners running: $($baselineA.kafka.listenersRunning)/$($baselineA.kafka.listenersTotal)")
    $reportLines.Add("- Node B Kafka listeners running: $($baselineB.kafka.listenersRunning)/$($baselineB.kafka.listenersTotal)")
    $reportLines.Add("- Load balancer health before scenario: $($lbHealthBefore.status)")
    $reportLines.Add("")

    switch ($Scenario) {
        'T1' {
            $stoppedProcessId = Stop-BackendProcessByPort -Port 8080
            $survivorStatus = Wait-ForNodeStatus -BaseUrl $NodeBBaseUrl
            $lbHealthAfter = Test-LoadBalancerHealth

            $reportLines.Add('## T1 — Backend-A hard crash')
            $reportLines.Add("")
            $reportLines.Add("- Stopped process ID: $stoppedProcessId")
            $reportLines.Add("- Surviving node ID: $($survivorStatus.nodeId)")
            $reportLines.Add("- Surviving node Kafka listeners running: $($survivorStatus.kafka.listenersRunning)/$($survivorStatus.kafka.listenersTotal)")
            $reportLines.Add("- Surviving node active ShedLock rows: $($survivorStatus.shedlock.activeLocksHeld -join ', ')")
            $reportLines.Add("- Load balancer health after crash: $($lbHealthAfter.status)")
        }
        'T4' {
            Start-BackendInstance -Node 'A'
            $nodeAStatus = Wait-ForNodeStatus -BaseUrl $NodeABaseUrl

            $reportLines.Add('## T4 — Backend-A comes back after crash')
            $reportLines.Add("")
            $reportLines.Add("- Restarted node A via: $instanceAScript")
            $reportLines.Add("- Node A status endpoint reachable: $($nodeAStatus.nodeId)")
            $reportLines.Add("- Node A Kafka listeners running: $($nodeAStatus.kafka.listenersRunning)/$($nodeAStatus.kafka.listenersTotal)")
            $reportLines.Add("- Node A WebSocket relay type: $($nodeAStatus.websocket.relayType)")
        }
        'T5' {
            try { Stop-BackendProcessByPort -Port 8080 | Out-Null } catch { }
            try { Stop-BackendProcessByPort -Port 8081 | Out-Null } catch { }

            Start-BackendInstance -Node 'A'
            $nodeAStatus = Wait-ForNodeStatus -BaseUrl $NodeABaseUrl

            Start-BackendInstance -Node 'B'
            $nodeBStatus = Wait-ForNodeStatus -BaseUrl $NodeBBaseUrl

            $reportLines.Add('## T5 — Both backends restart sequentially')
            $reportLines.Add("")
            $reportLines.Add("- Restarted node A, then node B")
            $reportLines.Add("- Node A status endpoint reachable: $($nodeAStatus.nodeId)")
            $reportLines.Add("- Node B status endpoint reachable: $($nodeBStatus.nodeId)")
            $reportLines.Add("- Node A Kafka partitions observed: $($nodeAStatus.kafka.partitionsAssigned -join ', ')")
            $reportLines.Add("- Node B Kafka partitions observed: $($nodeBStatus.kafka.partitionsAssigned -join ', ')")
        }
    }
} finally {
    $observerSummary = Complete-ObserverJob -Job $observerJob
    if ($observerSummary) {
        $reportLines.Add("")
        $reportLines.Add('## Observer Output')
        $reportLines.Add("")
        foreach ($line in $observerSummary) {
            $reportLines.Add("- $line")
        }
    }

    $reportDirectory = Split-Path -Parent $ReportPath
    if (-not [string]::IsNullOrWhiteSpace($reportDirectory)) {
        New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
    }
    $reportLines | Set-Content -Path $ReportPath
    Write-Host "Crash-test report written to $ReportPath" -ForegroundColor Green
}