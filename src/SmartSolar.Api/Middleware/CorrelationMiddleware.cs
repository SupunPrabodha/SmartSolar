/*
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Enterprise experience and operations security.
 */
namespace SmartSolar.Api.Middleware;

public sealed class CorrelationMiddleware(RequestDelegate next, ILogger<CorrelationMiddleware> logger)
{
    public async Task InvokeAsync(HttpContext context)
    {
        // Server-generated reference: a caller cannot inject log scope text or forge another request's ID.
        context.TraceIdentifier = Guid.NewGuid().ToString("N");
        context.Response.Headers["X-Correlation-ID"] = context.TraceIdentifier;
        using (logger.BeginScope(new Dictionary<string, object> { ["CorrelationId"] = context.TraceIdentifier }))
            await next(context);
    }
}
