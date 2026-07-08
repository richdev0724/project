param(
    [switch]$EnableKafka
)

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $repoRoot

$env:DB_HOST = 'localhost'
$env:DB_PORT = '5433'
$env:DB_USERNAME = 'postgres'
$env:DB_PASSWORD = 'postgres'
$env:SERVER_PORT = '8081'
$env:ACTIVE_ACTIVE_REDIS_ENABLED = 'true'
$env:WEBSOCKET_RELAY_ENABLED = 'true'
$env:REDIS_HOST = 'localhost'
$env:REDIS_PORT = '6379'
$env:REDIS_SSL = 'false'
$env:KAFKA_ENABLED = if ($EnableKafka) { 'true' } else { 'false' }
$env:KAFKA_LISTENER_CONCURRENCY = '2'

.\gradlew.bat bootRun --args='--spring.profiles.active=local,active-active --logging.file.name=boot-8081.log --server.port=8081'