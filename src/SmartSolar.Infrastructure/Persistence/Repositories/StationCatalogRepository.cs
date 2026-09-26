/*
 * File: StationCatalogRepository.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Persists station and slot administration in the original collections.
 */
using System.Text.RegularExpressions;
using MongoDB.Bson;
using MongoDB.Driver;
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Domain.Constants;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;
namespace SmartSolar.Infrastructure.Persistence.Repositories;

public sealed class StationCatalogRepository : IStationCatalogRepository, IReservationReferenceReader
{
    private readonly IMongoCollection<SolarStation> _stations;
    private readonly IMongoCollection<EnergyBookingSlot> _slots;
    private readonly IMongoCollection<EnergyReservation> _reservations;
    public StationCatalogRepository(IMongoDatabase database)
    {
        // Reuse exact collection contracts; no new collection or reservation write path is introduced.
        _stations = database.GetCollection<SolarStation>(CollectionNames.Stations);
        _slots = database.GetCollection<EnergyBookingSlot>(CollectionNames.BookingSlots);
        _reservations = database.GetCollection<EnergyReservation>(CollectionNames.Reservations);
    }

    public async Task<IReadOnlyList<SolarStation>> ListStationsAsync(bool includeInactive, string? search, CancellationToken ct)
    {
        // Escape search input so it is literal text rather than a user-controlled regular expression.
        var f = Builders<SolarStation>.Filter;
        var filter = includeInactive ? f.Empty : f.Eq(x => x.IsActive, true);
        if (!string.IsNullOrWhiteSpace(search))
        {
            var text = new BsonRegularExpression(Regex.Escape(search), "i");
            filter &= f.Or(f.Regex(x => x.Name, text), f.Regex(x => x.Address, text));
        }
        return await _stations.Find(filter).SortBy(x => x.Name).ThenBy(x => x.StationId).ToListAsync(ct);
    }

    public async Task<SolarStation?> GetStationAsync(string id, CancellationToken ct)
    {
        // Read by the original mapped Mongo identifier.
        return await _stations.Find(x => x.StationId == id).FirstOrDefaultAsync(ct);
    }

    public async Task InsertStationAsync(SolarStation station, CancellationToken ct)
    {
        // Persist a server-generated station ID without altering related collections.
        await _stations.InsertOneAsync(station, cancellationToken: ct);
    }

    public async Task<bool> UpdateStationAsync(SolarStation station, DateTime expected, CancellationToken ct)
    {
        // Compare the persisted timestamp atomically and update only Member 1-owned fields.
        var update = Builders<SolarStation>.Update.Set(x => x.Name, station.Name).Set(x => x.Address, station.Address)
            .Set(x => x.Latitude, station.Latitude).Set(x => x.Longitude, station.Longitude)
            .Set(x => x.CapacityKwh, station.CapacityKwh).Set(x => x.TotalBatterySlots, station.TotalBatterySlots)
            .Set(x => x.OperatingSchedule, station.OperatingSchedule).Set(x => x.IsActive, station.IsActive)
            .Set(x => x.UpdatedAtUtc, station.UpdatedAtUtc);
        return (await _stations.UpdateOneAsync(x => x.StationId == station.StationId && x.UpdatedAtUtc == expected,
            update, cancellationToken: ct)).MatchedCount == 1;
    }

    public async Task<IReadOnlyList<EnergyBookingSlot>> ListSlotsAsync(string stationId, bool includeInactive, CancellationToken ct)
    {
        // Preserve inactive inventory for administrative history while discovery receives active inventory only.
        return await _slots.Find(x => x.StationId == stationId && (includeInactive || x.IsActive))
            .SortBy(x => x.StartAtUtc).ThenBy(x => x.SlotId).ToListAsync(ct);
    }

    public async Task<EnergyBookingSlot?> GetSlotAsync(string id, CancellationToken ct)
    {
        // Keep the immutable slot reference shared with reservations.
        return await _slots.Find(x => x.SlotId == id).FirstOrDefaultAsync(ct);
    }

    public async Task InsertSlotAsync(EnergyBookingSlot slot, CancellationToken ct)
    {
        // Store inventory only; reservation creation and availability allocation are not implemented here.
        await _slots.InsertOneAsync(slot, cancellationToken: ct);
    }

    public async Task<bool> UpdateSlotAsync(EnergyBookingSlot slot, DateTime expected, CancellationToken ct)
    {
        // Atomically reject stale slot updates and preserve the station reference and creation timestamp.
        var update = Builders<EnergyBookingSlot>.Update.Set(x => x.StartAtUtc, slot.StartAtUtc)
            .Set(x => x.EndAtUtc, slot.EndAtUtc).Set(x => x.TotalSlots, slot.TotalSlots)
            .Set(x => x.AvailableSlots, slot.AvailableSlots).Set(x => x.IsActive, slot.IsActive)
            .Set(x => x.UpdatedAtUtc, slot.UpdatedAtUtc);
        return (await _slots.UpdateOneAsync(x => x.SlotId == slot.SlotId && x.UpdatedAtUtc == expected,
            update, cancellationToken: ct)).MatchedCount == 1;
    }

    public async Task<bool> HasActiveStationReservationsAsync(string stationId, CancellationToken ct)
    {
        // Match this station and the frozen protection statuses without changing reservations.
        var filter = Builders<EnergyReservation>.Filter.Eq(x => x.StationId, stationId) & ActiveReservationFilter();
        return await _reservations.Find(filter).AnyAsync(ct);
    }

    public async Task<bool> HasActiveSlotReservationsAsync(string slotId, CancellationToken ct)
    {
        // Match this slot only; terminal reservation history does not block inventory changes.
        var filter = Builders<EnergyReservation>.Filter.Eq(x => x.SlotId, slotId) & ActiveReservationFilter();
        return await _reservations.Find(filter).AnyAsync(ct);
    }

    public async Task<decimal> ActiveAllocatedEnergyAsync(string stationId, CancellationToken ct)
    {
        // Station capacity covers every active Pending/Approved allocation at the station.
        var filter = Builders<EnergyReservation>.Filter.Eq(x => x.StationId, stationId) & ActiveReservationFilter();
        var active = await _reservations.Find(filter).ToListAsync(ct);
        return active.Sum(x => x.EnergyAmountKwh);
    }

    private static FilterDefinition<EnergyReservation> ActiveReservationFilter()
    {
        // Consume the team's frozen rule in both queries using the existing string-enum BSON mapping.
        return Builders<EnergyReservation>.Filter.In(x => x.Status,
            new[] { ReservationStatus.Pending, ReservationStatus.Approved });
    }
}
