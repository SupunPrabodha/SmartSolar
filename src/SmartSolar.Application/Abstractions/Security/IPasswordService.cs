/*
 * File: IPasswordService.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Shared project source file for the SE4040 EAD implementation.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

using SmartSolar.Domain.Entities;

namespace SmartSolar.Application.Abstractions.Security;

public interface IPasswordService
{
    string HashPassword(User user, string password);
    bool VerifyPassword(User user, string passwordHash, string providedPassword);
}
