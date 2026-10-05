/*
 * File: EnterprisePolicyTests.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Liyanage S. P. (IT23187450)
 * Purpose: Tests shared password limits, session-version matching and safe CSV escaping.
 */
using SmartSolar.Application.Services;
using SmartSolar.Application.DTOs.Auth;
using Xunit;
namespace SmartSolar.UnitTests;
public sealed class EnterprisePolicyTests
{
    [Theory]
    [InlineData(null,0,true)] [InlineData(null,1,false)] [InlineData("0",1,false)]
    [InlineData("1",1,true)] [InlineData("-1",0,false)] [InlineData("invalid",0,false)]
    public void SessionVersionRevokesOldAndMalformedClaims(string? claim,long version,bool valid)
    {
        // Verify current versions match while stale or malformed session claims are rejected.
        Assert.Equal(valid,SessionVersion.Matches(claim,version));
    }
    [Theory]
    [InlineData("=cmd", "\"'=cmd\"")]
    [InlineData("  +SUM(A1)", "\"'  +SUM(A1)\"")]
    [InlineData("@formula", "\"'@formula\"")]
    [InlineData("a,\"b", "\"a,\"\"b\"")]
    public void CsvProtectsSpreadsheetFormulasAndEscapesQuotes(string value,string expected)
    {
        // Verify CSV cells neutralize formula prefixes and preserve quoted content.
        Assert.Equal(expected,CsvWriter.Cell(value));
    }
    [Theory]
    [InlineData(7,false)] [InlineData(8,true)] [InlineData(100,true)] [InlineData(101,false)]
    public void PasswordPolicyKeepsTheSharedLengthContract(int length,bool valid)
    {
        // Verify passwords obey the established shared length range.
        Assert.Equal(valid,new PasswordPolicyAttribute().IsValid(new string('p',length)));
    }
}
