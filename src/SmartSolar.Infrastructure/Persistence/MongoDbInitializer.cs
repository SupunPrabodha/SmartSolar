/*
 * File: MongoDbInitializer.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Creates the required MongoDB collections and shared indexes.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using MongoDB.Driver;
using SmartSolar.Domain.Constants;
using SmartSolar.Domain.Entities;

namespace SmartSolar.Infrastructure.Persistence;

public sealed class MongoDbInitializer
{
    private readonly IMongoDatabase _database;

    public MongoDbInitializer(IMongoDatabase database)
    {
        // Store the central MongoDB database used by all server-side repositories.
        _database = database;
    }

    public async Task InitializeAsync(CancellationToken cancellationToken = default)
    {
        // Ensure all assignment-required collections exist before client development starts.
        var existingCollections = await GetCollectionNamesAsync(cancellationToken);
        var requiredCollections = new[]
        {
            CollectionNames.Users,
            CollectionNames.Stations,
            CollectionNames.BookingSlots,
            CollectionNames.Reservations
        };

        foreach (var collectionName in requiredCollections.Where(x => !existingCollections.Contains(x)))
        {
            try
            {
                await _database.CreateCollectionAsync(collectionName, cancellationToken: cancellationToken);
            }
            catch (MongoCommandException exception) when (exception.Code == 48)
            {
                // Another API instance created this collection after our initial listing.
            }
        }

        await CreateIndexesAsync(cancellationToken);
    }

    private async Task<HashSet<string>> GetCollectionNamesAsync(CancellationToken cancellationToken)
    {
        // Read the existing collection names so initialization is safe to run repeatedly.
        using var cursor = await _database.ListCollectionNamesAsync(cancellationToken: cancellationToken);
        return (await cursor.ToListAsync(cancellationToken)).ToHashSet(StringComparer.Ordinal);
    }

    private async Task CreateIndexesAsync(CancellationToken cancellationToken)
    {
        // Create indexes for uniqueness and the query patterns expected by user, station, slot, and reservation workflows.
        var users = _database.GetCollection<User>(CollectionNames.Users);
        var stations = _database.GetCollection<SolarStation>(CollectionNames.Stations);
        var slots = _database.GetCollection<EnergyBookingSlot>(CollectionNames.BookingSlots);
        var reservations = _database.GetCollection<EnergyReservation>(CollectionNames.Reservations);

        await users.Indexes.CreateOneAsync(
            new CreateIndexModel<User>(
                Builders<User>.IndexKeys.Ascending(x => x.Email),
                new CreateIndexOptions { Unique = true, Name = "ux_users_email" }),
            cancellationToken: cancellationToken);

        await stations.Indexes.CreateOneAsync(
            new CreateIndexModel<SolarStation>(
                Builders<SolarStation>.IndexKeys
                    .Ascending(x => x.IsActive)
                    .Ascending(x => x.Name),
                new CreateIndexOptions { Name = "ix_stations_active_name" }),
            cancellationToken: cancellationToken);

        await slots.Indexes.CreateOneAsync(
            new CreateIndexModel<EnergyBookingSlot>(
                Builders<EnergyBookingSlot>.IndexKeys
                    .Ascending(x => x.StationId)
                    .Ascending(x => x.StartAtUtc),
                new CreateIndexOptions { Name = "ix_slots_station_start" }),
            cancellationToken: cancellationToken);

        await reservations.Indexes.CreateManyAsync(
            new[]
            {
                new CreateIndexModel<EnergyReservation>(
                    Builders<EnergyReservation>.IndexKeys
                        .Ascending(x => x.ProsumerNic)
                        .Descending(x => x.CreatedAtUtc),
                    new CreateIndexOptions { Name = "ix_reservations_prosumer_created" }),
                new CreateIndexModel<EnergyReservation>(
                    Builders<EnergyReservation>.IndexKeys
                        .Ascending(x => x.StationId)
                        .Ascending(x => x.Status),
                    new CreateIndexOptions { Name = "ix_reservations_station_status" })
            },
            cancellationToken: cancellationToken);
    }
}
