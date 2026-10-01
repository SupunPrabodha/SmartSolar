/*
 * File: QrSecurityService.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Issues cryptographically strong opaque QR references and one-way verification hashes.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

using System.Security.Cryptography;
using System.Text;
using SmartSolar.Application.Abstractions.Security;

namespace SmartSolar.Infrastructure.Security;

public sealed class QrSecurityService : IQrSecurityService
{
    private const string PayloadPrefix = "SMG1.";

    public string GeneratePayload()
    {
        // Generate 256 bits (32 bytes) of cryptographically secure randomness.
        var randomBytes = RandomNumberGenerator.GetBytes(32);
        var base64UrlToken = Convert.ToBase64String(randomBytes)
            .Replace("+", "-")
            .Replace("/", "_")
            .TrimEnd('=');
        return $"{PayloadPrefix}{base64UrlToken}";
    }

    public string ComputeHash(string qrPayload)
    {
        // Compute deterministic SHA-256 hex string from the opaque QR reference.
        var payloadBytes = Encoding.UTF8.GetBytes(qrPayload.Trim());
        var hashBytes = SHA256.HashData(payloadBytes);
        return Convert.ToHexString(hashBytes).ToLowerInvariant();
    }

    public bool IsValidPayloadFormat(string? qrPayload)
    {
        // Reject malformed or oversized QR payloads before database lookup.
        if (string.IsNullOrWhiteSpace(qrPayload)) return false;
        var trimmed = qrPayload.Trim();
        if (!trimmed.StartsWith(PayloadPrefix, StringComparison.Ordinal)) return false;
        if (trimmed.Length is < 20 or > 256) return false;
        return true;
    }
}
