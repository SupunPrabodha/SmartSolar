/*
 * File: UsersController.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Exposes authenticated user/profile and Backoffice user-management endpoints.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SmartSolar.Api.Extensions;
using SmartSolar.Application.Abstractions.Users;
using SmartSolar.Application.DTOs.Users;

namespace SmartSolar.Api.Controllers;

[ApiController]
[Route("api/v1/users")]
[Authorize]
public sealed class UsersController : ControllerBase
{
    private readonly IUserService _userService;

    public UsersController(IUserService userService)
    {
        // Store the application service so user business rules remain outside the controller.
        _userService = userService;
    }

    [HttpGet("me")]
    public async Task<ActionResult<UserResponse>> GetMe(CancellationToken cancellationToken)
    {
        // Resolve the authenticated user's NIC claim and return the current profile.
        return Ok(await _userService.GetByNicAsync(User.GetNic(), cancellationToken));
    }

    [HttpPut("me")]
    public async Task<ActionResult<UserResponse>> UpdateMe(
        [FromBody] UpdateOwnProfileRequest request,
        CancellationToken cancellationToken)
    {
        // Update only the editable profile fields belonging to the authenticated user.
        return Ok(await _userService.UpdateOwnProfileAsync(User.GetNic(), request, cancellationToken));
    }

    [HttpPost("me/deactivation-request")]
    [Authorize(Roles = "Prosumer")]
    public async Task<IActionResult> RequestOwnDeactivation(CancellationToken cancellationToken)
    {
        // Deactivate the authenticated Prosumer while preserving Backoffice-only reactivation authority.
        await _userService.RequestOwnDeactivationAsync(User.GetNic(), cancellationToken);
        return NoContent();
    }

    [HttpGet]
    [Authorize(Roles = "Backoffice")]
    public async Task<ActionResult<IReadOnlyList<UserResponse>>> GetAll(CancellationToken cancellationToken)
    {
        // Return the complete user list for Backoffice administration.
        return Ok(await _userService.GetAllAsync(cancellationToken));
    }

    [HttpGet("pending")]
    [Authorize(Roles = "Backoffice")]
    public async Task<ActionResult<IReadOnlyList<UserResponse>>> GetPending(CancellationToken cancellationToken)
    {
        // Return the pending-activation queue for Backoffice action.
        return Ok(await _userService.GetPendingAsync(cancellationToken));
    }

    [HttpGet("{nic}")]
    [Authorize(Roles = "Backoffice")]
    public async Task<ActionResult<UserResponse>> GetByNic(string nic, CancellationToken cancellationToken)
    {
        // Return one user by NIC for Backoffice detail and management screens.
        return Ok(await _userService.GetByNicAsync(nic, cancellationToken));
    }

    [HttpPost("staff")]
    [Authorize(Roles = "Backoffice")]
    public async Task<ActionResult<UserResponse>> CreateStaff(
        [FromBody] CreateStaffRequest request,
        CancellationToken cancellationToken)
    {
        // Create a Backoffice or GridOperator account through the server-side user service.
        var user = await _userService.CreateStaffAsync(request, cancellationToken);
        return StatusCode(StatusCodes.Status201Created, user);
    }

    [HttpPatch("{nic}/activate")]
    [Authorize(Roles = "Backoffice")]
    public async Task<IActionResult> Activate(string nic, CancellationToken cancellationToken)
    {
        // Activate a pending or deactivated account through the Backoffice-only workflow.
        await _userService.ActivateAsync(nic, cancellationToken);
        return NoContent();
    }

    [HttpPatch("{nic}/deactivate")]
    [Authorize(Roles = "Backoffice")]
    public async Task<IActionResult> Deactivate(string nic, CancellationToken cancellationToken)
    {
        // Deactivate the selected account through the Backoffice administration workflow.
        await _userService.DeactivateAsync(nic, cancellationToken);
        return NoContent();
    }
}
