/*
 * File: StationService.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Owns station validation and geographic discovery, preserving reservation ownership.
 */
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.DTOs.Stations;
using SmartSolar.Application.Exceptions;
using SmartSolar.Domain.Entities;
namespace SmartSolar.Application.Services;

public sealed class StationService
{
    private readonly IStationCatalogRepository _catalog;
    private readonly IReservationReferenceReader _references;
    private readonly CatalogWriteGate _gate;

    public StationService(IStationCatalogRepository catalog, IReservationReferenceReader references, CatalogWriteGate gate)
    {
        // Keep persistence behind interfaces and coordinate local catalog writes.
        _catalog = catalog; _references = references; _gate = gate;
    }

    public async Task<IReadOnlyList<StationResponse>> ListAsync(bool includeInactive, string? search, CancellationToken ct = default)
    {
        // Bound input and search authoritative station data.
        if (search?.Length > 120) throw new BadRequestException("Search must not exceed 120 characters.");
        return (await _catalog.ListStationsAsync(includeInactive, search?.Trim(), ct)).Select(CatalogRules.ToResponse).ToArray();
    }

    public async Task<StationResponse> GetAsync(string id, bool includeInactive, CancellationToken ct = default)
    {
        // Hide inactive station details from discovery clients.
        var station = await RequiredAsync(id, ct);
        if (!includeInactive && !station.IsActive) throw new NotFoundException("Station not found.");
        return CatalogRules.ToResponse(station);
    }

    public async Task<IReadOnlyList<NearbyStationResponse>> NearbyAsync(NearbyQuery query, CancellationToken ct = default)
    {
        // Return only real active stations inside the requested radius, nearest first.
        RequestValidation.EnsureValid(query);
        CatalogRules.Coordinates(query.Latitude!.Value, query.Longitude!.Value);
        if (!double.IsFinite(query.RadiusKm)) throw new BadRequestException("Radius must be finite.");
        return (await _catalog.ListStationsAsync(false, null, ct))
            .Select(x => new NearbyStationResponse(CatalogRules.ToResponse(x),
                CatalogRules.DistanceKm(query.Latitude.Value, query.Longitude.Value, x.Latitude, x.Longitude)))
            .Where(x => x.DistanceKm <= query.RadiusKm)
            .OrderBy(x => x.DistanceKm).ThenBy(x => x.Station.StationId).ToArray();
    }

    public async Task<StationResponse> CreateAsync(StationRequest request, CancellationToken ct = default)
    {
        // Validate every incoming field before persisting an active station with immutable identity.
        var station = new SolarStation();
        Apply(station, request);
        station.CreatedAtUtc = station.UpdatedAtUtc = CatalogRules.NextTimestamp();
        await _catalog.InsertStationAsync(station, ct);
        return CatalogRules.ToResponse(station);
    }

    public async Task<StationResponse> UpdateAsync(string id, StationRequest request, CancellationToken ct = default)
    {
        // Prevent a capacity reduction from invalidating existing active slot inventories.
        await _gate.Mutex.WaitAsync(ct);
        try
        {
            var station = await RequiredAsync(id, ct);
            var expected = CatalogRules.RequireExpected(request.ExpectedUpdatedAtUtc, station.UpdatedAtUtc);
            Apply(station, request);
            if (station.CapacityKwh < await _references.ActiveAllocatedEnergyAsync(id, ct))
                throw new ConflictException("Station capacity cannot be lower than energy allocated by active reservations.");
            var slots = await _catalog.ListSlotsAsync(id, false, ct);
            if (slots.Any(x => x.TotalSlots > station.TotalBatterySlots))
                throw new ConflictException("Station storage slots cannot be lower than an active booking slot's total.");
            station.UpdatedAtUtc = CatalogRules.NextTimestamp(expected);
            if (!await _catalog.UpdateStationAsync(station, expected, ct))
                throw new ConflictException("Station changed. Reload before saving.");
            return CatalogRules.ToResponse(station);
        }
        finally { _gate.Mutex.Release(); }
    }

    public async Task DeactivateAsync(string id, CatalogChangeRequest request, CancellationToken ct = default)
    {
        // Block station deactivation only while a Pending or Approved reservation references it.
        RequestValidation.EnsureValid(request);
        await _gate.Mutex.WaitAsync(ct);
        try
        {
            var station = await RequiredAsync(id, ct);
            var expected = CatalogRules.RequireExpected(request.ExpectedUpdatedAtUtc, station.UpdatedAtUtc);
            if (!station.IsActive) return;
            if (await _references.HasActiveStationReservationsAsync(id, ct))
                throw new ConflictException("Station has active reservations (Pending or Approved) and cannot be deactivated.");
            station.IsActive = false;
            station.UpdatedAtUtc = CatalogRules.NextTimestamp(expected);
            if (!await _catalog.UpdateStationAsync(station, expected, ct))
                throw new ConflictException("Station changed. Reload before deactivation.");
        }
        finally { _gate.Mutex.Release(); }
    }

    private async Task<SolarStation> RequiredAsync(string id, CancellationToken ct)
    {
        // Resolve only the immutable station identifier.
        return await _catalog.GetStationAsync(id, ct) ?? throw new NotFoundException("Station not found.");
    }

    private static void Apply(SolarStation station, StationRequest request)
    {
        // Enforce domain input constraints even when called outside an MVC controller.
        RequestValidation.EnsureValid(request);
        CatalogRules.Coordinates(request.Latitude!.Value, request.Longitude!.Value);
        if (request.CapacityKwh is null or <= 0) throw new BadRequestException("Capacity must be greater than zero.");
        if (request.Name.Trim().Length < 2 || request.Address.Trim().Length < 3)
            throw new BadRequestException("Station name and address are required.");
        var schedule = CatalogRules.Schedule(request.OperatingSchedule);
        station.Name = request.Name.Trim(); station.Address = request.Address.Trim();
        station.Latitude = request.Latitude.Value; station.Longitude = request.Longitude.Value;
        station.CapacityKwh = request.CapacityKwh.Value; station.TotalBatterySlots = request.TotalBatterySlots!.Value;
        station.OperatingSchedule = schedule;
    }
}
