/*
 * File: SlotsController.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Exposes GridOperator inventory operations and authenticated slot reads.
 */
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SmartSolar.Application.DTOs.Slots;
using SmartSolar.Application.DTOs.Stations;
using SmartSolar.Application.Exceptions;
using SmartSolar.Application.Services;
namespace SmartSolar.Api.Controllers;

[ApiController]
[Route("api/v1")]
[Authorize(Roles = "Backoffice,GridOperator,Prosumer")]
public sealed class SlotsController : ControllerBase
{
    private readonly SlotService _slots;
    public SlotsController(SlotService slots)
    {
        // Delegate inventory rules to the application service.
        _slots = slots;
    }

    [HttpGet("stations/{stationId}/slots")]
    public async Task<ActionResult<IReadOnlyList<SlotResponse>>> List(string stationId,
        [FromQuery] bool includeInactive = false, CancellationToken cancellationToken = default)
    {
        // Keep inactive records in staff views without exposing them as discoverable inventory.
        if (includeInactive && User.IsInRole("Prosumer")) throw new ForbiddenException("Inactive slots are staff-only.");
        return Ok(await _slots.ListAsync(stationId, includeInactive, cancellationToken));
    }

    [HttpGet("slots/{id}")]
    public async Task<ActionResult<SlotResponse>> Get(string id, CancellationToken cancellationToken)
    {
        // Return a single slot without exposing persistence entities.
        return Ok(await _slots.GetAsync(id, !User.IsInRole("Prosumer"), cancellationToken));
    }

    [HttpPost("stations/{stationId}/slots")]
    [Authorize(Roles = "GridOperator")]
    public async Task<ActionResult<SlotResponse>> Create(string stationId, SlotRequest request, CancellationToken cancellationToken)
    {
        // Only operational users create station inventory windows.
        var slot = await _slots.CreateAsync(stationId, request, cancellationToken);
        return CreatedAtAction(nameof(Get), new { id = slot.SlotId }, slot);
    }

    [HttpPut("slots/{id}")]
    [Authorize(Roles = "GridOperator")]
    public async Task<ActionResult<SlotResponse>> Update(string id, SlotRequest request, CancellationToken cancellationToken)
    {
        // Preserve shared IDs and enforce server-side capacity/time checks.
        return Ok(await _slots.UpdateAsync(id, request, cancellationToken));
    }

    [HttpPatch("slots/{id}/availability")]
    [Authorize(Roles = "GridOperator")]
    public async Task<ActionResult<SlotResponse>> Availability(string id, SlotAvailabilityRequest request, CancellationToken cancellationToken)
    {
        // Update operational inventory without creating a reservation.
        return Ok(await _slots.AvailabilityAsync(id, request, cancellationToken));
    }

    [HttpPatch("slots/{id}/deactivate")]
    [Authorize(Roles = "GridOperator")]
    public async Task<IActionResult> Deactivate(string id, CatalogChangeRequest request, CancellationToken cancellationToken)
    {
        // Soft-deactivate while keeping existing references intact.
        await _slots.DeactivateAsync(id, request, cancellationToken);
        return NoContent();
    }
}
