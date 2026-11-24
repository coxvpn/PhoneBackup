using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Reflection;
using System.Text;
using System.Text.Json.Nodes;
using System.Threading.Tasks;

namespace PhoneBackup;

public static class Updater
{
    public record Info(Version Version, string Url, string Notes);

    public static string Repo =>
        Assembly.GetExecutingAssembly().GetCustomAttributes<AssemblyMetadataAttribute>()
            .FirstOrDefault(a => a.Key == "RepoSlug")?.Value ?? "";

    static Version Normalize(Version v) => new(v.Major, v.Minor, Math.Max(v.Build, 0));

    public static Version Current => Normalize(Assembly.GetExecutingAssembly().GetName().Version ?? new Version(1, 0, 0));

    static HttpClient NewClient()
    {
        var http = new HttpClient { Timeout = TimeSpan.FromMinutes(10) };
        http.DefaultRequestHeaders.UserAgent.ParseAdd("PhoneBackup-Updater");
        http.DefaultRequestHeaders.Accept.ParseAdd("application/vnd.github+json");
        return http;
    }

    /// آخرین ریلیز گیت‌هاب را برمی‌گرداند (ریپو باید عمومی/public باشد).
    public static async Task<Info> CheckAsync()
    {
        using var http = NewClient();
        var json = await http.GetStringAsync($"https://api.github.com/repos/{Repo}/releases/latest");
        var node = JsonNode.Parse(json)!;
        var tag = (node["tag_name"]?.GetValue<string>() ?? "").TrimStart('v', 'V');
        if (!Version.TryParse(tag, out var ver)) throw new Exception("Bad release tag: " + tag);

        string? url = null;
        foreach (var a in node["assets"]!.AsArray())
        {
            var name = a!["name"]?.GetValue<string>() ?? "";
            if (name.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
            {
                url = a["browser_download_url"]?.GetValue<string>();
                break;
            }
        }
        if (url == null) throw new Exception("No .exe in the latest release");
        return new Info(Normalize(ver), url, node["body"]?.GetValue<string>() ?? "");
    }

    public static async Task<string> DownloadAsync(Info info, Action<int> progress)
    {
        var dest = Path.Combine(Path.GetTempPath(), "PhoneBackup_update.exe");
        using var http = NewClient();
        using var resp = await http.GetAsync(info.Url, HttpCompletionOption.ResponseHeadersRead);
        resp.EnsureSuccessStatusCode();
        long total = resp.Content.Headers.ContentLength ?? -1;
        using var src = await resp.Content.ReadAsStreamAsync();
        using (var dst = File.Create(dest))
        {
            var buf = new byte[81920];
            long done = 0;
            int n, last = -1;
            while ((n = await src.ReadAsync(buf, 0, buf.Length)) > 0)
            {
                await dst.WriteAsync(buf, 0, n);
                done += n;
                if (total > 0)
                {
                    int p = (int)(done * 100 / total);
                    if (p != last) { last = p; progress(p); }
                }
            }
        }
        return dest;
    }

    /// فایل جدید را بعد از بسته‌شدن برنامه جایگزین و برنامه را دوباره اجرا می‌کند.
    public static void ApplyAndRestart(string newExe)
    {
        var cur = Environment.ProcessPath ?? throw new Exception("Unknown process path");
        var script = Path.Combine(Path.GetTempPath(), "phonebackup_update.cmd");
        var sb = new StringBuilder();
        sb.AppendLine("@echo off");
        sb.AppendLine("chcp 65001 >nul");
        sb.AppendLine("set /a n=0");
        sb.AppendLine(":retry");
        sb.AppendLine("set /a n+=1");
        sb.AppendLine("timeout /t 1 /nobreak >nul");
        sb.AppendLine($"move /y \"{newExe}\" \"{cur}\" >nul 2>&1");
        sb.AppendLine("if errorlevel 1 (");
        sb.AppendLine("  if %n% lss 30 goto retry");
        sb.AppendLine("  exit /b 1");
        sb.AppendLine(")");
        sb.AppendLine($"start \"\" \"{cur}\"");
        sb.AppendLine("(goto) 2>nul & del \"%~f0\"");
        File.WriteAllText(script, sb.ToString(), new UTF8Encoding(false));

        var psi = new ProcessStartInfo("cmd.exe") { UseShellExecute = false, CreateNoWindow = true };
        psi.ArgumentList.Add("/c");
        psi.ArgumentList.Add(script);
        Process.Start(psi);
    }
}
