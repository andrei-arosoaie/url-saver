param([switch]$UnitOnly)
$ErrorActionPreference = 'Stop'
$workspaceRoot = Split-Path $PSScriptRoot -Parent
$portableJava = Join-Path $workspaceRoot '.tools\java'
if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath $portableJava)) {
    $env:JAVA_HOME = (Get-ChildItem -LiteralPath $portableJava -Directory | Select-Object -First 1).FullName
}
# Short, non-virtualized socket paths avoid the Windows MSIX Java NIO socket issue.
if ($IsWindows -or $env:OS -eq 'Windows_NT') {
    $socketDirectory = Join-Path $workspaceRoot '.tools\sockets'
    New-Item -ItemType Directory -Force -Path $socketDirectory | Out-Null
    $env:JAVA_TOOL_OPTIONS = (($env:JAVA_TOOL_OPTIONS + ' -Djdk.net.unixdomain.tmpdir=' + $socketDirectory).Trim())
}
Push-Location $PSScriptRoot
$savedErrorAction = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    if ($UnitOnly) { & .\gradlew.bat spotlessCheck test compileIntegrationTestJava bootJar }
    else { & .\gradlew.bat check bootJar }
    if ($LASTEXITCODE -ne 0) { throw "Backend verification failed (exit $LASTEXITCODE)." }
}
finally {
    $ErrorActionPreference = $savedErrorAction
    Pop-Location
}
