[CmdletBinding()]
param(
    [string]$BaseUrl = $(if ($env:MARKETMONO_BASE_URL) { $env:MARKETMONO_BASE_URL } else { 'https://marketmono.com' }),

    [int]$Users = 50,

    [int]$IterationsPerUser = 5,

    [int]$RampSeconds = 10,

    [int]$TimeoutSeconds = 20,

    [ValidateSet('Mixed', 'Raw', 'Aggregates')]
    [string]$EndpointMode = 'Mixed',

    [double]$MaxFailureRate = 0.05,

    [double]$MaxBusyRate = 0.20,

    [int]$MaxP95Ms = 8000,

    [string]$AccessToken = $env:MARKETMONO_ACCESS_TOKEN,

    [string]$Email = $env:MARKETMONO_EMAIL,

    [string]$Password = $env:MARKETMONO_PASSWORD,

    [string]$OutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Join-BaseUrl {
    param(
        [string]$Root,
        [string]$Path
    )

    return $Root.TrimEnd('/') + '/' + $Path.TrimStart('/')
}

function ConvertTo-QueryString {
    param([hashtable]$Query)

    $pairs = New-Object System.Collections.Generic.List[string]
    foreach ($key in ($Query.Keys | Sort-Object)) {
        $value = $Query[$key]
        if ($null -eq $value -or "$value" -eq '') {
            continue
        }

        $encodedKey = [uri]::EscapeDataString([string]$key)
        $encodedValue = [uri]::EscapeDataString([string]$value)
        $pairs.Add("$encodedKey=$encodedValue")
    }

    return ($pairs -join '&')
}

function New-Scenario {
    param(
        [string]$Name,
        [string]$Endpoint,
        [hashtable]$Query
    )

    $queryString = ConvertTo-QueryString -Query $Query
    return [pscustomobject]@{
        Name = $Name
        Url = Join-BaseUrl -Root $BaseUrl -Path ($Endpoint + '?' + $queryString)
    }
}

function Get-LoadTestAccessToken {
    if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
        return $AccessToken
    }

    if ([string]::IsNullOrWhiteSpace($Email) -or [string]::IsNullOrWhiteSpace($Password)) {
        throw 'Set MARKETMONO_ACCESS_TOKEN, or set MARKETMONO_EMAIL and MARKETMONO_PASSWORD for login.'
    }

    $loginBody = @{
        email = $Email
        password = $Password
    } | ConvertTo-Json -Compress

    $loginResponse = Invoke-RestMethod `
        -Method Post `
        -Uri (Join-BaseUrl -Root $BaseUrl -Path '/api/v1/auth/login') `
        -ContentType 'application/json' `
        -Body $loginBody `
        -TimeoutSec $TimeoutSeconds

    if ($loginResponse.requiresTwoFactor) {
        throw 'Login requires 2FA. Provide MARKETMONO_ACCESS_TOKEN instead of password login.'
    }

    if ([string]::IsNullOrWhiteSpace([string]$loginResponse.accessToken)) {
        throw 'Login succeeded but did not return accessToken.'
    }

    return [string]$loginResponse.accessToken
}

function New-FlowFilterScenarios {
    $rawScenarios = @(
        New-Scenario -Name 'raw-hot-default' -Endpoint '/api/v1/option-trades' -Query @{
            page = 1
            pageSize = 100
            minPremium = 50000
            minVolume = 100
            hasSingleleg = 'true'
            countMode = 'slice'
        }
        New-Scenario -Name 'raw-ticker-range' -Endpoint '/api/v1/option-trades' -Query @{
            page = 1
            pageSize = 100
            tickers = 'SPY,QQQ'
            minPremium = 50000
            minVolume = 100
            hasSingleleg = 'true'
            countMode = 'slice'
        }
        New-Scenario -Name 'raw-expensive-oi-otm' -Endpoint '/api/v1/option-trades' -Query @{
            page = 1
            pageSize = 100
            tickers = 'SPY,QQQ'
            minPremium = 50000
            minVolume = 100
            hasSingleleg = 'true'
            volumeGtOi = 'true'
            sizeGtOi = 'true'
            otmOnly = 'true'
            countMode = 'slice'
        }
        New-Scenario -Name 'raw-side-exclusion' -Endpoint '/api/v1/option-trades' -Query @{
            page = 1
            pageSize = 100
            minPremium = 50000
            minVolume = 100
            bid = 'false'
            mid = 'false'
            ask = 'false'
            noSide = 'false'
            aboveAsk = 'false'
            belowBid = 'false'
            countMode = 'slice'
        }
    )

    $aggregateScenarios = @(
        New-Scenario -Name 'aggregate-hot-default' -Endpoint '/api/v1/option-trades/aggregates' -Query @{
            page = 1
            pageSize = 100
            minPremium = 50000
            minVolume = 100
            hasSingleleg = 'true'
            countMode = 'slice'
        }
        New-Scenario -Name 'aggregate-ticker-range' -Endpoint '/api/v1/option-trades/aggregates' -Query @{
            page = 1
            pageSize = 100
            tickers = 'SPY,QQQ'
            minPremium = 50000
            minVolume = 100
            hasSingleleg = 'true'
            countMode = 'slice'
        }
        New-Scenario -Name 'aggregate-expensive-oi-otm' -Endpoint '/api/v1/option-trades/aggregates' -Query @{
            page = 1
            pageSize = 100
            tickers = 'SPY,QQQ'
            minPremium = 50000
            minVolume = 100
            hasSingleleg = 'true'
            volumeGtOi = 'true'
            sizeGtOi = 'true'
            otmOnly = 'true'
            countMode = 'slice'
        }
    )

    if ($EndpointMode -eq 'Raw') {
        return $rawScenarios
    }

    if ($EndpointMode -eq 'Aggregates') {
        return $aggregateScenarios
    }

    return @($rawScenarios + $aggregateScenarios)
}

function Get-Percentile {
    param(
        [int[]]$Values,
        [double]$Percentile
    )

    if ($Values.Count -eq 0) {
        return 0
    }

    $sorted = @($Values | Sort-Object)
    $index = [math]::Ceiling(($Percentile / 100.0) * $sorted.Count) - 1
    $index = [math]::Max(0, [math]::Min($sorted.Count - 1, $index))
    return [int]$sorted[$index]
}

function ConvertTo-StatusCodeDistribution {
    param([object[]]$Items)

    return @($Items |
        Group-Object StatusCode |
        Sort-Object Name |
        ForEach-Object {
            [pscustomobject]@{
                statusCode = [int]$_.Name
                count = $_.Count
            }
        })
}

$token = Get-LoadTestAccessToken
$scenarios = @(New-FlowFilterScenarios)

if ($scenarios.Count -eq 0) {
    throw 'No load-test scenarios were selected.'
}

$startedAt = Get-Date
$jobs = New-Object System.Collections.Generic.List[object]

Write-Host "Starting Flow filter load test against $($BaseUrl.TrimEnd('/'))"
Write-Host "Users=$Users IterationsPerUser=$IterationsPerUser Scenarios=$($scenarios.Count) RampSeconds=$RampSeconds TimeoutSeconds=$TimeoutSeconds EndpointMode=$EndpointMode"

for ($userIndex = 1; $userIndex -le $Users; $userIndex++) {
    $startDelayMs = 0
    if ($Users -gt 1 -and $RampSeconds -gt 0) {
        $startDelayMs = [int][math]::Round((($userIndex - 1) / [double]($Users - 1)) * $RampSeconds * 1000)
    }

    $jobs.Add((Start-Job -ScriptBlock {
        param(
            [int]$WorkerId,
            [object[]]$WorkerScenarios,
            [int]$WorkerIterations,
            [int]$WorkerStartDelayMs,
            [int]$WorkerTimeoutSeconds,
            [string]$WorkerToken
        )

        Set-StrictMode -Version Latest
        $ErrorActionPreference = 'Stop'

        function Assert-SuccessBody {
            param(
                [string]$ScenarioName,
                [string]$Body
            )

            if (-not $ScenarioName.EndsWith('hot-default')) {
                return $null
            }

            if ([string]::IsNullOrWhiteSpace($Body)) {
                return 'Default/latest-day success response did not include a JSON body.'
            }

            try {
                $parsed = $Body | ConvertFrom-Json
            } catch {
                return "Default/latest-day success response was not valid JSON: $($_.Exception.Message)"
            }

            if ($null -eq $parsed) {
                return 'Default/latest-day success response parsed to an empty JSON value.'
            }

            if (-not ($parsed.PSObject.Properties.Name -contains 'results')) {
                return 'Default/latest-day success response did not include a results array.'
            }

            return $null
        }

        function Assert-SanitizedErrorBody {
            param([string]$Body)

            if ([string]::IsNullOrWhiteSpace($Body)) {
                return $null
            }

            $blockedTokens = @(
                'filterSummary',
                'filterHash',
                'filterShape',
                'tickers',
                'minPremium',
                'minVolume',
                'volumeGtOi',
                'sizeGtOi',
                'otmOnly',
                'hasSingleleg'
            )

            foreach ($token in $blockedTokens) {
                if ($Body.IndexOf($token, [System.StringComparison]::OrdinalIgnoreCase) -ge 0) {
                    return "Error response body leaked internal Flow filter token '$token'."
                }
            }

            return $null
        }

        function Read-ErrorResponseBody {
            param([object]$ErrorResponse)

            if ($null -eq $ErrorResponse) {
                return $null
            }

            try {
                $stream = $ErrorResponse.GetResponseStream()
                if ($null -eq $stream) {
                    return $null
                }

                $reader = [System.IO.StreamReader]::new($stream)
                try {
                    return $reader.ReadToEnd()
                } finally {
                    $reader.Dispose()
                }
            } catch {
                return $null
            }
        }

        function Invoke-LoadRequest {
            param(
                [string]$ScenarioName,
                [string]$ScenarioUrl,
                [int]$WorkerId,
                [int]$Iteration,
                [int]$TimeoutSeconds,
                [hashtable]$Headers
            )

            $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
            $statusCode = 0
            $outcome = 'error'
            $errorCode = $null
            $responseBody = $null
            $assertionError = $null

            try {
                $response = Invoke-WebRequest `
                    -Method Get `
                    -Uri $ScenarioUrl `
                    -Headers $Headers `
                    -TimeoutSec $TimeoutSeconds `
                    -UseBasicParsing `
                    -UserAgent 'marketmono-flow-load-test/1.0'
                $statusCode = [int]$response.StatusCode
                $responseBody = [string]$response.Content
                if ($statusCode -ge 200 -and $statusCode -lt 300) {
                    $outcome = 'success'
                    $assertionError = Assert-SuccessBody -ScenarioName $ScenarioName -Body $responseBody
                } elseif ($statusCode -eq 429) {
                    $outcome = 'busy'
                    $assertionError = Assert-SanitizedErrorBody -Body $responseBody
                } else {
                    $outcome = 'http_error'
                    $assertionError = Assert-SanitizedErrorBody -Body $responseBody
                }
            } catch {
                $errorResponse = $null
                try {
                    $errorResponse = $_.Exception.Response
                } catch {
                    $errorResponse = $null
                }

                if ($null -ne $errorResponse -and $null -ne $errorResponse.StatusCode) {
                    $statusCode = [int]$errorResponse.StatusCode
                    $responseBody = Read-ErrorResponseBody -ErrorResponse $errorResponse
                    if ($statusCode -eq 429) {
                        $outcome = 'busy'
                    } else {
                        $outcome = 'http_error'
                    }
                    $assertionError = Assert-SanitizedErrorBody -Body $responseBody
                } else {
                    $outcome = 'exception'
                    $errorCode = $_.Exception.GetType().Name
                }
            } finally {
                $stopwatch.Stop()
            }

            if (-not [string]::IsNullOrWhiteSpace($assertionError)) {
                $outcome = 'assertion_error'
                $errorCode = 'BodyAssertionFailed'
            }

            return [pscustomobject]@{
                WorkerId = $WorkerId
                Iteration = $Iteration
                Scenario = $ScenarioName
                StatusCode = $statusCode
                Outcome = $outcome
                DurationMs = [int]$stopwatch.ElapsedMilliseconds
                ErrorCode = $errorCode
                AssertionError = $assertionError
            }
        }

        if ($WorkerStartDelayMs -gt 0) {
            Start-Sleep -Milliseconds $WorkerStartDelayMs
        }

        $headers = @{
            Authorization = "Bearer $WorkerToken"
            Accept = 'application/json'
        }

        $workerResults = New-Object System.Collections.Generic.List[object]

        for ($iteration = 1; $iteration -le $WorkerIterations; $iteration++) {
            foreach ($scenario in $WorkerScenarios) {
                $workerResults.Add((Invoke-LoadRequest `
                    -ScenarioName $scenario.Name `
                    -ScenarioUrl $scenario.Url `
                    -WorkerId $WorkerId `
                    -Iteration $iteration `
                    -TimeoutSeconds $WorkerTimeoutSeconds `
                    -Headers $headers))
            }
        }

        return $workerResults
    } -ArgumentList $userIndex, $scenarios, $IterationsPerUser, $startDelayMs, $TimeoutSeconds, $token))
}

Wait-Job -Job $jobs | Out-Null

$results = foreach ($job in $jobs) {
    Receive-Job -Job $job
}

Remove-Job -Job $jobs

$finishedAt = Get-Date
$durations = @($results | ForEach-Object { [int]$_.DurationMs })
$total = $results.Count
$success = @($results | Where-Object { $_.Outcome -eq 'success' }).Count
$busy = @($results | Where-Object { $_.Outcome -eq 'busy' }).Count
$failures = $total - $success - $busy
$failureRate = if ($total -gt 0) { $failures / [double]$total } else { 1.0 }
$busyRate = if ($total -gt 0) { $busy / [double]$total } else { 1.0 }
$p50 = Get-Percentile -Values $durations -Percentile 50
$p95 = Get-Percentile -Values $durations -Percentile 95
$p99 = Get-Percentile -Values $durations -Percentile 99

$byScenario = $results |
    Group-Object Scenario |
    ForEach-Object {
        $scenarioDurations = @($_.Group | ForEach-Object { [int]$_.DurationMs })
        [pscustomobject]@{
            scenario = $_.Name
            requests = $_.Count
            success = @($_.Group | Where-Object { $_.Outcome -eq 'success' }).Count
            busy = @($_.Group | Where-Object { $_.Outcome -eq 'busy' }).Count
            failures = @($_.Group | Where-Object { $_.Outcome -ne 'success' -and $_.Outcome -ne 'busy' }).Count
            statusCodes = ConvertTo-StatusCodeDistribution -Items $_.Group
            p95Ms = Get-Percentile -Values $scenarioDurations -Percentile 95
        }
    }

$assertionFailures = @($results |
    Where-Object { $_.Outcome -eq 'assertion_error' } |
    Select-Object WorkerId, Iteration, Scenario, StatusCode, AssertionError)

$summary = [pscustomobject]@{
    baseUrl = $BaseUrl.TrimEnd('/')
    startedAt = $startedAt.ToString('o')
    finishedAt = $finishedAt.ToString('o')
    users = $Users
    iterationsPerUser = $IterationsPerUser
    endpointMode = $EndpointMode
    scenarioCount = $scenarios.Count
    totalRequests = $total
    successfulRequests = $success
    busyRequests = $busy
    failedRequests = $failures
    failureRate = [math]::Round($failureRate, 4)
    busyRate = [math]::Round($busyRate, 4)
    p50Ms = $p50
    p95Ms = $p95
    p99Ms = $p99
    statusCodes = ConvertTo-StatusCodeDistribution -Items $results
    assertionFailures = $assertionFailures
    thresholds = [pscustomobject]@{
        maxFailureRate = $MaxFailureRate
        maxBusyRate = $MaxBusyRate
        maxP95Ms = $MaxP95Ms
    }
    scenarios = $byScenario
}

$summaryJson = $summary | ConvertTo-Json -Depth 6
$summaryJson

if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
    $outputDirectory = Split-Path -Parent $OutputPath
    if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) {
        New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    }

    $summaryJson | Set-Content -Path $OutputPath -Encoding UTF8
}

if ($failureRate -gt $MaxFailureRate) {
    throw "Failure rate $([math]::Round($failureRate, 4)) exceeded threshold $MaxFailureRate."
}

if ($busyRate -gt $MaxBusyRate) {
    throw "Busy rate $([math]::Round($busyRate, 4)) exceeded threshold $MaxBusyRate."
}

if ($p95 -gt $MaxP95Ms) {
    throw "P95 latency ${p95}ms exceeded threshold ${MaxP95Ms}ms."
}

Write-Host "Flow filter load test passed."
