/*
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Enterprise experience and operations security.
 */
using SmartSolar.Application.Abstractions.Security;
using System.Globalization;
using System.Text.RegularExpressions;
using MongoDB.Bson;
using MongoDB.Driver;
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.Exceptions;
using SmartSolar.Application.Services;
using SmartSolar.Domain.Constants;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;
namespace SmartSolar.Infrastructure.Persistence.Repositories;

public sealed class ExperienceRepository(IMongoDatabase database, IRequestIdentity? identity = null, TimeProvider? clock = null) : IExperienceRepository
{
    private IMongoCollection<User> Users => database.GetCollection<User>(CollectionNames.Users);
    public async Task SetAvatarAsync(string nic, byte[]? bytes, CancellationToken ct)
    {
        var update = Builders<User>.Update.Set(x => x.AvatarBytes, bytes)
            .Set(x => x.AvatarContentType, bytes is null ? null : "image/jpeg")
            .Set(x => x.AvatarVersion, bytes is null ? null : Guid.NewGuid().ToString("N"))
            .Set(x => x.ProfileCompletedAtUtc, null).Inc(x => x.AccountVersion, 1)
            .PushEach(x => x.AuditHistory, [AuditTrail.Create(identity, bytes is null ? "AvatarRemoved" : "AvatarUpdated", "Profile", nic)], slice: -100);
        await Users.UpdateOneAsync(x => x.Nic == nic, update, cancellationToken: ct);
    }
    public async Task<(byte[]? Bytes, string? Version)> GetAvatarAsync(string nic, CancellationToken ct)
    {
        var data = await Users.Find(x => x.Nic == nic)
            .Project(x => new { x.AvatarBytes, x.AvatarVersion }).FirstOrDefaultAsync(ct);
        return (data?.AvatarBytes, data?.AvatarVersion);
    }
    public async Task<IReadOnlyList<InboxNotification>> InboxAsync(string nic, CancellationToken ct) =>
        (await Users.Find(x => x.Nic == nic).Project(x => x.Notifications).FirstOrDefaultAsync(ct) ?? [])
            .OrderByDescending(x => x.AtUtc).Take(100).ToList();

    public async Task ReadAsync(string nic, string? id, DateTime now, CancellationToken ct)
    {
        // Array filters change only unread entries; concurrent additions cannot be overwritten.
        var filter = new BsonDocument("n.ReadAtUtc", BsonNull.Value);
        if (id is not null) filter.Add("n._id", id);
        await Users.UpdateOneAsync(Builders<User>.Filter.Eq(x => x.Nic, nic) & Builders<User>.Filter.Type(x => x.Notifications, BsonType.Array),
            Builders<User>.Update.Set("Notifications.$[n].ReadAtUtc", now),
            new UpdateOptions { ArrayFilters = [new BsonDocumentArrayFilterDefinition<BsonDocument>(filter)] }, ct);
    }

    private static string Collection(string kind) => kind switch
    {
        "users" => CollectionNames.Users, "stations" => CollectionNames.Stations,
        "slots" => CollectionNames.BookingSlots, "reservations" => CollectionNames.Reservations,
        _ => throw new BadRequestException("Unsupported resource.")
    };
    public async Task<IReadOnlyList<AuditEntry>> AuditAsync(string kind, string id, string nic, UserRole role, CancellationToken ct)
    {
        if (kind == "users" && role != UserRole.Backoffice && id != nic ||
            kind == "reservations" && role == UserRole.Backoffice ||
            kind is "stations" or "slots" && role == UserRole.Prosumer)
            throw new ForbiddenException("This history is not available for your role.");
        var filter = new BsonDocument("_id", id);
        if (kind == "reservations" && role == UserRole.Prosumer) filter.Add("ProsumerNic", nic);
        var data = await database.GetCollection<BsonDocument>(Collection(kind)).Find(filter)
            .Project(new BsonDocument("AuditHistory", 1)).FirstOrDefaultAsync(ct)
            ?? throw new NotFoundException("Record not found.");
        return data.GetValue("AuditHistory", new BsonArray()).AsBsonArray.Reverse().Take(100)
            .Select(x => MongoDB.Bson.Serialization.BsonSerializer.Deserialize<AuditEntry>(x.AsBsonDocument)).ToList();
    }
    public async Task<IReadOnlyList<SearchHit>> SearchAsync(string query, string nic, UserRole role, CancellationToken ct)
    {
        if (query.Length is < 2 or > 80) throw new BadRequestException("Enter between 2 and 80 characters.");
        // Prefix-only literal searches, a hard result cap and execution deadline protect large datasets.
        var rx = new BsonRegularExpression("^" + Regex.Escape(query), "");
        var result = new List<SearchHit>();
        foreach (var kind in new[] { "users", "stations", "reservations" })
        {
            if (kind == "users" && role != UserRole.Backoffice || kind == "reservations" && role == UserRole.Backoffice) continue;
            var fields = kind == "users" ? new[] { "_id", "FullName", "Email" } :
                kind == "stations" ? new[] { "_id", "Name" } : new[] { "_id", "ProsumerNic" };
            var filter = new BsonDocument("$or", new BsonArray(fields.Select(field => new BsonDocument(field, rx))));
            if (kind == "reservations" && role == UserRole.Prosumer) filter.Add("ProsumerNic", nic);
            if (kind == "stations" && role == UserRole.Prosumer) filter.Add("IsActive", true);
            var rows = await database.GetCollection<BsonDocument>(Collection(kind)).Find(filter,
                new FindOptions { MaxTime = TimeSpan.FromSeconds(2) }).Project(new BsonDocument
                { { "_id", 1 }, { "Name", 1 }, { "FullName", 1 } }).Limit(5).ToListAsync(ct);
            result.AddRange(rows.Select(row => new SearchHit(kind, row["_id"].AsString,
                row.GetValue(kind == "users" ? "FullName" : "Name", row["_id"]).AsString)));
        }
        return result;
    }
    public async Task<byte[]> ExportAsync(string kind, ExportQuery query, string nic, UserRole role, CancellationToken ct)
    {
        if (kind == "users" && role != UserRole.Backoffice || kind == "reservations" && role == UserRole.Backoffice ||
            kind == "slots" || kind == "stations" && role == UserRole.Prosumer)
            throw new ForbiddenException("Export is not available for your role.");
        if (query.FromUtc > query.ToUtc) throw new BadRequestException("Start must not follow end.");
        if (query.View is not null && query.View is not ("history" or "current" or "pending"))
            throw new BadRequestException("Unsupported reservation view.");
        var filter = new BsonDocument();
        if (kind == "stations" && !query.IncludeInactive) filter.Add("IsActive", true);
        if (kind == "reservations" && !string.IsNullOrWhiteSpace(query.ReservationId)) filter.Add("_id", query.ReservationId.Trim());
        if (kind == "reservations" && role == UserRole.Prosumer) filter.Add("ProsumerNic", nic);
        else if (kind == "reservations" && !string.IsNullOrWhiteSpace(query.ProsumerNic)) filter.Add("ProsumerNic", query.ProsumerNic);
        if (!string.IsNullOrWhiteSpace(query.Status))
        {
            if (kind == "users" ? !Enum.TryParse<UserStatus>(query.Status, out _) : !Enum.TryParse<ReservationStatus>(query.Status, out _))
                throw new BadRequestException("Unsupported status.");
            filter.Add("Status", query.Status);
        }
        if (kind == "reservations" && query.View is not null)
        {
            var now = (clock ?? TimeProvider.System).GetUtcNow().UtcDateTime;
            BsonDocument view = query.View switch
            {
                "pending" => new("Status", "Pending"),
                "current" => new() { { "Status", "Approved" }, { "ScheduledEndAtUtc", new BsonDocument("$gt", now) } },
                _ => new("$or", new BsonArray {
                    new BsonDocument("Status", new BsonDocument("$in", new BsonArray { "Rejected", "Cancelled", "Completed" })),
                    new BsonDocument { { "Status", new BsonDocument("$in", new BsonArray { "Pending", "Approved" }) },
                        { "ScheduledEndAtUtc", new BsonDocument("$lte", now) } } })
            };
            filter.Add("$and", new BsonArray { view });
        }
        if (kind == "reservations" && !string.IsNullOrWhiteSpace(query.StationId)) filter.Add("StationId", query.StationId);
        if (kind == "reservations" && (query.FromUtc.HasValue || query.ToUtc.HasValue))
        {
            var range = new BsonDocument();
            if (query.FromUtc.HasValue) range.Add("$gte", query.FromUtc.Value);
            if (query.ToUtc.HasValue) range.Add("$lte", query.ToUtc.Value);
            filter.Add("ScheduledStartAtUtc", range);
        }
        if (!string.IsNullOrWhiteSpace(query.Search))
        {
            if (query.Search.Length > 120) throw new BadRequestException("Search is too long.");
            var rx = new BsonRegularExpression(Regex.Escape(query.Search), "i");
            filter.Add("$or", new BsonArray((kind == "users" ? new[] { "_id", "FullName", "Email", "Role", "Status" } :
                kind == "stations" ? new[] { "Name", "Address" } : new[] { "_id", "ProsumerNic" })
                .Select(field => new BsonDocument(field, rx))));
        }
        var columns = kind switch
        {
            "users" => new[] { "_id", "FullName", "Email", "PhoneNumber", "Role", "Status" },
            "stations" => new[] { "_id", "Name", "Address", "CapacityKwh", "TotalBatterySlots", "IsActive" },
            "reservations" => new[] { "_id", "ProsumerNic", "StationId", "SlotId", "Status", "EnergyAmountKwh", "ScheduledStartAtUtc", "ScheduledEndAtUtc" },
            _ => throw new BadRequestException("Unsupported export.")
        };
        var projection = new BsonDocument(columns.Select(c => new BsonElement(c, 1)));
        var rows = await database.GetCollection<BsonDocument>(Collection(kind))
            .Find(filter, new FindOptions { MaxTime = TimeSpan.FromSeconds(5) }).Project(projection)
            .Sort(new BsonDocument("_id", 1)).Limit(1001).ToListAsync(ct);
        if (rows.Count > 1000) throw new BadRequestException("More than 1,000 rows match. Narrow the filters before exporting.");
        return CsvWriter.Encode(new[] { columns.AsEnumerable() }.Concat(rows.Select(row => columns.Select(c =>
        {
            var value = row.GetValue(c, BsonNull.Value);
            return value.IsBsonNull ? "" : value.IsValidDateTime ? value.ToUniversalTime().ToString("O", CultureInfo.InvariantCulture) : value.ToString();
        }))));
    }
}
