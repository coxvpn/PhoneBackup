using System.ComponentModel;

namespace PhoneBackup;

/// یک ردیف تیک‌دار (پوشه‌ی مدیا یا فایل برنامه)
public class PickItem : INotifyPropertyChanged
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

    public string Display { get; set; } = "";
    public string Key { get; set; } = "";

    public event PropertyChangedEventHandler? PropertyChanged;
}
