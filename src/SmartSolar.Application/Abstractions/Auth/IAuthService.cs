/*
 * File: IAuthService.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Wickramathilaka N. M. (IT23165434)
 * Purpose: Defines Prosumer registration and active-account sign-in operations.
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
