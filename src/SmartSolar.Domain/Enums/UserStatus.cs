/*
 * File: UserStatus.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Smart Solar Development Team
 * Purpose: Defines the pending, active and deactivated account states.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

namespace SmartSolar.Domain.Enums;

public enum UserStatus
{
    PendingActivation,
    Active,
    Deactivated
}
