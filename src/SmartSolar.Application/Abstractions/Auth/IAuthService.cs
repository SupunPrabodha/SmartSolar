/*
 * File: IAuthService.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Shared project source file for the SE4040 EAD implementation.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

using SmartSolar.Application.DTOs.Auth;
using SmartSolar.Application.DTOs.Users;

namespace SmartSolar.Application.Abstractions.Auth;

public interface IAuthService
{
    Task<UserResponse> RegisterProsumerAsync(RegisterProsumerRequest request, CancellationToken cancellationToken = default);
    Task<LoginResponse> LoginAsync(LoginRequest request, CancellationToken cancellationToken = default);
}
