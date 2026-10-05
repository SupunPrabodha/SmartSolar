/*
 * File: IUserService.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Wickramathilaka N. M. (IT23165434)
 * Purpose: Defines profile management, staff creation and Prosumer approval/verification operations.
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
    Task<UserResponse> UpdateProsumerAsync(string nic, UpdateProsumerRequest request, CancellationToken cancellationToken = default);
    Task RequestOwnDeactivationAsync(string nic, CancellationToken cancellationToken = default);
    Task ActivateAsync(string nic, CancellationToken cancellationToken = default);
    Task VerifyEmailAsync(string nic, string token, CancellationToken cancellationToken = default);
    Task DeactivateAsync(string nic, CancellationToken cancellationToken = default);
}
