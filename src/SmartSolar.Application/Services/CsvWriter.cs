/*
 * File: CsvWriter.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Liyanage S. P. (IT23187450)
 * Purpose: Escapes CSV values and protects spreadsheet consumers from formula execution.
 */
using System.Text;
namespace SmartSolar.Application.Services;

public static class CsvWriter
{
    // Formula prefix protection also covers leading control characters/whitespace before an operator.
    public static string Cell(string? value)
    {
        // Neutralize spreadsheet formulas and escape the value as a quoted CSV cell.
        var text = value ?? "";
        var trimmed = text.TrimStart();
        if (trimmed.Length > 0 && "=+-@".Contains(trimmed[0])) text = "'" + text;
        return "\"" + text.Replace("\"", "\"\"") + "\"";
    }
    public static byte[] Encode(IEnumerable<IEnumerable<string?>> rows)
    {
        // Encode escaped CSV rows as UTF-8 with a BOM and CRLF record separators.
        return new UTF8Encoding(true).GetPreamble().Concat(Encoding.UTF8.GetBytes(
            string.Join("\r\n", rows.Select(row => string.Join(",", row.Select(Cell)))) + "\r\n")).ToArray();
    }
}
