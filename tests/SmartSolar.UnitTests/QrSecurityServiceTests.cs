/*
 * File: QrSecurityServiceTests.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Tests cryptographic randomness, hashing determinism, and payload format rules.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using SmartSolar.Infrastructure.Security;
using Xunit;

namespace SmartSolar.UnitTests;

public sealed class QrSecurityServiceTests
{
    private readonly QrSecurityService _service = new();

    [Fact]
    public void GeneratePayloadReturnsUniquePrefixedHighEntropyTokens()
    {
        // Generates opaque reference with prefix and non-repeating cryptographically secure values.
        var p1 = _service.GeneratePayload();
        var p2 = _service.GeneratePayload();

        Assert.StartsWith("SMG1.", p1);
        Assert.StartsWith("SMG1.", p2);
        Assert.NotEqual(p1, p2);
        Assert.True(p1.Length >= 40);
        Assert.DoesNotContain("=", p1); // Base64Url unpadded
    }

    [Fact]
    public void ComputeHashProducesDeterministicSha256Hex()
    {
        // Verifies SHA-256 hex output is deterministic, 64-character lowercase string.
        var payload = "SMG1.test-opaque-payload-reference-123456789";
        var hash1 = _service.ComputeHash(payload);
        var hash2 = _service.ComputeHash(payload);

        Assert.Equal(hash1, hash2);
        Assert.Equal(64, hash1.Length);
        Assert.Matches("^[0-9a-f]{64}$", hash1);
    }

    [Theory]
    [InlineData("SMG1.valid-token-reference-string-value-4321", true)]
    [InlineData("SMG1.", false)] // Too short
    [InlineData("SMG2.valid-token-reference-string-value-4321", false)] // Wrong prefix
    [InlineData("", false)]
    [InlineData("   ", false)]
    [InlineData(null, false)]
    public void IsValidPayloadFormatValidatesPrefixAndLength(string? payload, bool expected)
    {
        // Validates format checks without relying on exceptions.
        Assert.Equal(expected, _service.IsValidPayloadFormat(payload));
    }
}
