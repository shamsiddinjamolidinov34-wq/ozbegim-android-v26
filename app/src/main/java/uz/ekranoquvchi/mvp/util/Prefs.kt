package uz.ekranoquvchi.mvp.util

import android.content.Context
import android.content.SharedPreferences
import uz.ekranoquvchi.mvp.content.BridgeMode
import uz.ekranoquvchi.mvp.content.DescOrder
import uz.ekranoquvchi.mvp.content.EmojiMode
import uz.ekranoquvchi.mvp.content.EngineProfiles
import uz.ekranoquvchi.mvp.content.Details
import uz.ekranoquvchi.mvp.content.NewLatinMode
import uz.ekranoquvchi.mvp.content.PipelineOptions
import uz.ekranoquvchi.mvp.content.ScienceOptions
import uz.ekranoquvchi.mvp.content.TextLang
import uz.ekranoquvchi.mvp.content.ScriptMode
import uz.ekranoquvchi.mvp.content.Verbosity
import uz.ekranoquvchi.mvp.core.GestureAction
import uz.ekranoquvchi.mvp.diag.swallowed
import java.util.Locale

/**
 * Sozlamalar qurilma-himoyalangan xotirada: qayta yoqilgandan keyin, qulf ochilmasdan (PIN ekranida)
 * ham o'qiladi (Direct Boot). Xizmat ham, sozlamalar ekrani ham shu bitta faylni ishlatadi.
 *
 * Barcha o'qish funksiyalari xatoga chidamli: faylda noto'g'ri tur yoki ruxsat etilmagan qiymat bo'lsa
 * (masalan, buzilgan zaxira fayli), standart qiymat qaytariladi. Ilgari bunday holat ClassCastException
 * bilan xizmatni qulatishi mumkin edi.
 */
object Prefs {
    const val FILE = "settings"
    const val RATE = "speech_rate"
    const val PITCH = "speech_pitch"
    const val ECHO_MODE = "echo_mode"
    const val MIC_POLICY = "mic_policy"
    const val GESTURE_PROFILE = "gesture_profile"
    const val SCRIPT = "script_mode"
    const val NEW_LATIN = "new_latin_mode"
    const val APOSTROPHE = "apostrophe"
    const val BRIDGE = "bridge_mode"
    const val PRIVATE_NOTIF = "private_notifications"
    const val ENGINE = "engine_pref"
    const val VOICE = "voice_name"

    /** ENGINE = "custom" bo'lganda: foydalanuvchi tanlagan (yuklab olingan ovoz paketi) nutq dvigatelining paket nomi. */
    const val ENGINE_PACKAGE = "engine_package"

    /** Tizim nutq dvigateli (OzbegimTtsService): ovoz beruvchi dvigatelning paket nomi; bo'sh = avtomatik. Qurilmaga xos: eksport qilinmaydi. */
    const val TTS_BACKEND = "tts_backend_pkg"

    /** O'zbegim afzal ko'rilgan mexanizm bo'lishidan oldingi tizim dvigateli (qaytarish uchun). Qurilmaga xos: eksport qilinmaydi. */
    const val TTS_PREV_DEFAULT = "tts_prev_default"

    /** Tizim dvigatelida O'zbegimning matn qoidalari (uch yozuv, raqamlar, Rim raqamlari, telefon va h.k.) qo'llanadimi. */
    const val TTS_RULES = "tts_apply_rules"
    const val OPEN_TTS_ENGINE = "open_tts_engine"

    /** Tanlangan mahalliy ovoz qaysi yozuvni o'qiy oladi: "auto" (dvigatelga qarab), "latin" (faqat lotin), "both" (lotin va kirill). */
    const val VOICE_READS = "voice_reads"
    const val VERBOSITY = "verbosity"
    const val EARCONS = "earcons"
    const val OPEN_TTS = "open_tts_settings"
    const val OPEN_VOICES = "open_voices"
    const val OPEN_GESTURES = "open_gestures"
    const val OPEN_AI = "open_ai_settings"
    const val OPEN_CONTACT = "open_contact"
    const val OPEN_DETAILS = "open_details"
    const val OPEN_MENU = "open_menu_editor"
    const val OPEN_RATE = "open_rate"
    const val OPEN_PITCH = "open_pitch"

    /** Nutq tezligi va toni ishoralar bilan o'zgartirilganda bir surishdagi qadam (foiz): 1, 2, 5, 10 yoki 20. */
    const val RATE_STEP = "rate_step"

    // Tafsilotlar (TalkBack'dagi "Verbosity" kabi): nimalar aytilishi. VERBOSITY: short, normal, full yoki custom (maxsus).
    const val DET_TYPE = "det_type"
    const val DET_TYPE_BTN = "det_type_btn"
    const val DET_POSITION = "det_position"
    const val DET_HINTS = "det_hints"
    const val DET_ORDER = "det_order"
    const val DET_WINDOW_TITLES = "det_window_titles"
    const val DET_SCREEN_OFF = "det_screen_off"
    const val DET_VOLUME_KEEP = "det_volume_keep"

    /** Emoji va stikerlarni o'qish (darajalarga bog'liq emas). */
    const val DET_EMOJI = "det_emoji"
    const val DET_STICKERS = "det_stickers"

    /** Stikerni qisqa o'qish: faqat stiker so'zi va birinchi emoji aytiladi (stikerlarni o'qish yoqiq bo'lganda). */
    const val DET_STICKER_SHORT = "det_sticker_short"

    /** O'zbekiston telefon raqamlarini guruh-guruh, har guruhdan keyin to'xtab o'qish: "+998. 94. 773. 99. 10." (Tafsilotlar). */
    const val DET_PHONE = "det_phone"

    /**
     * Fan qisqartmalarini o'qish (Tafsilotlar > Fan qisqartmalari). Har bir fan alohida: yoqilsa qisqartma o'zbekcha so'z bilan
     * o'qiladi ("5 kg" -> "besh kilogramm"), o'chirilsa aytilmaydi. O'lchov birliklari, fizika, matematika, informatika.
     */
    const val DET_SCI_UNITS = "det_sci_units"
    const val DET_SCI_PHYSICS = "det_sci_physics"
    const val DET_SCI_MATH = "det_sci_math"
    const val DET_SCI_INFO = "det_sci_info"

    /** Matn tili (Tafsilotlar): "auto" (ovoz paketi tili, bo'lmasa tizim tili), "system", "uz", "other". */
    const val DET_LANG = "det_lang"

    /** Tanlangan mahalliy ovozning tili ("uz", "ru", "en"...) va u qaysi ovoz uchun saqlangani: Ovozlar ekrani yozadi. */
    const val VOICE_LANG = "voice_lang"
    const val VOICE_LANG_FOR = "voice_lang_for"

    /** Boshqa ekran o'quvchi (TalkBack va h.k.) yoqilsa, O'zbegim kutish rejimiga o'tadi; o'chirilsa, qayta boshlaydi. */
    const val YIELD_OTHER = "yield_other_reader"

    /** O'zbegim menyusining foydalanuvchi tanlagan bandlari: vergul bilan ajratilgan amal nomlari; yo'q bo'lsa, standart ro'yxat. */
    const val MENU_ITEMS = "menu_items"
    private const val DET_MIGRATED = "det_migrated"

    // Tezlashtirish va avtomatik tarjima
    const val SPEED = "response_speed"

    // Vaqt va batareya e'loni (core/TimeAnnouncer): vaqt har 30 yoki 60 daqiqada, batareya har 10 foizda yoki o'zi belgilagan qadamda
    const val TIME_MODE = "time_mode"
    const val TIME_FORMAT = "time_format"
    const val TIME_STYLE = "time_style"
    const val TIME_BUSY = "time_busy"
    const val BATTERY_MODE = "battery_mode"
    const val BATTERY_STEP = "battery_step"

    // Bosish va navigatsiya (Tafsilotlar): ikki marta bosishni kim bajaradi; yorliqsiz tugmalarni o'tkazib yuborish
    const val DOUBLE_TAP = "double_tap_mode"
    const val SKIP_UNLABELED = "skip_unlabeled"
    const val AUTO_TRANSLATE = "auto_translate"
    const val TRANSLATE_SCRIPT = "translate_script"

    /** Ikki barmoqli ishorani ilovaga o'tkazib yuborish: "keyguard" (faqat qulf ekranida), "always", "off". */
    const val TWO_FINGER = "two_finger_pass"

    // Onlayn imkoniyatlar (ixtiyoriy). Kalitlar (AZURE_KEY, AI_KEY) Secrets orqali shifrlab saqlanadi.
    const val ONLINE = "online_enabled"
    const val AZURE_KEY = "azure_key"
    const val AZURE_REGION = "azure_region"
    const val AZURE_VOICE = "azure_voice"
    const val AZURE_QUALITY = "azure_quality"
    const val AI_KEY = "ai_key"
    const val AI_MODEL = "ai_model"
    const val AI_PROVIDER = "ai_provider"
    const val GEMINI_KEY = "gemini_key"
    const val GEMINI_MODEL = "gemini_model"
    const val OPENAI_KEY = "openai_key"
    const val OPENAI_MODEL = "openai_model"
    const val CUSTOM_KEY = "custom_key"
    const val CUSTOM_MODEL = "custom_model"
    const val CUSTOM_URL = "custom_url"
    const val TRANSLATE_FROM = "translate_from"
    const val TRANSLATE_TO = "translate_to"
    const val TYPING_MODE = "typing_mode"

    const val DEFAULT_AZURE_REGION = "westeurope"
    const val DEFAULT_AZURE_VOICE = "uz-UZ-MadinaNeural"
    const val DEFAULT_AI_MODEL = "claude-haiku-5-5"
    const val DEFAULT_GEMINI_MODEL = "gemini-flash-latest"
    const val DEFAULT_OPENAI_MODEL = "gpt-5.4-mini"

    private val REGION_RE = Regex("^[a-z0-9]{3,30}$")
    private val PACKAGE_RE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val LANG_TAG_RE = Regex("^[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})*$")
    private val MODEL_RE = Regex("^[A-Za-z0-9._-]{3,80}$")
    private val CUSTOM_MODEL_RE = Regex("^[A-Za-z0-9._:/-]{3,100}$")
    private val URL_RE = Regex("^https://[A-Za-z0-9.-]+(:[0-9]{1,5})?(/[A-Za-z0-9._~%/-]*)?$")

    /** Tarjima tillari (strings.xml dagi translate_values massivi bilan bir xil). Manba tilida qo'shimcha "auto" (avtomatik). */
    val TRANSLATE_LANGS = listOf(
        "uz", "ru", "en", "tr", "ar", "fa", "tg", "kk", "ky", "tk", "zh", "ko", "ja", "de", "fr", "es", "it",
    )

    /** Ishora sozlamalari kaliti: "g_" + ishora identifikatori. */
    const val GESTURE_PREFIX = "g_"

    /** Ovoz nomining eng katta uzunligi. */
    const val MAX_VOICE_LENGTH = 200

    /** Ichki xotira (eksport qilinmaydi): o'zbek tili tizim dvigatelida yo'qligi sababli tanlangan dvigatel. */
    private const val AUTO_ENGINE = "auto_engine_memo"

    /**
     * Qiymat chegaralari (foiz). Sozlamalarda aniq son yoziladi yoki 1 foizlik qadam bilan o'zgartiriladi (NumberDialog),
     * shuning uchun chegaralar keng: tezlik 20-500, ton 20-300. Onlayn (Azure) ovozda tezlik 50-200, ton 50-150
     * oralig'idan tashqarisi shu chegaraga keltiriladi (xizmat ruxsat bergan oraliq).
     */
    val RATE_RANGE = 20..500
    val PITCH_RANGE = 20..300

    /** Matnli sozlamalarning ruxsat etilgan qiymatlari (strings.xml dagi *_values massivlari bilan bir xil). */
    val CHOICES: Map<String, Set<String>> = mapOf(
        ECHO_MODE to setOf("chars", "words", "both", "off"),
        MIC_POLICY to setOf("stop", "duck", "continue"),
        GESTURE_PROFILE to setOf("talkback", "voiceover"),
        SCRIPT to setOf("auto", "latin", "keep"),
        NEW_LATIN to setOf("auto", "uz", "off"),
        APOSTROPHE to setOf("ascii", "modifier"),
        BRIDGE to setOf("adapt", "off"),
        ENGINE to setOf("auto", "rhvoice", "system", "azure", "custom"),
        VOICE_READS to setOf("auto", "latin", "both"),
        VERBOSITY to setOf("short", "normal", "full", "custom"),
        DET_ORDER to setOf("name_type_state", "type_name_state", "state_name_type"),
        SPEED to setOf("normal", "fast", "turbo"),
        TRANSLATE_SCRIPT to setOf("latin", "cyrl", "new"),
        TWO_FINGER to setOf("keyguard", "always", "off"),
        AZURE_VOICE to setOf(
            "uz-UZ-MadinaNeural", "uz-UZ-SardorNeural",
            "ru-RU-SvetlanaNeural", "ru-RU-DmitryNeural",
            "en-US-JennyNeural", "en-US-GuyNeural",
        ),
        AZURE_QUALITY to setOf("mp3_192", "mp3_96"),
        AI_PROVIDER to setOf("auto", "claude", "gemini", "openai", "custom"),
        TRANSLATE_FROM to (setOf("auto") + TRANSLATE_LANGS),
        TRANSLATE_TO to (setOf("uz-cyrl") + TRANSLATE_LANGS),
        TYPING_MODE to setOf("standard", "touch"),
        RATE_STEP to setOf("1", "2", "5", "10", "20"),
        TIME_MODE to setOf("off", "30", "60"),
        TIME_FORMAT to setOf("24", "12"),
        TIME_STYLE to setOf("past", "digital"),
        TIME_BUSY to setOf("wait", "interrupt"),
        BATTERY_MODE to setOf("off", "step10", "custom"),
        DOUBLE_TAP to setOf("service", "system"),
        DET_LANG to TextLang.ALL,
    )

    private val BOOLEANS = setOf(
        PRIVATE_NOTIF, EARCONS, AUTO_TRANSLATE,
        DET_TYPE, DET_TYPE_BTN, DET_POSITION, DET_HINTS, DET_WINDOW_TITLES, DET_SCREEN_OFF, DET_VOLUME_KEEP,
        DET_EMOJI, DET_STICKERS, DET_STICKER_SHORT, DET_PHONE, YIELD_OTHER, SKIP_UNLABELED, TTS_RULES,
        DET_SCI_UNITS, DET_SCI_PHYSICS, DET_SCI_MATH, DET_SCI_INFO,
    )

    @Volatile
    private var cached: SharedPreferences? = null

    /** Bitta umumiy nusxa: har chaqiruvda yangi Context o'rami yaratilmaydi (hodisalar ko'p, kechikish muhim). */
    fun get(context: Context): SharedPreferences {
        val existing = cached
        if (existing != null) return existing
        return synchronized(this) {
            cached ?: run {
                val base = context.applicationContext ?: context
                base.createDeviceProtectedStorageContext()
                    .getSharedPreferences(FILE, Context.MODE_PRIVATE)
                    .also { cached = it }
            }
        }
    }

    // ------------------------------------------------------------------ xavfsiz o'qish

    private fun readString(c: Context, key: String, def: String): String =
        try {
            get(c).getString(key, def) ?: def
        } catch (_: ClassCastException) {
            def
        }

    private fun readInt(c: Context, key: String, def: Int): Int =
        try {
            get(c).getInt(key, def)
        } catch (_: ClassCastException) {
            def
        }

    private fun readBool(c: Context, key: String, def: Boolean): Boolean =
        try {
            get(c).getBoolean(key, def)
        } catch (_: ClassCastException) {
            def
        }

    private fun choice(c: Context, key: String, def: String): String {
        val v = readString(c, key, def)
        return if (CHOICES[key]?.contains(v) == true) v else def
    }

    /** Ixtiyoriy matnli kalit uchun xavfsiz o'qish (tur mos kelmasa null). */
    fun string(c: Context, key: String): String? =
        try {
            get(c).getString(key, null)
        } catch (_: ClassCastException) {
            null
        }

    fun rate(c: Context): Int = readInt(c, RATE, 100).coerceIn(RATE_RANGE.first, RATE_RANGE.last)

    fun pitch(c: Context): Int = readInt(c, PITCH, 100).coerceIn(PITCH_RANGE.first, PITCH_RANGE.last)

    /** Tezlik va tonni ishora bilan o'zgartirishdagi qadam (foiz). */
    fun rateStep(c: Context): Int = choice(c, RATE_STEP, "10").toIntOrNull() ?: 10

    /** Yozilgan matn e'loni: "chars" (harflar), "words" (so'zlar), "both" (ikkalasi), "off". */
    fun echoMode(c: Context): String = choice(c, ECHO_MODE, "chars")

    /**
     * Mikrofon ochiq bo'lganda (masalan, ovozli xabar yozilayotganda): "stop" (avtomatik xabarlar o'qilmaydi,
     * bosib o'qiganlaringiz past ovozda aytiladi), "duck" (hammasi past ovozda), "continue" (o'zgarishsiz).
     */
    fun micPolicy(c: Context): String = choice(c, MIC_POLICY, "stop")

    /** Ishora profili: "talkback" (TalkBack-mos) yoki "voiceover" (VoiceOver-mos). */
    fun gestureProfile(c: Context): String = choice(c, GESTURE_PROFILE, "talkback")

    fun privateNotifications(c: Context): Boolean = readBool(c, PRIVATE_NOTIF, true)

    fun earcons(c: Context): Boolean = readBool(c, EARCONS, false)

    /** Vaqtni avtomatik aytish: "off", "30" yoki "60" (daqiqa). */
    fun timeMode(c: Context): String = choice(c, TIME_MODE, "off")

    /** "24" yoki "12" soatlik format. */
    fun timeFormat(c: Context): String = choice(c, TIME_FORMAT, "24")

    /**
     * Soatni o'qish uslubi: "past" (birinchi misol: "Soat o'n bir, soat o'n birdan o'ttiz daqiqa o'tdi") yoki
     * "digital" (ikkinchi misol: "Soat o'n bir, soat o'n bir o'ttiz"). 12 va 24 soatlik formatda ham shunday.
     */
    fun timeStyle(c: Context): String = choice(c, TIME_STYLE, "past")

    /** E'lon vaqtida matn o'qilayotgan bo'lsa: "wait" (tugashini kutish) yoki "interrupt" (to'xtatib, darhol aytish). */
    fun timeBusy(c: Context): String = choice(c, TIME_BUSY, "wait")

    /** Batareya e'loni: "off", "step10" (har 10 foizda) yoki "custom" (BATTERY_STEP foizda). */
    fun batteryMode(c: Context): String = choice(c, BATTERY_MODE, "off")

    /** Ikki marta bosishni kim bajaradi: "service" (O'zbegim, TalkBack kabi; standart) yoki "system" (Android o'zi). */
    fun doubleTapMode(c: Context): String = choice(c, DOUBLE_TAP, "service")

    /** Xabarlashuv ilovalarida yorliqsiz, ichi bo'sh tugmalarda to'xtamaslik (standart: yoqiq). */
    fun skipUnlabeled(c: Context): Boolean = readBool(c, SKIP_UNLABELED, true)

    /** "custom" rejimidagi foiz qadami (1 dan 50 gacha). */
    fun batteryStep(c: Context): Int = readInt(c, BATTERY_STEP, 10).coerceIn(1, 50)

    /**
     * "auto": tizim dvigateli, o'zbek tili bo'lmasa RHVoice; "rhvoice": RHVoice; "system": faqat tizim dvigateli;
     * "azure": onlayn Microsoft ovozi; "custom": foydalanuvchi tanlagan boshqa dvigatel (enginePackage).
     */
    fun engine(c: Context): String = choice(c, ENGINE, "auto")

    /** ENGINE = "custom" bo'lganda tanlangan nutq dvigatelining paket nomi; yo'q yoki noto'g'ri bo'lsa bo'sh. */
    fun enginePackage(c: Context): String {
        val v = readString(c, ENGINE_PACKAGE, "").trim()
        return if (v.length <= 200 && PACKAGE_RE.matches(v)) v else ""
    }

    /** Tanlangan ovoz paketining yozuvi: "auto", "latin" yoki "both". */
    fun voiceReads(c: Context): String = choice(c, VOICE_READS, "auto")

    /**
     * Joriy ovoz kirill yozuvini o'qiy oladimi (matnni tayyorlash uchun). Foydalanuvchi ovoz paketining yozuvini
     * belgilagan bo'lsa ("latin" yoki "both"), shu; "auto" bo'lsa dvigatel profili (RHVoice ikkalasini, boshqalar lotinni).
     * Onlayn Microsoft ovozi doim lotin oladi. Ovoz kirillni o'qiy olsa ham, matn O'zbegimning o'z qoidalari
     * (raqamlar, Rim raqamlari, telefon, yangi lotin, til ko'prigi) bo'yicha tayyorlanadi.
     */
    fun engineReadsCyrillic(c: Context, enginePackage: String?): Boolean {
        if (enginePackage == EngineProfiles.AZURE) return false
        return when (voiceReads(c)) {
            "both" -> true
            "latin" -> false
            else -> EngineProfiles.readsCyrillic(enginePackage)
        }
    }

    fun autoEngine(c: Context): String = readString(c, AUTO_ENGINE, "")

    /** Tizim dvigateli uchun tanlangan ovoz beruvchi dvigatel (paket nomi); bo'sh yoki noto'g'ri bo'lsa avtomatik. */
    fun ttsBackend(c: Context): String {
        val v = readString(c, TTS_BACKEND, "").trim()
        return if (v.length <= 200 && PACKAGE_RE.matches(v)) v else ""
    }

    fun setTtsBackend(c: Context, pkg: String) {
        try {
            get(c).edit().putString(TTS_BACKEND, pkg).apply()
        } catch (ex: Throwable) {
            swallowed("Prefs.setTtsBackend", ex)
        }
    }

    /** O'zbegim afzal ko'rilgan mexanizm bo'lishidan oldingi tizim dvigateli; noma'lum bo'lsa bo'sh. */
    fun ttsPrevDefault(c: Context): String {
        val v = readString(c, TTS_PREV_DEFAULT, "").trim()
        return if (v.length <= 200 && PACKAGE_RE.matches(v)) v else ""
    }

    fun setTtsPrevDefault(c: Context, pkg: String) {
        try {
            get(c).edit().putString(TTS_PREV_DEFAULT, pkg).apply()
        } catch (ex: Throwable) {
            swallowed("Prefs.setTtsPrevDefault", ex)
        }
    }

    /** Tizim dvigatelida O'zbegimning matn qoidalari qo'llanadimi (standart: ha). */
    fun ttsRules(c: Context): Boolean = readBool(c, TTS_RULES, true)

    /** Onlayn imkoniyatlar asosiy kaliti: standartda o'chiq, foydalanuvchi tasdig'i bilan yoqiladi. */
    fun onlineEnabled(c: Context): Boolean = readBool(c, ONLINE, false)

    fun azureRegion(c: Context): String {
        val v = readString(c, AZURE_REGION, DEFAULT_AZURE_REGION).trim().lowercase()
        return if (REGION_RE.matches(v)) v else DEFAULT_AZURE_REGION
    }

    fun azureVoice(c: Context): String = choice(c, AZURE_VOICE, DEFAULT_AZURE_VOICE)

    /** "mp3_192": 48 kHz, 192 kbit/s; "mp3_96": 48 kHz, 96 kbit/s. */
    fun azureQuality(c: Context): String = choice(c, AZURE_QUALITY, "mp3_192")

    fun aiModel(c: Context): String {
        val v = readString(c, AI_MODEL, DEFAULT_AI_MODEL).trim()
        return if (MODEL_RE.matches(v)) v else DEFAULT_AI_MODEL
    }

    /** Sun'iy intellekt xizmati: "auto" (kaliti kiritilgan birinchisi), "claude", "gemini", "openai" yoki "custom". */
    fun aiProvider(c: Context): String = choice(c, AI_PROVIDER, "auto")

    fun geminiModel(c: Context): String {
        val v = readString(c, GEMINI_MODEL, DEFAULT_GEMINI_MODEL).trim()
        return if (MODEL_RE.matches(v)) v else DEFAULT_GEMINI_MODEL
    }

    fun openaiModel(c: Context): String {
        val v = readString(c, OPENAI_MODEL, DEFAULT_OPENAI_MODEL).trim()
        return if (CUSTOM_MODEL_RE.matches(v)) v else DEFAULT_OPENAI_MODEL
    }

    /** Boshqa (OpenAI bilan mos) xizmat modeli; kiritilmagan yoki noto'g'ri bo'lsa bo'sh. */
    fun customModel(c: Context): String {
        val v = readString(c, CUSTOM_MODEL, "").trim()
        return if (CUSTOM_MODEL_RE.matches(v)) v else ""
    }

    /** Boshqa xizmat manzili (faqat https); kiritilmagan yoki noto'g'ri bo'lsa bo'sh. */
    fun customUrl(c: Context): String {
        val v = readString(c, CUSTOM_URL, "").trim()
        return if (URL_RE.matches(v)) v else ""
    }

    /** Tarjima manba tili: "auto" (tilni o'zi aniqlaydi, standart) yoki til kodi ("uz", "ru", "en" va h.k.). */
    fun translateFrom(c: Context): String = choice(c, TRANSLATE_FROM, "auto")

    /** Tarjima maqsad tili: standart "uz" (o'zbekcha, lotin); "uz-cyrl" o'zbekcha kirill. */
    fun translateTo(c: Context): String = choice(c, TRANSLATE_TO, "uz")

    /** Klaviatura rejimi: "standard" (harf ikki marta bosilganda yoziladi) yoki "touch" (barmoq ko'tarilganda yoziladi). */
    fun typingMode(c: Context): String = choice(c, TYPING_MODE, "standard")

    fun setAutoEngine(c: Context, engine: String) {
        try {
            get(c).edit().putString(AUTO_ENGINE, engine).apply()
        } catch (ex: Throwable) {
            swallowed("Prefs.setAutoEngine", ex)
        }
    }

    fun voice(c: Context): String = readString(c, VOICE, "").take(MAX_VOICE_LENGTH)

    /**
     * Tanlangan ovoz paketining tili: onlayn (Azure) ovoz yoki saqlangan mahalliy ovoz. Ovoz tanlanmagan yoki tili
     * noma'lum bo'lsa null (u holda tizim tili olinadi).
     */
    fun voiceLangTag(c: Context): String? {
        if (engine(c) == EngineProfiles.AZURE && onlineEnabled(c) && Secrets.has(c, AZURE_KEY)) return azureVoice(c)
        val v = voice(c)
        if (v.isEmpty() || readString(c, VOICE_LANG_FOR, "") != v) return null
        return readString(c, VOICE_LANG, "").ifEmpty { null }
    }

    /**
     * Tanlangan ovozning tilini eslab qoladi. Ovozlar ekrani ham yozadi, lekin bu bilan ilgari tanlangan ovozlar (til
     * saqlanmagan) ham dvigatel ishga tushganda tilini oladi: ular uchun matn tili "Avtomatik" to'g'ri ishlaydi.
     */
    fun rememberVoiceLang(c: Context, voice: String, lang: String) {
        if (voice.isEmpty() || !LANG_TAG_RE.matches(lang)) return
        if (readString(c, VOICE_LANG_FOR, "") == voice && readString(c, VOICE_LANG, "") == lang) return
        get(c).edit().putString(VOICE_LANG, lang).putString(VOICE_LANG_FOR, voice).apply()
    }

    /** Foydalanuvchi tanlovi: "auto", "system", "uz" yoki "other". */
    fun textLang(c: Context): String = choice(c, DET_LANG, TextLang.AUTO)

    /**
     * Matn o'zbekchami. Shunga qarab O'zbegimning o'zbekcha qoidalari (raqamlar, fan qisqartmalari, yozuv, til ko'prigi,
     * emoji nomlari) qo'llanadi; aks holda matnni ovoz dvigateli o'zi o'qiydi.
     */
    fun isUzbekText(c: Context): Boolean = TextLang.isUzbek(textLang(c), Locale.getDefault().language, voiceLangTag(c))

    fun verbosity(c: Context): Verbosity = when (choice(c, VERBOSITY, "normal")) {
        "short" -> Verbosity.SHORT
        "full" -> Verbosity.FULL
        else -> Verbosity.NORMAL
    }

    /** Tafsilot darajasi nomi: "short", "normal", "full" yoki "custom". */
    fun verbosityPreset(c: Context): String = choice(c, VERBOSITY, "normal")

    /** Hozirgi tafsilotlar: hamma qiymat alohida kalitlarda saqlanadi (daraja tanlansa, ular birdan o'rnatiladi). */
    fun details(c: Context): Details = Details(
        typeOther = readBool(c, DET_TYPE, true),
        typeButtonImage = readBool(c, DET_TYPE_BTN, false),
        position = readBool(c, DET_POSITION, false),
        hints = readBool(c, DET_HINTS, false),
        order = when (choice(c, DET_ORDER, "name_type_state")) {
            "type_name_state" -> DescOrder.TYPE_NAME_STATE
            "state_name_type" -> DescOrder.STATE_NAME_TYPE
            else -> DescOrder.NAME_TYPE_STATE
        },
        readEmoji = readBool(c, DET_EMOJI, true),
        readStickers = readBool(c, DET_STICKERS, true),
        stickerShort = readBool(c, DET_STICKER_SHORT, false),
        phoneGroups = readBool(c, DET_PHONE, true),
    )

    /** Telefon raqamlari guruh-guruh o'qiladimi (standart: ha). */
    fun phoneGroups(c: Context): Boolean = readBool(c, DET_PHONE, true)

    /** Qaysi fanlarning qisqartmalari o'qiladi (standart: hammasi). O'chirilgan fanning qisqartmasi aytilmaydi. */
    fun science(c: Context): ScienceOptions = ScienceOptions(
        units = readBool(c, DET_SCI_UNITS, true),
        physics = readBool(c, DET_SCI_PHYSICS, true),
        math = readBool(c, DET_SCI_MATH, true),
        info = readBool(c, DET_SCI_INFO, true),
    )

    /** Boshqa ekran o'quvchi yoqilganda O'zbegim kutish rejimiga o'tadimi (standart: ha). */
    fun yieldToOtherReaders(c: Context): Boolean = readBool(c, YIELD_OTHER, true)

    /** Emoji: o'qiladi (READ) yoki aytilmaydi (SKIP). Telefon tili o'zbekcha bo'lmasa, o'qish ovoz dvigateliga qoldiriladi. */
    fun emojiMode(c: Context): EmojiMode = if (readBool(c, DET_EMOJI, true)) EmojiMode.READ else EmojiMode.SKIP

    /** O'zbegim menyusining foydalanuvchi tanlagan bandlari; hech qachon saqlanmagan bo'lsa null (standart ro'yxat). */
    fun menuItems(c: Context): List<GestureAction>? {
        val raw = string(c, MENU_ITEMS) ?: return null
        val all = GestureAction.values()
        val out = ArrayList<GestureAction>()
        for (part in raw.split(',')) {
            val name = part.trim()
            val a = all.firstOrNull { it.name == name } ?: continue
            if (a != GestureAction.NONE && a != GestureAction.GLOBAL_MENU && a !in out) out.add(a)
        }
        return out
    }

    /** Bir martalik yangilanish belgisi: birinchi chaqirishda true qaytaradi va eslab qoladi (keyingilarida false). */
    fun takeMenuUpgrade(c: Context, tag: String): Boolean {
        return try {
            val key = "menu_upgrade_" + tag
            val p = get(c)
            if (p.getBoolean(key, false)) {
                false
            } else {
                p.edit().putBoolean(key, true).apply()
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    /** items == null: standart ro'yxatga qaytariladi. */
    fun saveMenuItems(c: Context, items: List<GestureAction>?) {
        try {
            val e = get(c).edit()
            if (items == null) e.remove(MENU_ITEMS) else e.putString(MENU_ITEMS, items.joinToString(",") { it.name })
            e.apply()
        } catch (ex: Throwable) {
            swallowed("Prefs.saveMenuItems", ex)
        }
    }

    /** Tafsilot darajasini tanlaydi va unga mos hamma qiymatni o'rnatadi (TalkBack'dagi "preset" kabi). */
    fun applyPreset(c: Context, preset: String) {
        try {
            val d = when (preset) {
                "short" -> Details.LOW
                "full" -> Details.HIGH
                else -> Details.MEDIUM
            }
            val name = if (preset == "short" || preset == "full") preset else "normal"
            get(c).edit()
                .putString(VERBOSITY, name)
                .putBoolean(DET_TYPE, d.typeOther)
                .putBoolean(DET_TYPE_BTN, d.typeButtonImage)
                .putBoolean(DET_POSITION, d.position)
                .putBoolean(DET_HINTS, d.hints)
                .apply()
        } catch (ex: Throwable) {
            swallowed("Prefs.applyPreset", ex)
        }
    }

    /** Eski versiyadan o'tish: saqlangan daraja (qisqa, oddiy, batafsil) alohida qiymatlarga bir marta yoziladi. */
    fun migrateDetails(c: Context) {
        try {
            if (readBool(c, DET_MIGRATED, false)) return
            val preset = choice(c, VERBOSITY, "normal")
            val d = if (preset == "custom") Details.MEDIUM else Details.forPreset(verbosity(c))
            get(c).edit()
                .putBoolean(DET_TYPE, d.typeOther)
                .putBoolean(DET_TYPE_BTN, d.typeButtonImage)
                .putBoolean(DET_POSITION, d.position)
                .putBoolean(DET_HINTS, d.hints)
                .putBoolean(DET_MIGRATED, true)
                .apply()
        } catch (ex: Throwable) {
            swallowed("Prefs.migrateDetails", ex)
        }
    }

    fun windowTitles(c: Context): Boolean = readBool(c, DET_WINDOW_TITLES, true)

    /** Ekran o'chiq bo'lganda ham avtomatik xabarlarni (bildirishnoma, jonli matn) aytish. */
    fun speakWhenScreenOff(c: Context): Boolean = readBool(c, DET_SCREEN_OFF, true)

    /** Ovoz tugmalari bosilganda hozirgi o'qish to'xtamasligi va fokus ovoz paneliga o'tmasligi. */
    fun volumeKeep(c: Context): Boolean = readBool(c, DET_VOLUME_KEEP, true)

    /** Javob tezligi: "normal", "fast" (standart) yoki "turbo". */
    fun speedLevel(c: Context): String = choice(c, SPEED, "fast")

    fun autoTranslate(c: Context): Boolean = readBool(c, AUTO_TRANSLATE, false)

    /** Avtomatik tarjima qaysi yozuvda tayyorlanadi: "latin" (hozirgi), "cyrl" (kirill) yoki "new" (yangi alifbo). */
    fun translateScript(c: Context): String = choice(c, TRANSLATE_SCRIPT, "latin")

    fun twoFingerPass(c: Context): String = choice(c, TWO_FINGER, "keyguard")

    fun pipelineOptions(c: Context, engineReadsCyrillic: Boolean): PipelineOptions =
        PipelineOptions(
            bridge = if (choice(c, BRIDGE, "adapt") == "off") BridgeMode.OFF else BridgeMode.ADAPT,
            scriptMode = when (choice(c, SCRIPT, "auto")) {
                "latin" -> ScriptMode.LATIN
                "keep" -> ScriptMode.KEEP
                else -> ScriptMode.AUTO
            },
            newLatin = when (choice(c, NEW_LATIN, "auto")) {
                "uz" -> NewLatinMode.UZ
                "off" -> NewLatinMode.OFF
                else -> NewLatinMode.AUTO
            },
            engineReadsCyrillic = engineReadsCyrillic,
            apostrophe = if (choice(c, APOSTROPHE, "ascii") == "modifier") '\u02BB' else '\'',
            phoneGroups = readBool(c, DET_PHONE, true),
            science = science(c),
        )

    /**
     * O'zbekcha bo'lmagan matn: yozuv, til ko'prigi, Rim raqamlari, o'zbekcha raqam so'zlari va fan qisqartmalari
     * qo'llanmaydi, matnni ovoz dvigateli o'zi o'qiydi. Faqat telefon raqami guruhlari (Tafsilotlardagi tanlov) qoladi.
     */
    fun otherLangOptions(c: Context): PipelineOptions =
        PipelineOptions(
            bridge = BridgeMode.OFF,
            scriptMode = ScriptMode.KEEP,
            newLatin = NewLatinMode.OFF,
            romanNumerals = false,
            phoneGroups = readBool(c, DET_PHONE, true),
            uzbekText = false,
        )

    // ------------------------------------------------------------------ eksport va import tekshiruvi

    /**
     * Eksport va importda bitta sozlamani tekshiradi. To'g'ri bo'lsa toza qiymatni (Int, Boolean yoki String),
     * noto'g'ri turdagi, chegaradan tashqaridagi yoki noma'lum kalit bo'lsa null qaytaradi.
     * Tekshirilmagan qiymat SharedPreferences'ga tushsa, keyingi o'qishda xizmat qulashi yoki nutq
     * butunlay jim bo'lib qolishi (masalan, tezlik 0) mumkin edi.
     *
     * @param gestureIds ma'lum ishora identifikatorlari (faqat shularga "g_" kaliti qabul qilinadi).
     */
    fun sanitize(key: String, value: Any?, gestureIds: Set<Int>): Any? = when {
        key == RATE -> (value as? Number)?.toInt()?.takeIf { it in RATE_RANGE }
        key == BATTERY_STEP -> (value as? Number)?.toInt()?.takeIf { it in 1..50 }
        key == PITCH -> (value as? Number)?.toInt()?.takeIf { it in PITCH_RANGE }
        key in BOOLEANS -> value as? Boolean
        key == VOICE || key == VOICE_LANG_FOR -> (value as? String)?.takeIf { it.length <= MAX_VOICE_LENGTH }
        key == VOICE_LANG -> (value as? String)?.takeIf { it.length <= 20 && LANG_TAG_RE.matches(it) }
        key == ENGINE_PACKAGE -> (value as? String)?.takeIf { it.length <= 200 && PACKAGE_RE.matches(it) }
        key == AZURE_REGION -> (value as? String)?.takeIf { REGION_RE.matches(it) }
        key == AI_MODEL -> (value as? String)?.takeIf { MODEL_RE.matches(it) }
        key == GEMINI_MODEL -> (value as? String)?.takeIf { MODEL_RE.matches(it) }
        key == OPENAI_MODEL || key == CUSTOM_MODEL -> (value as? String)?.takeIf { CUSTOM_MODEL_RE.matches(it) }
        key == CUSTOM_URL -> (value as? String)?.takeIf { URL_RE.matches(it) }
        key in CHOICES -> (value as? String)?.takeIf { it in CHOICES.getValue(key) }
        key.startsWith(GESTURE_PREFIX) -> sanitizeGesture(key, value, gestureIds)
        key == MENU_ITEMS -> sanitizeMenuItems(value)
        else -> null
    }

    private fun sanitizeMenuItems(value: Any?): String? {
        val raw = value as? String ?: return null
        val names = GestureAction.values().map { it.name }.toSet()
        return raw.split(',').map { it.trim() }.filter { it in names }.joinToString(",")
    }

    private fun sanitizeGesture(key: String, value: Any?, gestureIds: Set<Int>): String? {
        val id = key.removePrefix(GESTURE_PREFIX).toIntOrNull() ?: return null
        if (id !in gestureIds) return null
        val name = value as? String ?: return null
        return if (GestureAction.values().any { it.name == name }) name else null
    }
}
