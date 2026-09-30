/*
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Enterprise experience and operations security.
 */
using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Security.Cryptography;
using System.Text.Json;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.Extensions.Logging;
using MongoDB.Bson;
using MongoDB.Driver;
using SkiaSharp;
using SmartSolar.Application.Abstractions.Auth;
using SmartSolar.Application.Abstractions.Security;
using SmartSolar.Domain.Constants;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;
using SmartSolar.Infrastructure.Security;
using Xunit;
namespace SmartSolar.IntegrationTests;

public sealed class EnterpriseApiTests
{
    private sealed class Mail : IAccountSecurityEmailSender
    {
        public readonly TaskCompletionSource<string> Reset = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public Task SendResetAsync(string email,string token,CancellationToken ct) { Reset.TrySetResult(token);return Task.CompletedTask; }
        public Task SendChangedAsync(string email,bool reset,CancellationToken ct) => Task.CompletedTask;
    }
    private sealed class Factory(string connection,string name,Mail mail) : WebApplicationFactory<Program>
    {
        protected override void ConfigureWebHost(IWebHostBuilder builder)
        {
            builder.UseEnvironment("Testing");
            builder.UseSetting("MongoDb:ConnectionString",connection);builder.UseSetting("MongoDb:DatabaseName",name);
            builder.UseSetting("Jwt:Key",Convert.ToHexString(RandomNumberGenerator.GetBytes(32)));
            builder.UseSetting("Jwt:Issuer","Enterprise.Tests");builder.UseSetting("Jwt:Audience","Enterprise.Tests");
            builder.UseSetting("Jwt:ExpiryMinutes","60");
            builder.ConfigureLogging(x=>x.ClearProviders());
            builder.ConfigureServices(s=>{s.RemoveAll<IAccountSecurityEmailSender>();s.AddSingleton<IAccountSecurityEmailSender>(mail);});
        }
    }
    private static async Task WithApi(Func<Factory,IMongoDatabase,Mail,Task> action)
    {
        var connection=Environment.GetEnvironmentVariable("SMARTSOLAR_TEST_MONGO")!;
        var mongo=new MongoClient(connection);var name="SmartSolarTests_"+Guid.NewGuid().ToString("N");
        var mail=new Mail();
        try { await using var factory=new Factory(connection,name,mail); _=factory.Services; await action(factory,mongo.GetDatabase(name),mail); }
        finally {await mongo.DropDatabaseAsync(name);}
    }
    private static async Task<User> Seed(IMongoDatabase db,string nic,UserRole role)
    {
        var user=new User {Nic=nic,FullName="Test User",Email=nic.ToLowerInvariant()+"@example.invalid",PhoneNumber="0123456789",Role=role,Status=UserStatus.Active};
        user.PasswordHash=new PasswordService().HashPassword(user,"Initial-Test-Password");
        await db.GetCollection<User>(CollectionNames.Users).InsertOneAsync(user);return user;
    }
    private static HttpClient Client(Factory f,User? user=null)
    {
        var client=f.CreateClient(new WebApplicationFactoryClientOptions{BaseAddress=new Uri("https://localhost"),AllowAutoRedirect=false});
        if(user!=null)client.DefaultRequestHeaders.Authorization=new AuthenticationHeaderValue("Bearer",f.Services.GetRequiredService<IJwtTokenService>().CreateToken(user).AccessToken);
        return client;
    }
    [MongoFact]
    public Task HttpPasswordFlowsRejectOldJwtAndProtectAnonymousRecovery() => WithApi(async(f,db,mail)=>{
        var user=await Seed(db,"200012345678",UserRole.Prosumer);
        using var anonymous=Client(f);using var old=Client(f,user);
        using var denied=await anonymous.PostAsJsonAsync("/api/v1/users/me/change-password",new{currentPassword="Initial-Test-Password",newPassword="Changed-Test-Password"});
        Assert.Equal(HttpStatusCode.Unauthorized,denied.StatusCode);
        using var unknown=await anonymous.PostAsJsonAsync("/api/v1/auth/forgot-password",new{identifier="missing@example.invalid"});
        using var known=await anonymous.PostAsJsonAsync("/api/v1/auth/forgot-password",new{identifier=user.Email});
        Assert.Equal(unknown.StatusCode,known.StatusCode);Assert.Equal(await unknown.Content.ReadAsStringAsync(),await known.Content.ReadAsStringAsync());
        var token=await mail.Reset.Task.WaitAsync(TimeSpan.FromSeconds(10));
        using var reset=await anonymous.PostAsJsonAsync("/api/v1/auth/reset-password",new{token,newPassword="Reset-Test-Password"});
        Assert.Equal(HttpStatusCode.OK,reset.StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized,(await old.GetAsync("/api/v1/users/me")).StatusCode);
        user=(await db.GetCollection<User>(CollectionNames.Users).Find(x=>x.Nic==user.Nic).SingleAsync());
        using var current=Client(f,user);
        Assert.Equal(HttpStatusCode.OK,(await current.GetAsync("/api/v1/users/me")).StatusCode);
        using var wrong=await current.PostAsJsonAsync("/api/v1/users/me/change-password",new{currentPassword="wrong",newPassword="Changed-Test-Password"});
        Assert.Equal(HttpStatusCode.BadRequest,wrong.StatusCode);
        using var change=await current.PostAsJsonAsync("/api/v1/users/me/change-password",new{currentPassword="Reset-Test-Password",newPassword="Changed-Test-Password"});
        Assert.Equal(HttpStatusCode.OK,change.StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized,(await current.GetAsync("/api/v1/users/me")).StatusCode);
        using var login=await anonymous.PostAsJsonAsync("/api/v1/auth/login",new{nic=user.Nic,password="Changed-Test-Password"});
        Assert.Equal(HttpStatusCode.OK,login.StatusCode);
        using var json=JsonDocument.Parse(await login.Content.ReadAsStringAsync());
        anonymous.DefaultRequestHeaders.Authorization=new AuthenticationHeaderValue("Bearer",json.RootElement.GetProperty("accessToken").GetString());
        Assert.Equal(HttpStatusCode.OK,(await anonymous.GetAsync("/api/v1/users/me")).StatusCode);
    });
    [MongoFact]
    public Task MultipartAvatarProfileCompletionAndCorrelationAreCompatible() => WithApi(async(f,db,mail)=>{
        var user=await Seed(db,"200012345678",UserRole.GridOperator);using var client=Client(f,user);
        byte[] bytes;
        using(var bitmap=new SKBitmap(800,600)){using var canvas=new SKCanvas(bitmap);canvas.Clear(SKColors.Green);using var image=SKImage.FromBitmap(bitmap);using var encoded=image.Encode(SKEncodedImageFormat.Png,100);bytes=encoded.ToArray();}
        using var multipart=new MultipartFormDataContent();
        multipart.Add(new ByteArrayContent(bytes),"file","photo.png");
        using var upload=await client.PutAsync("/api/v1/users/me/avatar",multipart);
        Assert.Equal(HttpStatusCode.NoContent,upload.StatusCode);
        using var save=await client.PutAsJsonAsync("/api/v1/users/me",new{fullName="Saved Profile",email=user.Email,phoneNumber=user.PhoneNumber});
        Assert.Equal(HttpStatusCode.OK,save.StatusCode);
        using var profile=JsonDocument.Parse(await save.Content.ReadAsStringAsync());
        Assert.True(profile.RootElement.GetProperty("profileComplete").GetBoolean());
        Assert.False(profile.RootElement.TryGetProperty("avatarBytes",out _));
        Assert.False(profile.RootElement.TryGetProperty("securityVersion",out _));
        using var avatar=await client.GetAsync("/api/v1/users/me/avatar");
        Assert.Equal("image/jpeg",avatar.Content.Headers.ContentType?.MediaType);
        Assert.True(avatar.Headers.CacheControl?.NoStore);
        using var decoded=SKBitmap.Decode(await avatar.Content.ReadAsByteArrayAsync());Assert.True(decoded.Width<=512&&decoded.Height<=512);
        using var invalid=new MultipartFormDataContent();invalid.Add(new ByteArrayContent("not a real PNG"u8.ToArray()),"file","evil.png");
        using var rejected=await client.PutAsync("/api/v1/users/me/avatar",invalid);
        Assert.Equal(HttpStatusCode.BadRequest,rejected.StatusCode);
        using var bad=await client.PutAsJsonAsync("/api/v1/users/me",new{fullName="",email="invalid",phoneNumber=""});
        using var problem=JsonDocument.Parse(await bad.Content.ReadAsStringAsync());
        var reference=bad.Headers.GetValues("X-Correlation-ID").Single();
        Assert.Equal(reference,problem.RootElement.GetProperty("correlationId").GetString());
        Assert.Equal(reference,problem.RootElement.GetProperty("traceId").GetString());
        Assert.Equal(HttpStatusCode.NoContent,(await client.DeleteAsync("/api/v1/users/me/avatar")).StatusCode);
        using var removed=JsonDocument.Parse(await (await client.GetAsync("/api/v1/users/me")).Content.ReadAsStringAsync());
        Assert.False(removed.RootElement.GetProperty("profileComplete").GetBoolean());
        var document=await db.GetCollection<User>(CollectionNames.Users).Find(x=>x.Nic==user.Nic).SingleAsync();
        Assert.Contains(document.AuditHistory,x=>x.Event=="AvatarUpdated");Assert.Contains(document.AuditHistory,x=>x.Event=="ProfileCompleted");
    });
    [MongoFact]
    public Task SearchExportAuditAndInboxRespectRolesAndFilters() => WithApi(async(f,db,mail)=>{
        var bo=await Seed(db,"BO",UserRole.Backoffice);var op=await Seed(db,"OP",UserRole.GridOperator);
        var p1=await Seed(db,"P1",UserRole.Prosumer);var p2=await Seed(db,"P2",UserRole.Prosumer);
        var station=new SolarStation{Name="=unsafe spreadsheet formula",IsActive=true};
        await db.GetCollection<SolarStation>(CollectionNames.Stations).InsertOneAsync(station);
        await db.GetCollection<EnergyReservation>(CollectionNames.Reservations).InsertManyAsync(new[]{
            new EnergyReservation{ReservationId="RS1",ProsumerNic=p1.Nic,StationId=station.StationId,Status=ReservationStatus.Pending},
            new EnergyReservation{ReservationId="RS2",ProsumerNic=p2.Nic,StationId=station.StationId,Status=ReservationStatus.Completed}});
        using var backoffice=Client(f,bo);using var operatorClient=Client(f,op);using var prosumer=Client(f,p1);
        Assert.Equal(HttpStatusCode.Forbidden,(await backoffice.GetAsync("/api/v1/exports/reservations.csv")).StatusCode);
        Assert.Equal(HttpStatusCode.Forbidden,(await operatorClient.GetAsync("/api/v1/exports/users.csv")).StatusCode);
        Assert.Equal(HttpStatusCode.Forbidden,(await backoffice.GetAsync("/api/v1/audit/reservations/RS1")).StatusCode);
        Assert.Equal(HttpStatusCode.NotFound,(await prosumer.GetAsync("/api/v1/audit/reservations/RS2")).StatusCode);
        Assert.Equal(HttpStatusCode.NoContent,(await prosumer.PostAsync("/api/v1/notifications/read-all",null)).StatusCode);
        var own=await (await prosumer.GetAsync("/api/v1/search?q=RS")).Content.ReadAsStringAsync();
        Assert.Contains("RS1",own);Assert.DoesNotContain("RS2",own);
        Assert.DoesNotContain("RS1",await(await backoffice.GetAsync("/api/v1/search?q=RS")).Content.ReadAsStringAsync());
        var csv=await(await operatorClient.GetAsync("/api/v1/exports/reservations.csv?status=Pending")).Content.ReadAsStringAsync();
        Assert.Contains("RS1",csv);Assert.DoesNotContain("RS2",csv);Assert.DoesNotContain("QrToken",csv);
        var stationCsv=await(await backoffice.GetAsync("/api/v1/exports/stations.csv")).Content.ReadAsStringAsync();
        Assert.Contains("'=unsafe",stationCsv);
        using var invalid=await operatorClient.GetAsync("/api/v1/search?q=*");
        Assert.Equal(HttpStatusCode.BadRequest,invalid.StatusCode);
    });
    [Theory]
    [InlineData("<svg/>")] [InlineData("bad png")] [InlineData("not jpeg")]
    public void AvatarRejectsMalformedAndUnsupportedImages(string input)
    {
        Assert.Throws<SmartSolar.Application.Exceptions.BadRequestException>(()=>SmartSolar.Api.Security.AvatarNormalizer.Normalize(System.Text.Encoding.UTF8.GetBytes(input)));
    }
}
