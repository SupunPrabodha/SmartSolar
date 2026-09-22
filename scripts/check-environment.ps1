$ErrorActionPreference = 'Continue'
$failed = $false
function Check-Tool([string]$Name, [string[]]$Arguments, [bool]$Required = $true) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        if ($Required) { Write-Host "FAIL: $Name is missing."; $script:failed = $true }
        else { Write-Host "WARNING: $Name is missing (needed for Android development)." }
        return $false
    }
    $output = & $Name @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "$(if ($Required) { 'FAIL' } else { 'WARNING' }): $Name could not run. $output"
        if ($Required) { $script:failed = $true }
        return $false
    }
    Write-Host "PASS: $Name - $($output -join ' ')"
    return $true
}
if (Check-Tool 'dotnet' @('--list-sdks')) {
    if (-not ((& dotnet --list-sdks) -match '^8\.')) { Write-Host 'FAIL: Install the .NET 8 SDK.'; $failed = $true }
    else { Write-Host 'PASS: .NET 8 SDK installed.' }
}
$null = Check-Tool 'git' @('--version')
if (Check-Tool 'node' @('--version')) {
    $version = [version]((& node --version).TrimStart('v'))
    if ($version -lt [version]'22.12.0') { Write-Host 'FAIL: Use Node.js 22.12 or newer for this foundation.'; $failed = $true }
}
$null = Check-Tool 'npm.cmd' @('--version')
if (Check-Tool 'docker' @('--version')) {
    $null = Check-Tool 'docker' @('compose', 'version')
    $server = & docker info --format '{{.ServerVersion}}' 2>&1
    if ($LASTEXITCODE -eq 0) { Write-Host "PASS: Docker daemon/server $server" }
    else {
        Write-Host 'WARNING: Docker CLI found, but Docker daemon is unavailable.'
        Write-Host 'Start Docker Desktop manually and wait until the engine is running. If it is already running, check Docker context and access permissions.'
    }
}
$null = Check-Tool 'java' @('-version') $false
$null = Check-Tool 'javac' @('-version') $false
$androidProject = Join-Path (Split-Path -Parent $PSScriptRoot) 'mobile/SmartSolarMobile'
if (Test-Path (Join-Path $androidProject 'gradlew.bat')) {
    Write-Host 'PASS: Native Android Gradle project exists at mobile/SmartSolarMobile.'
    Write-Host 'Android build requires JDK 17, SDK Platform 35 and Build-Tools 35.0.0. Open the existing project in Android Studio; do not generate another project.'
    if (-not (Test-Path (Join-Path $androidProject 'local.properties')) -and -not $env:ANDROID_HOME) {
        Write-Host 'WARNING: Configure the SDK in Android Studio (ignored local.properties) or set ANDROID_HOME.'
    }
} else { Write-Host 'FAIL: Android Gradle wrapper is missing.'; $failed = $true }
if ($failed) { exit 1 }
exit 0
