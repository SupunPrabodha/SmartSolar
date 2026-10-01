/*
 * File: IReservationQueryService.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Defines authorized booking views and dashboard reads for a trusted caller NIC.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.DTOs.Reservations;

namespace SmartSolar.Application.Abstractions.Reservations;

public interface IReservationQueryService
{
    Task<ReservationPageResponse> QueryAsync(string actorNic, ReservationReadView view, ReservationSearchRequest request, CancellationToken ct = default);
    Task<ReservationDashboardSummaryResponse> DashboardAsync(string actorNic, CancellationToken ct = default);
}
