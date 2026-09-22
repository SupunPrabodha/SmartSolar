/*
 * File: MongoMappings.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Keeps MongoDB document mappings outside the dependency-free domain.
 */
using MongoDB.Bson;
using MongoDB.Bson.Serialization;
using MongoDB.Bson.Serialization.Serializers;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;

namespace SmartSolar.Infrastructure.Persistence;

public static class MongoMappings
{
    private static readonly Lazy<bool> Registration = new(() =>
    {
        // Register once before any repository serializes a domain entity.
        BsonClassMap.RegisterClassMap<User>(map =>
        {
            map.AutoMap();
            map.MapIdMember(x => x.Nic);
            map.MapMember(x => x.Role).SetSerializer(new EnumSerializer<UserRole>(BsonType.String));
            map.MapMember(x => x.Status).SetSerializer(new EnumSerializer<UserStatus>(BsonType.String));
        });
        BsonClassMap.RegisterClassMap<SolarStation>(map => { map.AutoMap(); map.MapIdMember(x => x.StationId); });
        BsonClassMap.RegisterClassMap<EnergyBookingSlot>(map => { map.AutoMap(); map.MapIdMember(x => x.SlotId); });
        BsonClassMap.RegisterClassMap<EnergyReservation>(map =>
        {
            map.AutoMap();
            map.MapIdMember(x => x.ReservationId);
            map.MapMember(x => x.Status).SetSerializer(new EnumSerializer<ReservationStatus>(BsonType.String));
        });
        return true;
    });

    public static void Register()
    {
        // Lazy initialization makes registration safe across concurrent application/test hosts.
        _ = Registration.Value;
    }
}
