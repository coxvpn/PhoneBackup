using System.Collections.Generic;
using System.Windows;

namespace PhoneBackup;

/// پنجره‌ی انتخاب اینکه موقع بازیابی کدام برنامه‌ها، پوشه‌ها، مخاطبین و پیامک‌ها منتقل شوند.
public partial class RestoreWindow : Window
{
    readonly List<PickItem> apps;
    readonly List<PickItem> media;

    public bool DoContacts { get; private set; }
    public bool DoSms { get; private set; }

    public RestoreWindow(string backupPath, string device, List<PickItem> apps, List<PickItem> media,
        bool hasContacts, bool hasSms)
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
        ContactsChk.Content = Loc.T("chk_contacts");
        SmsChk.Content = Loc.T("chk_sms");
        OkBtn.Content = Loc.T("btn_start_restore");
        CancelBtn.Content = Loc.T("btn_cancel_dialog");

        AppsBox.ItemsSource = apps;
        MediaBox.ItemsSource = media;
        GrpApps.Visibility = apps.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        GrpMedia.Visibility = media.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        ContactsChk.Visibility = hasContacts ? Visibility.Visible : Visibility.Collapsed;
        SmsChk.Visibility = hasSms ? Visibility.Visible : Visibility.Collapsed;
    }

    static void SetAll(List<PickItem> list, bool v)
    {
        foreach (var i in list) i.IsSelected = v;
    }

    void AppsAll_Click(object sender, RoutedEventArgs e) => SetAll(apps, true);
    void AppsNone_Click(object sender, RoutedEventArgs e) => SetAll(apps, false);
    void MediaAll_Click(object sender, RoutedEventArgs e) => SetAll(media, true);
    void MediaNone_Click(object sender, RoutedEventArgs e) => SetAll(media, false);

    void Ok_Click(object sender, RoutedEventArgs e)
    {
        DoContacts = ContactsChk.Visibility == Visibility.Visible && ContactsChk.IsChecked == true;
        DoSms = SmsChk.Visibility == Visibility.Visible && SmsChk.IsChecked == true;
        DialogResult = true;
    }
}
