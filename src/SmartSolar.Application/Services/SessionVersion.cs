/*
 * File: SessionVersion.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Liyanage S. P. (IT23187450)
 * Purpose: Compares JWT security-version claims with authoritative account versions.
 */
using System.Globalization;
namespace SmartSolar.Application.Services;
public static class SessionVersion
{
    // Legacy sessions work only while their account has never revoked sessions.
    public static bool Matches(string? claim, long current)
    {
        // Accept only a well-formed token version matching the account's current security version.
        return long.TryParse(claim ?? "0", NumberStyles.None, CultureInfo.InvariantCulture, out var version) && version == current;
    }
}
