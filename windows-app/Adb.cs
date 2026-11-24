using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Text;
using System.Threading.Tasks;

namespace PhoneBackup;

public class Adb
{
    public string Dir { get; } = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PhoneBackup", "tools");

    string AdbExe => Path.Combine(Dir, "adb.exe");
    public string HelperApk => Path.Combine(Dir, "helper.apk");

    /// فایل‌های adb و برنامه‌ی کمکی که داخل EXE هستند را بیرون می‌ریزد.
    public void Extract()
    {
        Directory.CreateDirectory(Dir);
        var asm = Assembly.GetExecutingAssembly();
        foreach (var name in asm.GetManifestResourceNames())
        {
            string? file = null;
            if (name.StartsWith("adb/")) file = name.Substring(4);
            else if (name == "helper.apk") file = name;
            if (file == null) continue;

            var dest = Path.Combine(Dir, file);
            using var s = asm.GetManifestResourceStream(name)!;
            bool always = file == "helper.apk";
            if (!always && File.Exists(dest) && new FileInfo(dest).Length == s.Length) continue;
            try
            {
                using var f = File.Create(dest);
                s.CopyTo(f);
            }
            catch (IOException) when (File.Exists(dest))
            {
            }
        }
    }

    public Task<(int Code, string Output)> Run(params string[] args) => Core(args, null);

    public Task<(int Code, string Output)> RunOn(string serial, params string[] args) =>
        Core(Prefix(serial, args), null);

    public Task<(int Code, string Output)> RunOnStream(string serial, Action<string> onLine, params string[] args) =>
        Core(Prefix(serial, args), onLine);

    static string[] Prefix(string serial, string[] args)
    {
        var l = new List<string> { "-s", serial };
        l.AddRange(args);
        return l.ToArray();
    }

    async Task<(int Code, string Output)> Core(string[] args, Action<string>? onLine)
    {
        var psi = new ProcessStartInfo(AdbExe)
        {
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardErrorEncoding = Encoding.UTF8,
            WorkingDirectory = Dir
        };
        foreach (var a in args) psi.ArgumentList.Add(a);

        using var p = new Process { StartInfo = psi };
        var sb = new StringBuilder();
        DataReceivedEventHandler h = (_, e) =>
        {
            if (e.Data == null) return;
            lock (sb) sb.AppendLine(e.Data);
            onLine?.Invoke(e.Data);
        };
        p.OutputDataReceived += h;
        p.ErrorDataReceived += h;
        p.Start();
        p.BeginOutputReadLine();
        p.BeginErrorReadLine();
        await p.WaitForExitAsync();
        string output;
        lock (sb) output = sb.ToString();
        return (p.ExitCode, output);
    }
}
