package uz.ekranoquvchi.mvp.tts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import uz.ekranoquvchi.mvp.diag.Diag
import uz.ekranoquvchi.mvp.diag.ErrorCode
import uz.ekranoquvchi.mvp.diag.swallowed
import uz.ekranoquvchi.mvp.util.Prefs
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal const val SYNTH_PENDING = 0
internal const val SYNTH_DONE = 1
internal const val SYNTH_FAILED = 2
internal const val SYNTH_STOPPED = 3

/**
 * O'zbegim tizim dvigatelining "orqa qismi": haqiqiy ovozni telefondagi BOSHQA nutq dvigateli (RHVoice, Google, Samsung va h.k.)
 * beradi. O'zbegim matnni o'z qoidalari bilan tayyorlaydi, bu yerda esa tayyor matn shu dvigatelga synthesizeToFile bilan
 * beriladi va chiqqan WAV fayl PCM bo'lib tizimga qaytariladi (OzbegimTtsService).
 *
 * Bir nechta bo'lak oldindan navbatga qo'yilishi mumkin: dvigatel ularni ketma-ket tayyorlaydi, shu orada oldingisi ijro etiladi.
 * Dvigatel va til sozlamalari hamma so'rovda bir xil bo'lgani uchun faqat o'zgarganda qayta qo'llanadi.
 * Hamma holat ichida lock bilan himoyalangan; TextToSpeech asosiy oqimda yaratiladi (qaytarma chaqiruvi shu yerga keladi).
 */
internal class TtsBackend(private val context: Context) {

    class Pending(val id: String, val file: File) {
        val latch = CountDownLatch(1)

        @Volatile
        var status = SYNTH_PENDING

        @Volatile
        var errorCode = 0
    }

    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val pending = ConcurrentHashMap<String, Pending>()
    private val langCache = ConcurrentHashMap<String, Int>()

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false

    @Volatile
    private var closed = false

    @Volatile
    private var readyLatch = CountDownLatch(1)

    /** Hozir ishlatilayotgan haqiqiy dvigatel (paket nomi); topilmasa null. */
    @Volatile
    var enginePackage: String? = null
        private set

    private var generation = 0
    private var counter = 0
    private var initRetries = 0
    private var appliedKey: String? = null

    private val workDir: File by lazy {
        val d = File(context.createDeviceProtectedStorageContext().cacheDir, "tts-engine")
        d.mkdirs()
        d
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
            finish(utteranceId, SYNTH_DONE, 0)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            finish(utteranceId, SYNTH_FAILED, -1)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            finish(utteranceId, SYNTH_FAILED, errorCode)
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            finish(utteranceId, SYNTH_STOPPED, 0)
        }
    }

    private fun finish(id: String?, status: Int, code: Int) {
        if (id == null) return
        val p = pending[id] ?: return
        if (p.status == SYNTH_PENDING) {
            p.errorCode = code
            p.status = status
        }
        p.latch.countDown()
    }

    // ------------------------------------------------------------------ ishga tushirish

    fun start() {
        cleanOld()
        if (Looper.myLooper() == Looper.getMainLooper()) create(null) else main.post { create(null) }
    }

    /** Ovoz beruvchi dvigatel tanlovi o'zgarganda dvigatelni qayta ishga tushiradi. */
    fun restart() {
        main.post {
            initRetries = 0
            create(null)
        }
    }

    private fun create(forced: String?) {
        if (closed) return
        val pkg = forced ?: EngineChoice.backendPackage(context)
        val gen = synchronized(lock) {
            generation += 1
            ready = false
            readyLatch = CountDownLatch(1)
            appliedKey = null
            langCache.clear()
            generation
        }
        val old = tts
        tts = null
        try {
            old?.stop()
            old?.shutdown()
        } catch (ex: Throwable) {
            swallowed("TtsBackend.create", ex)
        }
        enginePackage = pkg
        if (pkg == null) {
            Diag.log.log(ErrorCode.TTS_INIT_FAILED, mapOf("why" to "no_backend"))
            return
        }
        val listener = TextToSpeech.OnInitListener { status -> main.post { onInit(gen, status) } }
        try {
            tts = TextToSpeech(context, listener, pkg)
        } catch (t: Throwable) {
            Diag.log.log(ErrorCode.TTS_INIT_FAILED, mapOf("exc" to t.javaClass.simpleName))
        }
    }

    private fun onInit(gen: Int, status: Int) {
        if (closed) return
        val t = tts
        val stale = synchronized(lock) { gen != generation }
        if (stale || t == null) return
        if (status != TextToSpeech.SUCCESS) {
            Diag.log.log(ErrorCode.TTS_INIT_FAILED, mapOf("status" to status, "engine" to (enginePackage ?: "?")))
            if (initRetries < 1) {
                initRetries += 1
                val alt = EngineChoice.alternative(context, enginePackage)
                if (alt != null) create(alt)
            }
            return
        }
        try {
            t.setOnUtteranceProgressListener(progress)
        } catch (ex: Throwable) {
            swallowed("TtsBackend.onInit", ex)
        }
        val latch = synchronized(lock) {
            ready = true
            readyLatch
        }
        latch.countDown()
    }

    /** Dvigatel tayyor bo'lguncha kutadi (so'rov oqimida chaqiriladi, asosiy oqimda emas). */
    fun awaitReady(timeoutMs: Long): Boolean {
        if (closed) return false
        if (ready) return true
        val latch = readyLatch
        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            return false
        }
        return ready && !closed
    }

    // ------------------------------------------------------------------ tillar

    /** Orqa dvigatel tilni qo'llab-quvvatlashi: TextToSpeech.LANG_* kodlari. Natija keshlanadi. */
    fun languageStatus(locale: Locale): Int {
        val key = locale.toLanguageTag()
        val cached = langCache[key]
        if (cached != null) return cached
        val t = tts ?: return TextToSpeech.LANG_NOT_SUPPORTED
        val r = try {
            t.isLanguageAvailable(locale)
        } catch (_: Throwable) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
        langCache[key] = r
        return r
    }

    fun availableLocales(): Set<Locale> = try {
        tts?.availableLanguages ?: emptySet()
    } catch (_: Throwable) {
        emptySet()
    }

    // ------------------------------------------------------------------ sintez

    /**
     * Matn bo'lagini orqa dvigatelga navbatga qo'yadi. Natija WAV fayl bo'lib keladi (await, readPcm).
     * Til yoki ovoz qo'llanmasa yoki dvigatel rad etsa null.
     */
    fun submit(text: String, locale: Locale, rate: Int, pitch: Int): Pending? {
        val t = tts ?: return null
        return synchronized(lock) { submitLocked(t, text, locale, rate, pitch) }
    }

    private fun submitLocked(t: TextToSpeech, text: String, locale: Locale, rate: Int, pitch: Int): Pending? {
        if (!ready || closed) return null
        counter += 1
        val id = "z" + counter
        val file = File(workDir, id + "-" + SystemClock.uptimeMillis() + ".wav")
        val p = Pending(id, file)
        pending[id] = p
        try {
            if (!applyLanguage(t, locale)) {
                pending.remove(id)
                return null
            }
            t.setSpeechRate(rate / 100f)
            t.setPitch(pitch / 100f)
            val r = t.synthesizeToFile(text, Bundle(), file, id)
            if (r != TextToSpeech.SUCCESS) {
                pending.remove(id)
                return null
            }
        } catch (_: Throwable) {
            pending.remove(id)
            return null
        }
        return p
    }

    private fun applyLanguage(t: TextToSpeech, locale: Locale): Boolean {
        val voice = if (locale.language == "uz") Prefs.voice(context) else ""
        val key = locale.toLanguageTag() + "|" + voice
        if (key == appliedKey) return true
        val r = t.setLanguage(locale)
        if (r < TextToSpeech.LANG_AVAILABLE) return false
        if (voice.isNotEmpty()) applyVoice(t, voice)
        appliedKey = key
        return true
    }

    /** Ekran o'quvchi uchun tanlangan o'zbekcha ovoz (masalan, Islom) shu dvigatelda bo'lsa, tizim so'rovlarida ham shu ishlatiladi. */
    private fun applyVoice(t: TextToSpeech, saved: String) {
        try {
            val voices = t.voices
            if (voices != null) {
                for (v in voices) {
                    if (v.name == saved && v.locale.language == "uz") {
                        t.setVoice(v)
                        break
                    }
                }
            }
        } catch (ex: Throwable) {
            swallowed("TtsBackend.applyVoice", ex)
        }
    }

    /** Bo'lak tayyor bo'lguncha kutadi. SYNTH_PENDING qaytsa, vaqt tugagan. */
    fun await(p: Pending, timeoutMs: Long): Int {
        val ok = try {
            p.latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            false
        }
        return if (ok) p.status else SYNTH_PENDING
    }

    /** Tayyor WAV'ni o'qiydi va faylni o'chiradi. */
    fun readPcm(p: Pending): Pcm? {
        val pcm = WavReader.read(p.file)
        discard(p)
        return pcm
    }

    fun discard(p: Pending) {
        pending.remove(p.id)
        try {
            p.file.delete()
        } catch (ex: Throwable) {
            swallowed("TtsBackend.discard", ex)
        }
    }

    /** Hamma kutilayotgan bo'laklarni to'xtatadi (mijoz to'xtatganda yoki vaqt tugaganda). */
    fun stopAll() {
        try {
            tts?.stop()
        } catch (ex: Throwable) {
            swallowed("TtsBackend.stopAll", ex)
        }
        for (p in pending.values) {
            if (p.status == SYNTH_PENDING) p.status = SYNTH_STOPPED
            p.latch.countDown()
        }
    }

    fun shutdown() {
        closed = true
        main.removeCallbacksAndMessages(null)
        val t = tts
        tts = null
        ready = false
        try {
            t?.stop()
            t?.shutdown()
        } catch (ex: Throwable) {
            swallowed("TtsBackend.shutdown", ex)
        }
        for (p in pending.values) {
            p.status = SYNTH_STOPPED
            p.latch.countDown()
            try {
                p.file.delete()
            } catch (ex: Throwable) {
                swallowed("TtsBackend.shutdown", ex)
            }
        }
        pending.clear()
        readyLatch.countDown()
    }

    /** Oldingi ishdan qolgan eskirgan vaqtinchalik fayllarni o'chiradi. */
    private fun cleanOld() {
        try {
            val now = System.currentTimeMillis()
            val files = workDir.listFiles() ?: return
            for (f in files) if (now - f.lastModified() > 600_000L) f.delete()
        } catch (ex: Throwable) {
            swallowed("TtsBackend.cleanOld", ex)
        }
    }
}
