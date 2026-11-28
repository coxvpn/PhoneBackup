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

        // ---- app types / uninstall / open folder / sms ----
        ["show_user"] = ("User apps", "برنامه‌های کاربر"),
        ["show_system"] = ("System apps", "برنامه‌های سیستمی"),
        ["col_type"] = ("Type", "نوع"),
        ["type_user"] = ("User", "کاربر"),
        ["type_system"] = ("System", "سیستمی"),
        ["type_updated"] = ("System (updated)", "سیستمی (به‌روزشده)"),
        ["btn_uninstall"] = ("Uninstall ticked apps", "حذف برنامه‌های تیک‌خورده"),
        ["uninstall_title"] = ("Confirm uninstall", "تأیید حذف"),
        ["uninstall_none"] = ("Tick the apps you want to uninstall first.", "اول برنامه‌هایی را که می‌خواهید حذف شوند تیک بزنید."),
        ["uninstall_confirm"] = ("Uninstall {0} app(s) from the phone?\n\n{1}\n\nThe apps and their data will be deleted.", "{0} برنامه از گوشی حذف شود؟\n\n{1}\n\nخود برنامه و اطلاعاتش پاک می‌شود."),
        ["uninstall_confirm_system"] = ("\n\n⚠ {0} of them are SYSTEM apps. Removing system apps can break the phone.\n(Bring one back with: adb shell cmd package install-existing PACKAGE)", "\n\n⚠ {0} مورد از آن‌ها برنامه‌ی سیستمی است. حذف برنامه‌ی سیستمی ممکن است گوشی را دچار مشکل کند.\n(بازگرداندن: adb shell cmd package install-existing PACKAGE)"),
        ["uninstalling"] = ("Uninstalling {0} apps...", "حذف {0} برنامه..."),
        ["uninstall_ok"] = ("   ✓ removed: {0}", "   ✓ حذف شد: {0}"),
        ["uninstall_fail"] = ("   ✗ {0}: {1}", "   ✗ {0}: {1}"),
        ["uninstall_protected"] = ("   Skipped (protected system package): {0}", "   رد شد (پکیج حیاتی سیستم): {0}"),
        ["uninstall_done"] = ("Uninstall finished: {0} removed, {1} failed/skipped.", "حذف تمام شد: {0} حذف شد، {1} ناموفق/ردشده."),
        ["btn_open_folder"] = ("Open backup folder", "باز کردن پوشه‌ی بکاپ"),
        ["open_folder_missing"] = ("Folder not found: {0}", "پوشه پیدا نشد: {0}"),
        ["sms_previous"] = ("Current default SMS app: {0}", "برنامه‌ی پیامک پیش‌فرض فعلی: {0}"),
        ["sms_role_cmd"] = ("Setting the helper as default SMS app (automatic)...", "در حال پیش‌فرض‌کردن برنامه‌ی کمکی برای پیامک (خودکار)..."),
        ["sms_default_diag"] = ("The phone says the default SMS app is still: {0}", "گوشی می‌گوید برنامه‌ی پیامک پیش‌فرض هنوز این است: {0}"),
        ["sms_reverted"] = ("Default SMS app restored to: {0}", "برنامه‌ی پیامک پیش‌فرض به این برگشت: {0}"),

        // ---- device info window ----
        ["btn_info"] = ("Phone info", "مشخصات گوشی"),
        ["info_title"] = ("Phone information", "مشخصات گوشی"),
        ["info_loading"] = ("Reading phone information...", "در حال خواندن مشخصات گوشی..."),
        ["info_done"] = ("Double-click a row to copy its value.", "برای کپی‌کردن مقدار، روی هر ردیف دوبار کلیک کنید."),
        ["info_failed"] = ("Could not read info: {0}", "خواندن اطلاعات ناموفق بود: {0}"),
        ["info_copied"] = ("Copied.", "کپی شد."),
        ["info_saved"] = ("Saved: {0}", "ذخیره شد: {0}"),
        ["btn_info_refresh"] = ("Refresh", "بازخوانی"),
        ["btn_info_copy"] = ("Copy all", "کپی همه"),
        ["btn_info_save"] = ("Save as text file", "ذخیره به‌صورت فایل متنی"),
        ["btn_info_imei"] = ("Read IMEI from phone screen", "خواندن IMEI از صفحه‌ی گوشی"),
        ["btn_info_firmware"] = ("Open firmware page", "باز کردن صفحه‌ی فریمور"),
        ["info_imei_reading"] = ("Showing *#06# on the phone and reading it - keep the phone unlocked...", "در حال نمایش *#06# روی گوشی و خواندن آن - گوشی را بدون قفل نگه دارید..."),
        ["info_imei_found"] = ("IMEI read: {0}", "IMEI خوانده شد: {0}"),
        ["info_imei_fail"] = ("Could not read the IMEI automatically. Dial *#06# on the phone to see it.", "IMEI به‌صورت خودکار خوانده نشد. روی گوشی *#06# را شماره‌گیری کنید."),
        ["col_item"] = ("Item", "مورد"),
        ["col_value"] = ("Value", "مقدار"),
        ["sec_device"] = ("Device", "دستگاه"),
        ["sec_software"] = ("Software", "نرم‌افزار"),
        ["sec_firmware"] = ("Samsung firmware", "فریمور سامسونگ"),
        ["sec_hardware"] = ("Hardware", "سخت‌افزار"),
        ["sec_sim"] = ("SIM / Network", "سیم‌کارت و شبکه"),
        ["l_model"] = ("Model", "مدل"),
        ["l_brand"] = ("Brand", "برند"),
        ["l_manufacturer"] = ("Manufacturer", "سازنده"),
        ["l_codename"] = ("Device codename", "نام کدی دستگاه"),
        ["l_product"] = ("Product name", "نام محصول"),
        ["l_serial"] = ("Serial number", "شماره سریال"),
        ["l_imei"] = ("IMEI", "IMEI"),
        ["l_imei2"] = ("IMEI 2", "IMEI 2"),
        ["l_android"] = ("Android version", "نسخه‌ی اندروید"),
        ["l_oneui"] = ("One UI version", "نسخه‌ی One UI"),
        ["l_patch"] = ("Security patch level", "سطح پچ امنیتی"),
        ["l_buildid"] = ("Build ID", "شناسه‌ی بیلد"),
        ["l_builddate"] = ("Build date", "تاریخ بیلد"),
        ["l_fingerprint"] = ("Fingerprint", "Fingerprint بیلد"),
        ["l_kernel"] = ("Kernel", "کرنل"),
        ["l_blstate"] = ("Bootloader", "بوت‌لودر"),
        ["l_oemunlock"] = ("OEM unlock allowed", "اجازه‌ی OEM unlock"),
        ["l_knox"] = ("Knox warranty", "گارانتی Knox"),
        ["l_csc"] = ("CSC (sales code)", "CSC (کد فروش)"),
        ["l_ap"] = ("AP version", "نسخه‌ی AP"),
        ["l_bl"] = ("BL version", "نسخه‌ی BL"),
        ["l_cp"] = ("CP version", "نسخه‌ی CP"),
        ["l_cscver"] = ("CSC version", "نسخه‌ی CSC"),
        ["l_bit"] = ("Bit", "بیت (Bit)"),
        ["l_fwdate"] = ("Firmware date", "تاریخ فریمور"),
        ["l_fwurl"] = ("Firmware page", "صفحه‌ی فریمور"),
        ["l_chipset"] = ("Chipset", "چیپست"),
        ["l_hardware"] = ("Hardware", "سخت‌افزار"),
        ["l_abi"] = ("CPU architecture", "معماری پردازنده"),
        ["l_cores"] = ("CPU cores", "تعداد هسته"),
        ["l_ram"] = ("RAM", "حافظه‌ی RAM"),
        ["l_storage"] = ("Storage (user data)", "حافظه‌ی داخلی (داده‌ی کاربر)"),
        ["l_screen"] = ("Screen", "صفحه‌نمایش"),
        ["l_battery"] = ("Battery", "باتری"),
        ["l_operator"] = ("Operator", "اپراتور"),
        ["l_mcc"] = ("MCC", "MCC"),
        ["l_mnc"] = ("MNC", "MNC"),
        ["l_country"] = ("Country", "کشور"),
        ["l_simstate"] = ("SIM state", "وضعیت سیم‌کارت"),
        ["v_locked"] = ("Locked", "قفل"),
        ["v_unlocked"] = ("Unlocked", "باز"),
        ["v_yes"] = ("Yes", "بله"),
        ["v_no"] = ("No", "خیر"),
        ["v_knox_ok"] = ("Intact (0)", "سالم (0)"),
        ["v_knox_bad"] = ("Tripped (1)", "شکسته (1)"),
        ["v_storage"] = ("{0} total, {1} free", "{0} کل، {1} آزاد"),
        ["bat_good"] = ("Good", "سالم"),
        ["bat_overheat"] = ("Overheat", "داغ‌شده"),
        ["bat_dead"] = ("Dead", "خراب"),
        ["bat_overvoltage"] = ("Over voltage", "ولتاژ بالا"),
        ["bat_failure"] = ("Failure", "خطا"),
        ["bat_cold"] = ("Cold", "سرد"),
        ["bat_unknown"] = ("Unknown", "نامشخص"),

        // ---- install from folder ----
        ["btn_install_folder"] = ("Install APKs from a PC folder", "نصب چند APK از فولدر کامپیوتر"),
        ["btn_manage_apps"] = ("Manage apps", "مدیریت برنامه‌ها"),
        ["manage_apps_header"] = ("Install or uninstall apps on: {0}", "نصب یا حذف برنامه روی: {0}"),
        ["install_folder_empty"] = ("No .apk / .apks files found in: {0}", "فایل APK/.apks در این پوشه پیدا نشد: {0}"),
        ["install_folder_pick_title"] = ("Apps found in: {0}", "برنامه‌های پیدا‌شده در: {0}"),
        ["install_folder_pick_title_bar"] = ("Choose apps to install", "انتخاب برنامه‌ها برای نصب"),

        // ---- device actions ----
        ["grp_device_actions"] = ("Device actions", "عملیات گوشی"),
        ["btn_recovery"] = ("Reboot to Recovery", "ریبوت به Recovery"),
        ["btn_download_mode"] = ("Reboot to Download mode", "ریبوت به Download mode (Odin)"),
        ["btn_factory_reset"] = ("Factory reset", "فکتوری ریست"),
        ["btn_csc_guide"] = ("Change CSC guide", "راهنمای تغییر CSC"),
        ["confirm_title"] = ("Confirm", "تأیید"),
        ["confirm_recovery"] = ("Reboot the phone into Recovery mode now?", "گوشی الان به حالت Recovery ریبوت شود؟"),
        ["recovery_sent"] = ("Command sent. The phone is rebooting into Recovery.", "دستور ارسال شد. گوشی در حال ریبوت به Recovery است."),
        ["confirm_download_mode"] = ("Reboot the phone into Download (Odin) mode now?", "گوشی الان به حالت Download (Odin) ریبوت شود؟"),
        ["download_mode_sent"] = ("Command sent. The phone is rebooting into Download mode.", "دستور ارسال شد. گوشی در حال ریبوت به Download mode است."),
        ["confirm_factory_reset_1"] = ("⚠ FACTORY RESET\n\nThis erases ALL data on the phone: apps, photos, messages, accounts - everything. This cannot be undone.\n\nMake sure you already have a backup. Continue?", "⚠ فکتوری ریست\n\nاین کار همه‌ی اطلاعات گوشی را پاک می‌کند: برنامه‌ها، عکس‌ها، پیامک‌ها، حساب‌ها - همه‌چیز. این کار برگشت‌ناپذیر است.\n\nمطمئن شو قبلاً بکاپ گرفته‌ای. ادامه می‌دهی؟"),
        ["confirm_factory_reset_2"] = ("Last confirmation: erase phone \"{0}\" completely?", "تأیید نهایی: گوشی «{0}» کاملاً پاک شود؟"),
        ["factory_reset_running"] = ("Sending factory reset command...", "در حال ارسال دستور فکتوری ریست..."),
        ["factory_reset_sent"] = ("Command sent. If the phone does not reset within a minute, it blocked the command - use Settings > General management > Reset instead.", "دستور ارسال شد. اگر تا یک دقیقه گوشی ریست نشد، یعنی دستور را مسدود کرده - از مسیر تنظیمات > مدیریت کلی > بازنشانی استفاده کن."),
        ["factory_reset_maybe_blocked"] = ("The phone may have blocked this command on newer Android versions. Use Settings > General management > Reset > Factory data reset instead.", "ممکن است گوشی این دستور را در نسخه‌های جدید اندروید مسدود کرده باشد. از مسیر تنظیمات > مدیریت کلی > بازنشانی > بازنشانی اطلاعات کارخانه استفاده کن."),
        ["csc_guide_text"] = ("Current CSC: {0}\n\nOn modern Samsung phones there is no ADB command that changes CSC. A real CSC change needs re-flashing the phone with Odin using a CSC file for the target region (HOME_CSC keeps your data, CSC erases it).\n\nSteps:\n1. Download the firmware for the target CSC from the page that will now open (or samfw.com / Frija).\n2. Install Odin on the PC and Samsung USB drivers.\n3. Boot the phone into Download mode (button above), connect the cable.\n4. In Odin, load BL/AP/CP in their slots and the CSC file in the CSC slot (not HOME_CSC) and press Start.\n\nThis is a firmware flash, not something this app performs automatically; a wrong file can make the phone unusable, so only do this if you are comfortable with Odin.", "CSC فعلی: {0}\n\nروی گوشی‌های جدید سامسونگ هیچ دستور ADB‌ای وجود ندارد که CSC را واقعاً عوض کند. تغییر واقعی CSC نیاز به فلش‌کردن گوشی با Odin و فایل CSC مربوط به منطقه‌ی هدف دارد (HOME_CSC اطلاعات را نگه می‌دارد، CSC پاکشان می‌کند).\n\nمراحل:\n۱. فریمور مربوط به CSC موردنظر را از صفحه‌ای که الان باز می‌شود (یا samfw.com یا Frija) دانلود کن.\n۲. Odin و درایور USB سامسونگ را روی کامپیوتر نصب کن.\n۳. گوشی را با دکمه‌ی بالا وارد Download mode کن و کابل را وصل کن.\n۴. در Odin فایل‌های BL/AP/CP را در جایگاه خودشان و فایل CSC (نه HOME_CSC) را در جایگاه CSC بگذار و Start را بزن.\n\nاین کار فلش‌کردن فریمور است و این برنامه آن را خودکار انجام نمی‌دهد؛ فایل اشتباه می‌تواند گوشی را از کار بیندازد، پس فقط اگر با Odin راحتی این کار را انجام بده."),

        ["manual_restore_hint"] = ("The backup file(s) were also copied to the phone at {0}. On the phone, open the \"Phone Backup Helper\" app and use \"Restore contacts from file\" / \"Restore SMS from file\" to finish manually, then pick that file.", "فایل‌ها هم روی خود گوشی کپی شدند در: {0}\nروی گوشی برنامه‌ی «پشتیبان گوشی» را باز کن و از «بازیابی مخاطبین از فایل» / «بازیابی پیامک از فایل» استفاده کن و همان فایل را انتخاب کن."),

        // ---- Odin launcher ----
        ["grp_odin"] = ("Flash firmware (launch your own Odin)", "فلش فریمور (اجرای Odin خودت)"),
        ["odin_path_label"] = ("Odin.exe:", "مسیر Odin.exe:"),
        ["odin_launch"] = ("Launch Odin", "اجرای Odin"),
        ["odin_not_set"] = ("Browse to your own Odin.exe first (this app does not include or download Odin).", "اول مسیر Odin.exe خودت را انتخاب کن (این برنامه خودِ Odin را همراه ندارد و دانلودش نمی‌کند)."),
        ["odin_launch_failed"] = ("Could not launch Odin: {0}", "اجرای Odin ناموفق بود: {0}"),
        ["odin_source_link"] = ("Don't have Odin? Search for a copy (opens your browser)", "Odin نداری؟ جست‌وجوی یک نسخه (مرورگرت باز می‌شود)"),

        // ---- phone summary (normal state) / backup dialog ----
        ["grp_phone_summary"] = ("Phone", "گوشی"),
        ["summary_waiting"] = ("Connect a phone to see its specs here. Full details are in \"Phone info\".", "گوشی را وصل کن تا مشخصاتش اینجا دیده شود. جزئیات کامل در «مشخصات گوشی» است."),
        ["summary_battery"] = ("Battery", "باتری"),
        ["summary_media"] = ("Photos/Videos", "عکس و فیلم"),
        ["summary_music"] = ("Music", "موزیک"),
        ["summary_apps_size"] = ("Apps", "برنامه‌ها"),
        ["backup_dlg_title"] = ("Choose what to back up", "انتخاب موارد بکاپ"),
        ["backup_dlg_header"] = ("Back up from: {0}", "بکاپ از: {0}"),
        ["btn_start_backup"] = ("Start backup", "شروع بکاپ"),

        // ---- phone-to-phone transfer ----
        ["btn_transfer"] = ("Transfer phone to phone", "انتقال گوشی به گوشی"),
        ["transfer_dlg_title"] = ("Choose what to transfer", "انتخاب موارد انتقال"),
        ["transfer_dlg_header"] = ("Transfer from: {0}\nIt will be written straight onto the second phone - no files are kept.", "انتقال از: {0}\nمستقیم روی گوشی دوم نوشته می‌شود؛ هیچ فایلی نگه داشته نمی‌شود."),
        ["btn_start_transfer"] = ("Continue - connect the second phone next", "ادامه - بعدش گوشی دوم را وصل کن"),
        ["transfer_backup_from"] = ("Reading the selected items from: {0}", "خواندن موارد انتخابی از: {0}"),
        ["transfer_backup_done"] = ("Done reading from the first phone.", "خواندن از گوشی اول تمام شد."),
        ["transfer_disconnect_first"] = ("Now disconnect this phone and connect the SECOND phone with the cable...", "حالا این گوشی را جدا کن و گوشی دوم را با کابل وصل کن..."),
        ["transfer_second_unauth"] = ("A second phone is connected - confirm the \"Allow USB debugging\" prompt on it.", "گوشی دوم وصل شد؛ پیام «Allow USB debugging» را روی خودش تأیید کن."),
        ["transfer_restoring_to"] = ("Second phone: {0}. Writing the data onto it now...", "گوشی دوم: {0}. در حال نوشتن اطلاعات روی آن..."),
        ["transfer_done"] = ("✅ Transfer finished.", "✅ انتقال تمام شد."),
        ["transfer_kept_backup"] = ("Something went wrong partway through. What was already collected is kept at: {0} - you can restore it to a phone later from the Restore button.", "در میانه‌ی کار مشکلی پیش آمد. آنچه تا الان جمع شده اینجا نگه داشته شد: {0} - بعداً می‌توانی از دکمه‌ی «بازیابی» روی یک گوشی برش گردانی."),

        // ---- about ----
        ["about_title"] = ("About", "درباره برنامه"),
        ["about_desc"] = ("Back up and restore APK files, contacts, SMS, photos, videos and music of an Android phone from Windows.", "پشتیبان‌گیری و بازیابی فایل نصبی برنامه‌ها، مخاطبین، پیامک‌ها، عکس، فیلم و موزیک گوشی اندروید از ویندوز."),
        ["about_version"] = ("Version {0}", "نسخه {0}"),
        ["about_creator"] = ("Created by: Mostafa Mandegari", "سازنده: مصطفی ماندگاری"),
        ["about_close"] = ("Close", "بستن"),
    };
}
