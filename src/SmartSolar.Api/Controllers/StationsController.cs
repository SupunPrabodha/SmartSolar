/*
 * File: StationsController.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Exposes authenticated station discovery and Backoffice management.
 */
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SmartSolar.Application.DTOs.Stations;
using SmartSolar.Application.Exceptions;
using SmartSolar.Application.Services;
namespace SmartSolar.Api.Controllers;

[ApiController]
[Route("api/v1/stations")]
[Authorize(Roles = "Backoffice,GridOperator,Prosumer")]
public sealed class StationsController : ControllerBase
{
    private readonly StationService _stations;
    public StationsController(StationService stations)
    {
        // Delegate all station rules to the authoritative service.
        _stations = stations;
    }

    [HttpGet]
    public async Task<ActionResult<IReadOnlyList<StationResponse>>> List([FromQuery] bool includeInactive = false,
        [FromQuery] string? search = null, CancellationToken cancellationToken = default)
    {
        // Restrict inactive administrative records to staff.
        if (includeInactive && User.IsInRole("Prosumer")) throw new ForbiddenException("Inactive stations are staff-only.");
        return Ok(await _stations.ListAsync(includeInactive, search, cancellationToken));
    }

    [HttpGet("nearby")]
    public async Task<ActionResult<IReadOnlyList<NearbyStationResponse>>> Nearby([FromQuery] NearbyQuery query, CancellationToken cancellationToken)
    {
        // Use API geographic calculation; Google Maps only renders the returned station coordinates.
        return Ok(await _stations.NearbyAsync(query, cancellationToken));
    }

    [HttpGet("{id}")]
    public async Task<ActionResult<StationResponse>> Get(string id, CancellationToken cancellationToken)
    {
        // Staff may inspect inactive records; discovery clients may not.
        return Ok(await _stations.GetAsync(id, !User.IsInRole("Prosumer"), cancellationToken));
    }

    [HttpPost]
    [Authorize(Roles = "Backoffice")]
    public async Task<ActionResult<StationResponse>> Create(StationRequest request, CancellationToken cancellationToken)
    {
        // Create a station and identify its detail endpoint.
        var station = await _stations.CreateAsync(request, cancellationToken);
        return CreatedAtAction(nameof(Get), new { id = station.StationId }, station);
    }

    [HttpPut("{id}")]
    [Authorize(Roles = "Backoffice")]
    public async Task<ActionResult<StationResponse>> Update(string id, StationRequest request, CancellationToken cancellationToken)
    {
        // Forward only the editable station DTO; active state has a separate guarded operation.
        return Ok(await _stations.UpdateAsync(id, request, cancellationToken));
    }

    [HttpPatch("{id}/deactivate")]
    [Authorize(Roles = "Backoffice")]
    public async Task<IActionResult> Deactivate(string id, CatalogChangeRequest request, CancellationToken cancellationToken)
    {
        // Preserve station history and enforce the reservation-reference guard in the service.
        await _stations.DeactivateAsync(id, request, cancellationToken);
        return NoContent();
    }
}
