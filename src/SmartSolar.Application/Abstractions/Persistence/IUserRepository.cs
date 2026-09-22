/*
 * File: IUserRepository.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Shared project source file for the SE4040 EAD implementation.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;

namespace SmartSolar.Application.Abstractions.Persistence;

public interface IUserRepository
{
    Task<User?> GetByNicAsync(string nic, CancellationToken cancellationToken = default);
    Task<User?> GetByEmailAsync(string email, CancellationToken cancellationToken = default);
    Task<IReadOnlyList<User>> GetAllAsync(CancellationToken cancellationToken = default);
    Task<IReadOnlyList<User>> GetByStatusAsync(UserStatus status, CancellationToken cancellationToken = default);
    Task InsertAsync(User user, CancellationToken cancellationToken = default);
    Task ReplaceAsync(User user, CancellationToken cancellationToken = default);
}
