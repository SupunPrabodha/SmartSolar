/*
 * File: ReservationDashboardSummaryResponse.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): ALAHAKOON A. W. A. C. N. (IT23163522)
 * Purpose: Exposes server-calculated reservation counts and their UTC comparison instant.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
namespace SmartSolar.Application.DTOs.Reservations;

public sealed record ReservationDashboardSummaryResponse(
    long PendingReservations, long ApprovedFutureReservations, DateTime GeneratedAtUtc);
