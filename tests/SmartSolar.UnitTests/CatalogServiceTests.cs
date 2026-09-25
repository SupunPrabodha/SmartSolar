/*
 * File: CatalogServiceTests.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Tests Member 1 validation, catalog state changes and nearby discovery.
 */
using System.Text.Json;
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.DTOs.Stations;
using SmartSolar.Application.DTOs.Slots;
using SmartSolar.Application.Exceptions;
using SmartSolar.Application.Services;
using SmartSolar.Domain.Entities;
using Xunit;
namespace SmartSolar.UnitTests;

public sealed class CatalogServiceTests
{
    private readonly MemoryCatalog _repo = new();
    private readonly StationService _stations;
    private readonly SlotService _slots;
    public CatalogServiceTests()
    {
        // Share the same write gate across both services as the API does.
        var gate = new CatalogWriteGate();
        _stations = new(_repo, _repo, gate); _slots = new(_repo, _repo, gate);
    }
    private static StationRequest Station(double lat = 6.9, double lon = 79.8, decimal capacity = 50,
        int total = 10, DateTimeOffset? expected = null, List<OperatingDayDto>? schedule = null)
    {
        // Supply a complete valid weekly schedule unless a test overrides it.
        return new() { Name = "Test node", Address = "Test road", Latitude = lat, Longitude = lon, CapacityKwh = capacity,
            TotalBatterySlots = total, ExpectedUpdatedAtUtc = expected,
            OperatingSchedule = schedule ?? Enumerable.Range(1, 7).Select(x => new OperatingDayDto(x, false, "00:00", "24:00")).ToList() };
    }
    private static SlotRequest Slot(int total = 5, int available = 3, int endHours = 1, DateTimeOffset? expected = null, int startHours = 0)
    {
        // Use fixed UTC fixture times; tests do not assert any reservation time window.
        var start = new DateTimeOffset(2030, 1, 1, 0, 0, 0, TimeSpan.Zero);
        return new() { StartAtUtc = start.AddHours(startHours), EndAtUtc = start.AddHours(endHours),
            TotalSlots = total, AvailableSlots = available, ExpectedUpdatedAtUtc = expected };
    }
    [Fact]
    public async Task CreateUpdateAndSoftDeactivatePreserveIdentity()
    {
        // Exercise the complete unreferenced station lifecycle without deleting records.
        var created = await _stations.CreateAsync(Station());
        Assert.True(created.IsActive); Assert.Equal(7, created.OperatingSchedule.Count);
        var updated = await _stations.UpdateAsync(created.StationId, Station(capacity: 60, expected: created.UpdatedAtUtc));
        Assert.Equal(created.StationId, updated.StationId); Assert.Equal(60, updated.CapacityKwh);
        Assert.Equal(created.CreatedAtUtc, updated.CreatedAtUtc); Assert.True(updated.UpdatedAtUtc > created.UpdatedAtUtc);
        await Assert.ThrowsAsync<ConflictException>(() => _stations.UpdateAsync(created.StationId, Station(expected: created.UpdatedAtUtc)));
        await _stations.DeactivateAsync(created.StationId, new() { ExpectedUpdatedAtUtc = updated.UpdatedAtUtc });
        Assert.Empty(await _stations.ListAsync(false, null)); Assert.Single(await _stations.ListAsync(true, null));
        await Assert.ThrowsAsync<NotFoundException>(() => _stations.GetAsync(created.StationId, false));
    }
    [Theory]
    [InlineData(91, 0, 1, 1)] [InlineData(-91, 0, 1, 1)]
    [InlineData(0, 181, 1, 1)] [InlineData(0, -181, 1, 1)]
    [InlineData(0, 0, 0, 1)] [InlineData(0, 0, -1, 1)]
    [InlineData(0, 0, 1, 0)] [InlineData(0, 0, 1, -1)]
    public async Task StationRejectsInvalidFields(double lat, double lon, int capacity, int total)
    {
        // Verify server-side range validation rather than relying on client forms.
        await Assert.ThrowsAsync<BadRequestException>(() => _stations.CreateAsync(Station(lat, lon, capacity, total)));
        Assert.Empty(_repo.Stations);
    }
    [Fact]
    public async Task MissingStationAndActiveReservationReferencesAreProtected()
    {
        // The service rejects missing stations and consumes the active-reservation reader result.
        await Assert.ThrowsAsync<NotFoundException>(() => _stations.GetAsync("missing", true));
        var station = await _stations.CreateAsync(Station()); _repo.StationHasActiveReservations = true;
        await Assert.ThrowsAsync<ConflictException>(() => _stations.DeactivateAsync(station.StationId, new() { ExpectedUpdatedAtUtc = station.UpdatedAtUtc }));
        Assert.True((await _stations.GetAsync(station.StationId, true)).IsActive);
    }
    [Theory]
    [InlineData("08:00", "08:00", false)] [InlineData("17:00", "08:00", false)]
    [InlineData("8:00", "17:00", false)] [InlineData("24:00", "24:00", false)]
    [InlineData("08:00", "17:00", true)] [InlineData(null, null, false)]
    public void ScheduleRejectsAmbiguousOrInconsistentHours(string? opens, string? closes, bool closed)
    {
        // A closed day must omit hours; overnight windows must be split across days.
        var schedule = Station().OperatingSchedule!; schedule[0] = new(1, closed, opens, closes);
        Assert.Throws<BadRequestException>(() => CatalogRules.Schedule(schedule));
    }
    [Fact]
    public void ScheduleRequiresEveryDayExactlyOnce()
    {
        // Reject both missing days and a duplicated weekday.
        var schedule = Station().OperatingSchedule!;
        schedule.RemoveAt(0); Assert.Throws<BadRequestException>(() => CatalogRules.Schedule(schedule));
        schedule.Add(schedule[0]); Assert.Throws<BadRequestException>(() => CatalogRules.Schedule(schedule));
    }
    [Fact]
    public async Task SlotsCreateEditAvailabilityDeactivateAndKeepReferences()
    {
        // Inventory edits retain station/slot identity and preserve optimistic concurrency.
        var station = await _stations.CreateAsync(Station());
        var slot = await _slots.CreateAsync(station.StationId, Slot());
        Assert.Equal(station.StationId, slot.StationId);
        var updated = await _slots.UpdateAsync(slot.SlotId, Slot(total: 6, expected: slot.UpdatedAtUtc));
        Assert.Equal(slot.SlotId, updated.SlotId);
        var available = await _slots.AvailabilityAsync(slot.SlotId, new() { AvailableSlots = 0, ExpectedUpdatedAtUtc = updated.UpdatedAtUtc });
        Assert.Equal(0, available.AvailableSlots);
        await Assert.ThrowsAsync<ConflictException>(() => _slots.AvailabilityAsync(slot.SlotId, new() { AvailableSlots = 1, ExpectedUpdatedAtUtc = updated.UpdatedAtUtc }));
        await _slots.DeactivateAsync(slot.SlotId, new() { ExpectedUpdatedAtUtc = available.UpdatedAtUtc });
        Assert.Empty(await _slots.ListAsync(station.StationId, false));
        Assert.Single(await _slots.ListAsync(station.StationId, true));
    }
    [Theory]
    [InlineData(0, 0, 1)] [InlineData(-1, 0, 1)] [InlineData(3, -1, 1)]
    [InlineData(3, 4, 1)] [InlineData(3, 1, 0)] [InlineData(3, 1, -1)]
    public async Task SlotsRejectInvalidTimesAndCounts(int total, int available, int endHours)
    {
        // Validate all inventory bounds through the actual service.
        var station = await _stations.CreateAsync(Station());
        await Assert.ThrowsAsync<BadRequestException>(() => _slots.CreateAsync(station.StationId, Slot(total, available, endHours)));
        Assert.Empty(_repo.Slots);
    }
    [Fact]
    public async Task SlotsRequireExistingActiveStationAndRespectCapacity()
    {
        // An inactive station cannot acquire usable inventory.
        await Assert.ThrowsAsync<NotFoundException>(() => _slots.CreateAsync("missing", Slot()));
        var station = await _stations.CreateAsync(Station());
        await Assert.ThrowsAsync<ConflictException>(() => _slots.CreateAsync(station.StationId, Slot(total: 11)));
        await _stations.DeactivateAsync(station.StationId, new() { ExpectedUpdatedAtUtc = station.UpdatedAtUtc });
        await Assert.ThrowsAsync<ConflictException>(() => _slots.CreateAsync(station.StationId, Slot()));
    }
    [Fact]
    public async Task OverlapCapacityReductionAndReferencedInventoryFailWithoutPersisting()
    {
        // Guard related inventory, allow touching endpoints, and preserve failed-update state.
        var station = await _stations.CreateAsync(Station());
        var slot = await _slots.CreateAsync(station.StationId, Slot());
        await Assert.ThrowsAsync<ConflictException>(() => _slots.CreateAsync(station.StationId, Slot()));
        await _slots.CreateAsync(station.StationId, Slot(startHours: 1, endHours: 2));
        await Assert.ThrowsAsync<ConflictException>(() => _stations.UpdateAsync(station.StationId, Station(total: 4, expected: station.UpdatedAtUtc)));
        Assert.Equal(10, (await _stations.GetAsync(station.StationId, true)).TotalBatterySlots);
        _repo.SlotHasActiveReservations = true;
        await Assert.ThrowsAsync<ConflictException>(() => _slots.UpdateAsync(slot.SlotId, Slot(expected: slot.UpdatedAtUtc)));
        await Assert.ThrowsAsync<ConflictException>(() => _slots.AvailabilityAsync(slot.SlotId, new() { AvailableSlots = 2, ExpectedUpdatedAtUtc = slot.UpdatedAtUtc }));
        await Assert.ThrowsAsync<ConflictException>(() => _slots.DeactivateAsync(slot.SlotId, new() { ExpectedUpdatedAtUtc = slot.UpdatedAtUtc }));
        Assert.Equal(3, (await _slots.GetAsync(slot.SlotId, true)).AvailableSlots);
    }
    [Fact]
    public async Task NearbyFiltersRadiusInactiveAndOrdersNearestFirst()
    {
        // Real geographic coordinates determine distance; inactive data is never returned.
        var far = await _stations.CreateAsync(Station(lat: 0.1, lon: 0));
        var near = await _stations.CreateAsync(Station(lat: 0.01, lon: 0));
        await _stations.CreateAsync(Station(lat: 10, lon: 0));
        var inactive = await _stations.CreateAsync(Station(lat: 0, lon: 0));
        await _stations.DeactivateAsync(inactive.StationId, new() { ExpectedUpdatedAtUtc = inactive.UpdatedAtUtc });
        var result = await _stations.NearbyAsync(new() { Latitude = 0, Longitude = 0, RadiusKm = 20 });
        Assert.Equal(new[] { near.StationId, far.StationId }, result.Select(x => x.Station.StationId));
        Assert.InRange(result[0].DistanceKm, 1.11, 1.12);
        Assert.InRange(CatalogRules.DistanceKm(0, 179.9, 0, -179.9), 22.2, 22.3);
    }
    [Theory]
    [InlineData(91, 0, 25)] [InlineData(0, 181, 25)] [InlineData(0, 0, 0)]
    [InlineData(0, 0, 501)] [InlineData(double.NaN, 0, 25)] [InlineData(0, 0, double.PositiveInfinity)]
    public async Task NearbyRejectsInvalidCoordinatesOrRadius(double lat, double lon, double radius)
    {
        // Reject nonfinite geographic values as well as ordinary out-of-range input.
        await Assert.ThrowsAsync<BadRequestException>(() => _stations.NearbyAsync(new() { Latitude = lat, Longitude = lon, RadiusKm = radius }));
    }
}

// Test storage copies records to mirror persistence boundaries and avoid false positives from shared references.
internal sealed class MemoryCatalog : IStationCatalogRepository, IReservationReferenceReader
{
    internal readonly Dictionary<string, SolarStation> Stations = new();
    internal readonly Dictionary<string, EnergyBookingSlot> Slots = new();
    internal bool StationHasActiveReservations, SlotHasActiveReservations;
    private static T Copy<T>(T value)
    {
        // Isolate returned records from the stored document.
        return JsonSerializer.Deserialize<T>(JsonSerializer.Serialize(value))!;
    }
    public Task<IReadOnlyList<SolarStation>> ListStationsAsync(bool inactive, string? search, CancellationToken ct)
    {
        // Mimic the active filter used by nearby discovery.
        return Task.FromResult<IReadOnlyList<SolarStation>>(Stations.Values.Where(x => inactive || x.IsActive).Select(Copy).ToArray());
    }
    public Task<SolarStation?> GetStationAsync(string id, CancellationToken ct)
    {
        // Return a detached copy or a missing-record result.
        return Task.FromResult(Stations.TryGetValue(id, out var value) ? Copy(value) : null);
    }
    public Task InsertStationAsync(SolarStation value, CancellationToken ct)
    {
        // Store an isolated document for mutation assertions.
        Stations.Add(value.StationId, Copy(value)); return Task.CompletedTask;
    }
    public Task<bool> UpdateStationAsync(SolarStation value, DateTime expected, CancellationToken ct)
    {
        // Match the repository compare-and-update condition.
        var match = Stations[value.StationId].UpdatedAtUtc == expected;
        if (match) Stations[value.StationId] = Copy(value); return Task.FromResult(match);
    }
    public Task<IReadOnlyList<EnergyBookingSlot>> ListSlotsAsync(string id, bool inactive, CancellationToken ct)
    {
        // Scope all slot reads to the original station reference.
        return Task.FromResult<IReadOnlyList<EnergyBookingSlot>>(Slots.Values.Where(x => x.StationId == id && (inactive || x.IsActive)).Select(Copy).ToArray());
    }
    public Task<EnergyBookingSlot?> GetSlotAsync(string id, CancellationToken ct)
    {
        // Prevent failed validation from mutating persisted inventory in memory.
        return Task.FromResult(Slots.TryGetValue(id, out var value) ? Copy(value) : null);
    }
    public Task InsertSlotAsync(EnergyBookingSlot value, CancellationToken ct)
    {
        // Preserve caller-generated identity.
        Slots.Add(value.SlotId, Copy(value)); return Task.CompletedTask;
    }
    public Task<bool> UpdateSlotAsync(EnergyBookingSlot value, DateTime expected, CancellationToken ct)
    {
        // Return false when a concurrent write already advanced the timestamp.
        var match = Slots[value.SlotId].UpdatedAtUtc == expected;
        if (match) Slots[value.SlotId] = Copy(value); return Task.FromResult(match);
    }
    public Task<bool> HasActiveStationReservationsAsync(string id, CancellationToken ct)
    {
        // Simulate the active protection query; status filtering is tested against the real Mongo repository.
        return Task.FromResult(StationHasActiveReservations);
    }
    public Task<bool> HasActiveSlotReservationsAsync(string id, CancellationToken ct)
    {
        // Simulate an active slot reference without implementing reservation operations.
        return Task.FromResult(SlotHasActiveReservations);
    }
}
