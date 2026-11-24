using System;
using System.Collections.Generic;
using System.IO;

namespace PhoneBackup;

/// متن‌های برنامه به دو زبان. زبان پیش‌فرض انگلیسی است.
public static class Loc
{
    public static bool Persian { get; set; }

    static string SettingsFile => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PhoneBackup", "lang.txt");

    public static void Load()
    {
        try { Persian = File.Exists(SettingsFile) && File.ReadAllText(SettingsFile).Trim() == "fa"; }
        catch { Persian = false; }
    }

    public static void Save()
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(SettingsFile)!);
            File.WriteAllText(SettingsFile, Persian ? "fa" : "en");
        }
        catch { }
    }

    public static string T(string key, params object[] args)
    {
        if (!D.TryGetValue(key, out var v)) return key;
        var s = Persian ? v.Fa : v.En;
        return args.Length == 0 ? s : string.Format(s, args);
    }

    static readonly Dictionary<string, (string En, string Fa)> D = new()
    {
        // ---- window / general ----
        ["title"] = ("Android Phone Backup & Restore", "پشتیبان‌گیری و بازیابی گوشی اندروید"),
        ["language"] = ("Language:", "زبان:"),
        ["btn_update"] = ("Check for updates", "بررسی نسخه جدید"),
        ["btn_about"] = ("About", "درباره"),
        ["error"] = ("Error: {0}", "خطا: {0}"),

        // ---- device ----
        ["device_searching"] = ("Looking for a phone...", "در حال جستجوی گوشی..."),
        ["device_connected"] = ("Connected: {0}", "متصل: {0}"),
        ["device_unauth"] = ("Tap “Allow USB debugging” on the phone.", "روی گوشی پیام «Allow USB debugging» را تأیید کنید."),
        ["device_none"] = ("No phone found. Connect the cable and turn on USB debugging.", "گوشی وصل نیست. کابل را وصل کنید و USB Debugging را روشن کنید."),
        ["device_adb_error"] = ("ADB error: {0}", "خطا در اجرای ADB: {0}"),
        ["device_found"] = ("Phone detected: {0}", "گوشی شناسایی شد: {0}"),
        ["hint_connect"] = ("Connect your phone with a USB cable and enable USB debugging.", "گوشی را با کابل وصل کنید و USB Debugging را روشن کنید."),
        ["adb_prep_error"] = ("Could not prepare ADB: {0}", "خطا در آماده‌سازی ADB: {0}"),

        // ---- options ----
        ["grp_what"] = ("What to include", "چه چیزهایی؟"),
        ["chk_apps"] = ("Apps (installable APK files)", "برنامه‌ها (فایل نصبی APK)"),
        ["chk_media"] = ("Photos, videos and music", "عکس، فیلم و موزیک"),
        ["chk_contacts"] = ("Contacts", "مخاطبین"),
        ["chk_sms"] = ("SMS", "پیامک‌ها"),

        // ---- app list ----
        ["grp_apps"] = ("Apps to back up", "برنامه‌های موردنظر برای بکاپ"),
        ["btn_load_apps"] = ("Load app list from phone", "دریافت لیست برنامه‌ها از گوشی"),
        ["btn_sel_all"] = ("Select all", "انتخاب همه"),
        ["btn_sel_none"] = ("Select none", "هیچکدام"),
        ["search"] = ("Search:", "جستجو:"),
        ["col_name"] = ("App", "برنامه"),
        ["col_pkg"] = ("Package", "پکیج"),
        ["col_size"] = ("Size", "حجم"),
        ["apps_selected"] = ("Selected: {0} of {1}", "انتخاب‌شده: {0} از {1}"),
        ["loading_apps"] = ("Reading app list from the phone...", "در حال خواندن لیست برنامه‌ها از گوشی..."),
        ["apps_loaded"] = ("{0} apps found. Tick the ones you want.", "{0} برنامه پیدا شد. موردنظرها را تیک بزنید."),
        ["apps_load_failed"] = ("Could not load the app list.", "دریافت لیست برنامه‌ها انجام نشد."),
        ["apps_none_selected"] = ("No apps ticked - apps skipped. Use “Load app list” and tick some apps.", "هیچ برنامه‌ای تیک نخورده - برنامه‌ها رد شدند. ابتدا «دریافت لیست برنامه‌ها» را بزنید و تیک بزنید."),

        // ---- folder / buttons ----
        ["folder"] = ("Folder:", "پوشه:"),
        ["browse"] = ("Browse...", "انتخاب..."),
        ["btn_backup"] = ("Back up phone", "پشتیبان‌گیری از گوشی"),
        ["btn_restore"] = ("Restore to phone", "بازیابی روی گوشی"),
        ["select_one"] = ("Select at least one item.", "حداقل یک مورد را انتخاب کنید."),
        ["folder_invalid"] = ("The folder is not valid.", "پوشه‌ی انتخاب‌شده معتبر نیست."),
        ["backup_folder"] = ("Backup folder: {0}", "پوشه‌ی بکاپ: {0}"),

        // ---- helper app ----
        ["installing_helper"] = ("Installing helper app on the phone...", "نصب برنامه‌ی کمکی روی گوشی..."),
        ["helper_install_failed"] = ("Helper app install failed:\n{0}", "نصب برنامه‌ی کمکی ناموفق بود:\n{0}"),
        ["helper_xiaomi"] = ("On Xiaomi/Poco phones turn on “Install via USB” in Developer options.", "در گوشی‌های شیائومی/پوکو گزینه‌ی «Install via USB» را در Developer options روشن کنید."),
        ["connecting_helper"] = ("Connecting to the helper app (phone must be unlocked)...", "در حال اتصال به برنامه‌ی کمکی (گوشی باید روشن و بدون قفل باشد)..."),
        ["helper_connect_failed"] = ("Could not connect. Unlock the phone, accept any permission prompt and try again.", "اتصال برقرار نشد. صفحه‌ی گوشی را باز کنید، اگر پنجره‌ی مجوز آمد تأیید کنید و دوباره امتحان کنید."),
        ["skip_contacts_sms"] = ("Contacts and SMS were skipped.", "مخاطبین و پیامک‌ها انجام نشدند."),

        // ---- backup ----
        ["backing_contacts"] = ("Backing up contacts...", "بکاپ مخاطبین..."),
        ["contacts_saved"] = ("Contacts: {0} saved.", "مخاطبین: {0} مورد ذخیره شد."),
        ["contacts"] = ("Contacts", "مخاطبین"),
        ["backing_sms"] = ("Backing up SMS...", "بکاپ پیامک‌ها..."),
        ["sms_saved"] = ("SMS: {0} saved.", "پیامک‌ها: {0} مورد ذخیره شد."),
        ["sms"] = ("SMS", "پیامک‌ها"),
        ["backing_apps"] = ("Backing up {0} apps...", "بکاپ {0} برنامه..."),
        ["apps_progress"] = ("Apps: {0} of {1} - {2}", "برنامه‌ها: {0} از {1} — {2}"),
        ["app_pull_failed"] = ("   Failed: {0} ({1})", "   ناموفق: {0} ({1})"),
        ["apps_done"] = ("Apps finished: {0} OK, {1} failed.", "برنامه‌ها تمام شد: {0} موفق، {1} ناموفق."),
        ["copying_media"] = ("Copying /sdcard/{0} (this can take a while)...", "کپی /sdcard/{0} (ممکن است طول بکشد)..."),
        ["copying_status"] = ("Copying {0} ...", "در حال کپی {0} ..."),
        ["media_missing"] = ("   Folder {0} does not exist on the phone.", "   پوشه‌ی {0} روی گوشی نیست."),
        ["backup_done"] = ("✅ Backup finished.", "✅ پشتیبان‌گیری تمام شد."),

        // ---- restore ----
        ["no_backup_found"] = ("No backup found in this folder. Choose a PhoneBackup_... folder.", "در این پوشه بکاپی پیدا نشد. پوشه‌ی PhoneBackup_... را انتخاب کنید."),
        ["nothing_to_restore"] = ("None of the selected items exist in this backup.", "از موارد انتخاب‌شده چیزی در این بکاپ نیست."),
        ["restore_title"] = ("Confirm restore", "تأیید بازیابی"),
        ["restore_confirm"] = ("Restore from:\n{0}\n\nto the phone “{1}”?", "بازیابی از:\n{0}\n\nروی گوشی «{1}» انجام شود؟"),
        ["restoring_from"] = ("Restoring from: {0}", "بازیابی از: {0}"),
        ["restore_list"] = ("Restoring {0} ({1} items)...", "بازیابی {0} ({1} مورد)..."),
        ["list_progress"] = ("{0}: {1} of {2}", "{0}: {1} از {2}"),
        ["list_result"] = ("{0}: {1} added, {2} duplicates skipped.", "{0}: {1} مورد اضافه شد، {2} مورد تکراری رد شد."),
        ["sms_wait_default"] = ("On the phone, confirm the “default SMS app” prompt (I will wait up to 2 minutes)...", "روی گوشی پنجره‌ی «برنامه پیامک پیش‌فرض» را تأیید کنید (تا ۲ دقیقه صبر می‌کنم)..."),
        ["sms_not_confirmed"] = ("Not confirmed - SMS were not restored.", "تأیید نشد؛ پیامک‌ها بازیابی نشدند."),
        ["sms_revert"] = ("⚠ Now set your previous SMS app as default again (the settings page opens on the phone).", "⚠ حالا برنامه‌ی پیامک قبلی خود را دوباره پیش‌فرض کنید (صفحه‌ی تنظیمات روی گوشی باز می‌شود)."),
        ["installing_apps"] = ("Installing {0} apps...", "نصب {0} برنامه..."),
        ["install_failed"] = ("   ✗ {0}: {1}", "   ✗ {0}: {1}"),
        ["xiaomi_install_hint"] = ("If installs are blocked, enable “Install via USB” in Developer options and confirm prompts on the phone.", "اگر نصب مسدود شد، «Install via USB» را در Developer options روشن کنید و پیام‌های گوشی را تأیید کنید."),
        ["apps_installed"] = ("Apps: {0} of {1} installed.", "برنامه‌ها: {0} از {1} نصب شد."),
        ["sending_media"] = ("Sending {0} to the phone (this can take a while)...", "ارسال {0} به گوشی (ممکن است طول بکشد)..."),
        ["sending_status"] = ("Sending {0} ...", "در حال ارسال {0} ..."),
        ["media_rescan_hint"] = ("If files do not show in the gallery yet, restart the phone once.", "اگر فایل‌ها در گالری دیر پیدا شدند، یک‌بار گوشی را ری‌استارت کنید."),
        ["restore_done"] = ("✅ Restore finished.", "✅ بازیابی تمام شد."),

        // ---- update ----
        ["update_no_repo"] = ("Update check is not available in this build (no repository set).", "بررسی نسخه در این ساخت فعال نیست (آدرس ریپو تنظیم نشده)."),
        ["update_checking"] = ("Checking for a new version...", "در حال بررسی نسخه جدید..."),
        ["update_none"] = ("You are up to date (version {0}).", "برنامه به‌روز است (نسخه {0})."),
        ["update_prompt"] = ("A new version is available.\n\nCurrent: {0}\nNew: {1}\n\nDownload and update now?", "نسخه‌ی جدید موجود است.\n\nنسخه فعلی: {0}\nنسخه جدید: {1}\n\nالان دانلود و آپدیت شود؟"),
        ["update_downloading"] = ("Downloading update: {0}%", "در حال دانلود آپدیت: {0}%"),
        ["update_restart"] = ("Update downloaded. The app will restart.", "آپدیت دانلود شد. برنامه دوباره اجرا می‌شود."),
        ["update_failed"] = ("Update failed: {0}", "آپدیت ناموفق بود: {0}"),

        // ---- media folders / cancel / restore dialog ----
        ["grp_media"] = ("Media folders (read from the phone)", "پوشه‌های مدیا (خوانده‌شده از گوشی)"),
        ["btn_refresh_folders"] = ("Refresh folders", "بازخوانی پوشه‌ها"),
        ["folders_loaded"] = ("{0} folders found on the phone storage.", "{0} پوشه در حافظه‌ی گوشی پیدا شد."),
        ["media_none_selected"] = ("No media folders ticked - media skipped.", "هیچ پوشه‌ای تیک نخورده - مدیا رد شد."),
        ["btn_cancel"] = ("Cancel", "لغو"),
        ["cancelling"] = ("Cancelling...", "در حال لغو..."),
        ["cancelled"] = ("⛔ Stopped by user.", "⛔ توسط کاربر متوقف شد."),
        ["restore_dlg_title"] = ("Choose what to restore", "انتخاب موارد بازیابی"),
        ["restore_dlg_header"] = ("Backup: {0}\nPhone: {1}", "بکاپ: {0}\nگوشی: {1}"),
        ["restore_apps_group"] = ("Apps to install", "برنامه‌هایی که نصب شوند"),
        ["restore_media_group"] = ("Media folders to copy back", "پوشه‌های مدیا که برگردانده شوند"),
        ["restore_also"] = ("Also restoring: {0}", "همچنین بازیابی می‌شود: {0}"),
        ["btn_start_restore"] = ("Start restore", "شروع بازیابی"),
        ["btn_cancel_dialog"] = ("Cancel", "انصراف"),

        // ---- about ----
        ["about_title"] = ("About", "درباره برنامه"),
        ["about_desc"] = ("Back up and restore APK files, contacts, SMS, photos, videos and music of an Android phone from Windows.", "پشتیبان‌گیری و بازیابی فایل نصبی برنامه‌ها، مخاطبین، پیامک‌ها، عکس، فیلم و موزیک گوشی اندروید از ویندوز."),
        ["about_version"] = ("Version {0}", "نسخه {0}"),
        ["about_creator"] = ("Created by: Mostafa Mandegari", "سازنده: مصطفی ماندگاری"),
        ["about_close"] = ("Close", "بستن"),
    };
}
