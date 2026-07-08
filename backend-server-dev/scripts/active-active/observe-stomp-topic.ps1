param(
    [Parameter(Mandatory = $true)]
    [string]$WebSocketUrl,

    [Parameter(Mandatory = $true)]
    [string]$JwtToken,

    [string]$Topic = '/topic/option-trade-aggregates',

    [int]$DurationSeconds = 30,

    [string]$OutputPath = $(Join-Path $env:TEMP ("marketmono-stomp-observer-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + ".jsonl"))
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Send-StompFrame {
    param(
        [System.Net.WebSockets.ClientWebSocket]$Socket,
        [string]$Frame,
        [System.Threading.CancellationToken]$CancellationToken
    )

    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Frame)
    $segment = [System.ArraySegment[byte]]::new($bytes)
    $Socket.SendAsync($segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $CancellationToken).GetAwaiter().GetResult()
}

function Parse-StompFrame {
    param([string]$FrameText)

    $lines = $FrameText -split "`n"
    if ($lines.Count -eq 0) {
        return $null
    }

    $command = $lines[0].Trim("`r")
    $headers = @{}
    $bodyStartIndex = 1

    for ($i = 1; $i -lt $lines.Count; $i++) {
        $line = $lines[$i].TrimEnd("`r")
        if ($line -eq '') {
            $bodyStartIndex = $i + 1
            break
        }

        $parts = $line -split ':', 2
        if ($parts.Count -eq 2) {
            $headers[$parts[0]] = $parts[1]
        }
    }

    $body = if ($bodyStartIndex -lt $lines.Count) {
        ($lines[$bodyStartIndex..($lines.Count - 1)] -join "`n").Trim("`r")
    } else {
        ''
    }

    [pscustomobject]@{
        Command = $command
        Headers = $headers
        Body    = $body
    }
}

function Receive-StompFrames {
    param(
        [System.Net.WebSockets.ClientWebSocket]$Socket,
        [System.TimeSpan]$Timeout,
        [System.Threading.CancellationToken]$CancellationToken
    )

    $buffer = New-Object byte[] 8192
    $segment = [System.ArraySegment[byte]]::new($buffer)
    $builder = [System.Text.StringBuilder]::new()
    $deadline = [DateTimeOffset]::UtcNow.Add($Timeout)

    while ([DateTimeOffset]::UtcNow -lt $deadline -and $Socket.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
        $receiveTask = $Socket.ReceiveAsync($segment, $CancellationToken)
        if (-not $receiveTask.Wait([TimeSpan]::FromSeconds(1))) {
            continue
        }

        $result = $receiveTask.GetAwaiter().GetResult()
        if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
            break
        }

        $chunk = [System.Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count)
        [void]$builder.Append($chunk)

        $current = $builder.ToString()
        while ($current.Contains([char]0)) {
            $parts = $current -split ([char]0), 2
            $frameText = $parts[0]
            $current = if ($parts.Count -gt 1) { $parts[1] } else { '' }
            $builder.Clear() | Out-Null
            [void]$builder.Append($current)

            if (-not [string]::IsNullOrWhiteSpace($frameText)) {
                $frame = Parse-StompFrame -FrameText $frameText
                if ($frame -ne $null) {
                    $frame
                }
            }
        }
    }
}

$outputDirectory = Split-Path -Parent $OutputPath
if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}

$socket = [System.Net.WebSockets.ClientWebSocket]::new()
$socket.Options.KeepAliveInterval = [TimeSpan]::FromSeconds(15)
$cancellation = [System.Threading.CancellationTokenSource]::new()

try {
    $socket.ConnectAsync([Uri]$WebSocketUrl, $cancellation.Token).GetAwaiter().GetResult()

    $connectFrame = @(
        'CONNECT',
        'accept-version:1.2',
        'host:marketmono',
        'heart-beat:10000,10000',
        "Authorization: Bearer $JwtToken",
        '',
        [char]0
    ) -join "`n"
    Send-StompFrame -Socket $socket -Frame $connectFrame -CancellationToken $cancellation.Token

    $connectedFrame = Receive-StompFrames -Socket $socket -Timeout ([TimeSpan]::FromSeconds(10)) -CancellationToken $cancellation.Token |
        Select-Object -First 1

    if ($null -eq $connectedFrame -or $connectedFrame.Command -ne 'CONNECTED') {
        throw "Did not receive STOMP CONNECTED frame from $WebSocketUrl"
    }

    $connectedRecord = [pscustomobject]@{
        timestamp = (Get-Date).ToString('o')
        command   = $connectedFrame.Command
        topic     = $Topic
        nodeId    = $connectedFrame.Headers['X-Node-Id']
        headers   = $connectedFrame.Headers
    }
    $connectedRecord | ConvertTo-Json -Compress | Add-Content -Path $OutputPath

    $subscribeFrame = @(
        'SUBSCRIBE',
        'id:observer-0',
        "destination:$Topic",
        'ack:auto',
        '',
        [char]0
    ) -join "`n"
    Send-StompFrame -Socket $socket -Frame $subscribeFrame -CancellationToken $cancellation.Token

    Receive-StompFrames -Socket $socket -Timeout ([TimeSpan]::FromSeconds($DurationSeconds)) -CancellationToken $cancellation.Token |
        ForEach-Object {
            $record = [pscustomobject]@{
                timestamp   = (Get-Date).ToString('o')
                command     = $_.Command
                topic       = $Topic
                destination = $_.Headers['destination']
                subscription = $_.Headers['subscription']
                messageId   = $_.Headers['message-id']
                bodyPreview = if ($_.Body.Length -gt 200) { $_.Body.Substring(0, 200) } else { $_.Body }
            }
            $record | ConvertTo-Json -Compress | Add-Content -Path $OutputPath
        }

    Write-Host "STOMP observation complete. Output written to $OutputPath" -ForegroundColor Green
} finally {
    if ($socket.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
        $disconnectFrame = @(
            'DISCONNECT',
            '',
            [char]0
        ) -join "`n"
        Send-StompFrame -Socket $socket -Frame $disconnectFrame -CancellationToken $cancellation.Token
        $socket.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, 'done', $cancellation.Token).GetAwaiter().GetResult()
    }
    $socket.Dispose()
    $cancellation.Dispose()
}