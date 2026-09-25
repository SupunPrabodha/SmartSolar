/*
 * File: ReservationDtoTests.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Verifies reservation input constraints and the planned JSON boundary.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using System.ComponentModel.DataAnnotations;
using System.Text.Json;
using System.Text.Json.Serialization;
using SmartSolar.Application.DTOs.Reservations;
using SmartSolar.Domain.Enums;
using Xunit;

namespace SmartSolar.UnitTests;

public sealed class ReservationDtoTests
{
    [Theory]
    [InlineData("0")]
    [InlineData("-1")]
    [InlineData("0.0000000000000000000000000001")]
    [InlineData("79228162514264337593543950335")]
    public void BothRequestsRequirePositiveDecimalAmount(string amountText)
    {
        // Retain decimal precision without inventing a minimum trade size or capacity limit.
        var amount = decimal.Parse(amountText, System.Globalization.CultureInfo.InvariantCulture);
        var id = Guid.NewGuid().ToString("N");
        Assert.Equal(amount > 0, IsValid(new CreateReservationRequest { SlotId = id, EnergyAmountKwh = amount }));
        Assert.Equal(amount > 0, IsValid(new UpdateReservationRequest { SlotId = id, EnergyAmountKwh = amount }));
    }

    [Theory]
    [InlineData("")]
    [InlineData("not-a-guid")]
    [InlineData("00000000-0000-0000-0000-000000000000")]
    public void BothRequestsRejectInvalidSlotIds(string id)
    {
        // Required server references cannot be missing, malformed or empty GUIDs.
        Assert.False(IsValid(new CreateReservationRequest { SlotId = id, EnergyAmountKwh = 1 }));
        Assert.False(IsValid(new UpdateReservationRequest { SlotId = id, EnergyAmountKwh = 1 }));
    }

    [Theory]
    [InlineData("N")]
    [InlineData("D")]
    public void BothRequestsAcceptExistingGuidFormats(string format)
    {
        // Preserve stable GUID strings without forcing a new identifier representation.
        var id = Guid.NewGuid().ToString(format);
        Assert.True(IsValid(new CreateReservationRequest { SlotId = id, EnergyAmountKwh = 1 }));
        Assert.True(IsValid(new UpdateReservationRequest { SlotId = id, EnergyAmountKwh = 1 }));
    }

    [Theory]
    [InlineData("Station is under maintenance", true)]
    [InlineData("", false)]
    [InlineData("   ", false)]
    public void RejectRequestRequiresNonEmptyRemark(string remark, bool valid)
    {
        // Reject request requires a non-empty remark.
        var request = new RejectReservationRequest { Remark = remark };
        Assert.Equal(valid, IsValid(request));
    }

    [Fact]
    public void RequestsCannotBindServerOwnedFields()
    {
        // Existing JSON conventions ignore extra input; no authoritative field can bind to these DTOs.
        const string json = """
            {"slotId":"11111111111111111111111111111111","energyAmountKwh":1,
             "prosumerNic":"other","stationId":"other","status":"Completed","qrToken":"injected",
             "scheduledStartAtUtc":"2030-01-01T00:00:00Z"}
            """;
        var options = WireOptions();
        var create = JsonSerializer.Deserialize<CreateReservationRequest>(json, options)!;
        var update = JsonSerializer.Deserialize<UpdateReservationRequest>(json, options)!;
        Assert.True(IsValid(create));
        Assert.True(IsValid(update));
        foreach (var request in new object[] { create, update })
        {
            using var document = JsonDocument.Parse(JsonSerializer.Serialize(request, request.GetType(), options));
            Assert.Equal(new[] { "energyAmountKwh", "slotId" },
                document.RootElement.EnumerateObject().Select(x => x.Name).OrderBy(x => x).ToArray());
        }
    }

    [Fact]
    public void SummaryUsesCamelCaseStringStatusAndUtcSchedule()
    {
        // The planned response carries server times and identifiers, never a QR credential.
        var start = new DateTime(2030, 1, 2, 0, 0, 0, DateTimeKind.Utc);
        var response = new ReservationResponse("reservation", "prosumer", "station", "slot",
            1.5m, start, start.AddHours(1), ReservationStatus.Pending, start.AddDays(-1), start.AddDays(-1));
        using var json = JsonDocument.Parse(JsonSerializer.Serialize(response, WireOptions()));
        Assert.Equal("Pending", json.RootElement.GetProperty("status").GetString());
        Assert.EndsWith("Z", json.RootElement.GetProperty("scheduledStartAtUtc").GetString());
        Assert.Equal(start, json.RootElement.GetProperty("scheduledStartAtUtc").GetDateTime());
        Assert.False(json.RootElement.TryGetProperty("qrToken", out _));
    }

    private static bool IsValid(object request)
    {
        // Use the same DataAnnotations validation entry point as the application boundary.
        return Validator.TryValidateObject(request, new ValidationContext(request), [], true);
    }

    private static JsonSerializerOptions WireOptions()
    {
        // Match the existing MVC camelCase/string-enum settings without changing shared options.
        var options = new JsonSerializerOptions(JsonSerializerDefaults.Web);
        options.Converters.Add(new JsonStringEnumConverter(allowIntegerValues: false));
        return options;
    }
}
