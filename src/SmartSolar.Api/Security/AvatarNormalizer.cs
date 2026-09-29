/*
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Enterprise experience and operations security.
 */
using SkiaSharp;
using SmartSolar.Application.Exceptions;
namespace SmartSolar.Api.Security;

public static class AvatarNormalizer
{
    public static byte[] Normalize(byte[] input)
    {
        if (input.Length is < 1 or > 2_000_000) throw Invalid();
        using var data = SKData.CreateCopy(input);
        using var codec = SKCodec.Create(data);
        if (codec is null || codec.EncodedFormat is not (SKEncodedImageFormat.Jpeg or SKEncodedImageFormat.Png or SKEncodedImageFormat.Webp) ||
            codec.Info.Width is < 1 or > 4096 || codec.Info.Height is < 1 or > 4096 ||
            (long)codec.Info.Width * codec.Info.Height > 12_000_000 || codec.FrameCount > 1) throw Invalid();
        using var decoded = new SKBitmap(codec.Info.Width, codec.Info.Height, SKColorType.Rgba8888, SKAlphaType.Premul);
        if (codec.GetPixels(decoded.Info, decoded.GetPixels()) != SKCodecResult.Success) throw Invalid();
        var scale = Math.Min(1d, 512d / Math.Max(decoded.Width, decoded.Height));
        using var target = new SKBitmap(Math.Max(1, (int)(decoded.Width * scale)), Math.Max(1, (int)(decoded.Height * scale)));
        using (var canvas = new SKCanvas(target))
        {
            canvas.Clear(SKColors.White);
            canvas.DrawBitmap(decoded, new SKRect(0, 0, target.Width, target.Height));
        }
        // Re-encode pixels only: strip filenames, EXIF, arbitrary metadata and embedded payloads.
        using var image = SKImage.FromBitmap(target);
        using var encoded = image.Encode(SKEncodedImageFormat.Jpeg, 85);
        return encoded.ToArray();
    }
    private static BadRequestException Invalid() => new("Choose a valid, still JPEG, PNG or WebP image under 2 MB and 4096 pixels per side.");
}
