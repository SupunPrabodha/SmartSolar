/*
 * File: AppException.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Defines application-level exception types mapped to HTTP ProblemDetails responses.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

namespace SmartSolar.Application.Exceptions;

public abstract class AppException : Exception
{
    protected AppException(string message) : base(message)
    {
        // Preserve a business-safe error message for the global exception middleware.
    }
}

public sealed class NotFoundException : AppException
{
    public NotFoundException(string message) : base(message)
    {
        // Represent an application lookup that did not find the requested resource.
    }
}

public sealed class ConflictException : AppException
{
    public ConflictException(string message) : base(message)
    {
        // Represent a state or uniqueness conflict such as duplicate NIC/email data.
    }
}

public sealed class ForbiddenException : AppException
{
    public ForbiddenException(string message) : base(message)
    {
        // Represent an authenticated operation blocked by account state or business policy.
    }
}

public sealed class UnauthorizedException : AppException
{
    public UnauthorizedException(string message) : base(message)
    {
        // Represent failed authentication or missing authenticated identity information.
    }
}

public sealed class BadRequestException : AppException
{
    public BadRequestException(string message) : base(message)
    {
        // Represent invalid business input that cannot be processed by the requested operation.
    }
}
