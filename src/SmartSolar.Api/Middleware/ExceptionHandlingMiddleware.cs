/*
 * File: ExceptionHandlingMiddleware.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Converts application exceptions into consistent RFC-style ProblemDetails responses.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using Microsoft.AspNetCore.Mvc;
using SmartSolar.Application.Exceptions;

namespace SmartSolar.Api.Middleware;

public sealed class ExceptionHandlingMiddleware
{
    private readonly RequestDelegate _next;
    private readonly ILogger<ExceptionHandlingMiddleware> _logger;

    public ExceptionHandlingMiddleware(RequestDelegate next, ILogger<ExceptionHandlingMiddleware> logger)
    {
        // Store the downstream pipeline and logger used by the global exception boundary.
        _next = next;
        _logger = logger;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        // Execute the next middleware and translate known application failures into HTTP errors.
        try
        {
            await _next(context);
        }
        catch (OperationCanceledException) when (context.RequestAborted.IsCancellationRequested)
        {
            // The client disconnected; do not turn cancellation into a server error.
        }
        catch (Exception exception) when (!context.Response.HasStarted)
        {
            await HandleExceptionAsync(context, exception);
        }
    }

    private async Task HandleExceptionAsync(HttpContext context, Exception exception)
    {
        // Map exception types to status codes and return a consistent problem-details payload.
        var (statusCode, title) = exception switch
        {
            BadRequestException => (StatusCodes.Status400BadRequest, "Bad request"),
            UnauthorizedException => (StatusCodes.Status401Unauthorized, "Unauthorized"),
            ForbiddenException => (StatusCodes.Status403Forbidden, "Forbidden"),
            NotFoundException => (StatusCodes.Status404NotFound, "Not found"),
            ConflictException => (StatusCodes.Status409Conflict, "Conflict"),
            _ => (StatusCodes.Status500InternalServerError, "Server error")
        };

        if (statusCode >= 500)
        {
            _logger.LogError(exception, "Unhandled exception while processing {Method} {Path}", context.Request.Method, context.Request.Path);
        }
        else
        {
            _logger.LogWarning(exception, "Request failed with {StatusCode} for {Method} {Path}", statusCode, context.Request.Method, context.Request.Path);
        }

        context.Response.StatusCode = statusCode;
        context.Response.ContentType = "application/problem+json";

        var problem = new ProblemDetails
        {
            Status = statusCode,
            Title = title,
            Detail = statusCode == StatusCodes.Status500InternalServerError
                ? "An unexpected server error occurred."
                : exception.Message,
            Instance = context.Request.Path
        };

        problem.Extensions["traceId"] = context.TraceIdentifier;
        await context.Response.WriteAsJsonAsync(problem, options: null, contentType: "application/problem+json", cancellationToken: context.RequestAborted);
    }
}
