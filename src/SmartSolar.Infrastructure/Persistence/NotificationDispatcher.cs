/*
 * File: NotificationDispatcher.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Liyanage S. P. (IT23187450)
 * Purpose: Delivers retained audit events to bounded recipient inboxes with deduplication.
 */
using MongoDB.Bson;
using MongoDB.Bson.Serialization;
using MongoDB.Driver;
using SmartSolar.Domain.Constants;
using SmartSolar.Domain.Entities;
namespace SmartSolar.Infrastructure.Persistence;

public sealed class NotificationDispatcher(IMongoDatabase database)
{
    public async Task DispatchAsync(CancellationToken ct)
    {
        // Durable delivery checkpoints share the successful business document write.
        var users = database.GetCollection<User>(CollectionNames.Users);
        foreach (var name in new[] { CollectionNames.Users, CollectionNames.Stations, CollectionNames.BookingSlots, CollectionNames.Reservations })
        {
            var source = database.GetCollection<BsonDocument>(name);
            var documents = await source.Find(new BsonDocument("AuditHistory.Delivered", false))
                .Project(new BsonDocument { { "AuditHistory", 1 }, { "ProsumerNic", 1 } }).Limit(50).ToListAsync(ct);
            foreach (var document in documents)
            foreach (var raw in document["AuditHistory"].AsBsonArray.Where(x => !x.AsBsonDocument.GetValue("Delivered", true).AsBoolean))
            {
                var entry = BsonSerializer.Deserialize<AuditEntry>(raw.AsBsonDocument);
                var recipient = entry.RecipientNic;
                if (recipient is null && entry.Event == "ReservationCompleted" && document.Contains("ProsumerNic"))
                    recipient = document["ProsumerNic"].AsString;
                var conditions = new List<FilterDefinition<User>>();
                if (recipient is not null) conditions.Add(Builders<User>.Filter.Eq(x => x.Nic, recipient));
                if (entry.RecipientRole is not null)
                    conditions.Add(new BsonDocument { { "Role", entry.RecipientRole }, { "Status", "Active" } });
                if (conditions.Count > 0)
                {
                    var notice = new InboxNotification { Id = entry.Id, AtUtc = entry.AtUtc, Category = entry.Category,
                        Priority = entry.Priority, Message = entry.Message, Action = entry.Action, ResourceId = entry.ResourceId };
                    await users.UpdateManyAsync(Builders<User>.Filter.Or(conditions) &
                        Builders<User>.Filter.Not(Builders<User>.Filter.ElemMatch(x => x.Notifications, x => x.Id == entry.Id)),
                        Builders<User>.Update.PushEach(x => x.Notifications, [notice], slice: -100), cancellationToken: ct);
                }
                await source.UpdateOneAsync(new BsonDocument { { "_id", document["_id"] }, { "AuditHistory._id", entry.Id } },
                    new BsonDocument("$set", new BsonDocument("AuditHistory.$.Delivered", true)), cancellationToken: ct);
            }
        }
    }
}
