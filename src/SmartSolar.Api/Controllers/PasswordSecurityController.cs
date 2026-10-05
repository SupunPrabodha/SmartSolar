/*
 * File: PasswordSecurityController.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Liyanage S. P. (IT23187450)
 * Purpose: Exposes anonymous recovery and authenticated password-change endpoints.
 */
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.RateLimiting;
using SmartSolar.Application.DTOs.Auth;
using SmartSolar.Application.Services;
namespace SmartSolar.Api.Controllers;

[ApiController]
public sealed class PasswordSecurityController(PasswordSecurityService security, SmartSolar.Api.Configuration.PasswordRecoveryQueue queue) : ControllerBase
{
    [HttpPost("api/v1/auth/forgot-password"), AllowAnonymous, EnableRateLimiting("authentication")]
    public IActionResult Forgot(ForgotPasswordRequest request)
    {
        // Queue recovery work and acknowledge it without disclosing account existence.
        if (!queue.TryQueue(request.Identifier, HttpContext.TraceIdentifier))
            return StatusCode(429, new ProblemDetails { Status = 429, Title = "Please try again shortly.", Extensions = { ["correlationId"] = HttpContext.TraceIdentifier, ["traceId"] = HttpContext.TraceIdentifier } });
        return Ok(new { message = PasswordSecurityService.GenericResponse });
    }
    [HttpPost("api/v1/auth/reset-password"), AllowAnonymous, EnableRateLimiting("authentication")]
    public async Task<IActionResult> Reset(ResetPasswordRequest request, CancellationToken ct)
    {
        // Apply the one-use password reset and require a fresh sign-in.
        await security.ResetAsync(request, ct);
        return Ok(new { message = "Password reset successfully. Sign in with your new password." });
    }
    [HttpPost("api/v1/users/me/change-password"), Authorize, EnableRateLimiting("authentication")]
    public async Task<IActionResult> Change(ChangePasswordRequest request, CancellationToken ct)
    {
        // Verify the current password and revoke prior sessions after the change.
        await security.ChangeAsync(request, ct);
        return Ok(new { message = "Password changed. Sign in again." });
    }
}
