$ErrorActionPreference = 'Stop'
$previousOutputEncoding = $OutputEncoding
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) { throw 'Install the .NET 8 SDK first.' }
    function Read-PrivateValue([string]$Prompt) {
        $secure = Read-Host $Prompt -AsSecureString
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secure.Dispose() }
    }
    $values = @{}
    $values['Jwt:Key'] = Read-PrivateValue 'JWT signing key (at least 32 characters, generated securely)'
    if ([string]::IsNullOrWhiteSpace($values['Jwt:Key']) -or $values['Jwt:Key'].Length -lt 32) { throw 'JWT key must contain at least 32 characters.' }
    $values['SeedAdmin:Nic'] = (Read-Host 'Seed Backoffice NIC').Trim().ToUpperInvariant()
    if ($values['SeedAdmin:Nic'] -notmatch '^([0-9]{9}[VX]|[0-9]{12})$') { throw 'NIC must be 12 digits or 9 digits followed by V/X.' }
    $values['SeedAdmin:Password'] = Read-PrivateValue 'Seed Backoffice password (8-100 characters)'
    if ($values['SeedAdmin:Password'].Length -lt 8 -or $values['SeedAdmin:Password'].Length -gt 100) { throw 'Password must contain 8-100 characters.' }
    $values['SeedAdmin:Email'] = (Read-Host 'Seed Backoffice email').Trim()
    $values['SeedAdmin:FullName'] = (Read-Host 'Seed Backoffice full name').Trim()
    $values['SeedAdmin:PhoneNumber'] = (Read-Host 'Seed Backoffice phone number').Trim()
    foreach ($value in $values.Values) { if ([string]::IsNullOrWhiteSpace($value)) { throw 'All values are required.' } }
    if ($values['SeedAdmin:Email'] -notmatch '^[^\s@]+@[^\s@]+$') { throw 'Enter a valid email address.' }
    if ($values['SeedAdmin:FullName'].Length -lt 2 -or $values['SeedAdmin:FullName'].Length -gt 120) { throw 'Full name must contain 2-120 characters.' }
    if ($values['SeedAdmin:PhoneNumber'].Length -lt 7 -or $values['SeedAdmin:PhoneNumber'].Length -gt 20) { throw 'Phone number must contain 7-20 characters.' }
    # Send JSON on stdin so credentials are not included in process arguments or terminal output.
    $values | ConvertTo-Json -Compress | & dotnet user-secrets set --project 'src/SmartSolar.Api/SmartSolar.Api.csproj' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Saving User Secrets failed (exit $LASTEXITCODE)." }
    Write-Host 'PASS: Development secrets saved outside the repository. User Secrets are local development storage, not an encrypted production vault.'
} catch { Write-Host "FAIL: $($_.Exception.Message)"; exit 1 }
finally { $values = $null; $OutputEncoding = $previousOutputEncoding; Pop-Location }
