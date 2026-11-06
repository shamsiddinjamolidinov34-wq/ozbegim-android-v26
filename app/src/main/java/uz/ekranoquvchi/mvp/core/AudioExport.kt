package uz.ekranoquvchi.mvp.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import uz.ekranoquvchi.mvp.diag.swallowed
import uz.ekranoquvchi.mvp.net.AzureSpeaker
import uz.ekranoquvchi.mvp.net.Http
import uz.ekranoquvchi.mvp.util.Prefs
import uz.ekranoquvchi.mvp.util.Secrets
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Oxirgi o'qilgan matnni Microsoft (Azure) ovozida MP3 faylga saqlaydi ("O'zbegim menyusi" > "Oxirgi o'qilganni MP3 qilib saqlash").
 *
 * Sifat: 48 kHz, 192 kbit/s MP3 (Azure 48 kHz uchun 96 va 192 kbit/s beradi; so'ralgan 128 kbit/s dan past emas). Ovoz, tezlik va ton
 * hozirgi sozlamalardan. Uzun matn ~900 belgilik bo'laklarga bo'linib ketma-ket yuklanadi va bitta faylga yoziladi (xotira ko'tarilmaydi).
 * Fayl Android 10 va undan yuqorida "Music/Ozbegim" papkasiga (ruxsat so'ramasdan), eski Androidda ilovaning musiqa papkasiga tushadi.
 */
object AudioExport {
    class Result(
        val ok: Boolean,
        val name: String = "",
        val where: String = "",
        /** "auth" (kalit rad etildi), "net" (tarmoq yoki xizmat xatosi), "empty" (matn bo'sh). */
        val reason: String = "",
        /** Matn uzun bo'lib, faqat shuncha belgi saqlangan bo'lsa (aks holda 0). */
        val truncatedTo: Int = 0,
    )

    private const val MAX_CHARS = 30_000
    private const val MAX_CHUNK = 900
    private const val CONNECT_MS = 5000
    private const val READ_MS = 30000
    private const val MAX_AUDIO_BYTES = 8_000_000

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r ->
        val t = Thread(r, "ozbegim-audio-export")
        t.isDaemon = true
        t
    }

    private class AuthFailed : IOException("auth")

    private class Sink(val out: OutputStream, val uri: Uri?, val file: File?, val name: String, val where: String)

    /** Matnni (eshitiladigan ko'rinishda) MP3 qilib saqlaydi. done asosiy oqimda chaqiriladi. */
    fun save(context: Context, text: String, done: (Result) -> Unit) {
        val app = context.applicationContext
        io.execute {
            val r = try {
                run(app, text)
            } catch (_: Throwable) {
                Result(false, reason = "net")
            }
            main.post { done(r) }
        }
    }

    private fun run(app: Context, text: String): Result {
        val key = Secrets.get(app, Prefs.AZURE_KEY)
        if (key.isNullOrEmpty()) return Result(false, reason = "auth")
        var body = text.replace('\u00A0', ' ').trim()
        var truncated = 0
        if (body.length > MAX_CHARS) {
            body = body.substring(0, MAX_CHARS)
            truncated = MAX_CHARS
        }
        val parts = split(body)
        if (parts.isEmpty()) return Result(false, reason = "empty")

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val sink = try {
            openSink(app, "Ozbegim-$stamp.mp3")
        } catch (_: Throwable) {
            return Result(false, reason = "net")
        }
        try {
            val voice = Prefs.azureVoice(app)
            val rate = Prefs.rate(app)
            val pitch = Prefs.pitch(app)
            val url = "https://" + Prefs.azureRegion(app) + ".tts.speech.microsoft.com/cognitiveservices/v1"
            val headers = mapOf(
                "Ocp-Apim-Subscription-Key" to key,
                "Content-Type" to "application/ssml+xml",
                "X-Microsoft-OutputFormat" to AzureSpeaker.FORMAT_192,
                "User-Agent" to "Ozbegim",
            )
            for (p in parts) {
                val ssml = AzureSpeaker.buildSsml(p, voice, rate, pitch)
                val res = Http.post(url, headers, ssml.toByteArray(Charsets.UTF_8), CONNECT_MS, READ_MS, MAX_AUDIO_BYTES)
                if (res.code == 401 || res.code == 403) throw AuthFailed()
                if (res.code != 200 || res.body.isEmpty()) throw IOException("http ${res.code}")
                sink.out.write(res.body)
            }
            finish(app, sink)
        } catch (t: Throwable) {
            abort(app, sink)
            return Result(false, reason = if (t is AuthFailed) "auth" else "net")
        }
        return Result(true, name = sink.name, where = sink.where, truncatedTo = truncated)
    }

    /** Uzun matnni gap chegarasida (nuqta, so'roq, undov, yangi qator), bo'lmasa bo'sh joyda bo'laklarga bo'ladi. */
    private fun split(text: String): List<String> {
        val out = ArrayList<String>()
        var rest = text
        while (rest.length > MAX_CHUNK) {
            val window = rest.substring(0, MAX_CHUNK)
            var cut = window.lastIndexOfAny(charArrayOf('.', '!', '?', '\n', ';', ':'))
            if (cut < MAX_CHUNK / 3) cut = window.lastIndexOf(' ')
            if (cut < MAX_CHUNK / 3) cut = MAX_CHUNK - 1
            val piece = rest.substring(0, cut + 1).trim()
            if (piece.isNotEmpty()) out.add(piece)
            rest = rest.substring(cut + 1).trim()
        }
        if (rest.isNotEmpty()) out.add(rest)
        return out
    }

    private fun openSink(app: Context, name: String): Sink {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val folder = Environment.DIRECTORY_MUSIC + "/Ozbegim"
            val values = ContentValues()
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, folder)
            values.put(MediaStore.Audio.Media.IS_PENDING, 1)
            val uri = app.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("insert")
            val out = app.contentResolver.openOutputStream(uri) ?: throw IOException("open")
            return Sink(out, uri, null, name, folder)
        }
        val dir = app.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: app.filesDir
        dir.mkdirs()
        val f = File(dir, name)
        return Sink(FileOutputStream(f), null, f, name, dir.absolutePath)
    }

    private fun finish(app: Context, sink: Sink) {
        sink.out.flush()
        sink.out.close()
        val uri = sink.uri
        if (uri != null) {
            val v = ContentValues()
            v.put(MediaStore.Audio.Media.IS_PENDING, 0)
            app.contentResolver.update(uri, v, null, null)
        }
    }

    private fun abort(app: Context, sink: Sink) {
        try {
            sink.out.close()
        } catch (ex: Throwable) {
            swallowed("AudioExport.abort", ex)
        }
        try {
            val uri = sink.uri
            if (uri != null) app.contentResolver.delete(uri, null, null) else sink.file?.delete()
        } catch (ex: Throwable) {
            swallowed("AudioExport.abort", ex)
        }
    }
}
