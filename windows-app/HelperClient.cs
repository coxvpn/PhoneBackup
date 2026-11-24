using System;
using System.Collections.Generic;
using System.IO;
using System.Net.Sockets;
using System.Text;
using System.Text.Json.Nodes;
using System.Threading.Tasks;

namespace PhoneBackup;

/// ارتباط با برنامه‌ی کمکی روی گوشی (از طریق adb forward روی 127.0.0.1)
public class HelperClient : IDisposable
{
    public const int Port = 38455;
    readonly string token;
    TcpClient? tcp;
    StreamReader? reader;
    StreamWriter? writer;
    public bool DefaultSms { get; private set; }

    public HelperClient(string token) { this.token = token; }

    public async Task<bool> ConnectAsync(int seconds)
    {
        var end = DateTime.UtcNow.AddSeconds(seconds);
        while (DateTime.UtcNow < end)
        {
            try
            {
                tcp?.Dispose();
                tcp = new TcpClient();
                await tcp.ConnectAsync("127.0.0.1", Port);
                var ns = tcp.GetStream();
                var enc = new UTF8Encoding(false);
                reader = new StreamReader(ns, enc);
                writer = new StreamWriter(ns, enc) { AutoFlush = true, NewLine = "\n" };
                if (await PingAsync()) return true;
            }
            catch
            {
            }
            await Task.Delay(700);
        }
        return false;
    }

    public async Task<bool> PingAsync()
    {
        var r = await Request(new JsonObject { ["cmd"] = "ping" });
        DefaultSms = r["defaultSms"]?.GetValue<bool>() ?? false;
        return true;
    }

    public async Task<JsonNode> Request(JsonObject req)
    {
        req["token"] = token;
        await writer!.WriteLineAsync(req.ToJsonString());
        var line = await reader!.ReadLineAsync() ?? throw new IOException("ارتباط با گوشی قطع شد");
        var node = JsonNode.Parse(line)!;
        if (node["ok"]?.GetValue<bool>() == false)
            throw new Exception(node["error"]?.GetValue<string>() ?? "خطای ناشناخته");
        return node;
    }

    /// دستورهایی مثل get_contacts / get_sms که اول count و بعد خط‌به‌خط داده می‌دهند.
    public async Task<List<JsonNode>> Stream(string cmd, Action<int, int>? progress)
    {
        var head = await Request(new JsonObject { ["cmd"] = cmd });
        int count = head["count"]!.GetValue<int>();
        var list = new List<JsonNode>(count);
        for (int i = 0; i < count; i++)
        {
            var line = await reader!.ReadLineAsync() ?? throw new IOException("ارتباط با گوشی قطع شد");
            var n = JsonNode.Parse(line)!;
            if (n["ok"]?.GetValue<bool>() == false)
                throw new Exception(n["error"]?.GetValue<string>() ?? "خطا");
            list.Add(n);
            if (i % 200 == 0) progress?.Invoke(i, count);
        }
        progress?.Invoke(count, count);
        return list;
    }

    public async Task StopAsync()
    {
        try { await Request(new JsonObject { ["cmd"] = "stop" }); } catch { }
    }

    public void Dispose() => tcp?.Dispose();
}
