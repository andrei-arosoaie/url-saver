param(
    [string]$Distribution = 'Debian_Recovered',
    [switch]$UnitOnly,
    [switch]$ExportOpenApi
)

$ErrorActionPreference = 'Stop'
$workspaceRoot = Split-Path $PSScriptRoot -Parent
$wslExecutable = Join-Path $env:SystemRoot 'System32\wsl.exe'
$resolvedWorkspace = & $wslExecutable -d $Distribution --exec wslpath -a $workspaceRoot.Replace('\', '/')
if ($LASTEXITCODE -ne 0 -or -not $resolvedWorkspace) {
    throw 'Cannot resolve the workspace inside WSL.'
}
$linuxWorkspace = ($resolvedWorkspace -join "`n").Trim()

if ($UnitOnly -and $ExportOpenApi) { throw 'Choose UnitOnly or ExportOpenApi, not both.' }
$tasks = if ($ExportOpenApi) {
    @('spotlessCheck', 'test', 'exportOpenApi', 'bootJar')
} elseif ($UnitOnly) {
    @('spotlessCheck', 'test', 'compileIntegrationTestJava', 'bootJar')
} else {
    @('check', 'bootJar')
}

$dockerArguments = @(
    '-d', $Distribution, '--exec', 'docker', 'run', '--rm',
    '--mount', "type=bind,source=$linuxWorkspace,target=/workspace",
    '--mount', 'type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock',
    '--add-host', 'host.docker.internal:host-gateway',
    '-e', 'TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal',
    '-e', 'GRADLE_USER_HOME=/workspace/.tools/gradle-linux-cache',
    '-w', '/workspace/server',
    'eclipse-temurin:27-jdk@sha256:4a02e0ee713d13bb7974f4e19b7bea1746668603c3557fe09c4681784a86bb52',
    'sh', './gradlew', '--no-daemon',
    '--project-cache-dir', '/workspace/.tools/gradle-linux-project-cache'
) + $tasks

$savedErrorAction = $ErrorActionPreference
try {
    # Windows PowerShell 5 treats redirected native stderr as ErrorRecords, even for warnings.
    $ErrorActionPreference = 'Continue'
    & $wslExecutable @dockerArguments 2>&1 | ForEach-Object { $_.ToString() }
    $verificationExit = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $savedErrorAction
}
if ($verificationExit -ne 0) { throw "Backend verification failed (exit $verificationExit)." }
