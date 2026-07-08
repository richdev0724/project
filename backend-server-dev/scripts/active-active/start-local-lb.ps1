param(
    [int]$Port = 8090
)

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$configPath = Resolve-Path (Join-Path $repoRoot 'nginx-active-active-local.conf')

docker rm -f marketmono-active-active-lb 2>$null | Out-Null

docker run -d `
    --name marketmono-active-active-lb `
    -p "${Port}:8090" `
    -v "${configPath}:/etc/nginx/conf.d/default.conf:ro" `
    nginx:alpine