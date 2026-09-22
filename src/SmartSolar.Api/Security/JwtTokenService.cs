/*
 * File: JwtTokenService.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Issues signed JWT access tokens containing identity and role claims.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;
using SmartSolar.Api.Configuration;
using SmartSolar.Application.Abstractions.Auth;
using SmartSolar.Domain.Entities;

namespace SmartSolar.Api.Security;

public sealed class JwtTokenService : IJwtTokenService
{
    private readonly JwtOptions _options;

    public JwtTokenService(IOptions<JwtOptions> options)
    {
        // Capture validated JWT settings used for every access token issued by this service.
        _options = options.Value;
    }

    public TokenResult CreateToken(User user)
    {
        // Build a short-lived signed JWT containing the user's immutable NIC and authorization role.
        var now = DateTime.UtcNow;
        var expires = now.AddMinutes(_options.ExpiryMinutes);

        var claims = new[]
        {
            new Claim(ClaimTypes.NameIdentifier, user.Nic),
            new Claim(ClaimTypes.Name, user.FullName),
            new Claim(ClaimTypes.Email, user.Email),
            new Claim(ClaimTypes.Role, user.Role.ToString())
        };

        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_options.Key));
        var credentials = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);

        var token = new JwtSecurityToken(
            issuer: _options.Issuer,
            audience: _options.Audience,
            claims: claims,
            notBefore: now,
            expires: expires,
            signingCredentials: credentials);

        return new TokenResult(new JwtSecurityTokenHandler().WriteToken(token), expires);
    }
}
