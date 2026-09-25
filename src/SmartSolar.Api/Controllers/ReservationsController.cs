/*
 * File: ReservationsController.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Exposes authenticated Member 3 reservation lifecycle operations.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SmartSolar.Api.Extensions;
using SmartSolar.Application.Abstractions.Reservations;
using SmartSolar.Application.DTOs.Reservations;

namespace SmartSolar.Api.Controllers;

[ApiController]
[Route("api/v1/reservations")]
[Authorize(Roles = "Prosumer,GridOperator")]
public sealed class ReservationsController : ControllerBase
{
    private readonly IReservationService _reservations;

    public ReservationsController(IReservationService reservations)
    {
        // Application services own authorization, timing, state and persistence rules.
        _reservations = reservations;
    }

    [HttpGet]
    [Authorize(Roles = "GridOperator")]
    public async Task<ActionResult<IReadOnlyList<ReservationResponse>>> List(
        [FromQuery] ListReservationsRequest request, CancellationToken cancellationToken)
    {
        // Limit operational listing to active GridOperators and server-validated exact filters.
        return Ok(await _reservations.ListAsync(User.GetNic(), request, cancellationToken));
    }

    [HttpPost]
    [Authorize(Roles = "Prosumer")]
    public async Task<ActionResult<ReservationResponse>> Create(
        [FromBody] CreateReservationRequest request, CancellationToken cancellationToken)
    {
        // Only the authenticated NIC can own a self-service booking.
        var reservation = await _reservations.CreateAsync(User.GetNic(), request, cancellationToken);
        return CreatedAtAction(nameof(GetById), new { reservationId = reservation.ReservationId }, reservation);
    }

    [HttpPost("prosumers/{prosumerNic}")]
    [Authorize(Roles = "GridOperator")]
    public async Task<ActionResult<ReservationResponse>> CreateFor(
        string prosumerNic, [FromBody] CreateReservationRequest request, CancellationToken cancellationToken)
    {
        // The explicit assistance route still applies all Prosumer and booking validation.
        var reservation = await _reservations.CreateForAsync(User.GetNic(), prosumerNic, request, cancellationToken);
        return CreatedAtAction(nameof(GetById), new { reservationId = reservation.ReservationId }, reservation);
    }

    [HttpGet("{reservationId}")]
    public async Task<ActionResult<ReservationResponse>> GetById(string reservationId, CancellationToken cancellationToken)
    {
        // The service checks current account state and ownership before exposing a summary.
        return Ok(await _reservations.GetAsync(User.GetNic(), reservationId, cancellationToken));
    }

    [HttpPut("{reservationId}")]
    public async Task<ActionResult<ReservationResponse>> Update(
        string reservationId, [FromBody] UpdateReservationRequest request, CancellationToken cancellationToken)
    {
        // Owning Prosumers and assisting operators share the same application rules.
        return Ok(await _reservations.UpdateAsync(User.GetNic(), reservationId, request, cancellationToken));
    }

    [HttpPatch("{reservationId}/cancel")]
    public async Task<ActionResult<ReservationResponse>> Cancel(string reservationId, CancellationToken cancellationToken)
    {
        // Cancellation is a guarded state transition, not document deletion.
        return Ok(await _reservations.CancelAsync(User.GetNic(), reservationId, cancellationToken));
    }

    [HttpPost("{reservationId}/qr")]
    public async Task<ActionResult<ReservationQrResponse>> IssueQr(string reservationId, CancellationToken cancellationToken)
    {
        // Issues a cryptographically strong opaque reference for an approved reservation.
        return Ok(await _reservations.IssueQrAsync(User.GetNic(), reservationId, cancellationToken));
    }

    [HttpPost("qr/verify")]
    [Authorize(Roles = "GridOperator")]
    public async Task<ActionResult<ReservationVerificationResponse>> VerifyQr(
        [FromBody] VerifyReservationQrRequest request, CancellationToken cancellationToken)
    {
        // Verifies the scanned opaque reference against current authoritative server state.
        return Ok(await _reservations.VerifyQrAsync(User.GetNic(), request, cancellationToken));
    }

    [HttpPost("qr/complete")]
    [Authorize(Roles = "GridOperator")]
    public async Task<ActionResult<ReservationCompletionResponse>> CompleteTransfer(
        [FromBody] CompleteReservationTransferRequest request, CancellationToken cancellationToken)
    {
        // Authoritatively completes the energy transfer for a verified Approved reservation.
        return Ok(await _reservations.CompleteTransferAsync(User.GetNic(), request, cancellationToken));
    }

    [HttpPost("{reservationId}/complete")]
    [Authorize(Roles = "GridOperator")]
    public async Task<ActionResult<ReservationCompletionResponse>> CompleteTransferById(
        string reservationId, [FromBody] CompleteReservationTransferRequest? request, CancellationToken cancellationToken)
    {
        // Authoritatively completes the energy transfer identified by reservationId and scanned QR payload.
        var payload = request?.QrPayload ?? string.Empty;
        return Ok(await _reservations.CompleteTransferAsync(
            User.GetNic(), new CompleteReservationTransferRequest(payload, reservationId), cancellationToken));
    }
}
