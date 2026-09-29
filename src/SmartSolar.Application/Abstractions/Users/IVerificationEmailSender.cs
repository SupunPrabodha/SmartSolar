/*
 * File: IVerificationEmailSender.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Separates account approval from email transport.
 */
namespace SmartSolar.Application.Abstractions.Users;

public interface IVerificationEmailSender
{
    void EnsureConfigured();
    Task SendAsync(string email, string nic, string token, CancellationToken cancellationToken = default);
}
