/*
 * File: IReservationService.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Defines reservation use cases for a trusted authenticated caller identity.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using SmartSolar.Application.DTOs.Reservations;

namespace SmartSolar.Application.Abstractions.Reservations;

// actorNic must come from authenticated server context, never a request body.
public interface IReservationService
{
    Task<ReservationResponse> CreateAsync(string actorNic, CreateReservationRequest request, CancellationToken ct = default);
    Task<ReservationResponse> CreateForAsync(string actorNic, string prosumerNic, CreateReservationRequest request, CancellationToken ct = default);
    Task<ReservationResponse> GetAsync(string actorNic, string reservationId, CancellationToken ct = default);
    Task<ReservationResponse> UpdateAsync(string actorNic, string reservationId, UpdateReservationRequest request, CancellationToken ct = default);
    Task<ReservationResponse> CancelAsync(string actorNic, string reservationId, CancellationToken ct = default);
}

