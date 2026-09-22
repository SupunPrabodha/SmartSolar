# IIS Deployment

The production API will be published using `dotnet publish` and hosted behind Windows IIS with the ASP.NET Core Hosting Bundle installed.

Production secrets must be supplied through IIS/environment variables, not committed files.

Expected configuration keys:

- `MongoDb__ConnectionString`
- `MongoDb__DatabaseName`
- `Jwt__Issuer`
- `Jwt__Audience`
- `Jwt__Key`
- `Jwt__ExpiryMinutes`

## HTTPS environment boundary

Use Production (never Development) for IIS hosting. The API retains HTTPS redirection in every non-Development environment; configure the site's HTTPS binding/certificate and redirect destination as part of IIS hosting.

Development deliberately skips HTTP-to-HTTPS redirection so the Android emulator can call `http://10.0.2.2:5000/api/v1/` without trusting the Windows host development certificate. The existing dual-URL development profile also serves React at `https://localhost:7001/api/v1`. This development exception does not remove HTTPS listeners or relax Android release TLS validation.