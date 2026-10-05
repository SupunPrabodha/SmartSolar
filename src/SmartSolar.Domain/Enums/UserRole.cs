/*
 * File: UserRole.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Smart Solar Development Team
 * Purpose: Defines the Backoffice, GridOperator and Prosumer authorization roles.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

namespace SmartSolar.Domain.Enums;

public enum UserRole
{
    Backoffice,
    GridOperator,
    Prosumer
}
