/*
 * File: IExperienceRepository.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Liyanage S. P. (IT23187450)
 * Purpose: Defines authorized profile-image, notification, audit, search and export persistence.
 */
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;
namespace SmartSolar.Application.Abstractions.Persistence;

public sealed record SearchHit(string Kind, string Id, string Label);
public sealed record ExportQuery(string? Search = null, string? Status = null, string? StationId = null,
    string? ProsumerNic = null, DateTime? FromUtc = null, DateTime? ToUtc = null, string? ReservationId = null, bool IncludeInactive = false, string? View = null);
public interface IExperienceRepository
{
    Task SetAvatarAsync(string nic, byte[]? bytes, CancellationToken ct);
    Task<(byte[]? Bytes, string? Version)> GetAvatarAsync(string nic, CancellationToken ct);
    Task<IReadOnlyList<InboxNotification>> InboxAsync(string nic, CancellationToken ct);
    Task ReadAsync(string nic, string? id, DateTime now, CancellationToken ct);
    Task<IReadOnlyList<AuditEntry>> AuditAsync(string kind, string id, string nic, UserRole role, CancellationToken ct);
    Task<IReadOnlyList<SearchHit>> SearchAsync(string query, string nic, UserRole role, CancellationToken ct);
    Task<byte[]> ExportAsync(string kind, ExportQuery query, string nic, UserRole role, CancellationToken ct);
}
