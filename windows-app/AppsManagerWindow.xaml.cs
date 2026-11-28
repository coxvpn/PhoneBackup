using System;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;

namespace PhoneBackup;

/// پنجره‌ی مستقل مدیریت برنامه‌ها: دریافت لیست، نصب چند APK از فولدر کامپیوتر، حذف برنامه‌های تیک‌خورده.
/// برخلاف BackupWindow، هیچ ربطی به انتخاب موارد یک بکاپ ندارد.
public partial class AppsManagerWindow : Window
{
    readonly MainWindow owner;
    readonly ObservableCollection<AppItem> apps;
    ICollectionView? appsView;

    static string T(string key, params object[] a) => Loc.T(key, a);

    public AppsManagerWindow(MainWindow owner, ObservableCollection<AppItem> apps)
    {
        InitializeComponent();
        this.owner = owner;
        this.apps = apps;

        FlowDirection = Loc.Persian ? FlowDirection.RightToLeft : FlowDirection.LeftToRight;
        Title = T("btn_manage_apps");
        HeaderText.Text = T("manage_apps_header", owner.DeviceDesc ?? "");

        LoadAppsBtn.Content = T("btn_load_apps");
        InstallFolderBtn.Content = T("btn_install_folder");
        UninstallBtn.Content = T("btn_uninstall");
        SelAllBtn.Content = T("btn_sel_all");
        SelNoneBtn.Content = T("btn_sel_none");
        ShowUserChk.Content = T("show_user");
        ShowSystemChk.Content = T("show_system");
        SearchLabel.Text = T("search");
        CloseBtn.Content = T("about_close");

        if (AppList.View is GridView gv && gv.Columns.Count >= 5)
        {
            gv.Columns[1].Header = T("col_name");
            gv.Columns[2].Header = T("col_pkg");
            gv.Columns[3].Header = T("col_type");
            gv.Columns[4].Header = T("col_size");
        }

        AppList.ItemsSource = apps;
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
}
