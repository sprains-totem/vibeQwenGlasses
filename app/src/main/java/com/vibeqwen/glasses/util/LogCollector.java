package com.vibeqwen.glasses.util;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * APP 内日志收集器：
 * - 实时写入 /storage/emulated/0/Android/data/com.vibeqwen.glasses/files/logs/latest.log
 * - 方便 adb 直接读取: cat /sdcard/Android/data/com.vibeqwen.glasses/files/logs/latest.log
 * - 不弹系统分享框，纯本地文件存储与 logcat 输出
 */
public class LogCollector {

    private static final String TAG = "vibeLog";
    private static final int MAX_BUFFER = 5000;

    private static final Queue<String> buffer = new ConcurrentLinkedQueue<>();
    private static final java.util.List<LogListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static volatile boolean enabled = true;
    private static volatile Context appContext = null;
    private static final Object fileLock = new Object();

    public interface LogListener {
        void onLog(String line);
    }

    public static void addListener(LogListener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public static void removeListener(LogListener l) {
        if (l != null) listeners.remove(l);
    }

    public static void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
            ensureLatestLogHeader();
        }
    }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; }

    /** 记录一条日志并实时追加到 latest.log */
    public static void log(String scope, String message) {
        if (!enabled) return;
        String ts = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        String line = ts + " [" + scope + "] " + message;
        buffer.add(line);
        while (buffer.size() > MAX_BUFFER) buffer.poll();
        Log.i(TAG + "/" + scope, message);

        // 实时追加到 latest.log 文件
        appendToFile(line);

        // 通知 UI 实时监听器
        for (LogListener l : listeners) {
            try {
                l.onLog(line);
            } catch (Exception ignored) {
            }
        }
    }

    private static void ensureLatestLogHeader() {
        if (appContext == null) return;
        synchronized (fileLock) {
            try {
                File dir = new File(appContext.getExternalFilesDir(null), "logs");
                if (!dir.exists()) dir.mkdirs();
                File latest = new File(dir, "latest.log");
                if (!latest.exists() || latest.length() == 0) {
                    PrintWriter pw = new PrintWriter(new FileOutputStream(latest, false), true);
                    pw.println("==========================================");
                    pw.println("vibeQwenGlasses 运行日志 (latest)");
                    pw.println("启动时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
                    pw.println("设备型号: " + Build.MANUFACTURER + " " + Build.MODEL);
                    pw.println("系统版本: Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
                    pw.println("==========================================");
                    pw.println();
                    pw.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void appendToFile(String line) {
        if (appContext == null) return;
        synchronized (fileLock) {
            try {
                File dir = new File(appContext.getExternalFilesDir(null), "logs");
                if (!dir.exists()) dir.mkdirs();
                File latest = new File(dir, "latest.log");
                FileWriter fw = new FileWriter(latest, true);
                fw.write(line + "\n");
                fw.flush();
                fw.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** 连接层 */
    public static void c(String m) { log("CONN", m); }
    /** 握手层 */
    public static void h(String m) { log("HANDSHAKE", m); }
    /** 协议层 */
    public static void p(String m) { log("PROTO", m); }
    /** 录音层 */
    public static void r(String m) { log("RECORD", m); }
    /** 错误 */
    public static void e(String m) { log("ERROR", m); }

    /** 当前缓冲日志列表 */
    public static java.util.List<String> dump() {
        return new java.util.ArrayList<>(buffer);
    }

    /** 导出全部日志到独立带时间戳文件，并刷新 latest.log（返回 latest.log 路径） */
    public static File export(Context context) {
        init(context);
        Context ctx = context != null ? context : appContext;
        if (ctx == null) return null;
        synchronized (fileLock) {
            try {
                File dir = new File(ctx.getExternalFilesDir(null), "logs");
                if (!dir.exists()) dir.mkdirs();

                String dateStr = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                File historyFile = new File(dir, "vibeqwen_" + dateStr + ".log");
                File latestFile = new File(dir, "latest.log");

                PrintWriter pw = new PrintWriter(new FileOutputStream(historyFile), true);
                pw.println("==========================================");
                pw.println("vibeQwenGlasses 日志导出");
                pw.println("时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
                pw.println("设备: " + Build.MANUFACTURER + " " + Build.MODEL);
                pw.println("系统: Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
                pw.println("==========================================");
                pw.println();
                for (String s : buffer) pw.println(s);
                pw.println();
                pw.println("===== END =====");
                pw.close();

                // 同时重写 latest.log，保证其包含所有完整历史
                PrintWriter pwLatest = new PrintWriter(new FileOutputStream(latestFile, false), true);
                pwLatest.println("==========================================");
                pwLatest.println("vibeQwenGlasses 日志导出 (latest)");
                pwLatest.println("时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
                pwLatest.println("设备: " + Build.MANUFACTURER + " " + Build.MODEL);
                pwLatest.println("系统: Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
                pwLatest.println("==========================================");
                pwLatest.println();
                for (String s : buffer) pwLatest.println(s);
                pwLatest.println();
                pwLatest.println("===== END =====");
                pwLatest.close();

                Log.i(TAG, "日志已保存: " + latestFile.getAbsolutePath());
                return latestFile;
            } catch (Exception ex) {
                Log.e(TAG, "保存日志失败: " + ex.getMessage());
                return null;
            }
        }
    }

    public static void clear() {
        buffer.clear();
        if (appContext != null) {
            synchronized (fileLock) {
                try {
                    File dir = new File(appContext.getExternalFilesDir(null), "logs");
                    File latest = new File(dir, "latest.log");
                    if (latest.exists()) latest.delete();
                    ensureLatestLogHeader();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static int size() { return buffer.size(); }
}