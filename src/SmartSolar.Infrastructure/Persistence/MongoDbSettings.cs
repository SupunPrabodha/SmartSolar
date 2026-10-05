/*
 * File: MongoDbSettings.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Smart Solar Development Team
 * Purpose: Defines external MongoDB connection and database-name configuration.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

namespace SmartSolar.Infrastructure.Persistence;

public sealed class MongoDbSettings
{
    public const string SectionName = "MongoDb";

    public string ConnectionString { get; init; } = string.Empty;
    public string DatabaseName { get; init; } = string.Empty;
}
