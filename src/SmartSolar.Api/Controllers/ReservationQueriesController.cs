/*
 * File: ReservationQueriesController.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Exposes Member 4 read-only booking views using the existing JWT authorization style.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SmartSolar.Api.Extensions;
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.Abstractions.Reservations;
using SmartSolar.Application.DTOs.Reservations;

namespace SmartSolar.Api.Controllers;

[ApiController]
[Route("api/v1/reservations")]
[Authorize(Roles = "Prosumer,GridOperator")]
[ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status400BadRequest)]
[ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status401Unauthorized)]
[ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status403Forbidden)]
[ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status409Conflict)]
public sealed class ReservationQueriesController(IReservationQueryService queries) : ControllerBase
{
    [HttpGet("current")]
    public async Task<ActionResult<ReservationPageResponse>> Current([FromQuery] ReservationSearchRequest request, CancellationToken ct)
    {
        // The service scopes current bookings to the authenticated owner or permitted operational role.
        return Ok(await queries.QueryAsync(User.GetNic(), ReservationReadView.Current, request, ct));
    }

    [HttpGet("pending")]
    public async Task<ActionResult<ReservationPageResponse>> Pending([FromQuery] ReservationSearchRequest request, CancellationToken ct)
    {
        // Pending filtering happens in MongoDB, including already elapsed Pending reservations.
        return Ok(await queries.QueryAsync(User.GetNic(), ReservationReadView.Pending, request, ct));
    }

    [HttpGet("history")]
    public async Task<ActionResult<ReservationPageResponse>> History([FromQuery] ReservationSearchRequest request, CancellationToken ct)
    {
        // Historical membership does not change the persisted lifecycle status.
        return Ok(await queries.QueryAsync(User.GetNic(), ReservationReadView.History, request, ct));
    }

    [HttpGet("search")]
    public async Task<ActionResult<ReservationPageResponse>> Search([FromQuery] ReservationSearchRequest request, CancellationToken ct)
    {
        // Only controlled exact filters and bounded pages reach the query service.
        return Ok(await queries.QueryAsync(User.GetNic(), ReservationReadView.Search, request, ct));
    }

    [HttpGet("dashboard-summary")]
    public async Task<ActionResult<ReservationDashboardSummaryResponse>> Dashboard(CancellationToken ct)
    {
        // Identity and the UTC comparison instant come exclusively from trusted server context.
        return Ok(await queries.DashboardAsync(User.GetNic(), ct));
    }
}
