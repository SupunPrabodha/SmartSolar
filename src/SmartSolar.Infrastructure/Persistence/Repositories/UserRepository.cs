/*
 * File: UserRepository.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Encapsulates MongoDB persistence operations for users.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using MongoDB.Driver;
using SmartSolar.Application.Exceptions;
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Domain.Constants;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;

namespace SmartSolar.Infrastructure.Persistence.Repositories;

public sealed class UserRepository : IUserRepository
{
    private readonly IMongoCollection<User> _collection;

    public UserRepository(IMongoDatabase database)
    {
        // Resolve the assignment-required UsersDetail collection once for this repository instance.
        _collection = database.GetCollection<User>(CollectionNames.Users);
    }

    public async Task<User?> GetByNicAsync(string nic, CancellationToken cancellationToken = default)
    {
        // Retrieve a single user by NIC, which is persisted as the MongoDB document ID.
        return await _collection.Find(x => x.Nic == nic).FirstOrDefaultAsync(cancellationToken);
    }

    public async Task<User?> GetByEmailAsync(string email, CancellationToken cancellationToken = default)
    {
        // Retrieve a single user by normalized email for uniqueness checks.
        return await _collection.Find(x => x.Email == email).FirstOrDefaultAsync(cancellationToken);
    }

    public async Task<IReadOnlyList<User>> GetAllAsync(CancellationToken cancellationToken = default)
    {
        // Retrieve all user records ordered by name for administrative displays.
        return await _collection.Find(FilterDefinition<User>.Empty)
            .SortBy(x => x.FullName)
            .ToListAsync(cancellationToken);
    }

    public async Task<IReadOnlyList<User>> GetByStatusAsync(
        UserStatus status,
        CancellationToken cancellationToken = default)
    {
        // Retrieve users matching an account status, ordered by creation time.
        return await _collection.Find(x => x.Status == status)
            .SortBy(x => x.CreatedAtUtc)
            .ToListAsync(cancellationToken);
    }

    public async Task InsertAsync(User user, CancellationToken cancellationToken = default)
    {
        // Insert a new user document into MongoDB.
        try
        {
            await _collection.InsertOneAsync(user, cancellationToken: cancellationToken);
        }
        catch (MongoWriteException exception) when (exception.WriteError.Category == ServerErrorCategory.DuplicateKey)
        {
            throw new ConflictException("A user with this NIC or email already exists.");
        }
    }

    public async Task ReplaceAsync(User user, CancellationToken cancellationToken = default)
    {
        // Persist account fields by immutable NIC while preserving internal reservation coordination.
        try
        {
            // Update account fields only: a stale profile must never erase the reservation mutex.
            var update = Builders<User>.Update
                .Set(x => x.FullName, user.FullName).Set(x => x.Email, user.Email)
                .Set(x => x.PhoneNumber, user.PhoneNumber).Set(x => x.PasswordHash, user.PasswordHash)
                .Set(x => x.Role, user.Role).Set(x => x.Status, user.Status)
                .Set(x => x.CreatedAtUtc, user.CreatedAtUtc).Set(x => x.UpdatedAtUtc, user.UpdatedAtUtc);
            await _collection.UpdateOneAsync(x => x.Nic == user.Nic, update, cancellationToken: cancellationToken);
        }
        catch (MongoWriteException exception) when (exception.WriteError.Category == ServerErrorCategory.DuplicateKey)
        {
            throw new ConflictException("A user with this email already exists.");
        }
    }
}
