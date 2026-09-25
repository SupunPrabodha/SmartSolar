/*
 * File: SlotService.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Owns booking-slot inventory management without implementing reservation booking.
 */
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.DTOs.Slots;
using SmartSolar.Application.DTOs.Stations;
using SmartSolar.Application.Exceptions;
using SmartSolar.Domain.Entities;
namespace SmartSolar.Application.Services;

public sealed class SlotService
{
    private readonly IStationCatalogRepository _catalog;
    private readonly IReservationReferenceReader _references;
    private readonly CatalogWriteGate _gate;
    public SlotService(IStationCatalogRepository catalog, IReservationReferenceReader references, CatalogWriteGate gate)
    {
        // Reuse catalog persistence and the common write gate.
        _catalog = catalog; _references = references; _gate = gate;
    }

    public async Task<IReadOnlyList<SlotResponse>> ListAsync(string stationId, bool includeInactive, CancellationToken ct = default)
    {
        // Active discovery hides inventory at inactive stations; operators retain administrative history.
        var station = await StationAsync(stationId, ct);
        if (!includeInactive && !station.IsActive) throw new NotFoundException("Station not found.");
        return (await _catalog.ListSlotsAsync(stationId, includeInactive, ct)).Select(ToResponse).ToArray();
    }

    public async Task<SlotResponse> GetAsync(string id, bool includeInactive, CancellationToken ct = default)
    {
        // Return an explicit response DTO with both immutable reference IDs.
        var slot = await RequiredAsync(id, ct);
        var station = await StationAsync(slot.StationId, ct);
        if (!includeInactive && (!slot.IsActive || !station.IsActive)) throw new NotFoundException("Slot not found.");
        return ToResponse(slot);
    }

    public async Task<SlotResponse> CreateAsync(string stationId, SlotRequest request, CancellationToken ct = default)
    {
        // Serialize local related writes while checking station capacity and overlapping inventory windows.
        await _gate.Mutex.WaitAsync(ct);
        try
        {
            var station = await StationAsync(stationId, ct);
            var slot = new EnergyBookingSlot { StationId = stationId };
            Apply(slot, request, station);
            await CheckOverlapAsync(slot, ct);
            slot.CreatedAtUtc = slot.UpdatedAtUtc = CatalogRules.NextTimestamp();
            await _catalog.InsertSlotAsync(slot, ct);
            return ToResponse(slot);
        }
        finally { _gate.Mutex.Release(); }
    }

    public async Task<SlotResponse> UpdateAsync(string id, SlotRequest request, CancellationToken ct = default)
    {
        // Preserve linked reservation history and reject stale administration updates.
        await _gate.Mutex.WaitAsync(ct);
        try
        {
            var slot = await RequiredAsync(id, ct);
            var expected = CatalogRules.RequireExpected(request.ExpectedUpdatedAtUtc, slot.UpdatedAtUtc);
            await EnsureNoActiveReservationsAsync(id, ct);
            Apply(slot, request, await StationAsync(slot.StationId, ct));
            if (slot.IsActive) await CheckOverlapAsync(slot, ct);
            await SaveAsync(slot, expected, ct);
            return ToResponse(slot);
        }
        finally { _gate.Mutex.Release(); }
    }

    public async Task<SlotResponse> AvailabilityAsync(string id, SlotAvailabilityRequest request, CancellationToken ct = default)
    {
        // Availability is an operator inventory edit, never a reservation allocation operation.
        RequestValidation.EnsureValid(request);
        await _gate.Mutex.WaitAsync(ct);
        try
        {
            var slot = await RequiredAsync(id, ct);
            var expected = CatalogRules.RequireExpected(request.ExpectedUpdatedAtUtc, slot.UpdatedAtUtc);
            if (!slot.IsActive || !(await StationAsync(slot.StationId, ct)).IsActive)
                throw new ConflictException("Availability can only change for an active slot at an active station.");
            if (request.AvailableSlots > slot.TotalSlots) throw new BadRequestException("Available slots cannot exceed total slots.");
            await EnsureNoActiveReservationsAsync(id, ct);
            slot.AvailableSlots = request.AvailableSlots!.Value;
            await SaveAsync(slot, expected, ct);
            return ToResponse(slot);
        }
        finally { _gate.Mutex.Release(); }
    }

    public async Task DeactivateAsync(string id, CatalogChangeRequest request, CancellationToken ct = default)
    {
        // Soft-deactivate without removing historical IDs; protect Pending/Approved reservations.
        RequestValidation.EnsureValid(request);
        await _gate.Mutex.WaitAsync(ct);
        try
        {
            var slot = await RequiredAsync(id, ct);
            var expected = CatalogRules.RequireExpected(request.ExpectedUpdatedAtUtc, slot.UpdatedAtUtc);
            if (!slot.IsActive) return;
            await EnsureNoActiveReservationsAsync(id, ct);
            slot.IsActive = false;
            await SaveAsync(slot, expected, ct);
        }
        finally { _gate.Mutex.Release(); }
    }

    private async Task EnsureNoActiveReservationsAsync(string id, CancellationToken ct)
    {
        // Use the frozen Pending/Approved protection query for every protected slot mutation.
        if (await _references.HasActiveSlotReservationsAsync(id, ct))
            throw new ConflictException("Slot has active reservations (Pending or Approved) and cannot be changed.");
    }

    private async Task CheckOverlapAsync(EnergyBookingSlot slot, CancellationToken ct)
    {
        // One active inventory window per station at a time; touching endpoints are not overlapping.
        if ((await _catalog.ListSlotsAsync(slot.StationId, false, ct)).Any(x =>
            x.SlotId != slot.SlotId && x.StartAtUtc < slot.EndAtUtc && slot.StartAtUtc < x.EndAtUtc))
            throw new ConflictException("An active slot already overlaps this station's time window.");
    }

    private async Task SaveAsync(EnergyBookingSlot slot, DateTime expected, CancellationToken ct)
    {
        // Atomic compare-and-update protects an individual slot against lost updates.
        slot.UpdatedAtUtc = CatalogRules.NextTimestamp(expected);
        if (!await _catalog.UpdateSlotAsync(slot, expected, ct)) throw new ConflictException("Slot changed. Reload before saving.");
    }

    private async Task<SolarStation> StationAsync(string id, CancellationToken ct)
    {
        // Resolve the existing station without introducing another schema.
        return await _catalog.GetStationAsync(id, ct) ?? throw new NotFoundException("Station not found.");
    }

    private async Task<EnergyBookingSlot> RequiredAsync(string id, CancellationToken ct)
    {
        // Use the original slot identifier for all lookups.
        return await _catalog.GetSlotAsync(id, ct) ?? throw new NotFoundException("Slot not found.");
    }

    private static void Apply(EnergyBookingSlot slot, SlotRequest request, SolarStation station)
    {
        // Validate times/counts on the server without adding Member 3's reservation timing rules.
        RequestValidation.EnsureValid(request);
        if (!station.IsActive) throw new ConflictException("Station is inactive.");
        var start = request.StartAtUtc!.Value.UtcDateTime;
        var end = request.EndAtUtc!.Value.UtcDateTime;
        if (start == default || end == default || start >= end) throw new BadRequestException("Slot start must precede end.");
        if (start.Ticks % TimeSpan.TicksPerMillisecond != 0 || end.Ticks % TimeSpan.TicksPerMillisecond != 0)
            throw new BadRequestException("Slot times support millisecond precision.");
        if (request.AvailableSlots > request.TotalSlots) throw new BadRequestException("Available slots cannot exceed total slots.");
        if (request.TotalSlots > station.TotalBatterySlots) throw new ConflictException("Slot total exceeds station battery-slot capacity.");
        slot.StartAtUtc = start; slot.EndAtUtc = end;
        slot.TotalSlots = request.TotalSlots!.Value; slot.AvailableSlots = request.AvailableSlots!.Value;
    }

    private static SlotResponse ToResponse(EnergyBookingSlot slot)
    {
        // Expose explicit fields instead of the persisted domain instance.
        return new(slot.SlotId, slot.StationId, slot.StartAtUtc, slot.EndAtUtc,
            slot.TotalSlots, slot.AvailableSlots, slot.IsActive, slot.CreatedAtUtc, slot.UpdatedAtUtc);
    }
}
