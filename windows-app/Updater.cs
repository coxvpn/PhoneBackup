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
    public record Info(Version Version, string Url, string Notes, string AssetName);

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
        string assetName = "PhoneBackup.exe";
        foreach (var a in node["assets"]!.AsArray())
        {
            var name = a!["name"]?.GetValue<string>() ?? "";
            if (name.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
            {
                url = a["browser_download_url"]?.GetValue<string>();
                assetName = name;
                break;
            }
        }
        if (url == null) throw new Exception("No .exe in the latest release");
        return new Info(Normalize(ver), url, node["body"]?.GetValue<string>() ?? "", assetName);
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

    /// فایل جدید را بعد از بسته‌شدن برنامه با نام نسخه‌دار خودش (مثلاً PhoneBackup_v1.5.3.exe)
    /// کنار فایل فعلی می‌گذارد، فایل قدیمی را پاک می‌کند و برنامه را دوباره اجرا می‌کند.
    public static void ApplyAndRestart(string newExe, string assetName)
    {
        var cur = Environment.ProcessPath ?? throw new Exception("Unknown process path");
        var name = Path.GetFileName(assetName);
        if (string.IsNullOrWhiteSpace(name) || !name.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
            name = Path.GetFileName(cur);
        var target = Path.Combine(Path.GetDirectoryName(cur)!, name);

        var script = Path.Combine(Path.GetTempPath(), "phonebackup_update.cmd");
        var sb = new StringBuilder();
        sb.AppendLine("@echo off");
        sb.AppendLine("chcp 65001 >nul");
        sb.AppendLine($"set \"NEW={newExe}\"");
        sb.AppendLine($"set \"TARGET={target}\"");
        sb.AppendLine($"set \"CUR={cur}\"");
        sb.AppendLine("set /a n=0");
        sb.AppendLine(":retry");
        sb.AppendLine("set /a n+=1");
        sb.AppendLine("timeout /t 1 /nobreak >nul");
        sb.AppendLine("move /y \"%NEW%\" \"%TARGET%\" >nul 2>&1");
        sb.AppendLine("if not errorlevel 1 goto moved");
        sb.AppendLine("if %n% lss 30 goto retry");
        sb.AppendLine("exit /b 1");
        sb.AppendLine(":moved");
        sb.AppendLine("if /i \"%TARGET%\"==\"%CUR%\" goto run");
        sb.AppendLine("set /a d=0");
        sb.AppendLine(":del");
        sb.AppendLine("del /f /q \"%CUR%\" >nul 2>&1");
        sb.AppendLine("if not exist \"%CUR%\" goto run");
        sb.AppendLine("set /a d+=1");
        sb.AppendLine("if %d% lss 30 goto delwait");
        sb.AppendLine("goto run");
        sb.AppendLine(":delwait");
        sb.AppendLine("timeout /t 1 /nobreak >nul");
        sb.AppendLine("goto del");
        sb.AppendLine(":run");
        sb.AppendLine("start \"\" \"%TARGET%\"");
        sb.AppendLine("(goto) 2>nul & del \"%~f0\"");
        File.WriteAllText(script, sb.ToString(), new UTF8Encoding(false));

        var psi = new ProcessStartInfo("cmd.exe") { UseShellExecute = false, CreateNoWindow = true };
        psi.ArgumentList.Add("/c");
        psi.ArgumentList.Add(script);
        Process.Start(psi);
    }
}
