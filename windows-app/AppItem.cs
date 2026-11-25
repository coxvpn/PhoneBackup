using System.Collections.Generic;
using System.ComponentModel;

namespace PhoneBackup;

public class AppItem : INotifyPropertyChanged
{
    bool selected;

    public bool IsSelected
    {
        get => selected;
        set
        {
            if (selected == value) return;
            selected = value;
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(IsSelected)));
        }
    }

    public string Name { get; set; } = "";
    public string Package { get; set; } = "";
    public string Version { get; set; } = "";
    public long Size { get; set; }

    /// 0 = برنامه‌ی کاربر، 1 = سیستمی، 2 = سیستمی به‌روزشده
    public int Kind { get; set; }

    public string TypeText => Kind switch
    {
        1 => Loc.T("type_system"),
        2 => Loc.T("type_updated"),
        _ => Loc.T("type_user")
    };
    public List<string> Paths { get; set; } = new();

    public string SizeText => Size >= 1024 * 1024
        ? (Size / 1024.0 / 1024.0).ToString("0.0") + " MB"
        : (Size / 1024.0).ToString("0") + " KB";

    public event PropertyChangedEventHandler? PropertyChanged;
}
