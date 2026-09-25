/*
 * File: ReservationSwaggerFilter.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Documents reservation testing inputs, roles and responses in Swagger.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using Microsoft.AspNetCore.Mvc;
using Microsoft.OpenApi.Any;
using Microsoft.OpenApi.Models;
using SmartSolar.Api.Controllers;
using SmartSolar.Application.DTOs.Reservations;
using Swashbuckle.AspNetCore.SwaggerGen;

namespace SmartSolar.Api.Configuration;

public sealed class ReservationSwaggerFilter : IOperationFilter, ISchemaFilter
{
    public void Apply(OpenApiOperation operation, OperationFilterContext context)
    {
        // Enrich only reservation operations; runtime authorization and validation remain unchanged.
        if (context.MethodInfo.DeclaringType != typeof(ReservationsController)) return;
        var action = context.MethodInfo.Name;
        var creates = action is nameof(ReservationsController.Create) or nameof(ReservationsController.CreateFor);
        (operation.Summary, operation.Description) = action switch
        {
            nameof(ReservationsController.List) => (
                "List reservations (GridOperator)",
                "Requires an active GridOperator. Optional status, prosumerNic and stationId filters are exact matches combined with AND. Blank filters are omitted; NIC is trimmed and uppercased. Returns an array ordered by createdAtUtc descending, then reservationId. No matches returns []. Legacy records missing accepted snapshots return 409 until backfilled."),
            nameof(ReservationsController.Create) => (
                "Create my reservation (Prosumer)",
                "Requires an active Prosumer. Ownership comes from the signed-in account. The slot must start after server UTC now and no later than seven days from now (inclusive)."),
            nameof(ReservationsController.CreateFor) => (
                "Create an assisted reservation (GridOperator)",
                "Requires an active GridOperator and an active Prosumer identified by prosumerNic. The same seven-day, availability and overlap rules apply."),
            nameof(ReservationsController.GetById) => (
                "Inspect a reservation (owner or GridOperator)",
                "Requires the owning active Prosumer or an active GridOperator. Returns the accepted UTC schedule and current status."),
            nameof(ReservationsController.Update) => (
                "Modify a reservation (owner or GridOperator)",
                "Only Pending or Approved reservations can change. Both the accepted start and replacement start must be at least 12 hours away (inclusive); the replacement must also be within seven days. Successful updates return Pending and clear stale QR data."),
            nameof(ReservationsController.Cancel) => (
                "Cancel a reservation (owner or GridOperator)",
                "Only Pending or Approved reservations can be cancelled, at least 12 hours before the accepted start (inclusive). No request body is needed. Returns the Cancelled summary; a repeated cancellation returns 409."),
            nameof(ReservationsController.Approve) => (
                "Approve a reservation (GridOperator)",
                "Requires an active GridOperator. Only Pending reservations can be approved. Returns the Approved summary; retains slot capacity."),
            _ => (
                "Reject a reservation (GridOperator)",
                "Requires an active GridOperator. Only Pending reservations can be rejected with a mandatory remark. Returns the Rejected summary and releases slot capacity.")
        };
        operation.Description += "\n\nBackoffice is not permitted. Log in via POST /api/v1/auth/login, then use Authorize and paste only accessToken (without the Bearer prefix).";
        if (creates || action == nameof(ReservationsController.Update))
            operation.Description += "\n\nReplace the example slotId with an existing active EnergyBookingSlots document's _id. Its SolarStationInfo record must exist and be active. Energy must be greater than zero; a new slot needs available capacity. The example ID is a placeholder, not seeded data. Slot/station CRUD and listing endpoints are not part of this checkpoint.";

        foreach (var parameter in operation.Parameters)
            parameter.Description = parameter.Name.ToLowerInvariant() switch
            {
                "status" => "Optional: Pending, Approved, Rejected, Cancelled or Completed.",
                "stationid" => "Optional exact station GUID string; copy the persisted ID.",
                "prosumernic" => action == nameof(ReservationsController.List)
                    ? "Optional exact Prosumer NIC; whitespace is trimmed and letters uppercased."
                    : "Existing active Prosumer NIC for assisted creation.",
                _ => "Use reservationId returned by a successful create response."
            };

        operation.Responses.Clear();
        var success = new OpenApiResponse
        {
            Description = creates ? "Created Pending reservation. Use Location to retrieve it." : action == nameof(ReservationsController.List) ? "Matching reservation summaries; an empty array means no matches." : "Reservation summary.",
            Content = new Dictionary<string, OpenApiMediaType>
            {
                ["application/json"] = new() { Schema = context.SchemaGenerator.GenerateSchema(action == nameof(ReservationsController.List) ? typeof(IReadOnlyList<ReservationResponse>) : typeof(ReservationResponse), context.SchemaRepository) }
            }
        };
        if (creates)
            success.Headers["Location"] = new OpenApiHeader
            {
                Description = "URL of GET /api/v1/reservations/{reservationId}.",
                Schema = new OpenApiSchema { Type = "string", Format = "uri" }
            };
        operation.Responses[creates ? "201" : "200"] = success;
        foreach (var (code, description) in new[]
        {
            ("400", "Invalid JSON, request fields, energy amount, schedule or seven-day horizon. Validation failures may include errors."),
            ("401", "Missing, invalid or expired JWT, or stored account no longer matches the token."),
            ("403", "Wrong role, another Prosumer's reservation, or inactive/invalid assisted target."),
            ("404", "Reservation, slot, station or assisted Prosumer not found."),
            ("409", "Overlap, unavailable/inactive slot or station, insufficient 12-hour notice, terminal state, concurrent write, or reconciliation/backfill required."),
            ("500", "Unexpected server failure; internal details are not returned.")
        })
            operation.Responses[code] = new OpenApiResponse
            {
                Description = description,
                Content = new Dictionary<string, OpenApiMediaType>
                {
                    ["application/problem+json"] = new() { Schema = context.SchemaGenerator.GenerateSchema(typeof(ProblemDetails), context.SchemaRepository) }
                }
            };
    }

    public void Apply(OpenApiSchema schema, SchemaFilterContext context)
    {
        // Describe IValidatableObject constraints that Swagger cannot infer from attributes alone.
        if (context.Type != typeof(CreateReservationRequest) && context.Type != typeof(UpdateReservationRequest)) return;
        schema.Description = "Only slot selection and positive energy are accepted. Identity, station, schedule, status and timestamps are server-owned.";
        schema.Required.Add("energyAmountKwh");
        schema.Properties["slotId"].Description = "Existing nonempty GUID string, copied exactly from the slot _id. N and D GUID formats are accepted.";
        schema.Properties["energyAmountKwh"].Description = "Requested energy in kWh, strictly greater than zero.";
        schema.Properties["energyAmountKwh"].Minimum = 0;
        schema.Properties["energyAmountKwh"].ExclusiveMinimum = true;
        schema.Example = new OpenApiObject
        {
            ["slotId"] = new OpenApiString("11111111111111111111111111111111"),
            ["energyAmountKwh"] = new OpenApiDouble(1.5)
        };
    }
}
