using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text;
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

    void SetBusy(bool busy)
    {
        RefreshBtn.IsEnabled = ImeiBtn.IsEnabled = !busy;
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

    void Firmware_Click(object sender, RoutedEventArgs e)
    {
        if (firmwareUrl.Length == 0) return;
        try { Process.Start(new ProcessStartInfo(firmwareUrl) { UseShellExecute = true }); } catch { }
    }
}
