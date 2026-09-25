/*
 * File: Program.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Shared project source file for the SE4040 EAD implementation.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

using System.Text;
using System.Text.Json.Serialization;
using System.Security.Claims;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;
using MongoDB.Driver;
using SmartSolar.Api.Configuration;
using SmartSolar.Api.Middleware;
using SmartSolar.Api.Security;
using SmartSolar.Api.Seed;
using SmartSolar.Application.Abstractions.Auth;
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.Abstractions.Security;
using SmartSolar.Application.Abstractions.Users;
using SmartSolar.Application.Services;
using SmartSolar.Infrastructure.Persistence;
using SmartSolar.Infrastructure.Persistence.Repositories;
using SmartSolar.Infrastructure.Security;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers().AddJsonOptions(options =>
    options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter(allowIntegerValues: false)));
builder.Services.AddProblemDetails();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddHealthChecks().AddCheck<MongoHealthCheck>("mongodb");

builder.Services.AddSwaggerGen(options =>
{
    options.OperationFilter<ReservationSwaggerFilter>();
    options.SchemaFilter<ReservationSwaggerFilter>();
    options.SwaggerDoc("v1", new OpenApiInfo
    {
        Title = "Smart Solar Microgrid API",
        Version = "v1"
    });

    options.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        Name = "Authorization",
        Type = SecuritySchemeType.Http,
        Scheme = "bearer",
        BearerFormat = "JWT",
        In = ParameterLocation.Header,
        Description = "Enter the JWT access token."
    });

    options.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        {
            new OpenApiSecurityScheme
            {
                Reference = new OpenApiReference
                {
                    Type = ReferenceType.SecurityScheme,
                    Id = "Bearer"
                }
            },
            Array.Empty<string>()
        }
    });
});

var mongoSettings = builder.Configuration
    .GetSection(MongoDbSettings.SectionName)
    .Get<MongoDbSettings>()
    ?? throw new InvalidOperationException("MongoDb configuration is missing.");

if (string.IsNullOrWhiteSpace(mongoSettings.ConnectionString) ||
    string.IsNullOrWhiteSpace(mongoSettings.DatabaseName))
{
    throw new InvalidOperationException("MongoDb ConnectionString and DatabaseName are required.");
}

MongoMappings.Register();
builder.Services.AddSingleton<IMongoClient>(_ => new MongoClient(mongoSettings.ConnectionString));
builder.Services.AddSingleton(sp =>
    sp.GetRequiredService<IMongoClient>().GetDatabase(mongoSettings.DatabaseName));
builder.Services.AddSingleton<MongoDbInitializer>();
builder.Services.AddScoped<IUserRepository, UserRepository>();

builder.Services.AddSingleton<TimeProvider>(TimeProvider.System);
builder.Services.AddScoped<ReservationRules>();
builder.Services.AddScoped<IReservationRepository, ReservationRepository>();
builder.Services.AddScoped<IReservationReadRepository, ReservationReadRepository>();
builder.Services.AddScoped<SmartSolar.Application.Abstractions.Reservations.IReservationQueryService, ReservationQueryService>();
builder.Services.AddScoped<SmartSolar.Application.Abstractions.Reservations.IReservationService, ReservationService>();
builder.Services.AddSingleton<IPasswordService, PasswordService>();
builder.Services.AddSingleton<IQrSecurityService, QrSecurityService>();
builder.Services.AddScoped<IAuthService, AuthService>();
builder.Services.AddScoped<IUserService, UserService>();

var jwtSection = builder.Configuration.GetSection(JwtOptions.SectionName);
var jwtOptions = jwtSection.Get<JwtOptions>()
                 ?? throw new InvalidOperationException("Jwt configuration is missing.");

if (string.IsNullOrWhiteSpace(jwtOptions.Key) || jwtOptions.Key.Length < 32)
{
    throw new InvalidOperationException("Jwt:Key must be at least 32 characters and supplied through user-secrets or environment variables.");
}

if (string.IsNullOrWhiteSpace(jwtOptions.Issuer) || string.IsNullOrWhiteSpace(jwtOptions.Audience) ||
    jwtOptions.ExpiryMinutes is < 1 or > 1440)
{
    throw new InvalidOperationException("Jwt Issuer/Audience are required and ExpiryMinutes must be between 1 and 1440.");
}

builder.Services.Configure<JwtOptions>(jwtSection);
builder.Services.AddSingleton<IJwtTokenService, JwtTokenService>();

builder.Services
    .AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.Events = new JwtBearerEvents
        {
            OnTokenValidated = async context =>
            {
                // Enforce current account status/role even when a previously issued JWT has not expired.
                var nic = context.Principal?.FindFirstValue(ClaimTypes.NameIdentifier);
                var users = context.HttpContext.RequestServices.GetRequiredService<IUserRepository>();
                var user = nic is null ? null : await users.GetByNicAsync(nic, context.HttpContext.RequestAborted);
                if (user is null || user.Status != SmartSolar.Domain.Enums.UserStatus.Active ||
                    context.Principal?.FindFirstValue(ClaimTypes.Role) != user.Role.ToString())
                {
                    context.Fail("Account is unavailable or its permissions have changed.");
                }
            }
        };
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidIssuer = jwtOptions.Issuer,
            ValidateAudience = true,
            ValidAudience = jwtOptions.Audience,
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtOptions.Key)),
            ValidateLifetime = true,
            ClockSkew = TimeSpan.FromMinutes(1),
            NameClaimType = System.Security.Claims.ClaimTypes.Name,
            RoleClaimType = System.Security.Claims.ClaimTypes.Role
        };
    });

builder.Services.AddAuthorization();

var allowedOrigins = builder.Configuration.GetSection("Cors:AllowedOrigins").Get<string[]>() ?? Array.Empty<string>();
builder.Services.AddCors(options =>
{
    options.AddPolicy("ClientApps", policy =>
    {
        if (allowedOrigins.Length > 0)
        {
            policy.WithOrigins(allowedOrigins)
                .AllowAnyHeader()
                .AllowAnyMethod();
        }
    });
});

builder.Services.AddScoped<DevelopmentDataSeeder>();

var app = builder.Build();

app.UseMiddleware<ExceptionHandlingMiddleware>();
app.UseStatusCodePages();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

if (!app.Environment.IsDevelopment())
{
    // Local emulator HTTP avoids host-only development certificates; deployed environments retain HTTPS redirection.
    app.UseHttpsRedirection();
}
app.UseCors("ClientApps");
app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();
app.MapHealthChecks("/health");

using (var scope = app.Services.CreateScope())
{
    var initializer = scope.ServiceProvider.GetRequiredService<MongoDbInitializer>();
    await initializer.InitializeAsync();

    if (app.Environment.IsDevelopment())
    {
        var seeder = scope.ServiceProvider.GetRequiredService<DevelopmentDataSeeder>();
        await seeder.SeedAsync();
    }
}

await app.RunAsync();

public partial class Program;
