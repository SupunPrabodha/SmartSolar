/*
 * File: IUserService.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Shared project source file for the SE4040 EAD implementation.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

using SmartSolar.Application.DTOs.Users;

namespace SmartSolar.Application.Abstractions.Users;

public interface IUserService
{
    Task<UserResponse> GetByNicAsync(string nic, CancellationToken cancellationToken = default);
    Task<IReadOnlyList<UserResponse>> GetAllAsync(CancellationToken cancellationToken = default);
    Task<IReadOnlyList<UserResponse>> GetPendingAsync(CancellationToken cancellationToken = default);
    Task<UserResponse> CreateStaffAsync(CreateStaffRequest request, CancellationToken cancellationToken = default);
    Task<UserResponse> UpdateOwnProfileAsync(string nic, UpdateOwnProfileRequest request, CancellationToken cancellationToken = default);
    Task RequestOwnDeactivationAsync(string nic, CancellationToken cancellationToken = default);
    Task ActivateAsync(string nic, CancellationToken cancellationToken = default);
    Task DeactivateAsync(string nic, CancellationToken cancellationToken = default);
}
