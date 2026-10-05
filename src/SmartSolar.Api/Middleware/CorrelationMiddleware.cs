/*
 * File: CorrelationMiddleware.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Liyanage S. P. (IT23187450)
 * Purpose: Assigns server-generated request references and correlated logging scopes.
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
