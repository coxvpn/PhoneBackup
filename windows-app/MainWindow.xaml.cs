using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;

namespace PhoneBackup;

public partial class MainWindow : Window
{
    const string Pkg = "com.phonebackup.helper";
    const string SmsRole = "android.app.role.SMS";
    const string ManualRestoreDir = "/sdcard/Download/PhoneBackup_Restore";
    static readonly HashSet<string> Protected = new()
    {
        "android", "com.android.systemui", "com.android.phone", "com.android.settings", "com.android.shell",
        "com.android.providers.telephony", "com.android.providers.contacts", "com.android.providers.media",
        "com.android.providers.settings", "com.google.android.gms"
    };
    static readonly string[] DefaultFolders = { "DCIM", "Pictures", "Movies", "Music" };
    static readonly JsonSerializerOptions FileOpts = new()
    {
        WriteIndented = false,
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping
    };

    readonly Adb adb = new();
    readonly DispatcherTimer timer = new() { Interval = TimeSpan.FromSeconds(3) };
    internal readonly ObservableCollection<AppItem> apps = new();
    internal readonly ObservableCollection<PickItem> folders = new();
    CancellationTokenSource? cts;
    CancellationToken ct;
    string? serial;
    string? deviceDesc;
    string? deviceErr;
    string? deviceModel;
    string? lastBackupRoot;
    int devState = -1; // -1 searching, 0 none, 1 unauthorized, 2 connected, 3 adb error
    bool busy;
    bool ready;

    /// وقتی پنجره‌ی انتخاب برنامه‌های بکاپ باز است، این را برای به‌روز نگه‌داشتن شمارنده‌اش ست می‌کند.
    internal Action? OnAppsChanged;
    internal string? DeviceDesc => deviceDesc;

    static string T(string key, params object[] a) => Loc.T(key, a);

    public MainWindow()
    {
        InitializeComponent();
        Loc.Load();

        FolderBox.Text = Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments);
        LangBox.SelectedIndex = Loc.Persian ? 1 : 0;
        ready = true;
        ApplyLanguage();

        try { adb.Extract(); }
        catch (Exception ex) { Log(T("adb_prep_error", ex.Message)); }

        Loaded += (_, _) => FitToScreen();
        timer.Tick += async (_, _) => await RefreshDevice();
        Loaded += async (_, _) =>
        {
            Log(T("hint_connect"));
            await RefreshDevice();
            timer.Start();
        };
    }

    // ---------------- language / UI ----------------

    void ApplyLanguage()
    {
        FlowDirection = Loc.Persian ? FlowDirection.RightToLeft : FlowDirection.LeftToRight;
        Title = T("title") + " v" + Updater.Current.ToString(3);
        LangLabel.Text = T("language");
        UpdateBtn.Content = T("btn_update");
        AboutBtn.Content = T("btn_about");
        InfoBtn.Content = T("btn_info");
        GrpSummary.Header = T("grp_phone_summary");
        FolderLabel.Text = T("folder");
        BrowseBtn.Content = T("browse");
        BackupBtn.Content = T("btn_backup");
        RestoreBtn.Content = T("btn_restore");
        CancelBtn.Content = T("btn_cancel");
        OpenFolderBtn.Content = T("btn_open_folder");
        TransferBtn.Content = T("btn_transfer");
        RenderSummary();
        RenderDevice();
    }

    void Lang_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (!ready) return;
        Loc.Persian = LangBox.SelectedIndex == 1;
        Loc.Save();
        ApplyLanguage();
    }

    internal void Log(string s)
    {
        if (!Dispatcher.CheckAccess())
        {
            Dispatcher.BeginInvoke(new Action(() => Log(s)));
            return;
        }
        LogBox.AppendText(DateTime.Now.ToString("HH:mm:ss") + "  " + s + Environment.NewLine);
        LogBox.ScrollToEnd();
    }

    void RenderDevice()
    {
        switch (devState)
        {
            case 2:
                DeviceDot.Fill = Brushes.Green;
                DeviceText.Text = T("device_connected", deviceDesc ?? "");
                break;
            case 1:
                DeviceDot.Fill = Brushes.Orange;
                DeviceText.Text = T("device_unauth");
                break;
            case 3:
                DeviceDot.Fill = Brushes.Red;
                DeviceText.Text = T("device_adb_error", deviceErr ?? "");
                break;
            case 0:
                DeviceDot.Fill = Brushes.Red;
                DeviceText.Text = T("device_none");
                break;
            default:
                DeviceDot.Fill = Brushes.Gray;
                DeviceText.Text = T("device_searching");
                break;
        }
        if (!busy) SetButtons(devState == 2);
    }

    void SetButtons(bool enabled)
    {
        BackupBtn.IsEnabled = RestoreBtn.IsEnabled = InfoBtn.IsEnabled = TransferBtn.IsEnabled = enabled;
    }

    void SetProgress(int value, int max, string text)
    {
        Progress.IsIndeterminate = false;
        Progress.Maximum = Math.Max(1, max);
        Progress.Value = value;
        StatusText.Text = text;
    }

    /// اگر صفحه‌نمایش کوچک‌تر از اندازه‌ی پیش‌فرض پنجره باشد، خودکار کوچک و وسط صفحه می‌شود.
    void FitToScreen()
    {
        var wa = SystemParameters.WorkArea;
        double w = Math.Min(Width, wa.Width - 20);
        double h = Math.Min(Height, wa.Height - 20);
        Width = Math.Max(MinWidth, w);
        Height = Math.Max(MinHeight, h);
        Left = Math.Max(wa.Left, wa.Left + (wa.Width - Width) / 2);
        Top = Math.Max(wa.Top, wa.Top + (wa.Height - Height) / 2);
    }

    void Browse_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new Microsoft.Win32.OpenFolderDialog();
        if (dlg.ShowDialog() == true) FolderBox.Text = dlg.FolderName;
    }

    void Info_Click(object sender, RoutedEventArgs e)
    {
        if (serial == null || busy) return;
        new DeviceInfoWindow(adb, serial) { Owner = this }.ShowDialog();
    }

    void About_Click(object sender, RoutedEventArgs e) => new AboutWindow { Owner = this }.ShowDialog();

    void Cancel_Click(object sender, RoutedEventArgs e)
    {
        if (cts == null) return;
        Log(T("cancelling"));
        CancelBtn.IsEnabled = false;
        cts.Cancel();
    }

    // ---------------- phone summary (normal state) ----------------

    void RenderSummary()
    {
        SummaryText.Text = devState == 2 && !string.IsNullOrEmpty(summaryLine)
            ? summaryLine
            : T("summary_waiting");
    }

    string? summaryLine;

    static string SizeGb(double bytes)
    {
        double gb = bytes / 1024.0 / 1024.0 / 1024.0;
        if (gb >= 1) return gb.ToString("0.0", System.Globalization.CultureInfo.InvariantCulture) + " GB";
        double mb = bytes / 1024.0 / 1024.0;
        return mb.ToString("0", System.Globalization.CultureInfo.InvariantCulture) + " MB";
    }

    async Task RefreshSummary(string dev)
    {
        try
        {
            var infoTask = DeviceInfo.CollectAsync(adb, dev);
            var duTask = adb.RunOn(dev, "shell", "du", "-sh",
                "/sdcard/DCIM", "/sdcard/Pictures", "/sdcard/Movies", "/sdcard/Music", "/data/app");
            await Task.WhenAll(infoTask, duTask);

            var info = infoTask.Result;
            string Val(string key) => info.Rows.FirstOrDefault(r => r.Key == key)?.Value ?? "";
            var parts = new List<string>();
            var model = Val("model"); if (model.Length > 0) parts.Add(model);
            var android = Val("android"); if (android.Length > 0) parts.Add("Android " + android);
            var chipset = Val("chipset"); if (chipset.Length > 0) parts.Add(chipset);
            var ram = Val("ram"); if (ram.Length > 0) parts.Add("RAM " + ram);
            var storage = Val("storage"); if (storage.Length > 0) parts.Add(storage);
            var battery = Val("battery"); if (battery.Length > 0) parts.Add(T("summary_battery") + " " + battery);
            var line1 = string.Join("   •   ", parts);

            double media = 0, music = 0, appsSize = 0;
            foreach (var raw in duTask.Result.Output.Split('\n'))
            {
                var m = Regex.Match(raw.Trim(), @"^(\d+(?:\.\d+)?)([KMGT]?)\s+(.+)$", RegexOptions.IgnoreCase);
                if (!m.Success) continue;
                double val = double.Parse(m.Groups[1].Value, System.Globalization.CultureInfo.InvariantCulture);
                double bytes = m.Groups[2].Value.ToUpperInvariant() switch
                {
                    "K" => val * 1024,
                    "M" => val * 1024 * 1024,
                    "G" => val * 1024 * 1024 * 1024,
                    "T" => val * 1024d * 1024 * 1024 * 1024,
                    _ => val
                };
                var path = m.Groups[3].Value.Trim();
                if (path.EndsWith("/data/app")) appsSize += bytes;
                else if (path.EndsWith("/Music")) music += bytes;
                else media += bytes; // DCIM, Pictures, Movies
            }
            var line2parts = new List<string>();
            if (media > 0) line2parts.Add(T("summary_media") + ": " + SizeGb(media));
            if (music > 0) line2parts.Add(T("summary_music") + ": " + SizeGb(music));
            if (appsSize > 0) line2parts.Add(T("summary_apps_size") + ": " + SizeGb(appsSize));
            var line2 = string.Join("   •   ", line2parts);

            summaryLine = line2.Length > 0 ? line1 + "\n" + line2 : line1;
        }
        catch { summaryLine = null; }
        RenderSummary();
    }

    // ---------------- app list (loading from phone; UI lives in BackupWindow) ----------------

    internal async Task LoadApps()
    {
        using var h = await ConnectHelper(false);
        if (h == null) { Log(T("apps_load_failed")); return; }
        try
        {
            Log(T("loading_apps"));
            var list = await h.Stream("get_apps", (i, n) => SetProgress(i, n, T("loading_apps")));
            apps.Clear();
            foreach (var n in list)
            {
                var item = new AppItem
                {
                    Package = n["p"]?.GetValue<string>() ?? "",
                    Name = n["n"]?.GetValue<string>() ?? "",
                    Version = n["v"]?.GetValue<string>() ?? "",
                    Size = n["s"]?.GetValue<long>() ?? 0,
                    Kind = n["k"]?.GetValue<int>() ?? 0,
                    Paths = n["f"]!.AsArray().Select(x => x!.GetValue<string>()).ToList()
                };
                item.PropertyChanged += (_, _) => OnAppsChanged?.Invoke();
                apps.Add(item);
            }
            OnAppsChanged?.Invoke();
            Log(T("apps_loaded", apps.Count));
        }
        finally { await h.StopAsync(); }
    }

    // ---------------- media folders (read from phone; UI lives in BackupWindow) ----------------

    static IEnumerable<string> ParseDirs(string output)
    {
        foreach (var raw in output.Split('\n'))
        {
            var l = raw.Trim();
            if (l.Length < 2 || !l.EndsWith("/")) continue;
            l = l.TrimEnd('/');
            if (l.Length == 0 || l.StartsWith(".")) continue;
            yield return l;
        }
    }

    internal async Task LoadMediaFolders()
    {
        var found = new List<string>();
        var r = await adb.RunOn(serial!, "shell", "ls", "-p", "/sdcard/");
        foreach (var d in ParseDirs(r.Output))
            if (d != "Android") found.Add(d);
        var r2 = await adb.RunOn(serial!, "shell", "ls", "-p", "/sdcard/Android/media/");
        foreach (var d in ParseDirs(r2.Output))
            found.Add("Android/media/" + d);
        if (found.Count == 0) found.AddRange(DefaultFolders);

        var prev = new Dictionary<string, bool>();
        foreach (var f in folders) prev[f.Key] = f.IsSelected;

        int Rank(string f) { int i = Array.IndexOf(DefaultFolders, f); return i >= 0 ? i : 100; }
        folders.Clear();
        foreach (var f in found.Distinct().OrderBy(Rank).ThenBy(x => x, StringComparer.OrdinalIgnoreCase))
        {
            bool sel = prev.TryGetValue(f, out var was) && was;
            folders.Add(new PickItem { Key = f, Display = f, IsSelected = sel });
        }
        Log(T("folders_loaded", folders.Count));
    }

    // ---------------- device detection ----------------

    async Task RefreshDevice()
    {
        if (busy) return;
        string output;
        try { output = (await adb.Run("devices")).Output; }
        catch (Exception ex)
        {
            devState = 3;
            deviceErr = ex.Message;
            RenderDevice();
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
            bool isNew = ok != serial;
            if (isNew)
            {
                serial = ok;
                var model = (await adb.RunOn(ok, "shell", "getprop", "ro.product.model")).Output.Trim();
                deviceModel = model;
                var ver = (await adb.RunOn(ok, "shell", "getprop", "ro.build.version.release")).Output.Trim();
                deviceDesc = $"{model} (Android {ver})";
                Log(T("device_found", deviceDesc));
                apps.Clear();
                OnAppsChanged?.Invoke();
                summaryLine = null;
            }
            devState = 2;
            RenderDevice();
            if (isNew)
            {
                try { await LoadMediaFolders(); } catch { }
                await RefreshSummary(ok);
            }
        }
        else if (unauth != null) { serial = null; devState = 1; RenderDevice(); }
        else { serial = null; devState = 0; RenderDevice(); }
    }

    /// هر کار طولانی از اینجا اجرا می‌شود؛ دکمه‌ی «لغو» توکن ct را لغو می‌کند.
    async Task RunSafe(Func<Task> work)
    {
        busy = true;
        cts = new CancellationTokenSource();
        ct = cts.Token;
        adb.Token = ct;
        SetButtons(false);
        CancelBtn.IsEnabled = true;
        Progress.IsIndeterminate = true;

        bool cancelled = false;
        try { await work(); }
        catch (OperationCanceledException) { cancelled = true; }
        catch (Exception ex)
        {
            if (ct.IsCancellationRequested) cancelled = true;
            else Log(T("error", ex.Message));
        }

        adb.Token = default;
        if (cancelled)
        {
            Log(T("cancelled"));
            if (serial != null)
            {
                // اگر برنامه‌ی کمکی هنوز روی گوشی در حال اجراست، آن را متوقف کن
                try { await adb.RunOn(serial, "shell", "am", "stopservice", "-n", Pkg + "/.ServerService"); } catch { }
            }
        }
        CancelBtn.IsEnabled = false;
        cts.Dispose();
        cts = null;
        busy = false;
        Progress.IsIndeterminate = false;
        Progress.Value = 0;
        StatusText.Text = "";
        await RefreshDevice();
    }

    async void Backup_Click(object sender, RoutedEventArgs e)
    {
        if (busy) return;
        var dlg = new BackupWindow(this, apps, folders) { Owner = this };
        if (dlg.ShowDialog() != true) return;
        await RunSafe(() => DoBackup(dlg.DoApps, dlg.DoMedia, dlg.DoContacts, dlg.DoSms));
    }

    async void Restore_Click(object sender, RoutedEventArgs e) => await RunSafe(DoRestore);

    async void Transfer_Click(object sender, RoutedEventArgs e)
    {
        if (busy || devState != 2) return;
        var dlg = new BackupWindow(this, apps, folders, forTransfer: true) { Owner = this };
        if (dlg.ShowDialog() != true) return;
        await RunSafe(() => DoTransfer(dlg.DoApps, dlg.DoMedia, dlg.DoContacts, dlg.DoSms));
    }

    /// بکاپ از گوشی فعلی را در یک پوشه‌ی موقت می‌گیرد، منتظر وصل‌شدن گوشی دوم می‌ماند،
    /// و همان را مستقیم (بدون پرسیدن دوباره) روی گوشی دوم بازیابی می‌کند.
    async Task DoTransfer(bool doApps, bool media, bool contacts, bool sms)
    {
        if (!(doApps || media || contacts || sms)) { Log(T("select_one")); return; }

        var sourceSerial = serial!;
        var sourceDesc = deviceDesc ?? "";
        var tempRoot = Path.Combine(Path.GetTempPath(), "PhoneBackup_Transfer_" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(tempRoot);
        bool cleanTemp = true;
        try
        {
            Log(T("transfer_backup_from", sourceDesc));

            if (contacts || sms)
            {
                using var h = await ConnectHelper(false);
                if (h == null) Log(T("skip_contacts_sms"));
                else
                {
                    try
                    {
                        if (contacts)
                        {
                            Log(T("backing_contacts"));
                            var list = await h.Stream("get_contacts", (i, n) => SetProgress(i, n, T("list_progress", T("contacts"), i, n)));
                            SaveList(Path.Combine(tempRoot, "contacts.json"), list);
                            Log(T("contacts_saved", list.Count));
                        }
                        if (sms)
                        {
                            Log(T("backing_sms"));
                            var list = await h.Stream("get_sms", (i, n) => SetProgress(i, n, T("list_progress", T("sms"), i, n)));
                            SaveList(Path.Combine(tempRoot, "sms.json"), list);
                            Log(T("sms_saved", list.Count));
                        }
                    }
                    finally { await h.StopAsync(); }
                }
            }
            if (doApps)
            {
                var selected = apps.Where(a => a.IsSelected).ToList();
                if (selected.Count == 0) Log(T("apps_none_selected"));
                else await BackupApps(tempRoot, selected);
            }
            if (media) await BackupMedia(tempRoot);
            Log(T("transfer_backup_done"));

            var targetSerial = await WaitForSecondPhone(sourceSerial);

            serial = targetSerial;
            apps.Clear();
            OnAppsChanged?.Invoke();
            var model = (await adb.RunOn(targetSerial, "shell", "getprop", "ro.product.model")).Output.Trim();
            var ver = (await adb.RunOn(targetSerial, "shell", "getprop", "ro.build.version.release")).Output.Trim();
            deviceModel = model;
            deviceDesc = $"{model} (Android {ver})";
            devState = 2;
            RenderDevice();
            Log(T("transfer_restoring_to", deviceDesc));
            try { await LoadMediaFolders(); } catch { }
            await RefreshSummary(targetSerial);

            await RestoreAllFromFolder(tempRoot);
            Log(T("transfer_done"));
        }
        catch
        {
            cleanTemp = false;
            Log(T("transfer_kept_backup", tempRoot));
            throw;
        }
        finally
        {
            if (cleanTemp) { try { Directory.Delete(tempRoot, true); } catch { } }
        }
    }

    /// منتظر می‌ماند تا گوشیِ دیگری (با سریال متفاوت از گوشی اول) وصل و تأیید شود.
    async Task<string> WaitForSecondPhone(string excludeSerial)
    {
        Log(T("transfer_disconnect_first"));
        bool warnedUnauth = false;
        while (true)
        {
            ct.ThrowIfCancellationRequested();
            await Task.Delay(1500, ct);
            string output;
            try { output = (await adb.Run("devices")).Output; } catch { continue; }

            string? found = null, unauth = null;
            foreach (var raw in output.Split('\n'))
            {
                var line = raw.Trim();
                if (line.Length == 0 || line.StartsWith("*") || line.StartsWith("List of")) continue;
                var parts = line.Split(new[] { '\t', ' ' }, StringSplitOptions.RemoveEmptyEntries);
                if (parts.Length < 2 || parts[0] == excludeSerial) continue;
                if (parts[1] == "device") found ??= parts[0];
                else if (parts[1] == "unauthorized") unauth ??= parts[0];
            }
            if (found != null) return found;
            if (unauth != null && !warnedUnauth)
            {
                warnedUnauth = true;
                Log(T("transfer_second_unauth"));
            }
        }
    }

    /// همان‌چیزهایی که در پوشه‌ی بکاپ هست را، بدون باز کردن پنجره‌ی انتخاب، مستقیم بازیابی می‌کند.
    async Task RestoreAllFromFolder(string root)
    {
        var appsDir = Path.Combine(root, "apps");
        var mediaDir = Path.Combine(root, "media");
        bool hasC = File.Exists(Path.Combine(root, "contacts.json"));
        bool hasS = File.Exists(Path.Combine(root, "sms.json"));

        var appFiles = Directory.Exists(appsDir)
            ? Directory.GetFiles(appsDir)
                .Where(x => x.EndsWith(".apk", StringComparison.OrdinalIgnoreCase) || x.EndsWith(".apks", StringComparison.OrdinalIgnoreCase))
                .OrderBy(x => Path.GetFileName(x), StringComparer.OrdinalIgnoreCase).ToList()
            : new List<string>();
        var mediaFolders = BackupMediaFolders(mediaDir);

        if (hasC || hasS)
        {
            using var h = await ConnectHelper(false);
            if (h == null)
            {
                Log(T("skip_contacts_sms"));
                await PushForManualRestore(hasC ? Path.Combine(root, "contacts.json") : null, hasS ? Path.Combine(root, "sms.json") : null);
            }
            else
            {
                try
                {
                    if (hasC) await RestoreList(h, Path.Combine(root, "contacts.json"), "put_contacts", 200, T("contacts"));
                    if (hasS) await RestoreSms(h, Path.Combine(root, "sms.json"));
                }
                finally { await h.StopAsync(); }
            }
        }
        if (appFiles.Count > 0) await RestoreApps(appFiles);
        if (mediaFolders.Count > 0) await RestoreMedia(mediaDir, mediaFolders);
    }

    static void PullLine(Action<string> log, string l)
    {
        if (l.Contains("pulled") || l.Contains("pushed") || l.Contains("error", StringComparison.OrdinalIgnoreCase))
            log("   " + l.Trim());
    }

    // ---------------- update ----------------

    async void Update_Click(object sender, RoutedEventArgs e)
    {
        if (busy) return;
        if (string.IsNullOrEmpty(Updater.Repo)) { Log(T("update_no_repo")); return; }
        busy = true;
        UpdateBtn.IsEnabled = false;
        try
        {
            Log(T("update_checking"));
            var info = await Updater.CheckAsync();
            if (info.Version <= Updater.Current)
            {
                Log(T("update_none", Updater.Current.ToString(3)));
                return;
            }
            var ask = MessageBox.Show(
                T("update_prompt", Updater.Current.ToString(3), info.Version.ToString(3)),
                T("btn_update"), MessageBoxButton.YesNo, MessageBoxImage.Question, MessageBoxResult.Yes,
                Loc.Persian ? MessageBoxOptions.RtlReading | MessageBoxOptions.RightAlign : MessageBoxOptions.None);
            if (ask != MessageBoxResult.Yes) return;

            var file = await Updater.DownloadAsync(info, p => SetProgress(p, 100, T("update_downloading", p)));
            Log(T("update_restart"));
            Updater.ApplyAndRestart(file, info.AssetName);
            Application.Current.Shutdown();
        }
        catch (Exception ex)
        {
            Log(T("update_failed", ex.Message));
        }
        finally
        {
            busy = false;
            UpdateBtn.IsEnabled = true;
            StatusText.Text = "";
            Progress.Value = 0;
            await RefreshDevice();
        }
    }

    // ---------------- helper app ----------------

    async Task<HelperClient?> ConnectHelper(bool needSmsRole)
    {
        Log(T("installing_helper"));
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
                Log(T("helper_install_failed", r.Output.Trim()));
                if (r.Output.Contains("USER_RESTRICTED")) Log(T("helper_xiaomi"));
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

        Log(T("connecting_helper"));
        var client = new HelperClient(token, ct);
        if (!await client.ConnectAsync(25))
        {
            client.Dispose();
            Log(T("helper_connect_failed"));
            return null;
        }
        return client;
    }

    // ---------------- backup ----------------

    async Task DoBackup(bool doApps, bool media, bool contacts, bool sms)
    {
        if (!(doApps || media || contacts || sms)) { Log(T("select_one")); return; }

        var baseDir = FolderBox.Text.Trim();
        if (!Directory.Exists(baseDir)) { Log(T("folder_invalid")); return; }
        var modelPart = SafeName(deviceModel ?? "Phone", "Phone").Replace(' ', '-');
        var root = Path.Combine(baseDir, $"PhoneBackup_{modelPart}_{DateTime.Now:yyyyMMdd_HHmm}");
        Directory.CreateDirectory(root);
        lastBackupRoot = root;
        Log(T("backup_folder", root));

        if (contacts || sms)
        {
            using var h = await ConnectHelper(false);
            if (h == null) Log(T("skip_contacts_sms"));
            else
            {
                try
                {
                    if (contacts)
                    {
                        Log(T("backing_contacts"));
                        var list = await h.Stream("get_contacts", (i, n) => SetProgress(i, n, T("list_progress", T("contacts"), i, n)));
                        SaveList(Path.Combine(root, "contacts.json"), list);
                        Log(T("contacts_saved", list.Count));
                    }
                    if (sms)
                    {
                        Log(T("backing_sms"));
                        var list = await h.Stream("get_sms", (i, n) => SetProgress(i, n, T("list_progress", T("sms"), i, n)));
                        SaveList(Path.Combine(root, "sms.json"), list);
                        Log(T("sms_saved", list.Count));
                    }
                }
                finally { await h.StopAsync(); }
            }
        }

        if (doApps)
        {
            var selected = apps.Where(a => a.IsSelected).ToList();
            if (selected.Count == 0) Log(T("apps_none_selected"));
            else await BackupApps(root, selected);
        }
        if (media) await BackupMedia(root);
        Log(T("backup_done"));
    }

    static void SaveList(string path, List<JsonNode> list)
    {
        var arr = new JsonArray();
        foreach (var n in list) arr.Add(n);
        File.WriteAllText(path, arr.ToJsonString(FileOpts), new UTF8Encoding(false));
    }

    static string SafeName(string name, string fallback)
    {
        var sb = new StringBuilder();
        var bad = Path.GetInvalidFileNameChars();
        foreach (var c in name)
            sb.Append(bad.Contains(c) || char.IsControl(c) ? ' ' : c);
        var s = string.Join(" ", sb.ToString().Split(' ', StringSplitOptions.RemoveEmptyEntries)).Trim().TrimEnd('.');
        if (s.Length > 50) s = s.Substring(0, 50).Trim();
        return s.Length == 0 ? fallback : s;
    }

    static string UniqueName(string name, HashSet<string> used)
    {
        if (used.Add(name)) return name;
        for (int i = 2; ; i++)
        {
            var n = $"{name} ({i})";
            if (used.Add(n)) return n;
        }
    }

    /// همه‌ی فایل‌ها در یک پوشه‌ی apps کنار هم: «نام برنامه.apk»؛
    /// برنامه‌هایی که چند APK دارند (split) در یک فایل «نام برنامه.apks» جمع می‌شوند.
    async Task BackupApps(string root, List<AppItem> selected)
    {
        Log(T("backing_apps", selected.Count));
        var dir = Path.Combine(root, "apps");
        Directory.CreateDirectory(dir);
        var tmpRoot = Path.Combine(Path.GetTempPath(), "PhoneBackup_" + Guid.NewGuid().ToString("N"));
        var used = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        int i = 0, failed = 0;
        try
        {
            foreach (var app in selected)
            {
                ct.ThrowIfCancellationRequested();
                i++;
                SetProgress(i, selected.Count, T("apps_progress", i, selected.Count, app.Name));
                var tmp = Path.Combine(tmpRoot, i.ToString());
                Directory.CreateDirectory(tmp);

                var local = new List<string>();
                bool pulled = true;
                foreach (var remote in app.Paths)
                {
                    var fname = remote.Substring(remote.LastIndexOf('/') + 1);
                    var dest = Path.Combine(tmp, fname);
                    var res = await adb.RunOn(serial!, "pull", remote, dest);
                    if (res.Code != 0 || !File.Exists(dest))
                    {
                        Log(T("app_pull_failed", app.Name, res.Output.Trim().Replace("\r", "").Replace("\n", " ")));
                        pulled = false;
                        break;
                    }
                    local.Add(dest);
                }
                if (!pulled || local.Count == 0) { failed++; continue; }

                var baseName = UniqueName(SafeName(app.Name, app.Package), used);
                if (local.Count == 1)
                {
                    File.Move(local[0], Path.Combine(dir, baseName + ".apk"), true);
                }
                else
                {
                    var zipPath = Path.Combine(dir, baseName + ".apks");
                    if (File.Exists(zipPath)) File.Delete(zipPath);
                    using var za = ZipFile.Open(zipPath, ZipArchiveMode.Create);
                    foreach (var f in local)
                        za.CreateEntryFromFile(f, Path.GetFileName(f), CompressionLevel.NoCompression);
                }
            }
        }
        finally
        {
            try { Directory.Delete(tmpRoot, true); } catch { }
        }
        Log(T("apps_done", selected.Count - failed, failed));
    }

    async Task BackupMedia(string root)
    {
        var sel = folders.Where(x => x.IsSelected).Select(x => x.Key).ToList();
        if (sel.Count == 0) { Log(T("media_none_selected")); return; }
        var mediaDir = Path.Combine(root, "media");
        Directory.CreateDirectory(mediaDir);
        foreach (var rel in sel)
        {
            ct.ThrowIfCancellationRequested();
            int slash = rel.LastIndexOf('/');
            var localParent = slash < 0
                ? mediaDir
                : Path.Combine(mediaDir, rel.Substring(0, slash).Replace('/', Path.DirectorySeparatorChar));
            Directory.CreateDirectory(localParent);
            Log(T("copying_media", rel));
            StatusText.Text = T("copying_status", rel);
            Progress.IsIndeterminate = true;
            var r = await adb.RunOnStream(serial!, l => PullLine(Log, l), "pull", "-a", "/sdcard/" + rel, localParent);
            if (r.Code != 0 && r.Output.Contains("does not exist")) Log(T("media_missing", rel));
        }
    }

    // ---------------- restore ----------------

    static string? ResolveBackupRoot(string folder)
    {
        if (!Directory.Exists(folder)) return null;
        bool IsBackup(string f) =>
            Directory.Exists(Path.Combine(f, "apps")) || Directory.Exists(Path.Combine(f, "media")) ||
            File.Exists(Path.Combine(f, "contacts.json")) || File.Exists(Path.Combine(f, "sms.json"));
        if (IsBackup(folder)) return folder;
        return Directory.GetDirectories(folder, "PhoneBackup_*")
            .OrderByDescending(d => Directory.GetLastWriteTime(d)).FirstOrDefault(IsBackup);
    }

    static List<string> BackupMediaFolders(string mediaDir)
    {
        var list = new List<string>();
        if (!Directory.Exists(mediaDir)) return list;
        foreach (var d in Directory.GetDirectories(mediaDir))
        {
            var n = Path.GetFileName(d);
            if (n == "Android")
            {
                var m = Path.Combine(d, "media");
                if (Directory.Exists(m))
                    foreach (var s in Directory.GetDirectories(m))
                        list.Add("Android/media/" + Path.GetFileName(s));
            }
            else list.Add(n);
        }
        return list;
    }

    static string FileSizeText(long size) => size >= 1024 * 1024
        ? (size / 1024.0 / 1024.0).ToString("0.0") + " MB"
        : (size / 1024.0).ToString("0") + " KB";

    async Task DoRestore()
    {
        var root = ResolveBackupRoot(FolderBox.Text.Trim());
        if (root == null) { Log(T("no_backup_found")); return; }

        var appsDir = Path.Combine(root, "apps");
        var mediaDir = Path.Combine(root, "media");
        bool hasC = File.Exists(Path.Combine(root, "contacts.json"));
        bool hasS = File.Exists(Path.Combine(root, "sms.json"));

        var appItems = new List<PickItem>();
        if (Directory.Exists(appsDir))
        {
            foreach (var f in Directory.GetFiles(appsDir)
                         .Where(x => x.EndsWith(".apk", StringComparison.OrdinalIgnoreCase) ||
                                     x.EndsWith(".apks", StringComparison.OrdinalIgnoreCase))
                         .OrderBy(x => Path.GetFileName(x), StringComparer.OrdinalIgnoreCase))
            {
                appItems.Add(new PickItem
                {
                    Key = f,
                    Display = $"{Path.GetFileNameWithoutExtension(f)}   ({Path.GetExtension(f).ToLowerInvariant()}, {FileSizeText(new FileInfo(f).Length)})",
                    IsSelected = true
                });
            }
        }
        var mediaItems = new List<PickItem>();
        foreach (var rel in BackupMediaFolders(mediaDir))
            mediaItems.Add(new PickItem { Key = rel, Display = rel, IsSelected = true });

        if (!(appItems.Count > 0 || mediaItems.Count > 0 || hasC || hasS)) { Log(T("nothing_to_restore")); return; }

        var dlg = new RestoreWindow(root, deviceDesc ?? "", appItems, mediaItems, hasC, hasS) { Owner = this };
        if (dlg.ShowDialog() != true) return;

        var chosenApps = appItems.Where(it => it.IsSelected).Select(it => it.Key).ToList();
        var chosenMedia = mediaItems.Where(it => it.IsSelected).Select(it => it.Key).ToList();
        bool doContacts = hasC && dlg.DoContacts;
        bool doSms = hasS && dlg.DoSms;
        if (chosenApps.Count == 0 && chosenMedia.Count == 0 && !doContacts && !doSms) { Log(T("nothing_to_restore")); return; }
        Log(T("restoring_from", root));

        if (doContacts || doSms)
        {
            using var h = await ConnectHelper(false);
            if (h == null)
            {
                Log(T("skip_contacts_sms"));
                await PushForManualRestore(
                    doContacts ? Path.Combine(root, "contacts.json") : null,
                    doSms ? Path.Combine(root, "sms.json") : null);
            }
            else
            {
                try
                {
                    if (doContacts) await RestoreList(h, Path.Combine(root, "contacts.json"), "put_contacts", 200, T("contacts"));
                    if (doSms) await RestoreSms(h, Path.Combine(root, "sms.json"));
                }
                finally { await h.StopAsync(); }
            }
        }

        if (chosenApps.Count > 0) await RestoreApps(chosenApps);
        if (chosenMedia.Count > 0) await RestoreMedia(mediaDir, chosenMedia);
        Log(T("restore_done"));
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
        Log(T("restore_list", title, arr.Count));
        int done = 0, added = 0, skipped = 0;
        bool first = true;
        foreach (var part in Chunks(arr, chunk))
        {
            ct.ThrowIfCancellationRequested();
            int n = part.Count;
            var resp = await h.Request(new JsonObject { ["cmd"] = cmd, ["first"] = first, ["data"] = part });
            first = false;
            added += resp["added"]?.GetValue<int>() ?? 0;
            skipped += resp["skipped"]?.GetValue<int>() ?? 0;
            done += n;
            SetProgress(done, arr.Count, T("list_progress", title, done, arr.Count));
        }
        Log(T("list_result", title, added, skipped));
    }

    /// فایل‌های انتخاب‌شده‌ی .apk و .apks را نصب می‌کند.
    internal async Task RestoreApps(List<string> files)
    {
        Log(T("installing_apps", files.Count));
        var tmpRoot = Path.Combine(Path.GetTempPath(), "PhoneBackup_" + Guid.NewGuid().ToString("N"));
        int i = 0, okCount = 0;
        bool hinted = false;
        try
        {
            foreach (var f in files)
            {
                ct.ThrowIfCancellationRequested();
                i++;
                var name = Path.GetFileNameWithoutExtension(f);
                SetProgress(i, files.Count, T("apps_progress", i, files.Count, name));
                var tmp = Path.Combine(tmpRoot, i.ToString());
                Directory.CreateDirectory(tmp);

                var apks = new List<string>();
                if (f.EndsWith(".apks", StringComparison.OrdinalIgnoreCase))
                {
                    ZipFile.ExtractToDirectory(f, tmp);
                    apks.AddRange(Directory.GetFiles(tmp, "*.apk", SearchOption.AllDirectories));
                }
                else
                {
                    var dest = Path.Combine(tmp, "app.apk");
                    File.Copy(f, dest, true);
                    apks.Add(dest);
                }
                if (apks.Count == 0) continue;

                var args = new List<string> { "install-multiple", "-r" };
                args.AddRange(apks);
                var r = await adb.RunOn(serial!, args.ToArray());
                if (r.Output.Contains("Success")) okCount++;
                else
                {
                    Log(T("install_failed", name, r.Output.Trim().Replace("\r", "").Replace("\n", " ")));
                    if (!hinted && r.Output.Contains("USER_RESTRICTED")) { hinted = true; Log(T("xiaomi_install_hint")); }
                }
            }
        }
        finally
        {
            try { Directory.Delete(tmpRoot, true); } catch { }
        }
        Log(T("apps_installed", okCount, files.Count));
    }

    async Task RestoreMedia(string mediaDir, List<string> rels)
    {
        foreach (var rel in rels)
        {
            ct.ThrowIfCancellationRequested();
            var local = Path.Combine(mediaDir, rel.Replace('/', Path.DirectorySeparatorChar));
            if (!Directory.Exists(local)) continue;
            int slash = rel.LastIndexOf('/');
            var remoteParent = "/sdcard/" + (slash < 0 ? "" : rel.Substring(0, slash + 1));
            await adb.RunOn(serial!, "shell", "mkdir", "-p", remoteParent);
            Log(T("sending_media", rel));
            StatusText.Text = T("sending_status", rel);
            Progress.IsIndeterminate = true;
            await adb.RunOnStream(serial!, l => PullLine(Log, l), "push", local, remoteParent);
        }
        await adb.RunOn(serial!, "shell", "content", "call", "--uri", "content://media/",
            "--method", "scan_volume", "--arg", "external_primary");
        Log(T("media_rescan_hint"));
    }

    // ---------------- SMS restore (default SMS app handling) ----------------

    async Task<string> GetDefaultSmsPackage()
    {
        var r = await adb.RunOn(serial!, "shell", "cmd", "role", "get-role-holders", "--user", "0", SmsRole);
        foreach (var raw in r.Output.Split('\n'))
        {
            var l = raw.Trim();
            if (Regex.IsMatch(l, @"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")) return l;
        }
        var r2 = await adb.RunOn(serial!, "shell", "settings", "get", "secure", "sms_default_application");
        var t = r2.Output.Trim();
        return Regex.IsMatch(t, @"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$") ? t : "";
    }

    /// برنامه‌ی کمکی را (با دستور adb، بدون نیاز به تأیید روی گوشی) پیامک پیش‌فرض می‌کند،
    /// پیامک‌ها را برمی‌گرداند و در پایان برنامه‌ی پیامک قبلی را دوباره پیش‌فرض می‌کند.
    async Task RestoreSms(HelperClient h, string file)
    {
        var prev = await GetDefaultSmsPackage();
        if (prev.Length > 0) Log(T("sms_previous", prev));
        try
        {
            Log(T("sms_role_cmd"));
            var rr = await adb.RunOn(serial!, "shell", "cmd", "role", "add-role-holder", "--user", "0", SmsRole, Pkg);
            await h.PingAsync();
            bool ok = h.DefaultSms;

            if (!ok)
            {
                var msg = rr.Output.Trim();
                if (msg.Length > 0) Log("   " + msg.Replace("\r", "").Replace("\n", " "));
                // روش دوم: پنجره‌ی تأیید روی خود گوشی
                Log(T("sms_wait_default"));
                await adb.RunOn(serial!, "shell", "am", "start", "-n", Pkg + "/.MainActivity", "--ez", "request_sms_role", "true");
                for (int i = 0; i < 60 && !ok; i++)
                {
                    ct.ThrowIfCancellationRequested();
                    await Task.Delay(2000, ct);
                    await h.PingAsync();
                    ok = h.DefaultSms;
                }
            }
            if (!ok)
            {
                Log(T("sms_default_diag", h.DefaultPkg.Length > 0 ? h.DefaultPkg : "?"));
                Log(T("sms_not_confirmed"));
                await PushForManualRestore(null, file);
                return;
            }
            await RestoreList(h, file, "put_sms", 500, T("sms"));
        }
        finally
        {
            var old = adb.Token;
            adb.Token = default;
            try { await RevertSms(prev); } catch { }
            finally { adb.Token = old; }
        }
    }

    /// فایل‌های بکاپ را روی خود گوشی می‌گذارد تا در صورت نیاز از داخل خودِ برنامه‌ی کمکی
    /// (با دکمه‌های «بازیابی مخاطبین از فایل» / «بازیابی پیامک از فایل») به‌صورت دستی بازیابی شوند.
    async Task PushForManualRestore(string? contactsFile, string? smsFile)
    {
        if (contactsFile == null && smsFile == null) return;
        await adb.RunOn(serial!, "shell", "mkdir", "-p", ManualRestoreDir);
        if (contactsFile != null && File.Exists(contactsFile))
            await adb.RunOn(serial!, "push", contactsFile, ManualRestoreDir + "/contacts.json");
        if (smsFile != null && File.Exists(smsFile))
            await adb.RunOn(serial!, "push", smsFile, ManualRestoreDir + "/sms.json");
        Log(T("manual_restore_hint", ManualRestoreDir));
    }

    async Task RevertSms(string prev)
    {
        var cur = await GetDefaultSmsPackage();
        if (cur.Length > 0 && cur != Pkg) return; // نیازی به برگرداندن نیست
        if (prev.Length > 0 && prev != Pkg)
        {
            await adb.RunOn(serial!, "shell", "cmd", "role", "add-role-holder", "--user", "0", SmsRole, prev);
            if (await GetDefaultSmsPackage() == prev)
            {
                Log(T("sms_reverted", prev));
                return;
            }
        }
        Log(T("sms_revert"));
        await adb.RunOn(serial!, "shell", "am", "start", "-a", "android.settings.MANAGE_DEFAULT_APPS_SETTINGS");
    }

    // ---------------- uninstall / install-from-folder / open folder (invoked from BackupWindow) ----------------

    internal async Task UninstallSelectedApps()
    {
        var sel = apps.Where(a => a.IsSelected).ToList();
        if (sel.Count == 0) { Log(T("uninstall_none")); return; }

        int sysCount = sel.Count(a => a.Kind == 1);
        var names = string.Join("\n", sel.Take(15).Select(a => "• " + a.Name));
        if (sel.Count > 15) names += $"\n... (+{sel.Count - 15})";
        var msg = T("uninstall_confirm", sel.Count, names);
        if (sysCount > 0) msg += T("uninstall_confirm_system", sysCount);

        var ask = MessageBox.Show(msg, T("uninstall_title"), MessageBoxButton.YesNo,
            sysCount > 0 ? MessageBoxImage.Warning : MessageBoxImage.Question, MessageBoxResult.No,
            Loc.Persian ? MessageBoxOptions.RtlReading | MessageBoxOptions.RightAlign : MessageBoxOptions.None);
        if (ask != MessageBoxResult.Yes) return;

        Log(T("uninstalling", sel.Count));
        int done = 0, bad = 0, i = 0;
        foreach (var app in sel)
        {
            i++;
            SetProgress(i, sel.Count, T("apps_progress", i, sel.Count, app.Name));
            if (Protected.Contains(app.Package)) { bad++; Log(T("uninstall_protected", app.Package)); continue; }
            var r = await adb.RunOn(serial!, "shell", "pm", "uninstall", "--user", "0", app.Package);
            if (r.Output.Contains("Success"))
            {
                done++;
                Log(T("uninstall_ok", app.Name));
                apps.Remove(app);
            }
            else
            {
                bad++;
                Log(T("uninstall_fail", app.Name, r.Output.Trim().Replace("\r", "").Replace("\n", " ")));
            }
        }
        OnAppsChanged?.Invoke();
        Log(T("uninstall_done", done, bad));
    }

    internal async Task InstallFromFolder()
    {
        var dlg = new Microsoft.Win32.OpenFolderDialog { Title = T("btn_install_folder") };
        if (dlg.ShowDialog(this) != true) return;
        var files = Directory.GetFiles(dlg.FolderName)
            .Where(f => f.EndsWith(".apk", StringComparison.OrdinalIgnoreCase) ||
                        f.EndsWith(".apks", StringComparison.OrdinalIgnoreCase))
            .OrderBy(f => Path.GetFileName(f), StringComparer.OrdinalIgnoreCase).ToList();
        if (files.Count == 0) { Log(T("install_folder_empty", dlg.FolderName)); return; }

        var items = files.Select(f => new PickItem
        {
            Key = f,
            Display = $"{Path.GetFileNameWithoutExtension(f)}   ({Path.GetExtension(f).ToLowerInvariant()}, {FileSizeText(new FileInfo(f).Length)})",
            IsSelected = true
        }).ToList();

        var pick = new AppPickWindow(T("install_folder_pick_title", dlg.FolderName), items) { Owner = this };
        if (pick.ShowDialog() != true) return;
        var chosen = items.Where(it => it.IsSelected).Select(it => it.Key).ToList();
        if (chosen.Count == 0) return;
        await RestoreApps(chosen);
    }

    void OpenFolder_Click(object sender, RoutedEventArgs e)
    {
        var path = lastBackupRoot != null && Directory.Exists(lastBackupRoot) ? lastBackupRoot : FolderBox.Text.Trim();
        if (!Directory.Exists(path)) { Log(T("open_folder_missing", path)); return; }
        var psi = new ProcessStartInfo("explorer.exe") { UseShellExecute = false };
        psi.ArgumentList.Add(path);
        Process.Start(psi);
    }
}
