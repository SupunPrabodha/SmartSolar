/*
 * File: SmtpVerificationEmailSender.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Sends Prosumer verification links through deployment-configured SMTP.
 */
using System.Net;
using System.Net.Mail;
using SmartSolar.Application.Abstractions.Users;
using SmartSolar.Application.Exceptions;

namespace SmartSolar.Api.Configuration;

public sealed class SmtpVerificationEmailSender(IConfiguration configuration) : IVerificationEmailSender
{
    public void EnsureConfigured()
    {
        // Fail before changing account state if the required private deployment settings are missing.
        var section = configuration.GetSection("VerificationEmail");
        if (new[] { "Host", "Username", "Password", "From", "VerificationPageUrl" }
            .Any(key => string.IsNullOrWhiteSpace(section[key])) ||
            !int.TryParse(section["Port"], out var port) || port is < 1 or > 65535 || port == 465 ||
            !MailAddress.TryCreate(section["From"], out _) ||
            !Uri.TryCreate(section["VerificationPageUrl"], UriKind.Absolute, out var page) ||
            (page.Scheme != "https" && !(page.Scheme == "http" && page.IsLoopback)) ||
            !string.IsNullOrEmpty(page.Query) || !string.IsNullOrEmpty(page.Fragment) || !string.IsNullOrEmpty(page.UserInfo))
            throw new ConflictException("Verification email is not configured. Configure VerificationEmail SMTP settings and the public verification page URL before approving Prosumers.");
    }

    public async Task SendAsync(string email, string nic, string token, CancellationToken cancellationToken = default)
    {
        // Use STARTTLS and a fragment link so bearer tokens are not sent in page requests or referrers.
        EnsureConfigured();
        var settings = configuration.GetSection("VerificationEmail");
        var link = settings["VerificationPageUrl"] + "#nic=" + Uri.EscapeDataString(nic) + "&token=" + Uri.EscapeDataString(token);
        using var message = new MailMessage(settings["From"]!, email)
        {
            Subject = "Smart Solar: verify your email",
            Body = "Backoffice has approved your Smart Solar account. Open the link below and select Verify email to activate your account. " +
                "The link expires in 24 hours and can only be used once.\n\n" + link +
                "\n\nAfter verification, sign in to the Android app using your NIC and the password you chose. " +
                "If you did not request this account, ignore this email.",
            IsBodyHtml = false
        };
        using var client = new SmtpClient(settings["Host"], int.Parse(settings["Port"]!))
        {
            EnableSsl = true,
            UseDefaultCredentials = false,
            Credentials = new NetworkCredential(settings["Username"], settings["Password"])
        };
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(30));
        try { await client.SendMailAsync(message, timeout.Token); }
        catch (Exception error) when (error is SmtpException || (error is OperationCanceledException && !cancellationToken.IsCancellationRequested))
        {
            // Do not expose SMTP credentials, addresses or provider responses to clients or logs.
            throw new ConflictException("The account remains pending because verification email delivery failed. Check SMTP settings, then resend after one minute.");
        }
    }
}
