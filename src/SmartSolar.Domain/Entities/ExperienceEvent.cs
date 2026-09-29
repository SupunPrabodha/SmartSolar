/*
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Enterprise experience and operations security.
 */
namespace SmartSolar.Domain.Entities;

// Bounded, server-authored records. Never place credentials or request bodies here.
public sealed class AuditEntry
{
    public string Id { get; set; } = Guid.NewGuid().ToString("N");
    public DateTime AtUtc { get; set; }
    public string ActorNic { get; set; } = "";
    public string Event { get; set; } = "";
    public bool Delivered { get; set; } = true;
    public string? RecipientNic { get; set; }
    public string? RecipientRole { get; set; }
    public string? ResourceId { get; set; }
    public string Action { get; set; } = "Profile";
    public string Category { get; set; } = "Account";
    public string Priority { get; set; } = "Low";
    public string Message { get; set; } = "";
    public string CorrelationId { get; set; } = "";
}

public sealed class InboxNotification
{
    public string Id { get; set; } = "";
    public DateTime AtUtc { get; set; }
    public DateTime? ReadAtUtc { get; set; }
    public string Category { get; set; } = "";
    public string Priority { get; set; } = "Low";
    public string Message { get; set; } = "";
    public string Action { get; set; } = "Profile";
    public string? ResourceId { get; set; }
}
