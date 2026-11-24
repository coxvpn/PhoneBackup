using System.Collections.Generic;
using System.Windows;

namespace PhoneBackup;

/// پنجره‌ی انتخاب اینکه موقع بازیابی کدام برنامه‌ها و پوشه‌ها منتقل شوند.
public partial class RestoreWindow : Window
{
    readonly List<PickItem> apps;
    readonly List<PickItem> media;

    public RestoreWindow(string backupPath, string device, List<PickItem> apps, List<PickItem> media, string also)
    {
        InitializeComponent();
        this.apps = apps;
        this.media = media;
        FlowDirection = Loc.Persian ? FlowDirection.RightToLeft : FlowDirection.LeftToRight;
        Title = Loc.T("restore_dlg_title");
        HeaderText.Text = Loc.T("restore_dlg_header", backupPath, device);
        GrpApps.Header = Loc.T("restore_apps_group");
        GrpMedia.Header = Loc.T("restore_media_group");
        AppsAllBtn.Content = MediaAllBtn.Content = Loc.T("btn_sel_all");
        AppsNoneBtn.Content = MediaNoneBtn.Content = Loc.T("btn_sel_none");
        OkBtn.Content = Loc.T("btn_start_restore");
        CancelBtn.Content = Loc.T("btn_cancel_dialog");

        AppsBox.ItemsSource = apps;
        MediaBox.ItemsSource = media;
        GrpApps.Visibility = apps.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        GrpMedia.Visibility = media.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        AlsoText.Text = also;
        AlsoText.Visibility = also.Length > 0 ? Visibility.Visible : Visibility.Collapsed;
    }

    static void SetAll(List<PickItem> list, bool v)
    {
        foreach (var i in list) i.IsSelected = v;
    }

    void AppsAll_Click(object sender, RoutedEventArgs e) => SetAll(apps, true);
    void AppsNone_Click(object sender, RoutedEventArgs e) => SetAll(apps, false);
    void MediaAll_Click(object sender, RoutedEventArgs e) => SetAll(media, true);
    void MediaNone_Click(object sender, RoutedEventArgs e) => SetAll(media, false);
    void Ok_Click(object sender, RoutedEventArgs e) => DialogResult = true;
}
