using System;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;

namespace PhoneBackup;

/// پنجره‌ی انتخاب اینکه موقع بکاپ‌گیری چه چیزهایی شامل شود: برنامه‌ها (با انتخاب تک‌تک)،
/// عکس/فیلم/موزیک (با انتخاب پوشه‌ها)، مخاطبین و پیامک‌ها.
public partial class BackupWindow : Window
{
    readonly MainWindow owner;
    readonly ObservableCollection<AppItem> apps;
    readonly ObservableCollection<PickItem> folders;
    ICollectionView? appsView;

    public bool DoApps { get; private set; }
    public bool DoMedia { get; private set; }
    public bool DoContacts { get; private set; }
    public bool DoSms { get; private set; }

    static string T(string key, params object[] a) => Loc.T(key, a);

    public BackupWindow(MainWindow owner, ObservableCollection<AppItem> apps, ObservableCollection<PickItem> folders,
        bool forTransfer = false)
    {
        InitializeComponent();
        this.owner = owner;
        this.apps = apps;
        this.folders = folders;

        FlowDirection = Loc.Persian ? FlowDirection.RightToLeft : FlowDirection.LeftToRight;
        Title = forTransfer ? T("transfer_dlg_title") : T("backup_dlg_title");
        HeaderText.Text = forTransfer
            ? T("transfer_dlg_header", owner.DeviceDesc ?? "")
            : T("backup_dlg_header", owner.DeviceDesc ?? "");

        AppsChk.Content = T("chk_apps");
        MediaChk.Content = T("chk_media");
        ContactsChk.Content = T("chk_contacts");
        SmsChk.Content = T("chk_sms");

        LoadAppsBtn.Content = T("btn_load_apps");
        InstallFolderBtn.Content = T("btn_install_folder");
        UninstallBtn.Content = T("btn_uninstall");
        SelAllBtn.Content = T("btn_sel_all");
        SelNoneBtn.Content = T("btn_sel_none");
        ShowUserChk.Content = T("show_user");
        ShowSystemChk.Content = T("show_system");
        SearchLabel.Text = T("search");
        RefreshFoldersBtn.Content = T("btn_refresh_folders");
        StartBtn.Content = forTransfer ? T("btn_start_transfer") : T("btn_start_backup");
        CancelBtn.Content = T("btn_cancel_dialog");

        if (AppList.View is GridView gv && gv.Columns.Count >= 5)
        {
            gv.Columns[1].Header = T("col_name");
            gv.Columns[2].Header = T("col_pkg");
            gv.Columns[3].Header = T("col_type");
            gv.Columns[4].Header = T("col_size");
        }

        AppList.ItemsSource = apps;
        FolderList.ItemsSource = folders;
        appsView = CollectionViewSource.GetDefaultView(apps);
        appsView.Filter = o =>
        {
            var a = (AppItem)o;
            bool isSystem = a.Kind == 1;
            if (isSystem ? ShowSystemChk.IsChecked != true : ShowUserChk.IsChecked != true) return false;
            var t = SearchBox.Text.Trim();
            if (t.Length == 0) return true;
            return a.Name.Contains(t, StringComparison.OrdinalIgnoreCase) ||
                   a.Package.Contains(t, StringComparison.OrdinalIgnoreCase);
        };

        owner.OnAppsChanged = UpdateAppCount;
        Closed += (_, _) => { if (owner.OnAppsChanged == UpdateAppCount) owner.OnAppsChanged = null; };

        UpdateAppCount();
    }

    void UpdateAppCount() => AppCountText.Text = T("apps_selected", apps.Count(a => a.IsSelected), apps.Count);

    void AppsChk_Changed(object sender, RoutedEventArgs e) =>
        AppsPanel.Visibility = AppsChk.IsChecked == true ? Visibility.Visible : Visibility.Collapsed;

    void MediaChk_Changed(object sender, RoutedEventArgs e) =>
        MediaPanel.Visibility = MediaChk.IsChecked == true ? Visibility.Visible : Visibility.Collapsed;

    void Filter_Changed(object sender, RoutedEventArgs e) => appsView?.Refresh();
    void Search_Changed(object sender, TextChangedEventArgs e) => appsView?.Refresh();

    void SetVisibleSelection(bool value)
    {
        if (appsView == null) return;
        foreach (var a in appsView.Cast<AppItem>().ToList()) a.IsSelected = value;
        UpdateAppCount();
    }
    void SelAll_Click(object sender, RoutedEventArgs e) => SetVisibleSelection(true);
    void SelNone_Click(object sender, RoutedEventArgs e) => SetVisibleSelection(false);

    async void LoadApps_Click(object sender, RoutedEventArgs e)
    {
        LoadAppsBtn.IsEnabled = false;
        try { await owner.LoadApps(); }
        catch (Exception ex) { owner.Log(T("error", ex.Message)); }
        finally { LoadAppsBtn.IsEnabled = true; }
    }

    async void RefreshFolders_Click(object sender, RoutedEventArgs e)
    {
        RefreshFoldersBtn.IsEnabled = false;
        try { await owner.LoadMediaFolders(); }
        catch (Exception ex) { owner.Log(T("error", ex.Message)); }
        finally { RefreshFoldersBtn.IsEnabled = true; }
    }

    async void Uninstall_Click(object sender, RoutedEventArgs e)
    {
        UninstallBtn.IsEnabled = false;
        try { await owner.UninstallSelectedApps(); }
        catch (Exception ex) { owner.Log(T("error", ex.Message)); }
        finally { UninstallBtn.IsEnabled = true; }
    }

    async void InstallFolder_Click(object sender, RoutedEventArgs e)
    {
        InstallFolderBtn.IsEnabled = false;
        try { await owner.InstallFromFolder(); }
        catch (Exception ex) { owner.Log(T("error", ex.Message)); }
        finally { InstallFolderBtn.IsEnabled = true; }
    }

    void Start_Click(object sender, RoutedEventArgs e)
    {
        DoApps = AppsChk.IsChecked == true;
        DoMedia = MediaChk.IsChecked == true;
        DoContacts = ContactsChk.IsChecked == true;
        DoSms = SmsChk.IsChecked == true;
        if (!(DoApps || DoMedia || DoContacts || DoSms))
        {
            MessageBox.Show(T("select_one"), Title, MessageBoxButton.OK, MessageBoxImage.Information, MessageBoxResult.OK,
                Loc.Persian ? MessageBoxOptions.RtlReading | MessageBoxOptions.RightAlign : MessageBoxOptions.None);
            return;
        }
        DialogResult = true;
    }
}
