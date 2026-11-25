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

    void Firmware_Click(object sender, RoutedEventArgs e)
    {
        if (firmwareUrl.Length == 0) return;
        try { Process.Start(new ProcessStartInfo(firmwareUrl) { UseShellExecute = true }); } catch { }
    }
}
