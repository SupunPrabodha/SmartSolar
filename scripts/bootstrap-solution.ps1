param([ValidateSet('Debug', 'Release')][string]$Configuration = 'Debug')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) { throw 'Install the .NET 8 SDK, then reopen your terminal.' }
    $sdks = & dotnet --list-sdks
    if ($LASTEXITCODE -ne 0 -or -not ($sdks -match '^8\.')) { throw 'The .NET 8 SDK is required.' }
    function Invoke-Dotnet([string[]]$Arguments) {
        & dotnet @Arguments
        if ($LASTEXITCODE -ne 0) { throw "dotnet $($Arguments -join ' ') failed (exit $LASTEXITCODE)." }
    }
    $projects = @(
        'src/SmartSolar.Domain/SmartSolar.Domain.csproj',
        'src/SmartSolar.Application/SmartSolar.Application.csproj',
        'src/SmartSolar.Infrastructure/SmartSolar.Infrastructure.csproj',
        'src/SmartSolar.Api/SmartSolar.Api.csproj',
        'tests/SmartSolar.UnitTests/SmartSolar.UnitTests.csproj',
        'tests/SmartSolar.IntegrationTests/SmartSolar.IntegrationTests.csproj'
    )
    foreach ($project in $projects) {
        if (-not (Test-Path -LiteralPath $project -PathType Leaf)) { throw "Required project missing: $project. Restore it from the starter/source control." }
    }
    $solution = 'SmartSolarMicrogrid.sln'
    if (-not (Test-Path -LiteralPath $solution)) {
        Invoke-Dotnet @('new', 'sln', '-n', 'SmartSolarMicrogrid')
    } else { Write-Host 'Keeping and validating the existing solution.' }
    Invoke-Dotnet @('sln', $solution, 'list')
    # The SDK add command is idempotent and does not duplicate existing entries.
    Invoke-Dotnet (@('sln', $solution, 'add') + $projects)
    Invoke-Dotnet @('restore', $solution)
    Invoke-Dotnet @('build', $solution, '--configuration', $Configuration, '--no-restore')
    Invoke-Dotnet @('test', $solution, '--configuration', $Configuration, '--no-build', '--no-restore', '--logger', 'console;verbosity=normal')
    Write-Host 'PASS: Solution restored, built and tested. Review any explicitly skipped integration tests above.'
} catch {
    Write-Host "FAIL: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally { Pop-Location }
