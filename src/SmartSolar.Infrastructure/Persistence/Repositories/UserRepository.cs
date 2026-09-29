/*
 * File: UserRepository.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Encapsulates MongoDB persistence operations for users.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using SmartSolar.Application.Abstractions.Security;
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
    private readonly IRequestIdentity? _identity;

    public UserRepository(IMongoDatabase database, IRequestIdentity? identity = null)
    {
        // Resolve the assignment-required UsersDetail collection once for this repository instance.
        _identity = identity;
        _collection = database.GetCollection<User>(CollectionNames.Users);
    }

    public async Task<User?> GetByNicAsync(string nic, CancellationToken cancellationToken = default)
    {
        // Retrieve a single user by NIC, which is persisted as the MongoDB document ID.
        return await _collection.Find(x => x.Nic == nic).FirstOrDefaultAsync(cancellationToken);
    }

    public Task<User?> GetSessionUserAsync(string nic, CancellationToken cancellationToken = default) =>
        _collection.Find(x => x.Nic == nic)
            .Project<User>(Builders<User>.Projection.Include(x => x.Nic).Include(x => x.Role).Include(x => x.Status).Include(x => x.SecurityVersion))
            .FirstOrDefaultAsync(cancellationToken)!;

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
            user.AuditHistory.Add(AuditTrail.Create(_identity, "AccountCreated", "Profile", user.Nic, user.Nic,
                user.Status == UserStatus.PendingActivation ? "Backoffice" : null));
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
            var previous = await GetByNicAsync(user.Nic, cancellationToken);
            var eventName = previous?.Status != user.Status ? "Account" + user.Status :
                previous.ApprovedAtUtc != user.ApprovedAtUtc ? "AccountApproved" :
                previous.ProfileCompletedAtUtc != user.ProfileCompletedAtUtc && user.ProfileCompletedAtUtc is not null ? "ProfileCompleted" : "ProfileUpdated";
            var notifyOwner = eventName.StartsWith("Account") ? user.Nic : null;
            // Update account fields only: a stale profile must never erase the reservation mutex.
            var update = Builders<User>.Update
                .Set(x => x.FullName, user.FullName).Set(x => x.Email, user.Email)
                .Set(x => x.ProfileCompletedAtUtc, user.ProfileCompletedAtUtc)
                .Set(x => x.PhoneNumber, user.PhoneNumber).Set(x => x.PasswordHash, user.PasswordHash)
                .Set(x => x.Role, user.Role).Set(x => x.Status, user.Status)
                .Set(x => x.CreatedAtUtc, user.CreatedAtUtc).Set(x => x.UpdatedAtUtc, user.UpdatedAtUtc)
                .Set(x => x.ApprovedAtUtc, user.ApprovedAtUtc).Set(x => x.EmailVerifiedAtUtc, user.EmailVerifiedAtUtc)
                .Set(x => x.EmailVerificationHash, user.EmailVerificationHash)
                .Set(x => x.EmailVerificationExpiresAtUtc, user.EmailVerificationExpiresAtUtc)
                .Inc(x => x.AccountVersion, 1)
                .PushEach(x => x.AuditHistory, [AuditTrail.Create(_identity, eventName, "Profile", user.Nic, notifyOwner)], slice: -100);
            if (previous?.Email != user.Email || user.Status == UserStatus.Deactivated)
                update = update.Unset(x => x.PasswordResetTokenHash).Unset(x => x.PasswordResetExpiresAtUtc)
                    .Unset(x => x.PasswordResetRequestedAtUtc);
            // Legacy documents have no version; all subsequent account edits compare and increment it.
            var filters = Builders<User>.Filter;
            var version = filters.Eq(x => x.AccountVersion, user.AccountVersion);
            if (user.AccountVersion == 0) version |= filters.Exists(x => x.AccountVersion, false);
            var result = await _collection.UpdateOneAsync(filters.Eq(x => x.Nic, user.Nic) & version,
                update, cancellationToken: cancellationToken);
            if (result.MatchedCount == 0)
                throw new ConflictException("This account changed. Refresh and try again.");
            user.AccountVersion++;
        }
        catch (MongoWriteException exception) when (exception.WriteError.Category == ServerErrorCategory.DuplicateKey)
        {
            throw new ConflictException("A user with this email already exists.");
        }
    }
}
