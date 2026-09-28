/*
 * File: ReservationPageResponse.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Returns bounded pages using the existing reservation summary contract.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
namespace SmartSolar.Application.DTOs.Reservations;

public sealed record ReservationPageResponse(
    IReadOnlyList<ReservationResponse> Items, int Page, int PageSize, bool HasMore);
