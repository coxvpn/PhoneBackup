using System.Collections.Generic;
using System.Windows;

namespace PhoneBackup;

/// پنجره‌ی عمومی تیک‌دار برای انتخاب چند فایل/مورد از یک لیست.
public partial class AppPickWindow : Window
{
    readonly List<PickItem> items;

    public AppPickWindow(string header, List<PickItem> items)
    {
        InitializeComponent();
        this.items = items;
        FlowDirection = Loc.Persian ? FlowDirection.RightToLeft : FlowDirection.LeftToRight;
        Title = Loc.T("install_folder_pick_title_bar");
        HeaderText.Text = header;
        AllBtn.Content = Loc.T("btn_sel_all");
        NoneBtn.Content = Loc.T("btn_sel_none");
        OkBtn.Content = Loc.T("btn_start_restore");
        CancelBtn.Content = Loc.T("btn_cancel_dialog");
        ItemsBox.ItemsSource = items;
    }

    void All_Click(object sender, RoutedEventArgs e) { foreach (var i in items) i.IsSelected = true; }
    void None_Click(object sender, RoutedEventArgs e) { foreach (var i in items) i.IsSelected = false; }
    void Ok_Click(object sender, RoutedEventArgs e) => DialogResult = true;
}
