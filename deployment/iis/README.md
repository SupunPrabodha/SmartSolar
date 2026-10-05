# IIS deployment and runtime acceptance

This is a reproducible **manual** deployment checklist for the existing .NET 8 API and React client. No IIS deployment has been performed by the rubric audit. Use a team-controlled Windows host, hostname/certificate and privately provisioned configuration.

## 1. Prepare the host

Install IIS and the .NET 8 ASP.NET Core Hosting Bundle after IIS. Use an isolated application pool with **No Managed Code**, matching the publish architecture, and read/execute access for its application-pool identity. Follow [Microsoft's IIS publishing instructions](https://learn.microsoft.com/en-us/aspnet/core/tutorials/publish-to-iis?tabs=net-cli&view=aspnetcore-8.0) and [IIS hosting configuration](https://learn.microsoft.com/en-us/aspnet/core/host-and-deploy/iis/?view=aspnetcore-8.0).

**Smart Solar requires one API process.** Set maximum worker processes to 1, disable overlapping recycling and do not run an additional API instance/service against the same database. The shared CatalogWriteGate is an in-process coordination boundary. A farm/web garden requires a separately reviewed coordination design.

Provision MongoDB connectivity and a reviewed initial active Backoffice account before acceptance. The existing development-only seed is not a production provisioning mechanism. Do not switch IIS to Development to bypass this requirement.

## 2. Build release artifacts

From the repository root on the build machine:

```powershell
dotnet restore .\SmartSolarMicrogrid.sln
dotnet publish .\src\SmartSolar.Api\SmartSolar.Api.csproj --configuration Release --output .\artifacts\iis-api
```

Check both exit codes. Copy the published output (including generated web.config) to the API site's physical directory while the application pool is stopped; start it after configuration. Keep the previous approved artifact for rollback. Publish output is not source code and must not contain local User Secrets or environment files.

## 3. Configure the API privately

Set application-pool environment settings through the host administrator's protected configuration. Use these existing keys; do not paste values into screenshots, source or the report:

| Key | Value to supply |
| --- | --- |
| ASPNETCORE_ENVIRONMENT | Production |
| MongoDb__ConnectionString | Team's authenticated server URI |
| MongoDb__DatabaseName | Approved enterprise database |
| Jwt__Issuer / Jwt__Audience | Nonempty deployed identity values |
| Jwt__Key | Privately generated strong signing key, at least 32 characters |
| Jwt__ExpiryMinutes | 1–1440, per approved deployment policy |
| Cors__AllowedOrigins__0 | Exact HTTPS Web origin, without a path |
| VerificationEmail__Host / Port | SMTP provider with STARTTLS (465 is rejected by current code) |
| VerificationEmail__Username / Password / From | Private SMTP credentials and valid sender address |
| VerificationEmail__VerificationPageUrl | Public HTTPS Web URL ending /verify-email |
| VerificationEmail__ResetPageUrl | Public HTTPS Web URL ending /reset-password |

Restart the pool after changing settings. Development User Secrets and launchSettings.json are not deployment configuration. Use a trusted HTTPS binding and certificate for both sites. Non-Development API requests retain HTTPS redirection. Restrict MongoDB network access to the API host; clients never connect to MongoDB.

## 4. Build and host Web

Separate HTTPS static IIS site, with the API at its own origin:

```powershell
Set-Location .\web\smart-solar-web
$env:VITE_API_BASE_URL = 'https://YOUR-API-HOST/api/v1'
try {
    npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
    npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Web build failed' }
} finally { Remove-Item Env:VITE_API_BASE_URL -ErrorAction SilentlyContinue }
```

Replace YOUR-API-HOST with the actual host. Copy dist contents to the Web site's physical directory. Configure IIS Static Content, index.html as default document and the IIS URL Rewrite module. The Web site's deployment-only web.config should fall back to index.html for React routes, while preserving real files:

```xml
<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <system.webServer>
    <rewrite>
      <rules>
        <rule name="SmartSolarClientRoutes" stopProcessing="true">
          <match url=".*" />
          <conditions logicalGrouping="MatchAll">
            <add input="{REQUEST_FILENAME}" matchType="IsFile" negate="true" />
            <add input="{REQUEST_FILENAME}" matchType="IsDirectory" negate="true" />
          </conditions>
          <action type="Rewrite" url="/index.html" />
        </rule>
      </rules>
    </rewrite>
  </system.webServer>
</configuration>
```

Apply this only to the static Web site, not the API site's generated web.config. Keep the existing public browser referrer behavior for OpenStreetMap tile attribution/policy compliance.

## 5. Android deployment configuration

From mobile/SmartSolarMobile, use the existing release property:

```powershell
.\gradlew.bat :app:assembleRelease "-PsmartSolarApiBaseUrl=https://YOUR-API-HOST/api/v1/"
```

Use private signing configuration managed by the team and a restricted Maps key for the actual package/signing SHA-1. Do not commit configured APKs, keystores or secrets. Release has no debug HTTP exception; never add a trust-all certificate handler.

## 6. MANUAL SUBMISSION ACTION REQUIRED

Record host/runtime versions, deployment date, reviewed revision and redacted evidence:

1. HTTPS API /health returns success with the intended Mongo database; HTTP redirects correctly. Swagger may be disabled in Production.
2. Reload /login, /verify-email, /reset-password and protected Web routes directly; confirm routing, assets, CORS and API errors.
3. Test each role, direct unauthorized URLs, expiry/401/logout and account switching.
4. Complete real SMTP approval + verification and recovery using disposable accounts.
5. Test station GPS/map persistence and Pending/Approved protection; create, modify, cancel, approve/reject and complete disposable bookings within the established rules.
6. Test Android trusted HTTPS, Maps/location, QR camera, server verification and replay protection.
7. Confirm one API worker/process and no parallel instance; capture safe IIS/site/health evidence.
8. Run hosted CI on the eventual manually reviewed commit and verify the submitted video/repository are accessible.

Do not infer IIS, email, device or cloud-key success from a local build. Rollback procedures must preserve data compatibility; do not delete enterprise collections or alter IDs to make a deployment pass.
