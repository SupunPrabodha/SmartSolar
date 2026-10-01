/*
 * File: EmailVerificationController.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Consumes approved Prosumer email-verification links without granting a login session.
 */
using System.ComponentModel.DataAnnotations;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SmartSolar.Application.Abstractions.Users;

namespace SmartSolar.Api.Controllers;

[ApiController]
[Route("api/v1/auth/verify-email")]
public sealed class EmailVerificationController(IUserService users) : ControllerBase
{
    [HttpPost]
    [AllowAnonymous]
    public async Task<IActionResult> Verify(VerifyEmailRequest request, CancellationToken cancellationToken)
    {
        // Explicit POST confirmation prevents mail-link scanners from activating an account on GET.
        Response.Headers.CacheControl = "no-store";
        await users.VerifyEmailAsync(request.Nic, request.Token, cancellationToken);
        return NoContent();
    }
}

public sealed record VerifyEmailRequest(
    [Required, StringLength(20)] string Nic,
    [Required, StringLength(64, MinimumLength = 64)] string Token);
