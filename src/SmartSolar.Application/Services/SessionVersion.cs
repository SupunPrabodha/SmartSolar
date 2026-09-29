/*
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Enterprise experience and operations security.
 */
using System.Globalization;
namespace SmartSolar.Application.Services;
public static class SessionVersion
{
    // Legacy sessions work only while their account has never revoked sessions.
    public static bool Matches(string? claim, long current) =>
        long.TryParse(claim ?? "0", NumberStyles.None, CultureInfo.InvariantCulture, out var version) && version == current;
}
