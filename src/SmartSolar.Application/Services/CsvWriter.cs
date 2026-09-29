/*
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Enterprise experience and operations security.
 */
using System.Text;
namespace SmartSolar.Application.Services;

public static class CsvWriter
{
    // Formula prefix protection also covers leading control characters/whitespace before an operator.
    public static string Cell(string? value)
    {
        var text = value ?? "";
        var trimmed = text.TrimStart();
        if (trimmed.Length > 0 && "=+-@".Contains(trimmed[0])) text = "'" + text;
        return "\"" + text.Replace("\"", "\"\"") + "\"";
    }
    public static byte[] Encode(IEnumerable<IEnumerable<string?>> rows) =>
        new UTF8Encoding(true).GetPreamble().Concat(Encoding.UTF8.GetBytes(
            string.Join("\r\n", rows.Select(row => string.Join(",", row.Select(Cell)))) + "\r\n")).ToArray();
}
