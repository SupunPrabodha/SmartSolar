/*
 * File: RequestIdentity.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Liyanage S. P. (IT23187450)
 * Purpose: Provides trusted request identity and safe security-mail failure logging.
 */
using System.Security.Claims;
using SmartSolar.Application.Abstractions.Security;
namespace SmartSolar.Api.Security;

public sealed class RequestIdentity(IHttpContextAccessor accessor, ILogger<RequestIdentity> logger) : IRequestIdentity
{
    public string Nic => accessor.HttpContext?.User.FindFirstValue(ClaimTypes.NameIdentifier) ?? "";
    public string CorrelationId => accessor.HttpContext?.TraceIdentifier ?? Guid.NewGuid().ToString("N");
    public void DeliveryFailed(string operation)
    {
        // Log only the operation and correlation reference when a security email fails.
        logger.LogWarning("Security email delivery failed for {Operation}; reference {CorrelationId}", operation, CorrelationId);
    }
}
