/*
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Enterprise experience and operations security.
 */
using SmartSolar.Application.Abstractions.Security;
using SmartSolar.Domain.Entities;
namespace SmartSolar.Infrastructure.Persistence;

internal static class AuditTrail
{
    public static AuditEntry Create(IRequestIdentity? identity, string name, string action, string resource,
        string? recipient = null, string? role = null) => new()
    {
        AtUtc = DateTime.UtcNow, ActorNic = identity?.Nic ?? "System", Event = name,
        CorrelationId = identity?.CorrelationId ?? Guid.NewGuid().ToString("N"),
        RecipientNic = recipient, RecipientRole = role, ResourceId = resource, Action = action,
        Category = action == "Profile" ? "Account" : action == "Reservation" ? "Reservation" : "Catalog",
        Priority = name.Contains("Rejected") || name.Contains("Deactivated") ? "High" :
            name.Contains("Approved") || name.Contains("Completed") || name.Contains("Cancelled") ? "Medium" : "Low",
        Message = name switch
        {
            "AccountApproved" => "Your account was approved. Check your email to verify access.",
            "AccountCreated" => "A new account was created.",
            "AccountActive" => "Your account details were updated. Your account is active.",
            "AccountPendingActivation" => "Your account is awaiting approval or email verification.",
            "AccountDeactivated" => "Your account was deactivated.",
            "StationUpdated" => "Station details or availability changed.",
            "StationCreated" => "A station was added.",
            "SlotUpdated" => "Booking slot details or availability changed.",
            "SlotCreated" => "A booking slot was added.",
            "ReservationApproved" => "Your reservation was approved.",
            "ReservationRejected" => "Your reservation was rejected. Open details for the reason.",
            "ReservationCancelled" => "Your reservation was cancelled.",
            "ReservationCompleted" => "Your energy transfer was completed.",
            "ReservationCreated" => "A reservation is awaiting review.",
            "QrIssued" => "A reservation QR was issued.",
            _ => "Your reservation details changed."
        },
        Delivered = recipient is null && role is null
    };
}
