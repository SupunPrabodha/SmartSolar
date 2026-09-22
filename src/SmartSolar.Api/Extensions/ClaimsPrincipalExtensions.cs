/*
 * File: ClaimsPrincipalExtensions.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Reads common authenticated-user claims from the current request principal.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using System.Security.Claims;
using SmartSolar.Application.Exceptions;

namespace SmartSolar.Api.Extensions;

public static class ClaimsPrincipalExtensions
{
    public static string GetNic(this ClaimsPrincipal principal)
    {
        // Read the NIC stored as the NameIdentifier claim when the JWT was issued.
        return principal.FindFirstValue(ClaimTypes.NameIdentifier)
               ?? throw new UnauthorizedException("Authenticated user identifier is missing.");
    }
}
