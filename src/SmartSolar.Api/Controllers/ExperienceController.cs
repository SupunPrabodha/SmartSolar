/*
 * File: ExperienceController.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Liyanage S. P. (IT23187450)
 * Purpose: Exposes authorized avatar, inbox, audit, search and CSV export endpoints.
 */
using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SmartSolar.Api.Extensions;
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.Exceptions;
using SmartSolar.Domain.Enums;
namespace SmartSolar.Api.Controllers;

[ApiController, Authorize, Route("api/v1")]
public sealed class ExperienceController(IExperienceRepository repository, TimeProvider clock) : ControllerBase
{
    private UserRole Role => Enum.Parse<UserRole>(User.FindFirstValue(ClaimTypes.Role)!);
    [HttpGet("users/me/avatar")]
    public async Task<IActionResult> Avatar(CancellationToken ct)
    {
        // Return only the authenticated user's normalized avatar without shared caching.
        var image = await repository.GetAvatarAsync(User.GetNic(), ct);
        Response.Headers.CacheControl = "private, no-store";
        return image.Bytes is null ? NotFound() : File(image.Bytes, "image/jpeg");
    }
    [HttpPut("users/me/avatar"), RequestSizeLimit(2_100_000), RequestFormLimits(MultipartBodyLengthLimit = 2_100_000)]
    public async Task<IActionResult> Upload(IFormFile file, CancellationToken ct)
    {
        // Validate and normalize the uploaded image before storing it on the current account.
        if (file.Length is < 1 or > 2_000_000) throw new BadRequestException("Choose a JPEG, PNG or WebP image under 2 MB.");
        using var stream = new MemoryStream();
        await file.CopyToAsync(stream, ct);
        var bytes = SmartSolar.Api.Security.AvatarNormalizer.Normalize(stream.ToArray());
        await repository.SetAvatarAsync(User.GetNic(), bytes, ct);
        return NoContent();
    }
    [HttpDelete("users/me/avatar")]
    public async Task<IActionResult> RemoveAvatar(CancellationToken ct)
    {
        // Remove the owner's avatar and invalidate its profile-completion marker.
        await repository.SetAvatarAsync(User.GetNic(), null, ct);
        return NoContent();
    }
    [HttpGet("notifications")]
    public async Task<IActionResult> Inbox([FromQuery] bool unreadOnly, [FromQuery] string? priority, CancellationToken ct)
    {
        // Filter the owner's retained notifications while preserving the total unread count.
        if (priority is not null && priority is not ("High" or "Medium" or "Low"))
            throw new BadRequestException("Unsupported priority.");
        var all = await repository.InboxAsync(User.GetNic(), ct);
        return Ok(new { unreadCount = all.Count(x => x.ReadAtUtc is null),
            items = all.Where(x => (!unreadOnly || x.ReadAtUtc is null) && (priority is null || x.Priority == priority)) });
    }
    [HttpPost("notifications/read-all")]
    public async Task<IActionResult> ReadAll(CancellationToken ct)
    {
        // Mark the owner's unread notifications with the server read timestamp.
        await repository.ReadAsync(User.GetNic(), null, clock.GetUtcNow().UtcDateTime, ct);
        return NoContent();
    }
    [HttpPost("notifications/{id}/read")]
    public async Task<IActionResult> Read(string id, CancellationToken ct)
    {
        // Validate the notification identifier and mark only the owner's matching item read.
        if (id.Length != 32 || !id.All(Uri.IsHexDigit)) throw new BadRequestException("Invalid notification.");
        await repository.ReadAsync(User.GetNic(), id, clock.GetUtcNow().UtcDateTime, ct);
        return NoContent();
    }
    [HttpGet("audit/{kind}/{id}")]
    public async Task<IActionResult> Audit(string kind, string id, CancellationToken ct)
    {
        // Return permitted entity history without exposing internal delivery metadata.
        return Ok((await repository.AuditAsync(kind, id == "me" && kind == "users" ? User.GetNic() : id, User.GetNic(), Role, ct))
            .Select(x => new { x.Id, x.AtUtc, x.ActorNic, x.Event, x.CorrelationId }));
    }

    [HttpGet("search")]
    public async Task<IActionResult> Search([FromQuery] string q, CancellationToken ct)
    {
        // Delegate the trimmed search to the repository's role and ownership filters.
        return Ok(await repository.SearchAsync(q.Trim(), User.GetNic(), Role, ct));
    }

    [HttpGet("exports/{kind}.csv")]
    public async Task<IActionResult> Export(string kind, [FromQuery] ExportQuery query, CancellationToken ct)
    {
        // Require explicit date offsets and return the authorized, bounded CSV download.
        if (query.FromUtc?.Kind == DateTimeKind.Unspecified || query.ToUtc?.Kind == DateTimeKind.Unspecified)
            throw new BadRequestException("Export dates must include a UTC offset.");
        Response.Headers.CacheControl = "private, no-store";
        return File(await repository.ExportAsync(kind, query, User.GetNic(), Role, ct), "text/csv; charset=utf-8", kind + ".csv");
    }
}
