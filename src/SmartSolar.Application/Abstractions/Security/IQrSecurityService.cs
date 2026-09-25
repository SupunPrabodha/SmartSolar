/*
 * File: IQrSecurityService.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Defines secure QR reference generation, hashing, and structural validation.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

namespace SmartSolar.Application.Abstractions.Security;

public interface IQrSecurityService
{
    // Generates a cryptographically strong opaque QR reference string with the application prefix.
    string GeneratePayload();

    // Computes a deterministic SHA-256 one-way hash of the QR payload string.
    string ComputeHash(string qrPayload);

    // Validates that the payload follows the expected application prefix and length constraints.
    bool IsValidPayloadFormat(string? qrPayload);
}
