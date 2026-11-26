using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;

namespace PhoneBackup;

public partial class DeviceInfoWindow : Window
{
    readonly Adb adb;
    readonly string serial;
    readonly ObservableCollection<InfoRow> rows = new();
    readonly List<InfoRow> all = new();
    string firmwareUrl = "";
    string model = "";
    string csc = "";
    bool busy;

    static string T(string k, params object[] a) => Loc.T(k, a);

    public DeviceInfoWindow(Adb adb, string serial)
    {
        InitializeComponent();
        this.adb = adb;
        this.serial = serial;

        FlowDirection = Loc.Persian ? FlowDirection.RightToLeft : FlowDirection.LeftToRight;
        Title = T("info_title");
        RefreshBtn.Content = T("btn_info_refresh");
        CopyBtn.Content = T("btn_info_copy");
        SaveBtn.Content = T("btn_info_save");
        ImeiBtn.Content = T("btn_info_imei");
        FirmwareBtn.Content = T("btn_info_firmware");
        GrpActions.Header = T("grp_device_actions");
        GrpOdin.Header = T("grp_odin");
        OdinPathLabel.Text = T("odin_path_label");
        OdinBrowseBtn.Content = T("browse");
        OdinLaunchBtn.Content = T("odin_launch");
        OdinPathBox.Text = LoadOdinPath();
        OdinSourceRun.Text = T("odin_source_link");
        RecoveryBtn.Content = T("btn_recovery");
        DownloadModeBtn.Content = T("btn_download_mode");
        FactoryResetBtn.Content = T("btn_factory_reset");
        CscGuideBtn.Content = T("btn_csc_guide");
        CloseBtn.Content = T("about_close");
        if (InfoList.View is GridView gv && gv.Columns.Count >= 2)
        {
            gv.Columns[0].Header = T("col_item");
            gv.Columns[1].Header = T("col_value");
        }

        var view = CollectionViewSource.GetDefaultView(rows);
        view.GroupDescriptions.Add(new PropertyGroupDescription("Section"));
        InfoList.ItemsSource = rows;

        Loaded += async (_, _) => await Load();
        Loaded += async (_, _) => await AutoDetectOdin();
    }

    void SetBusy(bool v)
    {
        busy = v;
        RefreshBtn.IsEnabled = ImeiBtn.IsEnabled = RecoveryBtn.IsEnabled =
            DownloadModeBtn.IsEnabled = FactoryResetBtn.IsEnabled = !v;
    }

    async System.Threading.Tasks.Task Load()
    {
        SetBusy(true);
        StatusText.Text = T("info_loading");
        try
        {
            var res = await DeviceInfo.CollectAsync(adb, serial);
            all.Clear();
            all.AddRange(res.Rows);
            firmwareUrl = res.FirmwareUrl;
            model = all.FirstOrDefault(r => r.Key == "model")?.Value ?? "";
            csc = all.FirstOrDefault(r => r.Key == "csc")?.Value ?? "";
            Render();
            FirmwareBtn.Visibility = firmwareUrl.Length > 0 ? Visibility.Visible : Visibility.Collapsed;
            StatusText.Text = T("info_done");
        }
        catch (Exception ex)
        {
            StatusText.Text = T("info_failed", ex.Message);
        }
        finally { SetBusy(false); }
    }

    void Render()
    {
        rows.Clear();
        foreach (var r in all) rows.Add(r);
    }

    async void Refresh_Click(object sender, RoutedEventArgs e) => await Load();

    async void Imei_Click(object sender, RoutedEventArgs e)
    {
        SetBusy(true);
        StatusText.Text = T("info_imei_reading");
        try
        {
            var list = await DeviceInfo.ReadImeiFromScreenAsync(adb, serial);
            if (list.Count == 0) StatusText.Text = T("info_imei_fail");
            else
            {
                all.RemoveAll(r => r.Key == "imei" || r.Key == "imei2");
                int idx = all.FindIndex(r => r.Key == "serial");
                idx = idx < 0 ? all.Count : idx + 1;
                all.Insert(idx, new InfoRow { Section = T("sec_device"), Key = "imei", Label = T("l_imei"), Value = list[0] });
                if (list.Count > 1)
                    all.Insert(idx + 1, new InfoRow { Section = T("sec_device"), Key = "imei2", Label = T("l_imei2"), Value = list[1] });
                Render();
                StatusText.Text = T("info_imei_found", string.Join(", ", list));
            }
        }
        catch (Exception ex)
        {
            StatusText.Text = T("info_failed", ex.Message);
        }
        finally { SetBusy(false); }
    }

    string BuildText()
    {
        var sb = new StringBuilder();
        var cur = "";
        foreach (var r in all)
        {
            if (r.Section != cur)
            {
                if (sb.Length > 0) sb.AppendLine();
                sb.AppendLine("[" + r.Section + "]");
                cur = r.Section;
            }
            sb.AppendLine($"{r.Label}\t: {r.Value}");
        }
        return sb.ToString();
    }

    void Copy_Click(object sender, RoutedEventArgs e)
    {
        try { Clipboard.SetText(BuildText()); StatusText.Text = T("info_copied"); } catch { }
    }

    void Save_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new Microsoft.Win32.SaveFileDialog
        {
            FileName = $"PhoneInfo_{(model.Length > 0 ? model : "phone")}.txt",
            Filter = "Text|*.txt"
        };
        if (dlg.ShowDialog(this) != true) return;
        try
        {
            File.WriteAllText(dlg.FileName, BuildText(), new UTF8Encoding(true));
            StatusText.Text = T("info_saved", dlg.FileName);
        }
        catch (Exception ex) { StatusText.Text = T("info_failed", ex.Message); }
    }

    void Row_DoubleClick(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        if (InfoList.SelectedItem is InfoRow r)
        {
            try { Clipboard.SetText(r.Value); StatusText.Text = T("info_copied"); } catch { }
        }
    }

    bool Confirm(string msg, MessageBoxImage icon = MessageBoxImage.Warning)
    {
        return MessageBox.Show(msg, T("confirm_title"), MessageBoxButton.YesNo, icon, MessageBoxResult.No,
            Loc.Persian ? MessageBoxOptions.RtlReading | MessageBoxOptions.RightAlign : MessageBoxOptions.None) == MessageBoxResult.Yes;
    }

    async void Recovery_Click(object sender, RoutedEventArgs e)
    {
        if (busy) return;
        if (!Confirm(T("confirm_recovery"), MessageBoxImage.Question)) return;
        SetBusy(true);
        try
        {
            await adb.RunOn(serial, "reboot", "recovery");
            StatusText.Text = T("recovery_sent");
        }
        catch (Exception ex) { StatusText.Text = T("info_failed", ex.Message); }
        finally { SetBusy(false); }
    }

    async void DownloadMode_Click(object sender, RoutedEventArgs e)
    {
        if (busy) return;
        if (!Confirm(T("confirm_download_mode"), MessageBoxImage.Question)) return;
        SetBusy(true);
        try
        {
            await adb.RunOn(serial, "reboot", "download");
            StatusText.Text = T("download_mode_sent");
        }
        catch (Exception ex) { StatusText.Text = T("info_failed", ex.Message); }
        finally { SetBusy(false); }
    }

    async void FactoryReset_Click(object sender, RoutedEventArgs e)
    {
        if (busy) return;
        if (!Confirm(T("confirm_factory_reset_1"), MessageBoxImage.Warning)) return;
        if (!Confirm(T("confirm_factory_reset_2", model), MessageBoxImage.Stop)) return;
        SetBusy(true);
        StatusText.Text = T("factory_reset_running");
        try
        {
            var r = await adb.RunOn(serial, "shell", "am", "broadcast", "-a", "android.intent.action.MASTER_CLEAR");
            if (r.Output.Contains("Broadcast completed") || r.Output.Trim().Length == 0)
                StatusText.Text = T("factory_reset_sent");
            else
                StatusText.Text = T("factory_reset_maybe_blocked");
        }
        catch (Exception ex) { StatusText.Text = T("info_failed", ex.Message); }
        finally { SetBusy(false); }
    }

    void CscGuide_Click(object sender, RoutedEventArgs e)
    {
        MessageBox.Show(T("csc_guide_text", csc.Length > 0 ? csc : "?"), T("btn_csc_guide"),
            MessageBoxButton.OK, MessageBoxImage.Information, MessageBoxResult.OK,
            Loc.Persian ? MessageBoxOptions.RtlReading | MessageBoxOptions.RightAlign : MessageBoxOptions.None);
        if (firmwareUrl.Length > 0)
            try { Process.Start(new ProcessStartInfo(firmwareUrl) { UseShellExecute = true }); } catch { }
    }

    // ---------------- Odin launcher (we only launch the user's own Odin.exe; we never flash anything ourselves) ----------------

    static string OdinSettingsFile => System.IO.Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PhoneBackup", "odin_path.txt");

    static string LoadOdinPath()
    {
        try { return System.IO.File.Exists(OdinSettingsFile) ? System.IO.File.ReadAllText(OdinSettingsFile).Trim() : ""; }
        catch { return ""; }
    }

    static void SaveOdinPath(string path)
    {
        try
        {
            System.IO.Directory.CreateDirectory(System.IO.Path.GetDirectoryName(OdinSettingsFile)!);
            System.IO.File.WriteAllText(OdinSettingsFile, path);
        }
        catch { }
    }

    /// جست‌وجوی پس‌زمینه برای یک کپی از Odin که از قبل خودِ کاربر دانلود کرده (ما Odin را همراه برنامه نمی‌گذاریم،
    /// چون فایل بسته و متعلق به سامسونگ است و حق بازتوزیعش را نداریم؛ فقط اگر جایی پیدایش کنیم مسیرش را پر می‌کنیم).
    async Task AutoDetectOdin()
    {
        if (OdinPathBox.Text.Trim().Length > 0 && File.Exists(OdinPathBox.Text.Trim())) return;
        var found = await Task.Run(SearchForOdin);
        if (found != null)
        {
            OdinPathBox.Text = found;
            SaveOdinPath(found);
        }
    }

    static string? SearchForOdin()
    {
        string[] roots =
        {
            Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),
            Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads"),
            Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
            Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86),
        };
        foreach (var root in roots.Distinct())
        {
            if (string.IsNullOrEmpty(root) || !Directory.Exists(root)) continue;
            var hit = SearchDir(root, 0);
            if (hit != null) return hit;
        }
        return null;
    }

    static string? SearchDir(string dir, int depth)
    {
        if (depth > 3) return null;
        try
        {
            foreach (var f in Directory.EnumerateFiles(dir))
            {
                var n = Path.GetFileName(f);
                if (n.StartsWith("odin", StringComparison.OrdinalIgnoreCase) && n.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
                    return f;
            }
            foreach (var d in Directory.EnumerateDirectories(dir))
            {
                var hit = SearchDir(d, depth + 1);
                if (hit != null) return hit;
            }
        }
        catch { /* بدون دسترسی یا پوشه‌ی غیرقابل‌خواندن؛ رد شو */ }
        return null;
    }

    void OdinSource_Click(object sender, RoutedEventArgs e)
    {
        try { Process.Start(new ProcessStartInfo("https://www.bing.com/search?q=Odin+flash+tool+XDA+download") { UseShellExecute = true }); } catch { }
    }

    void OdinBrowse_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new Microsoft.Win32.OpenFileDialog { Filter = "Odin.exe|*.exe", Title = T("grp_odin") };
        if (dlg.ShowDialog(this) != true) return;
        OdinPathBox.Text = dlg.FileName;
        SaveOdinPath(dlg.FileName);
    }

    void OdinLaunch_Click(object sender, RoutedEventArgs e)
    {
        var path = OdinPathBox.Text.Trim();
        if (path.Length == 0 || !System.IO.File.Exists(path)) { StatusText.Text = T("odin_not_set"); return; }
        try { Process.Start(new ProcessStartInfo(path) { UseShellExecute = true, WorkingDirectory = System.IO.Path.GetDirectoryName(path) }); }
        catch (Exception ex) { StatusText.Text = T("odin_launch_failed", ex.Message); }
    }

    void Firmware_Click(object sender, RoutedEventArgs e)
    {
        if (firmwareUrl.Length == 0) return;
        try { Process.Start(new ProcessStartInfo(firmwareUrl) { UseShellExecute = true }); } catch { }
    }
}
