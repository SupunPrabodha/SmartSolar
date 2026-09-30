/*
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Enterprise experience and operations security.
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
    public void SessionVersionRevokesOldAndMalformedClaims(string? claim,long version,bool valid) =>
        Assert.Equal(valid,SessionVersion.Matches(claim,version));
    [Theory]
    [InlineData("=cmd", "\"'=cmd\"")]
    [InlineData("  +SUM(A1)", "\"'  +SUM(A1)\"")]
    [InlineData("@formula", "\"'@formula\"")]
    [InlineData("a,\"b", "\"a,\"\"b\"")]
    public void CsvProtectsSpreadsheetFormulasAndEscapesQuotes(string value,string expected) => Assert.Equal(expected,CsvWriter.Cell(value));
    [Theory]
    [InlineData(7,false)] [InlineData(8,true)] [InlineData(100,true)] [InlineData(101,false)]
    public void PasswordPolicyKeepsTheSharedLengthContract(int length,bool valid) => Assert.Equal(valid,new PasswordPolicyAttribute().IsValid(new string('p',length)));
}
