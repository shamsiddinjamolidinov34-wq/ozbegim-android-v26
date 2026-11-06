package uz.ekranoquvchi.mvp.tts

import android.content.SharedPreferences
import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import uz.ekranoquvchi.mvp.content.TextChunker
import uz.ekranoquvchi.mvp.diag.Diag
import uz.ekranoquvchi.mvp.diag.ErrorCode
import uz.ekranoquvchi.mvp.diag.swallowed
import uz.ekranoquvchi.mvp.util.Prefs
import java.util.Locale

/**
 * O'zbegimning tizim nutq dvigateli. Android sozlamalarida "Matnni nutqqa aylantirish" > "Afzal ko'rilgan mexanizm"
 * ro'yxatida chiqadi. Shu dvigatel tanlansa, TalkBack, kitob o'quvchilar va boshqa hamma ilova matnni O'zbegimga beradi.
 *
 * Ishlash tartibi (ovozning o'zini O'zbegim sintez qilmaydi):
 *  1) tizim matn va tilni beradi (onSynthesizeText);
 *  2) matn O'zbegimning o'z qoidalari bilan tayyorlanadi (TtsText -> ContentPipeline: uch yozuv, raqamlar, Rim raqamlari, telefon);
 *  3) gap bo'laklariga bo'linib, telefondagi BOSHQA dvigatelga (RHVoice, Google, Samsung...) beriladi (TtsBackend);
 *  4) tayyor audio PCM bo'lib SynthesisCallback orqali tizimga qaytariladi; ijroni, ovoz balandligini va audio oqimini tizim o'zi boshqaradi.
 *
 * Tarmoq ishlatilmaydi: matn faqat qurilmadagi dvigatelga boradi. O'zbegim o'zini o'zi ovoz beruvchi qilib tanlay olmaydi
 * (EngineChoice), shuning uchun cheksiz aylanish bo'lmaydi.
 */
class OzbegimTtsService : TextToSpeechService() {

    private var backend: TtsBackend? = null

    @Volatile
    private var cancelled = false

    @Volatile
    private var curLang = "uzb"

    @Volatile
    private var curCountry = "UZB"

    @Volatile
    private var curVariant = ""

    /** Ovoz beruvchi dvigatel tanlovi o'zgarsa, orqa dvigatel qayta ishga tushadi. */
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.TTS_BACKEND || key == Prefs.ENGINE || key == Prefs.ENGINE_PACKAGE) backend?.restart()
    }

    override fun onCreate() {
        super.onCreate()
        try {
            Diag.init(applicationContext)
        } catch (ex: Throwable) {
            swallowed("OzbegimTtsService.onCreate", ex)
        }
        val be = TtsBackend(applicationContext)
        backend = be
        be.start()
        try {
            Prefs.get(applicationContext).registerOnSharedPreferenceChangeListener(prefListener)
        } catch (ex: Throwable) {
            swallowed("OzbegimTtsService.onCreate", ex)
        }
    }

    override fun onDestroy() {
        try {
            Prefs.get(applicationContext).unregisterOnSharedPreferenceChangeListener(prefListener)
        } catch (ex: Throwable) {
            swallowed("OzbegimTtsService.onDestroy", ex)
        }
        backend?.shutdown()
        backend = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ tillar

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val loc = Iso.toLocale(lang, country, variant) ?: return TextToSpeech.LANG_NOT_SUPPORTED
        return statusFor(loc)
    }

    override fun onGetLanguage(): Array<String> = arrayOf(curLang, curCountry, curVariant)

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val status = onIsLanguageAvailable(lang, country, variant)
        if (status >= TextToSpeech.LANG_AVAILABLE) {
            curLang = lang ?: ""
            curCountry = country ?: ""
            curVariant = variant ?: ""
        }
        return status
    }

    private fun statusFor(loc: Locale): Int {
        val be = backend ?: return TextToSpeech.LANG_NOT_SUPPORTED
        if (!be.awaitReady(READY_WAIT_MS)) return TextToSpeech.LANG_NOT_SUPPORTED
        return be.languageStatus(loc)
    }

    // ------------------------------------------------------------------ ovozlar (nom = til belgisi, masalan "uz-UZ")

    override fun onGetVoices(): List<Voice> {
        val out = ArrayList<Voice>()
        val be = backend ?: return out
        if (!be.awaitReady(READY_WAIT_MS)) return out
        val seen = HashSet<String>()
        for (l in be.availableLocales()) {
            if (be.languageStatus(l) < TextToSpeech.LANG_AVAILABLE) continue
            val name = l.toLanguageTag()
            if (!seen.add(name)) continue
            val features = HashSet<String>()
            features.add(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS)
            out.add(Voice(name, l, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false, features))
        }
        return out
    }

    override fun onIsValidVoiceName(voiceName: String?): Int {
        val loc = localeOfVoice(voiceName) ?: return TextToSpeech.ERROR
        return if (statusFor(loc) >= TextToSpeech.LANG_AVAILABLE) TextToSpeech.SUCCESS else TextToSpeech.ERROR
    }

    override fun onLoadVoice(voiceName: String?): Int {
        val loc = localeOfVoice(voiceName) ?: return TextToSpeech.ERROR
        if (statusFor(loc) < TextToSpeech.LANG_AVAILABLE) return TextToSpeech.ERROR
        curLang = Iso.iso3Language(loc)
        curCountry = Iso.iso3Country(loc)
        curVariant = loc.variant
        return TextToSpeech.SUCCESS
    }

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
        val loc = Iso.toLocale(lang, country, variant) ?: return null
        val proper = when (statusFor(loc)) {
            TextToSpeech.LANG_AVAILABLE -> Locale(loc.language)
            TextToSpeech.LANG_COUNTRY_AVAILABLE -> Locale(loc.language, loc.country)
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> loc
            else -> return null
        }
        return proper.toLanguageTag()
    }

    private fun localeOfVoice(name: String?): Locale? {
        if (name.isNullOrEmpty()) return null
        val l = Locale.forLanguageTag(name)
        return if (l.language.isNullOrEmpty()) null else l
    }

    private fun requestLocale(r: SynthesisRequest): Locale {
        val fromRequest = Iso.toLocale(r.language, r.country, r.variant)
        if (fromRequest != null) return fromRequest
        return Iso.toLocale(curLang, curCountry, curVariant) ?: Locale("uz", "UZ")
    }

    // ------------------------------------------------------------------ sintez

    override fun onStop() {
        cancelled = true
        backend?.stopAll()
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null) return
        cancelled = false
        val be = backend
        if (be == null || !be.awaitReady(READY_WAIT_MS)) {
            Diag.log.log(ErrorCode.TTS_SERVICE_ERROR, mapOf("why" to "backend_not_ready"))
            callback.error(TextToSpeech.ERROR_SERVICE)
            return
        }
        val locale = requestLocale(request)
        val raw = request.charSequenceText?.toString() ?: ""
        val text = if (Prefs.ttsRules(applicationContext)) {
            TtsText.prepare(applicationContext, raw, locale, be.enginePackage)
        } else {
            raw
        }
        val chunks = ArrayList<String>()
        for (c in TextChunker.split(text.trim(), SOFT_CHUNK, HARD_CHUNK)) {
            if (c.isNotBlank()) chunks.add(c.trim())
        }
        var rate = request.speechRate
        if (rate < 20) rate = 20
        if (rate > 500) rate = 500
        var pitch = request.pitch
        if (pitch < 20) pitch = 20
        if (pitch > 300) pitch = 300

        val queue = ArrayDeque<TtsBackend.Pending>()
        var next = 0
        var started = false
        var fmtRate = 0
        var fmtChannels = 0
        var failed = 0
        var timedOut = false
        while (!cancelled) {
            // Oldindan bir necha bo'lak navbatga qo'yiladi: dvigatel keyingisini tayyorlayotganda oldingisi ijro etiladi
            while (queue.size < WINDOW && next < chunks.size) {
                val p = be.submit(chunks[next], locale, rate, pitch)
                next += 1
                if (p == null) failed += 1 else queue.addLast(p)
            }
            if (queue.isEmpty()) break
            val p = queue.removeFirst()
            val st = be.await(p, CHUNK_TIMEOUT_MS)
            if (cancelled) {
                be.discard(p)
                break
            }
            if (st == SYNTH_PENDING) {
                be.discard(p)
                timedOut = true
                be.stopAll()
                break
            }
            if (st != SYNTH_DONE) {
                failed += 1
                be.discard(p)
                continue
            }
            val pcm = be.readPcm(p)
            if (pcm == null) {
                failed += 1
                continue
            }
            if (pcm.data.isEmpty()) continue
            var out: Pcm = pcm
            if (!started) {
                fmtRate = pcm.sampleRate
                fmtChannels = pcm.channels
                if (callback.start(fmtRate, AudioFormat.ENCODING_PCM_16BIT, fmtChannels) != TextToSpeech.SUCCESS) {
                    cancelled = true
                    break
                }
                started = true
            } else if (pcm.sampleRate != fmtRate || pcm.channels != fmtChannels) {
                out = PcmConvert.to(pcm, fmtRate, fmtChannels)
            }
            if (!stream(callback, out)) cancelled = true
        }
        for (p in queue) be.discard(p)

        if (cancelled) return
        if (timedOut) {
            Diag.log.log(ErrorCode.TTS_SERVICE_ERROR, mapOf("why" to "timeout", "engine" to (be.enginePackage ?: "?")))
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
            return
        }
        if (started) {
            callback.done()
            return
        }
        if (failed > 0) {
            Diag.log.log(ErrorCode.TTS_SERVICE_ERROR, mapOf("why" to "chunks_failed", "n" to failed, "engine" to (be.enginePackage ?: "?")))
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
            return
        }
        // Aytiladigan narsa yo'q (bo'sh matn yoki faqat belgilar): jimgina tugatiladi
        callback.start(SILENT_RATE, AudioFormat.ENCODING_PCM_16BIT, 1)
        callback.done()
    }

    /** PCM'ni tizim ruxsat bergan eng katta bo'laklarda (kadr chegarasida) uzatadi. false: mijoz to'xtatgan yoki xato. */
    private fun stream(callback: SynthesisCallback, pcm: Pcm): Boolean {
        val frame = pcm.frameBytes
        var max = callback.getMaxBufferSize()
        max -= max % frame
        if (max < frame) max = frame
        val data = pcm.data
        var off = 0
        while (off < data.size) {
            if (cancelled) return false
            val n = minOf(max, data.size - off)
            if (callback.audioAvailable(data, off, n) != TextToSpeech.SUCCESS) return false
            off += n
        }
        return true
    }

    companion object {
        private const val READY_WAIT_MS = 3000L
        private const val CHUNK_TIMEOUT_MS = 30_000L
        private const val WINDOW = 3
        private const val SOFT_CHUNK = 120
        private const val HARD_CHUNK = 200
        private const val SILENT_RATE = 22050
    }
}
