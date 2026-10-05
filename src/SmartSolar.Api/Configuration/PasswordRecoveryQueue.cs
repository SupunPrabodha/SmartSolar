/*
 * File: PasswordRecoveryQueue.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Liyanage S. P. (IT23187450)
 * Purpose: Queues bounded password-recovery work for asynchronous scoped processing.
 */
using System.Threading.Channels;
using SmartSolar.Application.DTOs.Auth;
using SmartSolar.Application.Services;
namespace SmartSolar.Api.Configuration;

// The anonymous response never waits for an account lookup or SMTP delivery, avoiding timing enumeration.
// This bounded in-memory queue contains identifiers only, never passwords or raw reset tokens.
public sealed class PasswordRecoveryQueue
{
    private readonly Channel<(string Identifier, string Correlation)> queue =
        Channel.CreateBounded<(string, string)>(new BoundedChannelOptions(100) { FullMode = BoundedChannelFullMode.Wait });
    public bool TryQueue(string identifier, string correlation)
    {
        // Enqueue a recovery request only when the bounded worker queue has room.
        return queue.Writer.TryWrite((identifier, correlation));
    }
    public IAsyncEnumerable<(string Identifier, string Correlation)> ReadAsync(CancellationToken ct)
    {
        // Stream queued recovery identities until processing is cancelled.
        return queue.Reader.ReadAllAsync(ct);
    }
}
public sealed class PasswordRecoveryWorker(PasswordRecoveryQueue queue, IServiceScopeFactory scopes,
    ILogger<PasswordRecoveryWorker> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        // Process queued recovery requests with scoped dependencies and safe failure logging.
        await foreach (var item in queue.ReadAsync(stoppingToken))
        {
            using var scope = scopes.CreateScope();
            using var logScope = logger.BeginScope(new Dictionary<string,object> { ["CorrelationId"] = item.Correlation });
            try { await scope.ServiceProvider.GetRequiredService<PasswordSecurityService>()
                .ForgotAsync(new ForgotPasswordRequest { Identifier = item.Identifier }, stoppingToken); }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested) { break; }
            catch (Exception) { logger.LogWarning("Password recovery could not be processed; reference {CorrelationId}", item.Correlation); }
        }
    }
}
