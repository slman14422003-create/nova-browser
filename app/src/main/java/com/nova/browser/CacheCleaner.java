package com.nova.browser;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * تنظيف الملفات المؤقتة وكاش المتصفح (Java / NIO).
 * يجب استدعاؤه قبل إنشاء أي WebView حتى لا تكون ملفات الكاش مفتوحة.
 * لا يمسّ: الكوكيز، التنزيلات، التبويبات المحفوظة، الإعدادات.
 */
public final class CacheCleaner {
    private CacheCleaner() {}

    /** مجلدات الكاش داخل بيانات الـ WebView (آمنة الحذف عند بدء التطبيق). */
    private static final String[] WEBVIEW_CACHE_DIRS = {
            "HTTP Cache", "Code Cache", "GPUCache", "GrShaderCache", "ShaderCache",
            "Service Worker/CacheStorage", "Service Worker/ScriptCache"
    };

    /** يحذف الكاش ويعيد عدد البايتات المحررة. */
    public static long clean(Context ctx) {
        long freed = 0;
        freed += deleteContents(ctx.getCacheDir());
        File ext = ctx.getExternalCacheDir();
        if (ext != null) freed += deleteContents(ext);
        File base = new File(ctx.getApplicationInfo().dataDir, "app_webview/Default");
        for (String d : WEBVIEW_CACHE_DIRS) freed += deleteContents(new File(base, d));
        return freed;
    }

    /** حجم الكاش الحالي بالبايت (للعرض في الإعدادات). */
    public static long size(Context ctx) {
        long total = sizeOf(ctx.getCacheDir());
        File ext = ctx.getExternalCacheDir();
        if (ext != null) total += sizeOf(ext);
        File base = new File(ctx.getApplicationInfo().dataDir, "app_webview/Default");
        for (String d : WEBVIEW_CACHE_DIRS) total += sizeOf(new File(base, d));
        return total;
    }

    private static long sizeOf(File dir) {
        if (dir == null || !dir.exists()) return 0;
        final long[] sum = {0};
        try {
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    sum[0] += a.size();
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException ignored) { }
        return sum[0];
    }

    /** يحذف محتويات المجلد ويُبقي المجلد نفسه. */
    private static long deleteContents(File dir) {
        if (dir == null || !dir.exists()) return 0;
        final long[] freed = {0};
        final Path root = dir.toPath();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    try { long s = a.size(); Files.deleteIfExists(f); freed[0] += s; } catch (IOException ignored) { }
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path d, IOException e) {
                    if (!d.equals(root)) { try { Files.deleteIfExists(d); } catch (IOException ignored) { } }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException ignored) { }
        return freed[0];
    }

    /** تنسيق الحجم للعرض. */
    public static String format(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb);
        return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0);
    }
}
