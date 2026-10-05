/*
 * File: ReservationStatus.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): RAMANAYAKE R. H. B. D. G. (IT23164130)
 * Purpose: Defines the persisted reservation lifecycle status values.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

namespace SmartSolar.Domain.Enums;

public enum ReservationStatus
{
    Pending,
    Approved,
    Rejected,
    Cancelled,
    Completed
}
