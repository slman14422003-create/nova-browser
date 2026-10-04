package com.nova.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.URL

/** سجل تشخيص تشغيل يوتيوب (يُعرض في الإعدادات ويمكن نسخه). */
object YtLog {
    private val lines = ArrayDeque<String>()
    private val fmt = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
    @Synchronized fun add(m: String) {
        lines.addLast(fmt.format(java.util.Date()) + "  " + m)
        while (lines.size > 160) lines.removeFirst()
    }
    @Synchronized fun text(): String = lines.joinToString("\n")
    @Synchronized fun clear() = lines.clear()
}

/** حالة تشغيل يوتيوب الحالية + أوامر التحكم من إشعار الوسائط/شاشة القفل/Now Bar. */
object YtMedia {
    @Volatile var playing = false
    @Volatile var title = ""
    @Volatile var artist = ""
    @Volatile var art: Bitmap? = null
    @Volatile var pos = 0L
    @Volatile var dur = 0L
    var owner: BrowserTab? = null
    private var wvRef: WeakReference<WebView>? = null
    private var artUrl = ""

    fun control(a: String) {
        val w = wvRef?.get() ?: return
        w.post {
            // الصفحة قد تكون مجمّدة (onPause/pauseTimers) فنوقظها قبل تنفيذ الأمر
            if (a != "pause") { w.resumeTimers(); w.onResume() }
            w.evaluateJavascript("window.__novaYtCtl&&window.__novaYtCtl(${JSONObject.quote(a)})", null)
        }
    }

    fun update(ctx: Context, tab: BrowserTab, wv: WebView, o: JSONObject) {
        owner = tab; wvRef = WeakReference(wv)
        playing = o.optBoolean("playing"); pos = o.optLong("pos"); dur = o.optLong("dur")
        title = o.optString("title"); artist = o.optString("artist")
        val url = o.optString("art")
        if (url.isNotBlank() && url != artUrl && isTrustedArt(url)) {
            artUrl = url
            Thread {
                runCatching {
                    val c = URL(url).openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }
                    val bmp = c.getInputStream().use { BitmapFactory.decodeStream(it) }
                    if (bmp != null) {
                        val k = 512f / maxOf(bmp.width, bmp.height)
                        art = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
                        MediaService.instance?.refresh()
                    }
                }
            }.start()
        }
        val svc = MediaService.instance
        if (svc != null) svc.refresh()
        else if (playing && title.isNotBlank())
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, MediaService::class.java)) }
    }

    // نجلب الصور من خوادم يوتيوب/جوجل فقط (لا نطلب روابط عشوائية جاءت من الصفحة)
    private fun isTrustedArt(u: String): Boolean {
        val h = runCatching { java.net.URI(u) }.getOrNull()?.takeIf { it.scheme == "https" }?.host ?: return false
        return h.endsWith("ytimg.com") || h.endsWith("ggpht.com") || h.endsWith("googleusercontent.com")
    }

    fun stop() { playing = false; owner = null; MediaService.instance?.shutdown() }
    fun pageChanged(tab: BrowserTab, url: String) { if (owner === tab && !isYtVideo(url)) stop() }
    fun tabClosed(tab: BrowserTab) { if (owner === tab) stop() }
}

/** جسر سكربت yt.js: حالة التشغيل والعنوان والصورة. */
object YtBridge {
    private val origins = setOf("https://m.youtube.com", "https://www.youtube.com", "https://music.youtube.com", "https://youtube.com")
    private val docStart by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }
    private val listenerOk by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) }

    private fun script(c: Context): String? =
        runCatching { c.assets.open("yt.js").bufferedReader().use { it.readText() } }.getOrNull()
            ?.replace("__BG__", Prefs.ytBg.toString())

    fun install(wv: WebView, tab: BrowserTab, h: Handlers) {
        if (!listenerOk) return
        val js = script(wv.context) ?: return
        runCatching {
            WebViewCompat.addWebMessageListener(wv, "NovaYt", origins, WebViewCompat.WebMessageListener { view, message, _, isMain, _ ->
                if (!isMain) return@WebMessageListener
                val data = message.data ?: return@WebMessageListener
                if (data.length > 6000) return@WebMessageListener
                val o = runCatching { JSONObject(data) }.getOrNull() ?: return@WebMessageListener
                val type = o.optString("t")
                if (type == "log") { YtLog.add(o.optString("m")); return@WebMessageListener }
                if (type != "state") return@WebMessageListener
                tab.ytPlaying = o.optBoolean("playing")
                if (!isYtVideo(view.url ?: "")) return@WebMessageListener   // لا إشعار لمعاينات الصفحة الرئيسية
                YtMedia.update(view.context.applicationContext, tab, view, o)
                h.onYtState(tab)
            })
            if (docStart) WebViewCompat.addDocumentStartJavaScript(wv, js, origins)
        }
    }

    fun onPageDone(wv: WebView) {
        if (!listenerOk || docStart) return
        val u = wv.url ?: return
        val host = hostOf(u)
        if (host == "youtube.com" || host == "m.youtube.com" || host == "music.youtube.com") script(wv.context)?.let { wv.evaluateJavascript(it, null) }
    }
}

class MediaService : Service() {
    companion object { @Volatile var instance: MediaService? = null }

    private lateinit var session: MediaSession
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        instance = this
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("media", L("تشغيل الوسائط"), NotificationManager.IMPORTANCE_LOW))
        session = MediaSession(this, "NovaBrowser").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = YtMedia.control("play")
                override fun onPause() = YtMedia.control("pause")
                override fun onSeekTo(pos: Long) = YtMedia.control("seek:$pos")
                override fun onFastForward() = YtMedia.control("fwd")
                override fun onRewind() = YtMedia.control("back")
            })
            isActive = true
        }
        applySession()
        startForeground(2, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val a = intent?.action ?: return START_NOT_STICKY
        if (a == "stop") shutdown() else YtMedia.control(a)
        return START_NOT_STICKY
    }

    fun refresh() = main.post {
        if (instance == null) return@post
        applySession()
        getSystemService(NotificationManager::class.java).notify(2, build())
    }

    fun shutdown() = main.post {
        YtMedia.playing = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun applySession() {
        val st = if (YtMedia.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND
                )
                .setState(st, YtMedia.pos, if (YtMedia.playing) 1f else 0f).build()
        )
        val md = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, YtMedia.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, YtMedia.artist)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, YtMedia.dur)
        YtMedia.art?.let { md.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
        session.setMetadata(md.build())
    }

    private fun pi(action: String, code: Int): PendingIntent =
        PendingIntent.getService(this, code, Intent(this, MediaService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)

    private fun action(icon: Int, label: String, act: String, code: Int) =
        Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi(act, code)).build()

    private fun build(): Notification {
        val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val b = Notification.Builder(this, "media")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(YtMedia.title.ifBlank { "YouTube" })
            .setContentText(YtMedia.artist)
            .setContentIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setDeleteIntent(pi("stop", 9))
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(YtMedia.playing)
            .addAction(action(android.R.drawable.ic_media_rew, L("رجوع 10 ثوانٍ"), "back", 1))
            .addAction(
                if (YtMedia.playing) action(android.R.drawable.ic_media_pause, L("إيقاف مؤقت"), "pause", 2)
                else action(android.R.drawable.ic_media_play, L("تشغيل"), "play", 3)
            )
            .addAction(action(android.R.drawable.ic_media_ff, L("تقديم 10 ثوانٍ"), "fwd", 4))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
        YtMedia.art?.let { b.setLargeIcon(it) }
        return b.build()
    }

    override fun onDestroy() {
        instance = null
        runCatching { session.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
