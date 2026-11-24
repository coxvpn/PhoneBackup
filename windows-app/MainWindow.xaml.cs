using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Media;
using System.Windows.Threading;

namespace PhoneBackup;

public partial class MainWindow : Window
{
    const string Pkg = "com.phonebackup.helper";
    static readonly string[] MediaFolders = { "DCIM", "Pictures", "Movies", "Music" };
    static readonly JsonSerializerOptions FileOpts = new()
    {
        WriteIndented = false,
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping
    };

    readonly Adb adb = new();
    readonly DispatcherTimer timer = new() { Interval = TimeSpan.FromSeconds(3) };
    string? serial;
    string? deviceDesc;
    bool busy;

    public MainWindow()
    {
        InitializeComponent();
        FolderBox.Text = Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments);
        try { adb.Extract(); }
        catch (Exception ex) { Log("خطا در آماده‌سازی ADB: " + ex.Message); }

        timer.Tick += async (_, _) => await RefreshDevice();
        Loaded += async (_, _) =>
        {
            Log("گوشی را با کابل وصل کنید و USB Debugging را روشن کنید.");
            await RefreshDevice();
            timer.Start();
        };
    }

    // ---------------- رابط ----------------

    void Log(string s)
    {
        if (!Dispatcher.CheckAccess())
        {
            Dispatcher.BeginInvoke(new Action(() => Log(s)));
            return;
        }
        LogBox.AppendText(DateTime.Now.ToString("HH:mm:ss") + "  " + s + Environment.NewLine);
        LogBox.ScrollToEnd();
    }

    void SetDevice(bool connected, Brush color, string text)
    {
        DeviceDot.Fill = color;
        DeviceText.Text = text;
        if (!busy) BackupBtn.IsEnabled = RestoreBtn.IsEnabled = connected;
    }

    void SetProgress(int value, int max, string text)
    {
        Progress.IsIndeterminate = false;
        Progress.Maximum = Math.Max(1, max);
        Progress.Value = value;
        StatusText.Text = text;
    }

    void Browse_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new Microsoft.Win32.OpenFolderDialog();
        if (dlg.ShowDialog() == true) FolderBox.Text = dlg.FolderName;
    }

    async Task RefreshDevice()
    {
        if (busy) return;
        string output;
        try { output = (await adb.Run("devices")).Output; }
        catch (Exception ex)
        {
            SetDevice(false, Brushes.Red, "خطا در اجرای ADB: " + ex.Message);
            return;
        }

        string? ok = null, unauth = null;
        foreach (var raw in output.Split('\n'))
        {
            var line = raw.Trim();
            if (line.Length == 0 || line.StartsWith("*") || line.StartsWith("List of")) continue;
            var parts = line.Split(new[] { '\t', ' ' }, StringSplitOptions.RemoveEmptyEntries);
            if (parts.Length < 2) continue;
            if (parts[1] == "device") ok ??= parts[0];
            else if (parts[1] == "unauthorized") unauth ??= parts[0];
        }

        if (ok != null)
        {
            if (ok != serial)
            {
                serial = ok;
                var model = (await adb.RunOn(ok, "shell", "getprop", "ro.product.model")).Output.Trim();
                var ver = (await adb.RunOn(ok, "shell", "getprop", "ro.build.version.release")).Output.Trim();
                deviceDesc = $"{model} (Android {ver})";
                Log("گوشی شناسایی شد: " + deviceDesc);
            }
            SetDevice(true, Brushes.Green, "متصل: " + deviceDesc);
        }
        else if (unauth != null)
        {
            serial = null;
            SetDevice(false, Brushes.Orange, "روی گوشی پیام «Allow USB debugging» را تأیید کنید.");
        }
        else
        {
            serial = null;
            SetDevice(false, Brushes.Red, "گوشی وصل نیست. کابل را وصل کنید و USB Debugging را روشن کنید.");
        }
    }

    async Task RunSafe(Func<Task> work)
    {
        busy = true;
        BackupBtn.IsEnabled = RestoreBtn.IsEnabled = false;
        Progress.IsIndeterminate = true;
        try { await work(); }
        catch (Exception ex) { Log("خطا: " + ex.Message); }
        finally
        {
            busy = false;
            Progress.IsIndeterminate = false;
            Progress.Value = 0;
            StatusText.Text = "";
            await RefreshDevice();
        }
    }

    async void Backup_Click(object sender, RoutedEventArgs e) => await RunSafe(DoBackup);
    async void Restore_Click(object sender, RoutedEventArgs e) => await RunSafe(DoRestore);

    static void PullLine(Action<string> log, string l)
    {
        if (l.Contains("pulled") || l.Contains("pushed") || l.Contains("error", StringComparison.OrdinalIgnoreCase))
            log("   " + l.Trim());
    }

    // ---------------- برنامه‌ی کمکی ----------------

    async Task<HelperClient?> ConnectHelper(bool needSmsRole)
    {
        Log("نصب برنامه‌ی کمکی روی گوشی...");
        var r = await adb.RunOn(serial!, "install", "-r", adb.HelperApk);
        if (!r.Output.Contains("Success"))
        {
            if (r.Output.Contains("INCOMPATIBLE") || r.Output.Contains("signatures"))
            {
                await adb.RunOn(serial!, "uninstall", Pkg);
                r = await adb.RunOn(serial!, "install", adb.HelperApk);
            }
            if (!r.Output.Contains("Success"))
            {
                Log("نصب برنامه‌ی کمکی ناموفق بود:\n" + r.Output.Trim());
                if (r.Output.Contains("USER_RESTRICTED"))
                    Log("در گوشی‌های شیائومی/پوکو گزینه‌ی «Install via USB» را در Developer options روشن کنید.");
                return null;
            }
        }

        foreach (var p in new[] { "READ_CONTACTS", "WRITE_CONTACTS", "READ_SMS", "SEND_SMS", "RECEIVE_SMS", "POST_NOTIFICATIONS" })
            await adb.RunOn(serial!, "shell", "pm", "grant", Pkg, "android.permission." + p);

        var token = Guid.NewGuid().ToString("N");
        var args = new List<string> { "shell", "am", "start", "-n", Pkg + "/.MainActivity", "--es", "token", token };
        if (needSmsRole) { args.Add("--ez"); args.Add("request_sms_role"); args.Add("true"); }
        await adb.RunOn(serial!, args.ToArray());
        await adb.RunOn(serial!, "forward", $"tcp:{HelperClient.Port}", $"tcp:{HelperClient.Port}");

        Log("در حال اتصال به برنامه‌ی کمکی (گوشی باید روشن و بدون قفل باشد)...");
        var client = new HelperClient(token);
        if (!await client.ConnectAsync(25))
        {
            client.Dispose();
            Log("اتصال برقرار نشد. صفحه‌ی گوشی را باز کنید، اگر پنجره‌ی مجوز آمد تأیید کنید و دوباره امتحان کنید.");
            return null;
        }
        return client;
    }

    // ---------------- پشتیبان‌گیری ----------------

    async Task DoBackup()
    {
        bool apps = ChkApps.IsChecked == true, media = ChkMedia.IsChecked == true;
        bool contacts = ChkContacts.IsChecked == true, sms = ChkSms.IsChecked == true;
        if (!(apps || media || contacts || sms)) { Log("حداقل یک مورد را انتخاب کنید."); return; }

        var baseDir = FolderBox.Text.Trim();
        if (!Directory.Exists(baseDir)) { Log("پوشه‌ی ذخیره‌سازی معتبر نیست."); return; }
        var root = Path.Combine(baseDir, "PhoneBackup_" + DateTime.Now.ToString("yyyyMMdd_HHmm"));
        Directory.CreateDirectory(root);
        Log("پوشه‌ی بکاپ: " + root);

        if (contacts || sms)
        {
            using var h = await ConnectHelper(false);
            if (h == null) Log("مخاطبین و پیامک‌ها انجام نشدند.");
            else
            {
                try
                {
                    if (contacts)
                    {
                        Log("بکاپ مخاطبین...");
                        var list = await h.Stream("get_contacts", (i, n) => SetProgress(i, n, $"مخاطبین: {i} از {n}"));
                        SaveList(Path.Combine(root, "contacts.json"), list);
                        Log($"مخاطبین: {list.Count} مورد ذخیره شد.");
                    }
                    if (sms)
                    {
                        Log("بکاپ پیامک‌ها...");
                        var list = await h.Stream("get_sms", (i, n) => SetProgress(i, n, $"پیامک‌ها: {i} از {n}"));
                        SaveList(Path.Combine(root, "sms.json"), list);
                        Log($"پیامک‌ها: {list.Count} مورد ذخیره شد.");
                    }
                }
                finally { await h.StopAsync(); }
            }
        }

        if (apps) await BackupApps(root);
        if (media) await BackupMedia(root);
        Log("✅ پشتیبان‌گیری تمام شد.");
    }

    static void SaveList(string path, List<JsonNode> list)
    {
        var arr = new JsonArray();
        foreach (var n in list) arr.Add(n);
        File.WriteAllText(path, arr.ToJsonString(FileOpts), new UTF8Encoding(false));
    }

    async Task BackupApps(string root)
    {
        Log("بکاپ برنامه‌ها (فایل نصبی)...");
        var r = await adb.RunOn(serial!, "shell", "pm", "list", "packages", "-3");
        var pkgs = r.Output.Split('\n').Select(l => l.Trim())
            .Where(l => l.StartsWith("package:")).Select(l => l.Substring(8).Trim())
            .Where(p => p != Pkg).OrderBy(p => p).ToList();
        Log($"{pkgs.Count} برنامه پیدا شد.");

        int i = 0, failed = 0;
        foreach (var pkg in pkgs)
        {
            i++;
            SetProgress(i, pkgs.Count, $"برنامه‌ها: {i} از {pkgs.Count} — {pkg}");
            var pr = await adb.RunOn(serial!, "shell", "pm", "path", pkg);
            var paths = pr.Output.Split('\n').Select(l => l.Trim())
                .Where(l => l.StartsWith("package:")).Select(l => l.Substring(8).Trim()).ToList();
            if (paths.Count == 0) { failed++; continue; }
            var dir = Path.Combine(root, "apps", pkg);
            Directory.CreateDirectory(dir);
            foreach (var path in paths)
            {
                var res = await adb.RunOn(serial!, "pull", path, dir);
                if (res.Code != 0) { failed++; Log($"   خطا در {pkg}: {res.Output.Trim()}"); break; }
            }
        }
        Log($"برنامه‌ها تمام شد. ({pkgs.Count - failed} موفق، {failed} ناموفق)");
    }

    async Task BackupMedia(string root)
    {
        var mediaDir = Path.Combine(root, "media");
        Directory.CreateDirectory(mediaDir);
        foreach (var d in MediaFolders)
        {
            Log($"کپی /sdcard/{d} (ممکن است طول بکشد)...");
            StatusText.Text = $"در حال کپی {d} ...";
            Progress.IsIndeterminate = true;
            var r = await adb.RunOnStream(serial!, l => PullLine(Log, l), "pull", "-a", "/sdcard/" + d, mediaDir);
            if (r.Code != 0 && r.Output.Contains("does not exist")) Log($"   پوشه‌ی {d} روی گوشی نیست.");
        }
    }

    // ---------------- بازیابی ----------------

    static string? ResolveBackupRoot(string folder)
    {
        if (!Directory.Exists(folder)) return null;
        bool IsBackup(string f) =>
            Directory.Exists(Path.Combine(f, "apps")) || Directory.Exists(Path.Combine(f, "media")) ||
            File.Exists(Path.Combine(f, "contacts.json")) || File.Exists(Path.Combine(f, "sms.json"));
        if (IsBackup(folder)) return folder;
        return Directory.GetDirectories(folder, "PhoneBackup_*")
            .OrderByDescending(d => Path.GetFileName(d)).FirstOrDefault(IsBackup);
    }

    async Task DoRestore()
    {
        bool apps = ChkApps.IsChecked == true, media = ChkMedia.IsChecked == true;
        bool contacts = ChkContacts.IsChecked == true, sms = ChkSms.IsChecked == true;

        var root = ResolveBackupRoot(FolderBox.Text.Trim());
        if (root == null) { Log("در این پوشه بکاپی پیدا نشد. پوشه‌ی PhoneBackup_... را انتخاب کنید."); return; }

        bool hasApps = apps && Directory.Exists(Path.Combine(root, "apps"));
        bool hasMedia = media && Directory.Exists(Path.Combine(root, "media"));
        bool hasC = contacts && File.Exists(Path.Combine(root, "contacts.json"));
        bool hasS = sms && File.Exists(Path.Combine(root, "sms.json"));
        if (!(hasApps || hasMedia || hasC || hasS)) { Log("از موارد انتخاب‌شده چیزی در این بکاپ نیست."); return; }

        var ask = MessageBox.Show(
            $"بازیابی از:\n{root}\n\nروی گوشی «{deviceDesc}» انجام شود؟",
            "تأیید بازیابی", MessageBoxButton.YesNo, MessageBoxImage.Question, MessageBoxResult.No,
            MessageBoxOptions.RtlReading | MessageBoxOptions.RightAlign);
        if (ask != MessageBoxResult.Yes) return;
        Log("بازیابی از: " + root);

        if (hasC || hasS)
        {
            using var h = await ConnectHelper(hasS);
            if (h == null) Log("مخاطبین و پیامک‌ها انجام نشدند.");
            else
            {
                try
                {
                    if (hasC) await RestoreList(h, Path.Combine(root, "contacts.json"), "put_contacts", 200, "مخاطبین");
                    if (hasS)
                    {
                        Log("روی گوشی پنجره‌ی «برنامه پیامک پیش‌فرض» را تأیید کنید (تا ۲ دقیقه صبر می‌کنم)...");
                        bool ok = false;
                        for (int i = 0; i < 60 && !ok; i++)
                        {
                            await h.PingAsync();
                            ok = h.DefaultSms;
                            if (!ok) await Task.Delay(2000);
                        }
                        if (!ok) Log("تأیید نشد؛ پیامک‌ها بازیابی نشدند.");
                        else
                        {
                            await RestoreList(h, Path.Combine(root, "sms.json"), "put_sms", 500, "پیامک‌ها");
                            Log("⚠ حالا برنامه‌ی پیامک قبلی خود را دوباره پیش‌فرض کنید (صفحه‌ی تنظیمات روی گوشی باز می‌شود).");
                            await adb.RunOn(serial!, "shell", "am", "start", "-a", "android.settings.MANAGE_DEFAULT_APPS_SETTINGS");
                        }
                    }
                }
                finally { await h.StopAsync(); }
            }
        }

        if (hasApps) await RestoreApps(Path.Combine(root, "apps"));
        if (hasMedia) await RestoreMedia(Path.Combine(root, "media"));
        Log("✅ بازیابی تمام شد.");
    }

    static IEnumerable<JsonArray> Chunks(JsonArray src, int size)
    {
        for (int i = 0; i < src.Count; i += size)
        {
            var c = new JsonArray();
            for (int j = i; j < Math.Min(i + size, src.Count); j++) c.Add(src[j]!.DeepClone());
            yield return c;
        }
    }

    async Task RestoreList(HelperClient h, string file, string cmd, int chunk, string title)
    {
        var arr = JsonNode.Parse(File.ReadAllText(file, Encoding.UTF8))!.AsArray();
        Log($"بازیابی {title} ({arr.Count} مورد)...");
        int done = 0, added = 0, skipped = 0;
        bool first = true;
        foreach (var part in Chunks(arr, chunk))
        {
            int n = part.Count;
            var resp = await h.Request(new JsonObject { ["cmd"] = cmd, ["first"] = first, ["data"] = part });
            first = false;
            added += resp["added"]?.GetValue<int>() ?? 0;
            skipped += resp["skipped"]?.GetValue<int>() ?? 0;
            done += n;
            SetProgress(done, arr.Count, $"{title}: {done} از {arr.Count}");
        }
        Log($"{title}: {added} مورد اضافه شد، {skipped} مورد تکراری رد شد.");
    }

    async Task RestoreApps(string appsDir)
    {
        var dirs = Directory.GetDirectories(appsDir).OrderBy(d => d).ToList();
        Log($"نصب {dirs.Count} برنامه...");
        int i = 0, okCount = 0;
        foreach (var dir in dirs)
        {
            i++;
            var name = Path.GetFileName(dir);
            SetProgress(i, dirs.Count, $"برنامه‌ها: {i} از {dirs.Count} — {name}");
            var apks = Directory.GetFiles(dir, "*.apk");
            if (apks.Length == 0) continue;
            var args = new List<string> { "install-multiple", "-r" };
            args.AddRange(apks);
            var r = await adb.RunOn(serial!, args.ToArray());
            if (r.Output.Contains("Success")) okCount++;
            else Log($"   ✗ {name}: {r.Output.Trim().Replace("\r", "").Replace("\n", " ")}");
        }
        Log($"برنامه‌ها: {okCount} از {dirs.Count} نصب شد.");
    }

    async Task RestoreMedia(string mediaDir)
    {
        foreach (var dir in Directory.GetDirectories(mediaDir))
        {
            var name = Path.GetFileName(dir);
            Log($"ارسال {name} به گوشی (ممکن است طول بکشد)...");
            StatusText.Text = $"در حال ارسال {name} ...";
            Progress.IsIndeterminate = true;
            await adb.RunOnStream(serial!, l => PullLine(Log, l), "push", dir, "/sdcard/");
        }
        await adb.RunOn(serial!, "shell", "content", "call", "--uri", "content://media/",
            "--method", "scan_volume", "--arg", "external_primary");
        Log("اگر فایل‌ها در گالری دیر پیدا شدند، یک‌بار گوشی را ری‌استارت کنید.");
    }
}
