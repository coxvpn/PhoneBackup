using System;
using System.Diagnostics;
using System.Windows;

namespace PhoneBackup;

public partial class AboutWindow : Window
{
    string url = "";

    public AboutWindow()
    {
        InitializeComponent();
        FlowDirection = Loc.Persian ? FlowDirection.RightToLeft : FlowDirection.LeftToRight;
        Title = Loc.T("about_title");
        AppTitle.Text = Loc.T("title");
        VersionText.Text = Loc.T("about_version", Updater.Current.ToString(3));
        DescText.Text = Loc.T("about_desc");
        CreatorText.Text = Loc.T("about_creator");
        CloseBtn.Content = Loc.T("about_close");
        if (string.IsNullOrEmpty(Updater.Repo))
        {
            RepoLink.IsEnabled = false;
        }
        else
        {
            url = "https://github.com/" + Updater.Repo;
            RepoRun.Text = url;
        }
    }

    void Repo_Click(object sender, RoutedEventArgs e)
    {
        if (url.Length == 0) return;
        try { Process.Start(new ProcessStartInfo(url) { UseShellExecute = true }); } catch { }
    }

    void Close_Click(object sender, RoutedEventArgs e) => Close();
}
