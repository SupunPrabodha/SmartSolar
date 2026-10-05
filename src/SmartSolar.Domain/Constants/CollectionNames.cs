/*
 * File: CollectionNames.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Smart Solar Development Team
 * Purpose: Defines the four required enterprise MongoDB collection names.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

namespace SmartSolar.Domain.Constants;

public static class CollectionNames
{
    public const string Users = "UsersDetail";
    public const string Stations = "SolarStationInfo";
    public const string BookingSlots = "EnergyBookingSlots";
    public const string Reservations = "EnergyReservation";
}
