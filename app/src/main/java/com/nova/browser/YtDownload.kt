package com.nova.browser

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import org.schabi.newpipe.extractor.MediaFormat as NpFormat

const val YT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

/** هل الرابط صفحة فيديو يوتيوب (مشاهدة/شورتس/بث)؟ */
fun isYtVideo(u: String): Boolean {
    val p = runCatching { Uri.parse(u) }.getOrNull() ?: return false
    val h = (p.host ?: return false).removePrefix("www.")
    val path = p.path ?: ""
    return (h == "youtu.be" && path.length > 1) ||
        ((h == "youtube.com" || h == "m.youtube.com" || h == "music.youtube.com") &&
            (path == "/watch" || path.startsWith("/shorts/") || path.startsWith("/live/")))
}

/** منفّذ طلبات الشبكة الذي تحتاجه مكتبة NewPipeExtractor. */
class NpDownloader : org.schabi.newpipe.extractor.downloader.Downloader() {
    override fun execute(request: Request): Response {
        val c = URL(request.url()).openConnection() as HttpURLConnection
        try {
            c.requestMethod = request.httpMethod()
            c.connectTimeout = 20000; c.readTimeout = 25000
            c.setRequestProperty("User-Agent", YT_UA)
            for ((k, vs) in request.headers()) {
                if (k.equals("Accept-Encoding", true)) continue   // نترك أندرويد يفكّ الضغط تلقائياً
                vs.forEachIndexed { i, v -> if (i == 0) c.setRequestProperty(k, v) else c.addRequestProperty(k, v) }
            }
            request.dataToSend()?.let { body -> c.doOutput = true; c.outputStream.use { it.write(body) } }
            val code = c.responseCode
            if (code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            val text = (if (code >= 400) c.errorStream else c.inputStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val hdrs = HashMap<String, List<String>>()
            for ((k, v) in c.headerFields) if (k != null) hdrs[k] = v
            return Response(code, c.responseMessage ?: "", hdrs, text, c.url.toString())
        } finally { c.disconnect() }
    }
}

/** خيار تنزيل: audioUrl غير فارغ = فيديو بلا صوت يُدمج مع هذا الصوت بعد التنزيل. */
class YtOpt(val label: String, val sub: String, val url: String, val ext: String, val mime: String, val audioUrl: String? = null)
class YtInfo(val title: String, val author: String, val videos: List<YtOpt>, val audios: List<YtOpt>)

object YtFetch {
    private var ready = false

    private fun ensure() { if (!ready) { NewPipe.init(NpDownloader()); ready = true } }

    fun info(url: String): YtInfo {
        ensure()
        val si = StreamInfo.getInfo(ServiceList.YouTube, url)
        val http = DeliveryMethod.PROGRESSIVE_HTTP

        val audiosAll = si.audioStreams.filter { it.deliveryMethod == http && it.isUrl }
        val original = { a: org.schabi.newpipe.extractor.stream.AudioStream -> a.audioTrackType == null || a.audioTrackType == AudioTrackType.ORIGINAL }
        val aac = audiosAll.filter { it.format == NpFormat.M4A && original(it) }.maxByOrNull { it.averageBitrate }
        val opus = audiosAll.filter { it.format == NpFormat.WEBMA && original(it) }.maxByOrNull { it.averageBitrate }

        val videos = ArrayList<YtOpt>()
        if (aac != null) {
            si.videoOnlyStreams
                .filter { it.deliveryMethod == http && it.isUrl && it.format == NpFormat.MPEG_4 && (it.codec ?: "").startsWith("avc1") }
                .groupBy { it.height }
                .mapNotNull { (_, l) -> l.maxByOrNull { it.bitrate } }
                .sortedByDescending { it.height }
                .forEach { videos.add(YtOpt(it.resolution + " MP4", L("فيديو + صوت (دمج تلقائي)"), it.content, "mp4", "video/mp4", aac.content)) }
        }
        si.videoStreams
            .filter { it.deliveryMethod == http && it.isUrl && it.format == NpFormat.MPEG_4 }
            .sortedByDescending { it.height }
            .forEach { videos.add(YtOpt(it.resolution + " MP4", L("فيديو + صوت جاهز"), it.content, "mp4", "video/mp4")) }

        val audios = ArrayList<YtOpt>()
        if (aac != null) audios.add(YtOpt("M4A • ${aac.averageBitrate} kbps", "AAC", aac.content, "m4a", "audio/mp4"))
        if (opus != null) audios.add(YtOpt("Opus • ${opus.averageBitrate} kbps", "WebM", opus.content, "webm", "audio/webm"))
        return YtInfo(si.name, si.uploaderName ?: "", videos, audios)
    }
}

object YtDownload {
    private fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(80).ifBlank { "video" }

    fun start(ctx: Context, info: YtInfo, o: YtOpt) {
        val base = safeName(info.title)
        val ref = "https://www.youtube.com/"
        if (o.audioUrl == null) {
            Downloader.startNamed(ctx, o.url, YT_UA, ref, "$base.${o.ext}", o.mime)
            return
        }
        val holder = arrayOfNulls<DlTask>(2)
        val left = AtomicInteger(2)
        val finish: (DlTask) -> Unit = {
            if (left.decrementAndGet() == 0) Thread { runMux(ctx.applicationContext, base, holder[0]!!, holder[1]!!) }.start()
        }
        holder[0] = Downloader.startNamed(ctx, o.url, YT_UA, ref, "$base [video].mp4", "video/mp4", finish)
        holder[1] = Downloader.startNamed(ctx, o.audioUrl, YT_UA, ref, "$base [audio].m4a", "audio/mp4", finish)
    }

    private fun runMux(app: Context, base: String, v: DlTask, a: DlTask) {
        val ui = Handler(Looper.getMainLooper())
        ui.post { toast(app, L("جارٍ دمج الصوت والفيديو…")) }
        val r = app.contentResolver
        var out: Uri? = null
        try {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "$base.mp4")
                put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Nova")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val dst = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: throw IOException("insert failed")
            out = dst
            Mux.mux(app, v.uri ?: throw IOException("no video"), a.uri ?: throw IOException("no audio"), dst)
            r.update(dst, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            val size = r.openFileDescriptor(dst, "r")?.use { it.statSize } ?: -1L
            Downloader.addFinished("$base.mp4", dst, "video/mp4", size)
            Downloader.removeOnMain(v, true); Downloader.removeOnMain(a, true)
        } catch (e: Throwable) {
            out?.let { runCatching { r.delete(it, null, null) } }
            ui.post { toast(app, L("فشل الدمج — الملفان محفوظان منفصلَين")) }
        }
    }
}

/** دمج مسار فيديو (H.264) مع مسار صوت (AAC) في ملف MP4 بلا إعادة ترميز. */
object Mux {
    private fun track(ex: MediaExtractor, prefix: String): Int =
        (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith(prefix) == true }

    fun mux(ctx: Context, video: Uri, audio: Uri, out: Uri) {
        val r = ctx.contentResolver
        r.openFileDescriptor(video, "r")!!.use { vfd ->
            r.openFileDescriptor(audio, "r")!!.use { afd ->
                r.openFileDescriptor(out, "rw")!!.use { ofd ->
                    val ve = MediaExtractor(); val ae = MediaExtractor()
                    var mx: MediaMuxer? = null
                    var started = false
                    try {
                        ve.setDataSource(vfd.fileDescriptor); ae.setDataSource(afd.fileDescriptor)
                        val vt = track(ve, "video/"); val at = track(ae, "audio/")
                        ve.selectTrack(vt); ae.selectTrack(at)
                        val m = MediaMuxer(ofd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                        mx = m
                        val vi = m.addTrack(ve.getTrackFormat(vt)); val ai = m.addTrack(ae.getTrackFormat(at))
                        m.start(); started = true
                        val buf = ByteBuffer.allocate(4 * 1024 * 1024)
                        val bi = MediaCodec.BufferInfo()
                        while (true) {
                            val vtm = ve.sampleTime; val atm = ae.sampleTime
                            if (vtm < 0 && atm < 0) break
                            val useV = atm < 0 || (vtm in 0L..atm)    // تداخل المسارين حسب الزمن
                            val ex = if (useV) ve else ae
                            buf.clear()
                            val n = ex.readSampleData(buf, 0)
                            if (n < 0) break
                            val key = (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                            bi.set(0, n, ex.sampleTime, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                            m.writeSampleData(if (useV) vi else ai, buf, bi)
                            ex.advance()
                        }
                    } finally {
                        if (started) runCatching { mx?.stop() }
                        runCatching { mx?.release() }
                        ve.release(); ae.release()
                    }
                }
            }
        }
    }
}

// ───────────── الواجهة ─────────────

@Composable
fun YtBar(onDownload: () -> Unit, onPip: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(onClick = onPip, shape = CircleShape, color = cs.primaryContainer, shadowElevation = 4.dp) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(L("منبثق"), style = MaterialTheme.typography.labelLarge)
            }
        }
        Surface(onClick = onDownload, shape = CircleShape, color = cs.primaryContainer, shadowElevation = 4.dp) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(L("تنزيل"), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtDownloadSheet(url: String, onDismiss: () -> Unit, onStarted: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<YtInfo?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            try { info = YtFetch.info(url) } catch (e: Throwable) { err = e.message ?: e.javaClass.simpleName }
        }
    }
    fun pick(i: YtInfo, o: YtOpt) { YtDownload.start(ctx, i, o); toast(ctx, L("بدأ التنزيل — القائمة ⋮ ثم التنزيلات")); onStarted(); onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = cs.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(L("تنزيل من يوتيوب"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val i = info
            when {
                err != null -> {
                    Spacer(Modifier.height(12.dp))
                    Text(L("تعذّر جلب الفيديو"), color = cs.error, fontWeight = FontWeight.Bold)
                    Text(err ?: "", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                i == null -> {
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    Spacer(Modifier.height(24.dp))
                }
                else -> {
                    Text(i.title, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 2)
                    if (i.videos.isEmpty() && i.audios.isEmpty()) {
                        Spacer(Modifier.height(12.dp)); Text(L("لا توجد صيغ قابلة للتنزيل لهذا الفيديو (قد يكون بثاً مباشراً)"))
                    }
                    if (i.videos.isNotEmpty()) {
                        Text(L("فيديو"), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            i.videos.forEachIndexed { n, o -> ListRow(groupShape(n, i.videos.size), o.label, o.sub, { pick(i, o) }) { IconCircle { Icon(Icons.Default.PlayArrow, null) } } }
                        }
                    }
                    if (i.audios.isNotEmpty()) {
                        Text(L("صوت فقط"), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            i.audios.forEachIndexed { n, o -> ListRow(groupShape(n, i.audios.size), o.label, o.sub, { pick(i, o) }) { IconCircle { Icon(Icons.Default.KeyboardArrowDown, null) } } }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(L("للاستخدام الشخصي فقط — احترم حقوق صاحب المحتوى وشروط الخدمة."), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}
