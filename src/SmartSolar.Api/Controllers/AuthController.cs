/*
 * File: AuthController.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Exposes public registration and login endpoints for API clients.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SmartSolar.Application.Abstractions.Auth;
using SmartSolar.Application.DTOs.Auth;
using SmartSolar.Application.DTOs.Users;

namespace SmartSolar.Api.Controllers;

[ApiController]
[Route("api/v1/auth")]
public sealed class AuthController : ControllerBase
{
    private readonly IAuthService _authService;

    public AuthController(IAuthService authService)
    {
        // Store the application service so the controller remains a thin HTTP adapter.
        _authService = authService;
    }

    [HttpPost("register-prosumer")]
    [AllowAnonymous]
    [ProducesResponseType(typeof(UserResponse), StatusCodes.Status201Created)]
    public async Task<ActionResult<UserResponse>> RegisterProsumer(
        [FromBody] RegisterProsumerRequest request,
        CancellationToken cancellationToken)
    {
        // Delegate Prosumer registration rules to the application service and return the created account DTO.
        var user = await _authService.RegisterProsumerAsync(request, cancellationToken);
        return StatusCode(StatusCodes.Status201Created, user);
    }

    [HttpPost("login")]
    [AllowAnonymous]
    [ProducesResponseType(typeof(LoginResponse), StatusCodes.Status200OK)]
    public async Task<ActionResult<LoginResponse>> Login(
        [FromBody] LoginRequest request,
        CancellationToken cancellationToken)
    {
        // Delegate credential/status validation to the application service and return the issued access token.
        return Ok(await _authService.LoginAsync(request, cancellationToken));
    }
}
