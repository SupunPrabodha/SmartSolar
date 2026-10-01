/*
 * File: ReservationDashboardSummaryResponse.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Exposes server-calculated reservation counts and their UTC comparison instant.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
namespace SmartSolar.Application.DTOs.Reservations;

public sealed record ReservationDashboardSummaryResponse(
    long PendingReservations, long ApprovedFutureReservations, DateTime GeneratedAtUtc);
