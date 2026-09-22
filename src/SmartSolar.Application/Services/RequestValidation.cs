/*
 * File: RequestValidation.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Applies shared DTO constraints at the authoritative application boundary.
 */
using System.ComponentModel.DataAnnotations;
using SmartSolar.Application.Exceptions;

namespace SmartSolar.Application.Services;

internal static class RequestValidation
{
    public static void EnsureValid(object request)
    {
        // Enforce constraints for all service callers, including callers outside MVC controllers.
        var errors = new List<ValidationResult>();
        if (!Validator.TryValidateObject(request, new ValidationContext(request), errors, validateAllProperties: true))
            throw new BadRequestException(string.Join(" ", errors.Select(x => x.ErrorMessage)));
    }
}
