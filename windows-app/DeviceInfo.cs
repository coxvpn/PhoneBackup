using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace PhoneBackup;

public class InfoRow
{
    public string Section { get; set; } = "";
    public string Key { get; set; } = "";
    public string Label { get; set; } = "";
    public string Value { get; set; } = "";
}

public class InfoResult
{
    public List<InfoRow> Rows { get; } = new();
    public string FirmwareUrl { get; set; } = "";
}

/// مشخصات نرم‌افزاری و سخت‌افزاری گوشی را از طریق ADB می‌خواند.
public static class DeviceInfo
{
    static string T(string k, params object[] a) => Loc.T(k, a);

    static Dictionary<string, string> ParseProps(string output)
    {
        var d = new Dictionary<string, string>();
        foreach (var raw in output.Split('\n'))
        {
            var m = Regex.Match(raw.Trim(), @"^\[(.+?)\]: \[(.*)\]$");
            if (m.Success) d[m.Groups[1].Value] = m.Groups[2].Value;
        }
        return d;
    }

    static string Gb(long kb) => (kb / 1024.0 / 1024.0).ToString("0.0", CultureInfo.InvariantCulture) + " GB";

    static string OneUi(string raw)
    {
        if (!int.TryParse(raw, out var v) || v <= 0) return "";
        int major = v / 10000, minor = (v % 10000) / 100, patch = v % 100;
        return patch > 0 ? $"{major}.{minor}.{patch}" : $"{major}.{minor}";
    }

    /// رمزگشایی نسخه‌ی AP سامسونگ مثل A346EXXSGFZJ4: ۵ نویسه‌ی آخر = bit، major، سال، ماه، build
    static (string Bit, string Date)? DecodeSamsung(string ap)
    {
        if (ap.Length < 9 || !ap.All(char.IsLetterOrDigit)) return null;
        var t = ap.Substring(ap.Length - 5);
        char bit = t[0], year = t[2], month = t[3];
        if (!char.IsLetter(year) || month < 'A' || month > 'L') return null;
        int bitVal = char.IsDigit(bit) ? bit - '0' : 10 + (char.ToUpperInvariant(bit) - 'A');
        int y = 2001 + (char.ToUpperInvariant(year) - 'A');
        int m = month - 'A' + 1;
        return ($"{bit} ({bitVal})", $"{y}-{m:00}");
    }

    static async Task<string?> TryImei(Adb adb, string serial)
    {
        try
        {
            var r = await adb.RunOn(serial, "shell", "dumpsys", "iphonesubinfo");
            var m = Regex.Match(r.Output, @"Device ID\s*=\s*(\d{14,16})");
            if (m.Success) return m.Groups[1].Value;
        }
        catch { }
        try
        {
            var r = await adb.RunOn(serial, "shell", "service", "call", "iphonesubinfo", "1");
            var sb = new StringBuilder();
            foreach (Match m in Regex.Matches(r.Output, @"'([^']*)'")) sb.Append(m.Groups[1].Value);
            var digits = new string(sb.ToString().Where(char.IsDigit).ToArray());
            if (digits.Length >= 14 && digits.Length <= 16) return digits;
        }
        catch { }
        return null;
    }

    /// اندروید جدید اجازه‌ی خواندن IMEI با ADB را نمی‌دهد؛ این روش *#06# را روی خود گوشی
    /// نشان می‌دهد و عددها را از روی صفحه می‌خواند (بهترین تلاش، ممکن است روی بعضی گوشی‌ها کار نکند).
    public static async Task<List<string>> ReadImeiFromScreenAsync(Adb adb, string serial)
    {
        var found = new List<string>();
        await adb.RunOn(serial, "shell", "am", "start", "-a", "android.intent.action.CALL", "-d", "tel:%2A%2306%23");
        await Task.Delay(2500);
        for (int attempt = 0; attempt < 2 && found.Count == 0; attempt++)
        {
            await adb.RunOn(serial, "shell", "uiautomator", "dump", "/sdcard/pb_ui.xml");
            var xml = (await adb.RunOn(serial, "shell", "cat", "/sdcard/pb_ui.xml")).Output;
            foreach (Match m in Regex.Matches(xml, @"(?<!\d)\d{15}(?!\d)"))
                if (!found.Contains(m.Value)) found.Add(m.Value);
            if (found.Count == 0) await Task.Delay(1500);
        }
        await adb.RunOn(serial, "shell", "rm", "-f", "/sdcard/pb_ui.xml");
        await adb.RunOn(serial, "shell", "input", "keyevent", "4");
        await adb.RunOn(serial, "shell", "input", "keyevent", "4");
        return found;
    }

    public static async Task<InfoResult> CollectAsync(Adb adb, string serial)
    {
        var propsTask = adb.RunOn(serial, "shell", "getprop");
        var memTask = adb.RunOn(serial, "shell", "cat", "/proc/meminfo");
        var dfTask = adb.RunOn(serial, "shell", "df", "-k", "/data");
        var sizeTask = adb.RunOn(serial, "shell", "wm", "size");
        var denTask = adb.RunOn(serial, "shell", "wm", "density");
        var batTask = adb.RunOn(serial, "shell", "dumpsys", "battery");
        var kerTask = adb.RunOn(serial, "shell", "uname", "-r");
        var cpuTask = adb.RunOn(serial, "shell", "nproc");
        var imeiTask = TryImei(adb, serial);
        await Task.WhenAll(propsTask, memTask, dfTask, sizeTask, denTask, batTask, kerTask, cpuTask, imeiTask);

        var props = ParseProps(propsTask.Result.Output);
        string P(string k) => props.TryGetValue(k, out var v) ? v.Trim() : "";
        string First(params string[] keys)
        {
            foreach (var k in keys) { var v = P(k); if (v.Length > 0) return v; }
            return "";
        }

        var res = new InfoResult();
        void Add(string sec, string key, string label, string value)
        {
            value = (value ?? "").Trim();
            if (value.Length == 0) return;
            res.Rows.Add(new InfoRow { Section = T(sec), Key = key, Label = T(label), Value = value });
        }

        // ---- Device ----
        var model = P("ro.product.model");
        Add("sec_device", "model", "l_model", model);
        Add("sec_device", "brand", "l_brand", P("ro.product.brand"));
        Add("sec_device", "manufacturer", "l_manufacturer", P("ro.product.manufacturer"));
        Add("sec_device", "codename", "l_codename", P("ro.product.device"));
        Add("sec_device", "product", "l_product", P("ro.product.name"));
        var sn = First("ro.serialno", "ro.boot.serialno");
        Add("sec_device", "serial", "l_serial", sn.Length > 0 ? sn : serial);
        Add("sec_device", "imei", "l_imei", imeiTask.Result ?? "");

        // ---- Software ----
        var rel = P("ro.build.version.release");
        var sdk = P("ro.build.version.sdk");
        Add("sec_software", "android", "l_android", rel.Length > 0 ? (sdk.Length > 0 ? $"{rel} (API {sdk})" : rel) : "");
        Add("sec_software", "oneui", "l_oneui", OneUi(P("ro.build.version.oneui")));
        Add("sec_software", "patch", "l_patch", P("ro.build.version.security_patch"));
        Add("sec_software", "buildid", "l_buildid", P("ro.build.display.id"));
        Add("sec_software", "builddate", "l_builddate", P("ro.build.date"));
        Add("sec_software", "fingerprint", "l_fingerprint", P("ro.build.fingerprint"));
        Add("sec_software", "kernel", "l_kernel", kerTask.Result.Output.Trim());

        var vb = P("ro.boot.vbmeta.device_state");
        var fl = P("ro.boot.flash.locked");
        string bl = vb == "locked" ? T("v_locked") : vb == "unlocked" ? T("v_unlocked")
            : fl == "1" ? T("v_locked") : fl == "0" ? T("v_unlocked") : "";
        Add("sec_software", "blstate", "l_blstate", bl);
        var oem = P("sys.oem_unlock_allowed");
        Add("sec_software", "oemunlock", "l_oemunlock", oem == "1" ? T("v_yes") : oem == "0" ? T("v_no") : "");
        var wb = First("ro.boot.warranty_bit", "ro.warranty_bit");
        Add("sec_software", "knox", "l_knox", wb == "0" ? T("v_knox_ok") : wb.Length > 0 ? T("v_knox_bad") : "");

        // ---- Samsung firmware ----
        var csc = P("ro.csc.sales_code");
        var ap = First("ro.build.PDA");
        var bootloader = P("ro.bootloader");
        var cp = P("gsm.version.baseband");
        var cscVer = First("ril.official_cscver", "ril.sw_ver");
        Add("sec_firmware", "csc", "l_csc", csc);
        Add("sec_firmware", "ap", "l_ap", ap);
        Add("sec_firmware", "bl", "l_bl", bootloader);
        Add("sec_firmware", "cp", "l_cp", cp);
        Add("sec_firmware", "cscver", "l_cscver", cscVer);
        var dec = DecodeSamsung(ap);
        if (dec != null)
        {
            Add("sec_firmware", "bit", "l_bit", dec.Value.Bit);
            Add("sec_firmware", "fwdate", "l_fwdate", dec.Value.Date);
        }
        if (P("ro.product.manufacturer").Equals("samsung", StringComparison.OrdinalIgnoreCase) &&
            model.Length > 0 && csc.Length > 0)
        {
            res.FirmwareUrl = $"https://samfw.com/firmware/{model}/{csc}";
            Add("sec_firmware", "fwurl", "l_fwurl", res.FirmwareUrl);
        }

        // ---- Hardware ----
        var soc = $"{P("ro.soc.manufacturer")} {P("ro.soc.model")}".Trim();
        if (soc.Length == 0) soc = First("ro.board.platform");
        Add("sec_hardware", "chipset", "l_chipset", soc);
        Add("sec_hardware", "hardware", "l_hardware", P("ro.hardware"));
        Add("sec_hardware", "abi", "l_abi", P("ro.product.cpu.abilist"));
        Add("sec_hardware", "cores", "l_cores", cpuTask.Result.Output.Trim());

        var mem = Regex.Match(memTask.Result.Output, @"MemTotal:\s+(\d+)\s*kB");
        if (mem.Success) Add("sec_hardware", "ram", "l_ram", Gb(long.Parse(mem.Groups[1].Value)));

        var dfLines = dfTask.Result.Output.Split('\n').Select(l => l.Trim()).Where(l => l.Length > 0).ToList();
        if (dfLines.Count >= 2)
        {
            var c = dfLines.Last().Split(new[] { ' ', '\t' }, StringSplitOptions.RemoveEmptyEntries);
            if (c.Length >= 4 && long.TryParse(c[1], out var total) && long.TryParse(c[3], out var free))
                Add("sec_hardware", "storage", "l_storage", T("v_storage", Gb(total), Gb(free)));
        }

        var size = Regex.Match(sizeTask.Result.Output, @"Physical size:\s*(\S+)");
        var den = Regex.Match(denTask.Result.Output, @"Physical density:\s*(\d+)");
        if (size.Success)
            Add("sec_hardware", "screen", "l_screen", size.Groups[1].Value + (den.Success ? $" ({den.Groups[1].Value} dpi)" : ""));

        var bat = batTask.Result.Output;
        var lv = Regex.Match(bat, @"^\s*level:\s*(\d+)", RegexOptions.Multiline);
        if (lv.Success)
        {
            var parts = new List<string> { lv.Groups[1].Value + "%" };
            var tp = Regex.Match(bat, @"^\s*temperature:\s*(\d+)", RegexOptions.Multiline);
            if (tp.Success) parts.Add((int.Parse(tp.Groups[1].Value) / 10.0).ToString("0.0", CultureInfo.InvariantCulture) + " °C");
            var hl = Regex.Match(bat, @"^\s*health:\s*(\d+)", RegexOptions.Multiline);
            if (hl.Success)
            {
                parts.Add(hl.Groups[1].Value switch
                {
                    "2" => T("bat_good"),
                    "3" => T("bat_overheat"),
                    "4" => T("bat_dead"),
                    "5" => T("bat_overvoltage"),
                    "6" => T("bat_failure"),
                    "7" => T("bat_cold"),
                    _ => T("bat_unknown")
                });
            }
            Add("sec_hardware", "battery", "l_battery", string.Join(" - ", parts));
        }

        // ---- SIM / network ----
        Add("sec_sim", "operator", "l_operator", P("gsm.sim.operator.alpha"));
        var numeric = P("gsm.sim.operator.numeric");
        if (numeric.Length > 0)
        {
            var codes = numeric.Split(',', StringSplitOptions.RemoveEmptyEntries).Where(x => x.Length >= 5).ToList();
            if (codes.Count > 0)
            {
                Add("sec_sim", "mcc", "l_mcc", string.Join(",", codes.Select(x => x.Substring(0, 3))));
                Add("sec_sim", "mnc", "l_mnc", string.Join(",", codes.Select(x => x.Substring(3))));
            }
        }
        Add("sec_sim", "country", "l_country", P("gsm.sim.operator.iso-country").ToUpperInvariant());
        Add("sec_sim", "simstate", "l_simstate", P("gsm.sim.state"));

        return res;
    }
}
