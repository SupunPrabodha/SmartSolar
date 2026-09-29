/*
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Enterprise experience and operations security.
 */
using System.Security.Claims;
using SmartSolar.Application.Abstractions.Security;
namespace SmartSolar.Api.Security;

public sealed class RequestIdentity(IHttpContextAccessor accessor, ILogger<RequestIdentity> logger) : IRequestIdentity
{
    public string Nic => accessor.HttpContext?.User.FindFirstValue(ClaimTypes.NameIdentifier) ?? "";
    public string CorrelationId => accessor.HttpContext?.TraceIdentifier ?? Guid.NewGuid().ToString("N");
    public void DeliveryFailed(string operation) =>
        logger.LogWarning("Security email delivery failed for {Operation}; reference {CorrelationId}", operation, CorrelationId);
}
