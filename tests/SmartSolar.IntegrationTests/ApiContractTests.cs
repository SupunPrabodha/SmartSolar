/*
 * File: ApiContractTests.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Verifies HTTP error formatting and BSON mapping without external services.
 */
using System.Text.Json;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging.Abstractions;
using MongoDB.Bson;
using SmartSolar.Api.Middleware;
using SmartSolar.Application.Exceptions;
using SmartSolar.Domain.Entities;
using SmartSolar.Infrastructure.Persistence;
using Xunit;

namespace SmartSolar.IntegrationTests;

public sealed class ApiContractTests
{
    [Theory]
    [InlineData(400)]
    [InlineData(401)]
    [InlineData(403)]
    [InlineData(404)]
    [InlineData(409)]
    [InlineData(500)]
    public async Task MiddlewareReturnsProblemJsonWithoutLeakingServerDetails(int status)
    {
        // Exercise the actual middleware response writer and its status/content-type mapping.
        Exception exception = status switch
        {
            400 => new BadRequestException("Invalid request"),
            401 => new UnauthorizedException("Invalid credentials"),
            403 => new ForbiddenException("Not allowed"),
            404 => new NotFoundException("Missing user"),
            409 => new ConflictException("Duplicate user"),
            _ => new InvalidOperationException("internal-sensitive-detail")
        };
        using var services = new ServiceCollection().AddOptions().BuildServiceProvider();
        var context = new DefaultHttpContext { RequestServices = services };
        using var body = new MemoryStream();
        context.Response.Body = body;
        var middleware = new ExceptionHandlingMiddleware(_ => Task.FromException(exception), NullLogger<ExceptionHandlingMiddleware>.Instance);
        await middleware.InvokeAsync(context);
        Assert.Equal(status, context.Response.StatusCode);
        Assert.StartsWith("application/problem+json", context.Response.ContentType);
        body.Position = 0;
        using var json = await JsonDocument.ParseAsync(body);
        Assert.Equal(status, json.RootElement.GetProperty("status").GetInt32());
        if (status == 500) Assert.DoesNotContain("internal-sensitive-detail", json.RootElement.GetRawText());
    }

    [Fact]
    public void AllEntityIdsMapToMongoDocumentIds()
    {
        // Future feature entities retain their starter identifier contract after moving BSON mappings.
        MongoMappings.Register();
        Assert.Equal("station", new SolarStation { StationId = "station" }.ToBsonDocument()["_id"].AsString);
        Assert.Equal("slot", new EnergyBookingSlot { SlotId = "slot" }.ToBsonDocument()["_id"].AsString);
        Assert.Equal("reservation", new EnergyReservation { ReservationId = "reservation" }.ToBsonDocument()["_id"].AsString);
    }
}
