/*
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Enterprise experience and operations security.
 */
using System.ComponentModel.DataAnnotations;

namespace SmartSolar.Application.DTOs.Auth;

public sealed class PasswordPolicyAttribute : StringLengthAttribute
{
    public const int Minimum = 8;
    public const int Maximum = 100;
    public PasswordPolicyAttribute() : base(Maximum)
    {
        MinimumLength = Minimum;
        ErrorMessage = "Use a password between 8 and 100 characters.";
    }
}

public sealed class ForgotPasswordRequest
{
    [Required, StringLength(254)]
    public string Identifier { get; init; } = "";
}
public sealed class ResetPasswordRequest
{
    [Required, StringLength(64, MinimumLength = 64)]
    public string Token { get; init; } = "";
    [Required, PasswordPolicy]
    public string NewPassword { get; init; } = "";
}
public sealed class ChangePasswordRequest
{
    [Required, StringLength(100)]
    public string CurrentPassword { get; init; } = "";
    [Required, PasswordPolicy]
    public string NewPassword { get; init; } = "";
}
