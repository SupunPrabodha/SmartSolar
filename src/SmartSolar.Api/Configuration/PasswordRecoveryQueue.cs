/*
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Enterprise experience and operations security.
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
    public bool TryQueue(string identifier, string correlation) => queue.Writer.TryWrite((identifier, correlation));
    public IAsyncEnumerable<(string Identifier, string Correlation)> ReadAsync(CancellationToken ct) => queue.Reader.ReadAllAsync(ct);
}
public sealed class PasswordRecoveryWorker(PasswordRecoveryQueue queue, IServiceScopeFactory scopes,
    ILogger<PasswordRecoveryWorker> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
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
