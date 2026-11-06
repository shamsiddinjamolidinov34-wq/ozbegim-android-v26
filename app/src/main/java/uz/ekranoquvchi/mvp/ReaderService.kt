package uz.ekranoquvchi.mvp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import uz.ekranoquvchi.mvp.content.BridgeMode
import uz.ekranoquvchi.mvp.content.Change
import uz.ekranoquvchi.mvp.content.CharNames
import uz.ekranoquvchi.mvp.content.ContentPipeline
import uz.ekranoquvchi.mvp.content.Details
import uz.ekranoquvchi.mvp.content.EmojiMode
import uz.ekranoquvchi.mvp.content.EngineProfiles
import uz.ekranoquvchi.mvp.content.LangDetect
import uz.ekranoquvchi.mvp.content.NewLatinMode
import uz.ekranoquvchi.mvp.content.PipelineOptions
import uz.ekranoquvchi.mvp.content.ProfileStore
import uz.ekranoquvchi.mvp.content.ScriptMode
import uz.ekranoquvchi.mvp.content.SensitiveText
import uz.ekranoquvchi.mvp.content.UzNumbers
import uz.ekranoquvchi.mvp.core.Adjustable
import uz.ekranoquvchi.mvp.core.AudioExport
import uz.ekranoquvchi.mvp.core.AudioRoute
import uz.ekranoquvchi.mvp.core.CallActions
import uz.ekranoquvchi.mvp.core.Earcons
import uz.ekranoquvchi.mvp.core.TimeAnnouncer
import uz.ekranoquvchi.mvp.core.TouchActions
import uz.ekranoquvchi.mvp.core.GestureAction
import uz.ekranoquvchi.mvp.core.GestureMap
import uz.ekranoquvchi.mvp.core.GlobalMenu
import uz.ekranoquvchi.mvp.core.displayName
import uz.ekranoquvchi.mvp.core.MenuOverlay
import uz.ekranoquvchi.mvp.core.Navigator
import uz.ekranoquvchi.mvp.core.NodeDescriber
import uz.ekranoquvchi.mvp.core.OtherReaders
import uz.ekranoquvchi.mvp.core.PlaybackMonitor
import uz.ekranoquvchi.mvp.core.SpeechManager
import uz.ekranoquvchi.mvp.core.SpeedProfile
import uz.ekranoquvchi.mvp.core.Spelling
import uz.ekranoquvchi.mvp.core.SystemSurfaces
import uz.ekranoquvchi.mvp.core.TextCursor
import uz.ekranoquvchi.mvp.core.TextGran
import uz.ekranoquvchi.mvp.core.Tutorial
import uz.ekranoquvchi.mvp.core.findFirst
import uz.ekranoquvchi.mvp.core.isListBoundary
import uz.ekranoquvchi.mvp.core.isShadow
import uz.ekranoquvchi.mvp.diag.CrashGuard
import uz.ekranoquvchi.mvp.diag.Diag
import uz.ekranoquvchi.mvp.diag.ErrorCode
import uz.ekranoquvchi.mvp.diag.swallowed
import uz.ekranoquvchi.mvp.net.AiClient
import uz.ekranoquvchi.mvp.net.AutoTranslator
import uz.ekranoquvchi.mvp.net.NetWatch
import uz.ekranoquvchi.mvp.net.Online
import uz.ekranoquvchi.mvp.ui.AiSettingsActivity
import uz.ekranoquvchi.mvp.ui.BackupActivity
import uz.ekranoquvchi.mvp.ui.DetailsActivity
import uz.ekranoquvchi.mvp.ui.GesturesActivity
import uz.ekranoquvchi.mvp.ui.HealthActivity
import uz.ekranoquvchi.mvp.ui.LogActivity
import uz.ekranoquvchi.mvp.ui.MenuActivity
import uz.ekranoquvchi.mvp.ui.ScreenSearchActivity
import uz.ekranoquvchi.mvp.ui.SettingsActivity
import uz.ekranoquvchi.mvp.ui.TextFileEditorActivity
import uz.ekranoquvchi.mvp.ui.TutorialActivity
import uz.ekranoquvchi.mvp.ui.VoicesActivity
import uz.ekranoquvchi.mvp.util.Prefs
import uz.ekranoquvchi.mvp.util.UserFiles
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.abs
import java.util.concurrent.Executor

/**
 * Ekran o'quvchi xizmati: hodisalar -> tavsif -> til quvuri (uch yozuv, til ko'prigi) -> nutq.
 * Ishoralar tizimning "teginib o'rganish" rejimi orqali keladi; ikki marta bosishni tizim o'zi bajaradi.
 * Qaysi ishora nima qilishi GestureMap'da (sozlanadi).
 */
class ReaderService : AccessibilityService() {

    companion object {
        private const val TAG = "ReaderService"

        /** Ovoz tugmasi bosilgandan keyin shu vaqt ichida tizim ovoz paneli hodisalari nutqni to'xtatmaydi (ms). */
        private const val VOLUME_GUARD_MS = 3500L

        /** Ekran o'zgargandan keyin tarjimani oldindan tayyorlashgacha kutish (ms) va ko'riladigan tugunlar soni. */
        private const val PREFETCH_DELAY_MS = 600L
        private const val PREFETCH_NODES = 160

        /** Xizmat salomatligini davriy tekshirish (ms): 24 soatlik barqarorlik uchun. */
        private const val WATCHDOG_MS = 30_000L

        /** AI so'rovi shundan uzoq "band" tursa, qotib qolgan deb hisoblanadi (ms). */
        private const val AI_STUCK_MS = 90_000L

        /** Mikrofon holati shundan uzoq "band" tursa, tizimdan qayta so'raladi (ms). */
        private const val MIC_RECHECK_MS = 8_000L

        /** Ikki barmoqli ishorani o'tkazib yuborish holatini tekshirishdan oldingi kutish (ms). */
        private const val SURFACE_CHECK_MS = 400L

        /** Fokus bermagan (yaroqsiz) elementni o'tkazib, keyingisiga urinishlar soni: bitta nosoz element yurishni to'smasin. */
        private const val MAX_FOCUS_TRIES = 12

        /** Aylantirish hodisalari tinchigach, fokusni tekshirishdan oldingi kutish (ms). */
        private const val SCROLL_SETTLE_MS = 160L

        /** Aylantirishdan oldin foydalanuvchi shuncha vaqt ichida ekranga tekkan bo'lsa, fokus ko'chirilishi mumkin (ms). */
        private const val SCROLL_FOCUS_WINDOW_MS = 4_000L

        /** Ovozli xabar (media) ijrosi shuncha davom etgandan keyingina "eshitish rejimi" boshlanadi: qisqa signallar hisobga olinmaydi (ms). */
        private const val LISTEN_DEBOUNCE_MS = 450L

        /** Foydalanuvchi shuncha vaqt ichida ekranga tekkan bo'lsa, uning buyrug'i eshitish rejimida ham aytiladi (ms). */
        private const val USER_ACTIVE_MS = 1_500L

        /** Slayder qiymati ishora yoki teginishdan keyin shuncha vaqt ichida o'zgarsa, yangi qiymat aytiladi (ms). */
        private const val RANGE_ACTIVE_MS = 2_500L

        /** Bir xil slayder qiymati shuncha vaqt ichida ikki marta aytilmaydi: o'z qadamimiz va tizim hodisasi qo'shilib ketmasin (ms). */
        private const val RANGE_DEDUP_MS = 900L

        /** Barmoq ekranga tegib turgan holat shundan uzoq davom etgan bo'lsa, eskirgan hisoblanadi (ms). */
        private const val TOUCH_MAX_MS = 20_000L

        /**
         * Element amallari menyusida slayder qiymatini oshirish va kamaytirish (slayder oldinga/orqaga aylantirishni bilmasa,
         * faqat ACTION_SET_PROGRESS bo'lsa). Manfiy raqamlar tizim amallari bilan aralashmaydi.
         */
        private const val ACTION_VALUE_UP = -101
        private const val ACTION_VALUE_DOWN = -102

        /** Suhbat rejimida bir xil matn shuncha vaqt ichida qayta o'qilmaydi (ms). */
        private const val CHAT_DEDUP_MS = 20_000L

        /** Foydalanuvchi ro'yxat ichida fokusni shuncha vaqt ichida ko'chirgan bo'lsa, yangi xabar avtomatik o'qilmaydi (ms). */
        private const val CHAT_FOCUS_IDLE_MS = 30_000L

        /** Shu vaqt ichida yurish yana aynan shu elementga olib kelsa, joriy fokus eskirgan deb hisoblanadi (ms). */
        private const val STUCK_GUARD_MS = 4_000L

        /** Fokusdagi elementning holati (tanlangan, belgilangan) yoki tavsifi o'zgarganini bildiruvchi kontent o'zgarishi turlari. */
        private val STATE_CHANGE_TYPES: Int = AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION

        /**
         * Xizmat faqat shu hodisalarni oladi: qolganlari (oynalar ro'yxati, fokus tozalash va h.k.)
         * ishlatilmaydi, lekin har biri asosiy oqimni band qilib, harakatlanishni sekinlashtirardi.
         * Ishora aniqlash hodisalari ishoralar to'g'ri ishlashi uchun qoldirilgan. Aylantirish hodisasi
         * (TYPE_VIEW_SCROLLED) qaytarildi: u soniyasiga o'nlab marta keladi, shuning uchun har hodisada faqat
         * kutish vaqtini suramiz, asosiy ish aylantirish tinchigach bir marta bajariladi (onScrolled).
         */
        private val NEEDED_EVENTS: Int = AccessibilityEvent.TYPE_VIEW_CLICKED or
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER or
            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START or
            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_ANNOUNCEMENT or
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED or
            AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY or
            AccessibilityEvent.TYPE_GESTURE_DETECTION_START or
            AccessibilityEvent.TYPE_GESTURE_DETECTION_END or
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START or
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END or
            AccessibilityEvent.TYPE_VIEW_SCROLLED or
            AccessibilityEvent.TYPE_VIEW_SELECTED or
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED

        /** Shundan uzoq davom etgan ishlov jurnalga yoziladi (SLOW_HANDLER), ms. */
        private const val SLOW_HANDLER_MS = 120L

        /** Boshqa ekran o'quvchilar holatini davriy tekshirish (ms): sozlamalar kuzatuvchisiga qo'shimcha xavfsizlik. */
        private const val READER_POLL_MS = 2_500L

        /** Sozlamalar o'zgargandan keyin holatni tekshirishdan oldingi kutish (ms): ketma-ket o'zgarishlar bir marta ko'riladi. */
        private const val READER_DEBOUNCE_MS = 350L

        /** Kutish rejimiga o'tilgandan keyin shu vaqt ichida tizimning "to'xtat" so'rovi e'lonni kesmaydi (ms). */
        private const val STANDBY_INTERRUPT_GUARD_MS = 5_000L

        /** Kutish rejimiga o'tishdan oldingi xizmat bayroqlari saqlanmagan. */
        private const val NO_FLAGS = Int.MIN_VALUE

        /** AI tavsifi uchun ekran rasmining eng uzun tomoni (piksel): hajm va trafik kamayadi. */
        private const val MAX_SHOT_SIDE = 1280

        /** Suhbat rejimi (yangi xabarlarni avtomatik o'qish) standartda yoqiladigan ilovalar. */
        private val CHAT_PACKAGES = setOf(
            "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram",
            "com.whatsapp", "com.whatsapp.w4b", "com.viber.voip",
        )

        @Volatile
        var instance: ReaderService? = null
            private set

        /** "Oxirgi o'qilganni MP3 qilib saqlash" uchun eslab qolinadigan matnning eng katta hajmi (belgi). */
        private const val SESSION_MAX_CHARS = 30_000

        /** AccessibilityService.GESTURE_DOUBLE_TAP va GESTURE_DOUBLE_TAP_AND_HOLD (Android 10+): xizmat ikki marta bosishni o'zi bajarganda keladi. */
        private const val GESTURE_DOUBLE_TAP_ID = 17
        private const val GESTURE_DOUBLE_TAP_HOLD_ID = 18

        /** Haqiqiy tegish (tapByTouch) tugab, teginib o'rganish qayta yoqilgach yangi ekranda fokus tekshiriladigan payt (ms). */
        private const val TAP_REFOCUS_MS = 1_100L
        private const val TAP_REFOCUS_LONG_MS = 1_800L
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var speech: SpeechManager
    private lateinit var describer: NodeDescriber
    private lateinit var prefs: SharedPreferences
    private val navigator = Navigator()
    private lateinit var surfaces: SystemSurfaces

    /** Tizim qo'llamaydigan elementlarda (masalan, Telegram xabari) harf, so'z, qator bo'ylab virtual kursor. */
    private val textCursor = TextCursor()
    private val menu = MenuOverlay(this)

    /** Menyu ochilgan paytdagi fokus: menyu bekor qilinsa yoki element amali bajarilsa, fokus shu yerga qaytadi. */
    private var menuReturn: AccessibilityNodeInfo? = null

    /** Teginib o'rganishda oxirgi fokuslangan nuqta (teginib yozish rejimi uchun). */
    private var lastHoverUnit: AccessibilityNodeInfo? = null

    /** Foydalanuvchi ishora yoki ikkala ovoz tugmasini bosib turib to'xtatgan holat. */
    private var userSuspended = false

    /**
     * Boshqa ekran o'quvchi (TalkBack va h.k.) yoqilgani uchun kutish rejimida: O'zbegim hech narsa o'qimaydi, ishora va
     * tugmalarni ushlamaydi, teginib o'rganishni bo'shatadi. Boshqa ekran o'quvchi o'chirilsa, o'zi qayta boshlaydi.
     */
    private var yielded = false
    private var standbyAt = 0L
    private var standbyFlags = NO_FLAGS
    private var readerWatcher: OtherReaders.Watcher? = null
    private val readerCheck = Runnable { evaluateOtherReader() }
    private val readerPoll = object : Runnable {
        override fun run() {
            try {
                evaluateOtherReader()
            } catch (ex: Throwable) {
                swallowed("ReaderService.run", ex)
            }
            main.postDelayed(this, READER_POLL_MS)
        }
    }

    /** Ikki holatdan biri bo'lsa, xizmat o'qimaydi va ishoralarni ushlamaydi. */
    private val suspended: Boolean get() = userSuspended || yielded
    private var safeMode = false
    private var curtain: View? = null
    private var lastPackage: String? = null
    private var lastTouchAt = 0L
    private var lastChatText = ""
    private var lastRaw = ""

    /** Oxirgi o'qish seansi: ketma-ket (to'xtatmay) aytilgan mazmun matnlari birlashadi; to'xtatib aytilgan yangi mazmun yangi seans boshlaydi. */
    private var sessionRaw = ""
    private var sessionSensitive = false
    private var audioSaving = false
    private var micActive = false
    private var lastWindowAnnounce = 0L
    private var lastWindowText = ""
    private var lastFocusText = ""
    private var lastFocusAt = 0L
    private var lastLiveText = ""
    private var lastLiveAt = 0L
    private var pendingPrivate: String? = null
    private var pendingPrivateAt = 0L
    private var lastChanges: List<Change> = emptyList()
    private var bridgeErrors = 0
    private var eventErrors = 0
    private var volUp = false
    private var volDown = false

    /** Alt+tugma buyrug'i iste'mol qilingan tugmalar: ularning "qo'yib yuborish" hodisasi ham iste'mol qilinadi. */
    private val swallowedKeys = HashSet<Int>()

    /** Maxfiy xabar xotirada 2 daqiqadan ortiq turmaydi (ishora bosilmasa ham). */
    private val clearPrivate = Runnable { pendingPrivate = null }

    /** Oxirgi oyna hodisasidagi matn: kutish vaqti tugagach bir marta aytiladi (windowTask). */
    private var pendingWindowText = ""
    private val windowTask = Runnable { handleWindowChange() }
    private var lastContentAt = 0L
    private var lastSlowLogAt = 0L

    /** Tafsilotlar (nimalar aytilishi) va javob tezligi: sozlama o'zgarganda yangilanadi, har hodisada o'qilmaydi. */
    private var details: Details = Details.MEDIUM
    private var speed: SpeedProfile = SpeedProfile.FAST
    private var windowTitles = true
    private var speakScreenOff = true

    /** Ovoz tugmalari bosilgan oxirgi payt: shu paytdan keyin ovoz paneli o'qishni to'xtatmaydi. */
    private var lastVolumeKeyAt = 0L
    private var lastVolumeSpokenAt = 0L

    /** Har bir "to'xtatib aytish" bu sonni oshiradi: tarjimani kutayotgan eski matn yangisidan keyin aytilmasligi uchun. */
    private var speakToken = 0

    /** Avtomatik tarjima faolligi (2 soniya keshlanadi) va tarjimasi keshda yo'q yorliqlar (describeNode paytida to'planadi). */
    private var autoTrOk = false
    private var autoTrCheckedAt = 0L
    private var missing: MutableList<String>? = null
    private var chatPending = false
    private var prefetchPending = false
    private val prefetchTask = Runnable {
        prefetchPending = false
        runPrefetch()
    }

    // Barqarorlik (24 soat): davriy tekshiruv, mikrofon holati, ikki barmoqli ishora, ekran o'lchami
    private var aiBusySince = 0L
    private var micSince = 0L
    private var micCheckedAt = 0L
    private var passthroughOn = false
    private var surfaceReceiver: BroadcastReceiver? = null
    private val surfaceTask = Runnable { updatePassthrough() }
    private var lastFocusFailLogAt = 0L

    /** Oxirgi fokus hodisasining manbasi: ba'zi ilovalarda (Telegram) fokusdagi maxsus element findFocus bilan topilmaydi. */
    private var lastFocusedSource: AccessibilityNodeInfo? = null
    private var lastFocusedAt = 0L
    private var lastFocusFallbackLogAt = 0L
    private var lastUnlabeledLogAt = 0L

    /** focus() oxirgi chaqirilganda element allaqachon fokusda bo'lganmi (tizim bunda hodisa yubormaydi). */
    private var focusAlready = false
    private var lastEdgeUpAt = 0L
    private var homePkg: String? = null

    /** Tizim fokus bermagan, lekin o'zimiz o'qib bergan element: yangi fokus hodisasi kelguncha yurish shu yerdan davom etadi. */
    private var softFocus: AccessibilityNodeInfo? = null
    private var softFocusAt = 0L
    private var lastSoftLogAt = 0L

    /** Oxirgi so'ralgan fokus: Telegram'da maxsus element uchun fokus hodisasi manbasiz keladi, qaysi element ekanini shundan bilamiz. */
    private var focusRequest: AccessibilityNodeInfo? = null
    private var focusRequestAt = 0L

    /** Oxirgi yurish natijasi: shu yo'nalishda yana aynan shu element chiqsa, joriy fokus eskirgan deb hisoblanadi. */
    private var lastMoveTarget: AccessibilityNodeInfo? = null
    private var lastMoveForward = true
    private var lastMoveAt = 0L
    private var lastStuckLogAt = 0L
    private var lastClickFailLogAt = 0L

    /** Fokusdagi elementning oxirgi ma'lum holati va uni tekshirish rejasi (tanlangan, belgilangan...). */
    private var stateNode: AccessibilityNodeInfo? = null
    private var stateText = ""
    private var stateRetry = 0
    private var statePending = false
    private val stateTask = Runnable {
        statePending = false
        checkStateChange()
    }

    /**
     * Sozlanadigan element (slayder, tanlagich): oxirgi aytilgan qiymat va vaqti, ketma-ket qadamlarning tartib raqami
     * (faqat oxirgisining tekshiruvi ishlaydi) hamda barmoq ekranga tegib turgan holat (slayderni sudrab o'zgartirish).
     */
    private var rangeText = ""
    private var rangeAt = 0L
    private var adjustSeq = 0
    private var touching = false
    private var touchStartedAt = 0L
    private var homePkgAt = 0L
    private var lastDensity = 0
    private var lastFontScale = 0f

    // Ovozli xabar (media) eshitilayotganda avtomatik nutq to'xtatiladi: foydalanuvchi xabarni eshita olishi uchun
    private var playback: PlaybackMonitor? = null
    private var playbackActive = false
    private var listening = false
    private val listenStart = Runnable { beginListening() }

    /** Suhbat rejimida yaqinda o'qilgan xabarlar (normallashtirilgan matn, vaqt): bir xil matn qayta-qayta o'qilmasligi uchun. */
    private val recentChat = ArrayList<Pair<String, Long>>()
    private var lastNavLogAt = 0L
    private val watchdog = object : Runnable {
        override fun run() {
            try {
                healthTick()
            } catch (ex: Throwable) {
                swallowed("ReaderService.run", ex)
            }
            main.postDelayed(this, WATCHDOG_MS)
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val k = key ?: ""
        if (k.isEmpty() || k.startsWith("det_") || k == Prefs.VERBOSITY) {
            details = Prefs.details(this)
            windowTitles = Prefs.windowTitles(this)
            speakScreenOff = Prefs.speakWhenScreenOff(this)
        }
        if (k.isEmpty() || k == Prefs.SPEED) {
            speed = SpeedProfile.current(this)
            if (::describer.isInitialized) describer.light = speed.light
        }
        if (k.isEmpty() || k == Prefs.AUTO_TRANSLATE || k == Prefs.ONLINE || k == Prefs.DET_LANG || k.startsWith("ai_")) autoTrCheckedAt = 0L
        if (k.isEmpty() || k == Prefs.TWO_FINGER) scheduleSurfaceCheck()
        if (k.isEmpty() || k == Prefs.YIELD_OTHER) scheduleReaderCheck()
        if (::speech.isInitialized) {
            speech.applyPrefs()
            if (k == Prefs.ENGINE || k == Prefs.VOICE || k == Prefs.ENGINE_PACKAGE || k == Prefs.DET_LANG) speech.reconfigure()
        }
    }
    private val toggleSuspend = Runnable { setSuspended(!userSuspended) }

    /**
     * Mikrofon siyosati (FR-15): boshqa ilova mikrofonni ochsa (masalan, ovozli xabar yozilsa), nutq sozlamaga ko'ra
     * o'zgaradi. Doim tinglovchi tizim yozuvlari (hotword) va ovozi o'chirilgan yozuvlar hisobga olinmaydi, aks holda
     * ekran o'quvchi butunlay jim qolib ketishi mumkin edi.
     */
    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            applyMicState(realRecordingCount(configs) > 0)
        }
    }

    private fun applyMicState(active: Boolean) {
        if (active == micActive || !::speech.isInitialized) return
        micActive = active
        micSince = if (active) SystemClock.uptimeMillis() else 0L
        speech.setMicActive(active, Prefs.micPolicy(this))
        Diag.blackBox.add("mic", if (active) 1 else 0)
    }

    private fun realRecordingCount(configs: List<AudioRecordingConfiguration>?): Int {
        if (configs == null) return 0
        var n = 0
        for (c in configs) {
            try {
                if (Build.VERSION.SDK_INT >= 29 && c.isClientSilenced) continue
                val src = c.clientAudioSource
                // 1999 HOTWORD, 1998 RADIO_TUNER, 1997 ECHO_REFERENCE: tizim ichki manbalari, foydalanuvchi yozuvi emas
                if (src == 1999 || src == 1998 || src == 1997) continue
                n++
            } catch (_: Throwable) {
                n++
            }
        }
        return n
    }

    // ------------------------------------------------------------------ hayot sikli

    override fun onServiceConnected() {
        super.onServiceConnected()
        Diag.init(this)
        CrashGuard.install(this)
        prefs = Prefs.get(this)
        Prefs.migrateDetails(this)
        GestureMap.migrate(this)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        UserFiles.reloadAll(this)
        safeMode = CrashGuard.shouldEnterSafeMode(this)
        describer = NodeDescriber(this)
        surfaces = SystemSurfaces(this, main, { on -> setTouchExploration(on) }, { suspended })
        details = Prefs.details(this)
        windowTitles = Prefs.windowTitles(this)
        speakScreenOff = Prefs.speakWhenScreenOff(this)
        speed = SpeedProfile.current(this)
        describer.light = speed.light
        describer.translate = { s -> translateLabel(s) }
        describer.onUnlabeled = { n -> logUnlabeled(n) }
        AutoTranslator.problemListener = { onTranslatorProblem() }
        applyEventMask()
        passthroughOn = false
        updatePassthrough()
        registerSurfaceReceiver()
        if (::speech.isInitialized) speech.shutdown()
        speech = SpeechManager(this)
        micActive = false
        try {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).registerAudioRecordingCallback(recordingCallback, main)
        } catch (ex: Throwable) {
            swallowed("ReaderService.onServiceConnected", ex)
        }
        navigator.chatApp = { p -> isChatPackage(p) }
        refreshInputPrefs()
        playback?.stop()
        playbackActive = false
        listening = false
        playback = PlaybackMonitor(this, main) { active -> onPlaybackChanged(active) }.also { it.start() }
        instance = this
        // Tarmoq qaytganda onlayn ovoz va tarjimadagi to'xtash-kutish darhol olib tashlanadi
        NetWatch.start(this)
        // Vaqt va batareya e'loni: jadval qayta tiklanadi (ilova yoki qurilma qayta yoqilganda ham)
        TimeAnnouncer.start(this)
        // Xizmat qayta ulanganda kutish holati toza boshlanadi
        yielded = false
        standbyFlags = NO_FLAGS
        val other = if (Prefs.yieldToOtherReaders(this)) OtherReaders.find(this) else null
        if (other != null) {
            // Boshqa ekran o'quvchi allaqachon ishlayapti: ikkalasi aralashib ketmasligi uchun kutish rejimida boshlaymiz
            enterStandby(other)
        } else {
            speak(getString(R.string.msg_service_on), true)
        }
        if (safeMode) {
            Diag.log.log(ErrorCode.SAFE_MODE_ENTERED, mapOf("crashes" to CrashGuard.recentCount(this)))
            speak(getString(R.string.msg_safe_mode), false)
        }
        logDisplayInfo("connected")
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, WATCHDOG_MS)
        startReaderWatch()
        Log.i(TAG, "connected safeMode=$safeMode yielded=$yielded")
    }

    override fun onInterrupt() {
        // Ovoz tugmasi bosilganda tizim "to'xtat" desa ham o'qish davom etadi (tafsilotlardagi sozlama)
        if (volumeGuardActive()) return
        // Boshqa ekran o'quvchi ishga tushganda tizim "to'xtat" deydi: kutish rejimi haqidagi e'lon kesilmasin
        if (yielded && SystemClock.uptimeMillis() - standbyAt < STANDBY_INTERRUPT_GUARD_MS) return
        if (::speech.isInitialized) speech.stop()
    }

    /** Xotira kam bo'lganda keshlar tozalanadi: xizmat soatlab ishlaganda xotira o'sib ketmasligi uchun. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            AutoTranslator.clearCache()
            navigator.clearCaches()
        } catch (ex: Throwable) {
            swallowed("ReaderService.onTrimMemory", ex)
        }
    }

    /** Ekran o'lchami yoki shrift masshtabi o'zgarsa jurnalga yoziladi: "interfeys kichrayib ketdi" holatini aniqlash uchun. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        logDisplayInfo("config")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (instance === this) instance = null
        stopReaderWatch()
        main.removeCallbacksAndMessages(null)
        menu.dismiss()
        menuReturn = null
        lastHoverUnit = null
        softFocus = null
        focusRequest = null
        lastMoveTarget = null
        stateNode = null
        statePending = false
        setCurtain(false)
        // Ekran matnidan olingan nusxalar xizmat to'xtaganda xotirada qolmasin
        pendingPrivate = null
        lastRaw = ""
        lastChatText = ""
        lastFocusText = ""
        lastWindowText = ""
        lastLiveText = ""
        lastChanges = emptyList()
        swallowedKeys.clear()
        volUp = false
        volDown = false
        unregisterSurfaceReceiver()
        NetWatch.stop(this)
        TimeAnnouncer.stop(this)
        AutoTranslator.cancelPending()
        Earcons.release()
        AutoTranslator.problemListener = null
        navigator.clearCaches()
        scrollSource = null
        if (::surfaces.isInitialized) surfaces.release()
        try {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).unregisterAudioRecordingCallback(recordingCallback)
        } catch (ex: Throwable) {
            swallowed("ReaderService.shutdown", ex)
        }
        playback?.stop()
        playback = null
        playbackActive = false
        listening = false
        main.removeCallbacks(listenStart)
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        if (::speech.isInitialized) speech.shutdown()
    }

    /** Lug'at, yorliqlar va profillar o'zgargandan keyin qayta o'qiladi. */
    fun reloadUserData() = UserFiles.reloadAll(this)

    fun speakTest() = speak(getString(R.string.test_phrase), true)

    /** Boshqa ekranlar (masalan, o'quv mashqlari) nutq aytishi uchun. */
    fun announce(text: String) = speak(text, true)

    /**
     * Vaqt va batareya e'loni (core/TimeAnnouncer): ekran yoqiq yoki o'chiqligidan qat'i nazar aytiladi.
     * waitForIdle=true: davom etayotgan o'qish tugashini kutadi (eng ko'pi bilan 2 daqiqa), so'ng aytadi;
     * false: o'qishni to'xtatib, darhol aytadi. Boshqa ekran o'quvchiga o'tilgan (kutish) holatda aytilmaydi.
     */
    fun announceTimed(text: String, waitForIdle: Boolean) {
        if (!::speech.isInitialized || suspended || instance !== this) return
        keepAwakeBriefly()
        if (waitForIdle) announceWhenIdle(text, SystemClock.uptimeMillis() + 120_000L) else speak(text, true)
    }

    private fun announceWhenIdle(text: String, deadline: Long) {
        if (!::speech.isInitialized || suspended || instance !== this) return
        if (speech.isBusy() && SystemClock.uptimeMillis() < deadline) {
            main.postDelayed({ announceWhenIdle(text, deadline) }, 400L)
            return
        }
        speak(text, false)
    }

    /** Ekran o'chiq paytida e'lon oxirigacha aytilishi uchun qurilma 30 soniyagacha uyg'oq tutiladi (o'zi bo'shatiladi). */
    private fun keepAwakeBriefly() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ozbegim:announce")
            wl.setReferenceCounted(false)
            wl.acquire(30_000L)
        } catch (ex: Throwable) {
            swallowed("ReaderService.keepAwakeBriefly", ex)
        }
    }

    // ------------------------------------------------------------------ hodisalar

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val t0 = SystemClock.uptimeMillis()
        try {
            handleEvent(event)
        } catch (t: Throwable) {
            // Hodisa ishlovidagi kutilmagan xato xizmatni yiqitmasligi kerak
            if (eventErrors++ < 20) {
                Diag.log.log(ErrorCode.FOCUS_NODE_FAILED, mapOf("exc" to t.javaClass.simpleName, "ev" to event.eventType))
            }
        }
        noteDuration("ev", event.eventType, SystemClock.uptimeMillis() - t0)
    }

    /** Sekin ishlovni (asosiy oqimni band qilgan) jurnalga yozadi: keyingi safar sababini aniq ko'rish uchun. */
    private fun noteDuration(kind: String, what: Int, ms: Long) {
        if (ms < SLOW_HANDLER_MS) return
        val now = SystemClock.uptimeMillis()
        if (now - lastSlowLogAt < 5_000L) return
        lastSlowLogAt = now
        Diag.log.log(ErrorCode.SLOW_HANDLER, mapOf("kind" to kind, "what" to what, "ms" to ms))
    }

    private fun handleEvent(event: AccessibilityEvent) {
        if (suspended || !::speech.isInitialized) return
        val pkg = event.packageName?.toString()
        // Ovoz balandligi paneli: hozirgi o'qish to'xtamaydi va fokus panelga o'tmaydi
        if (volumeGuardActive() && isVolumeUi(event, pkg)) {
            onVolumeUi(event)
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != null &&
            (pkg.contains("systemui", ignoreCase = true) || pkg.contains("keyguard", ignoreCase = true))
        ) {
            scheduleSurfaceCheck()
        }
        if (ProfileStore.get(pkg)?.sensitive != true) Diag.blackBox.add("ev", event.eventType)
        if (pkg != null && event.eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) lastPackage = pkg
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> onHover(event, pkg)
                AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START -> onTouchExploreStart()
                AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END -> onTouchExploreEnd()
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> onFocused(event, pkg)
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowChanged(event)
                AccessibilityEvent.TYPE_ANNOUNCEMENT -> onAnnouncement(event)
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> onNotification(event, pkg)
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> onTextChanged(event, pkg)
                AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY -> onTraversed(event)
                AccessibilityEvent.TYPE_VIEW_CLICKED -> onClicked(event)
                AccessibilityEvent.TYPE_VIEW_SELECTED,
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> scheduleStateCheck(event.source)
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> onContentChanged(event, pkg)
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScrolled(event)
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                    lastTouchAt = SystemClock.uptimeMillis()
                    touching = true
                    touchStartedAt = lastTouchAt
                }
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                    lastTouchAt = SystemClock.uptimeMillis()
                    touching = false
                }
            }
        } catch (t: Throwable) {
            if (eventErrors++ < 20) {
                Diag.log.log(ErrorCode.FOCUS_NODE_FAILED, mapOf("exc" to t.javaClass.simpleName, "ev" to event.eventType))
            }
        }
    }

    /** Ilova profilida tafsilot darajasi belgilangan bo'lsa shu, aks holda umumiy tafsilotlar sozlamasi. */
    private fun detailsFor(pkg: String?): Details {
        val preset = ProfileStore.get(pkg)?.verbosity
        // Emoji, stikerlar va telefon raqamlarini o'qish darajalarga bog'liq emas: ilova profilidagi daraja ularni bekor qilmaydi
        return if (preset != null) {
            Details.forPreset(preset).copy(
                readEmoji = details.readEmoji,
                readStickers = details.readStickers,
                stickerShort = details.stickerShort,
                phoneGroups = details.phoneGroups,
            )
        } else {
            details
        }
    }

    /**
     * Teginib o'rganish: barmoq elementga tekkanda tizim faqat "hover" hodisasini yuboradi, fokusni esa xizmatning
     * o'zi qo'yishi kerak (TalkBack ham shunday qiladi). Ilgari bu hodisa umuman qayta ishlanmagani uchun barmoq
     * ostidagi element o'qilmasdi, holat paneli va pastki tugmalar (Bosh ekran, Orqaga) bosilmasdi, qulf ekranidagi
     * raqamlarni tanlab bo'lmasdi. Ikki marta bosishni tizim fokusdagi elementga o'zi bajaradi.
     */
    private fun onHover(event: AccessibilityEvent, pkg: String?) {
        lastTouchAt = SystemClock.uptimeMillis()
        // Barmoq tez yurganda hodisalar ketma-ket va bir zumda keladi (jurnalda 9 tasi): faqat oxirgisini qayta ishlaymiz
        val src = event.source
        hoverNode = src
        hoverPkg = pkg
        hoverTime = event.eventTime
        val own = event.contentDescription?.toString()?.trim().orEmpty()
        // ViewGroup hodisa matniga hamma bolalarining matnini qo'shadi: bo'sh joyga tegilganda bosh ekrandagi hamma ilova nomi
        // birdan aytilardi. Bir nechta bolasi bor konteynerning birlashtirilgan matni aytilmaydi (faqat o'z tavsifi).
        val aggregated = src != null && src.childCount > 1 && event.text.size > 1
        hoverFallback = when {
            own.isNotEmpty() -> own
            aggregated -> ""
            else -> event.text.joinToString(" ").trim()
        }
        main.removeCallbacks(hoverRunnable)
        main.post(hoverRunnable)
    }

    private fun processHover() {
        if (suspended || !::speech.isInitialized) return
        val src = hoverNode
        val pkg = hoverPkg
        val time = hoverTime
        val fallback = hoverFallback
        hoverNode = null
        try {
            val unit = if (src != null) navigator.unitFor(src, screenArea() * 3L / 10L) else null
            lastHoverUnit = unit
            if (unit != null) {
                val wasFocused = unit.isAccessibilityFocused
                val focused = !wasFocused && focus(unit, false)
                if (focused && !focusAlready) {
                    // Odatda o'qishni TYPE_VIEW_ACCESSIBILITY_FOCUSED bajaradi. Tizim bu hodisani yubormasa (ba'zi tizim
                    // oynalarida, qulf ekranida shunday bo'lishi mumkin), biroz kutib, o'zimiz o'qib beramiz.
                    val before = focusEvents
                    val seq = focusSeq
                    main.postDelayed({
                        if (focusEvents == before && seq == focusSeq && !suspended && ::speech.isInitialized) {
                            try {
                                if (speakUnit(unit, pkg, time, fallback)) markHeard(unit)
                            } catch (ex: Throwable) {
                                swallowed("ReaderService.processHover", ex)
                            }
                        }
                    }, 350L)
                    return
                }
                // Allaqachon fokusda (tizim qayta hodisa yubormaydi) yoki fokus qo'yib bo'lmadi: o'zimiz o'qib beramiz
                if (speakUnit(unit, pkg, time, fallback)) {
                    // Tizim fokus bermagan element eshitildi: ikki marta bosish va keyingi surish aynan shu elementdan davom
                    // etishi uchun joriy element qilib eslab qolinadi (ilgari eski fokusdagi boshqa element bosilardi)
                    if (!wasFocused && !focused) markHeard(unit)
                    return
                }
            }
            // Tugun topilmadi yoki bo'sh (tizim oynalarida shunday bo'ladi): hodisaning o'z matnini aytamiz
            if (fallback.isNotBlank()) sayTextAuto(fallback, true, time)
        } catch (t: Throwable) {
            Log.w(TAG, "hover", t)
        }
    }

    private fun speakUnit(unit: AccessibilityNodeInfo, pkg: String?, time: Long, hint: String? = null): Boolean =
        sayNode(unit, pkg, true, time, hint = hint) { true }

    // ------------------------------------------------------------------ avtomatik tarjima (o'zbekchaga)

    private class Described(val text: String, val missing: List<String>)

    /**
     * Avtomatik tarjima ishlaydimi: yoqilgan, telefon tili o'zbekcha, onlayn xizmat va kalit tayyor, xavfsiz rejim emas
     * va joriy ilova maxfiy emas (bank va h.k.). Tekshiruv 2 soniya keshlanadi.
     */
    private fun autoTranslateActive(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - autoTrCheckedAt > 2000L) {
            autoTrCheckedAt = now
            autoTrOk = !safeMode && Prefs.autoTranslate(this) && Prefs.isUzbekText(this) && Online.aiReady(this)
        }
        return autoTrOk && !isSensitiveContext()
    }

    /** NodeDescriber ilgagi: yorliq tarjimasi keshda bo'lsa o'sha, aks holda asl matn (va tarjima kerakligi eslab qolinadi). */
    private fun translateLabel(s: String): String {
        if (!autoTranslateActive()) return s
        if (s.length > AutoTranslator.MAX_ITEM_CHARS || !LangDetect.needsTranslation(s)) return s
        val hit = AutoTranslator.lookup(s)
        if (hit != null) return AutoTranslator.toScript(hit, Prefs.translateScript(this))
        missing?.add(s)
        return s
    }

    private fun describeNode(node: AccessibilityNodeInfo, d: Details, hint: String? = null): Described {
        if (!autoTranslateActive()) return Described(describer.describe(node, d, hint), emptyList())
        val miss = ArrayList<String>(3)
        missing = miss
        return try {
            Described(describer.describe(node, d, hint), miss)
        } finally {
            missing = null
        }
    }

    /**
     * Elementni tavsiflab aytadi. Avtomatik tarjima yoqilgan va yorliq tarjimasi hali keshda bo'lmasa, qisqa vaqt
     * (tezlik rejimiga qarab 0,9-1,6 soniya) kutiladi, so'ng tarjima, bo'lmasa asl matn aytiladi.
     * accept: aytishdan oldin chaqiriladi; false qaytarsa aytilmaydi (takrorni oldini olish uchun).
     * Qaytaradi: tavsif bo'sh emasmi.
     */
    private fun sayNode(
        node: AccessibilityNodeInfo,
        pkg: String?,
        interrupt: Boolean,
        eventTime: Long,
        auto: Boolean = false,
        hint: String? = null,
        accept: (String) -> Boolean,
    ): Boolean {
        val dt = detailsFor(pkg)
        val d = describeNode(node, dt, hint)
        if (d.text.isBlank()) return false
        if (d.missing.isEmpty()) {
            if (accept(d.text)) sayContent(d.text, interrupt, eventTime, auto = auto)
            return true
        }
        // Tarjima keshda yo'q: eski nutq darhol to'xtaydi, tarjimani kutamiz
        if (interrupt) speech.stop()
        val token = ++speakToken
        schedulePrefetch(80L, true)
        waitForTranslation(d.missing) {
            if (interrupt && token != speakToken) return@waitForTranslation
            val text = try {
                node.refresh()
                describer.describe(node, dt, hint)
            } catch (_: Throwable) {
                d.text
            }
            if (text.isNotBlank() && accept(text)) sayContent(text, interrupt, eventTime, auto = auto)
        }
        return true
    }

    /** Tarjima tayyor bo'lganda yoki kutish tugaganda (qaysi biri avval bo'lsa) onReady bir marta chaqiriladi. */
    private fun waitForTranslation(texts: List<String>, onReady: () -> Unit) {
        val fired = BooleanArray(1)
        val fire = Runnable {
            if (!fired[0]) {
                fired[0] = true
                onReady()
            }
        }
        main.postDelayed(fire, speed.translateWaitMs)
        AutoTranslator.request(applicationContext, texts, true) {
            main.removeCallbacks(fire)
            fire.run()
        }
    }

    /** Tayyor matn (bildirishnoma, e'lon, oyna sarlavhasi): kerak bo'lsa avval o'zbekchaga tarjima qilinadi. */
    private fun sayTextAuto(
        text: String,
        interrupt: Boolean,
        eventTime: Long = 0L,
        remember: Boolean = true,
        auto: Boolean = false,
    ) {
        if (!autoTranslateActive() || text.length > AutoTranslator.MAX_ITEM_CHARS || !LangDetect.needsTranslation(text)) {
            deliver(text, interrupt, eventTime, remember, auto)
            return
        }
        val script = Prefs.translateScript(this)
        val hit = AutoTranslator.lookup(text)
        if (hit != null) {
            deliver(AutoTranslator.toScript(hit, script), interrupt, eventTime, remember, auto)
            return
        }
        if (interrupt) speech.stop()
        val token = ++speakToken
        waitForTranslation(listOf(text)) {
            if (interrupt && token != speakToken) return@waitForTranslation
            val h = AutoTranslator.lookup(text)
            deliver(if (h != null) AutoTranslator.toScript(h, script) else text, interrupt, eventTime, remember, auto)
        }
    }

    private fun deliver(text: String, interrupt: Boolean, eventTime: Long, remember: Boolean, auto: Boolean) {
        if (remember) sayContent(text, interrupt, eventTime, auto = auto) else speak(text, interrupt, eventTime, auto = auto)
    }

    /** Ekrandagi ko'rinadigan matnlarni oldindan tarjima qildiradi: fokus ularga yetganda tarjima tayyor bo'ladi. */
    private fun schedulePrefetch(delayMs: Long, force: Boolean = false) {
        if (!autoTranslateActive()) return
        if (force) main.removeCallbacks(prefetchTask) else if (prefetchPending) return
        prefetchPending = true
        main.postDelayed(prefetchTask, delayMs)
    }

    private fun runPrefetch() {
        if (suspended || !autoTranslateActive() || AutoTranslator.isBlocked()) return
        val root = try {
            rootInActiveWindow
        } catch (_: Throwable) {
            null
        }
        if (root != null) AutoTranslator.prefetchTree(applicationContext, root, PREFETCH_NODES)
    }

    private fun onTranslatorProblem() {
        if (::speech.isInitialized && Prefs.autoTranslate(this)) speak(getString(R.string.msg_auto_tr_failed), false, auto = true)
    }

    /** Avtomatik tarjimani bitta amal bilan yoqadi yoki o'chiradi (menyu, ishora). Keyin u hamma joyda o'zi ishlaydi. */
    private fun toggleAutoTranslate() {
        if (Prefs.autoTranslate(this)) {
            prefs.edit().putBoolean(Prefs.AUTO_TRANSLATE, false).apply()
            autoTrCheckedAt = 0L
            speak(getString(R.string.msg_auto_tr_off), true)
            return
        }
        if (AiClient.activeProvider(this) == null) {
            // Kalitsiz tarjima qilib bo'lmaydi: kalit kiritiladigan ekranni ochamiz
            speak(getString(R.string.msg_auto_tr_need_key), true)
            openScreen(AiSettingsActivity::class.java)
            return
        }
        if (!Prefs.onlineEnabled(this)) {
            // Ekran matni tashqi xizmatga yuboriladi: bir marta aniq rozilik olinadi
            val labels = listOf(getString(R.string.auto_tr_consent_yes), getString(R.string.auto_tr_consent_no))
            openMenu(getString(R.string.auto_tr_consent_title), labels, currentFocus()) { which ->
                if (which == 0) main.postDelayed({ enableAutoTranslate() }, 300L)
            }
            return
        }
        enableAutoTranslate()
    }

    private fun enableAutoTranslate() {
        prefs.edit().putBoolean(Prefs.ONLINE, true).putBoolean(Prefs.AUTO_TRANSLATE, true).apply()
        autoTrCheckedAt = 0L
        val uz = Prefs.isUzbekText(this)
        speak(getString(if (uz) R.string.msg_auto_tr_on else R.string.msg_auto_tr_on_not_uz), true)
        schedulePrefetch(300L, true)
    }

    /** TYPE_VIEW_ACCESSIBILITY_FOCUSED hodisalari soni: hover'dan keyin fokus hodisasi kelganini bilish uchun. */
    private var focusEvents = 0

    /**
     * Har bir fokus so'rovi (focus) va har bir fokus hodisasi bu sonni oshiradi. Kechikib ishga tushadigan "o'qib berish"
     * vazifalari so'ralgan paytdagi qiymatni eslab qoladi: foydalanuvchi shu orada boshqa elementga o'tgan bo'lsa, eski
     * vazifa ishlamaydi. Ilgari tez surishda eski element qayta o'qilib, joriy element qilib belgilanardi (fokus "siljib ketardi").
     */
    private var focusSeq = 0
    private var hoverNode: AccessibilityNodeInfo? = null
    private var hoverPkg: String? = null
    private var hoverTime = 0L
    private var hoverFallback = ""
    private val hoverRunnable = Runnable { processHover() }

    /** Barmoq ekranga tegib o'rganishni boshlaganda davom etayotgan nutq to'xtaydi. */
    private fun onTouchExploreStart() {
        lastTouchAt = SystemClock.uptimeMillis()
        speech.stop()
    }

    /**
     * Teginib yozish (TalkBack'dagi "Touch typing"): klaviaturada barmoq ko'tarilganda oxirgi tegilgan tugma bosiladi.
     * Standart rejimda har bir harf ikki marta bosish bilan yoziladi. Faqat klaviatura oynasidagi tugmalar uchun ishlaydi.
     */
    private fun onTouchExploreEnd() {
        lastTouchAt = SystemClock.uptimeMillis()
        if (Prefs.typingMode(this) != "touch") return
        // Barmoq tez ko'tarilganda oxirgi hover hodisasi hali qayta ishlanmagan bo'lishi mumkin: avval uni yakunlaymiz
        if (hoverNode != null) {
            main.removeCallbacks(hoverRunnable)
            processHover()
        }
        val unit = lastHoverUnit ?: return
        lastHoverUnit = null
        try {
            unit.refresh()
            val onKeyboard = unit.window?.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
            if (onKeyboard && unit.isVisibleToUser) clickNode(unit, false)
        } catch (ex: Throwable) {
            swallowed("ReaderService.onTouchExploreEnd", ex)
        }
    }

    private fun onFocused(event: AccessibilityEvent, pkg: String?) {
        focusEvents++
        focusSeq++
        val hint = event.contentDescription?.toString()?.trim().orEmpty().ifEmpty { event.text.joinToString(" ").trim() }
        val node = event.source
        if (node == null) {
            // Ba'zi ilovalar (Telegram) maxsus elementlar uchun manbasiz hodisa yuboradi: hodisaning o'z matnini aytamiz.
            // Qaysi element ekanini o'zimiz so'ragan fokusdan bilamiz: shunda keyingi surish aynan shu yerdan davom etadi.
            // Ilgari bunday paytda eski element "joriy" bo'lib qolardi va yurish bir joyda qotib qolardi
            // (javob tariqasida yozilgan xabarda) yoki elementlar tushib qolardi.
            val req = focusRequest
            val nowMs = SystemClock.uptimeMillis()
            if (req != null && nowMs - focusRequestAt < 1500L) {
                lastFocusedSource = req
                lastFocusedAt = nowMs
                softFocus = null
                rememberState(req)
            }
            // Ovozli xabar eshitilayotganda ilovaning o'zi yuborgan takroriy hodisa (o'zimiz so'ramagan, ekranga ham tegilmagan) aytilmaydi
            val ours = req != null && nowMs - focusRequestAt < 1500L
            if (hint.isNotBlank() && !screenOffMuted() && !(listening && !ours && !userActiveRecently())) {
                sayTextAuto(hint, true, event.eventTime)
            }
            return
        }
        softFocus = null
        // Yurishdan boshqa yo'l bilan (teginish) fokus o'zgargan bo'lsa, qotib qolishdan himoya eskirgan hisoblanadi
        val moved = lastMoveTarget
        if (moved != null && node != moved) lastMoveTarget = null
        val previousFocus = lastFocusedSource
        lastFocusedSource = node
        lastFocusedAt = SystemClock.uptimeMillis()
        rememberState(node)
        // Boshqa elementga o'tilsa, matn bo'ylab kursor boshidan boshlanadi (bir xil element qayta bildirilsa saqlanadi)
        if (cursorKey(node) != textCursor.boundKey) textCursor.reset()
        // Ovozli xabar eshitilayotganda ilova ijro holati yangilanganda o'sha elementga fokus hodisasini qayta-qayta yuborishi
        // mumkin. Fokusni foydalanuvchi ham, biz ham ko'chirmagan bo'lsak, element qayta o'qilmaydi (xabar eshitilsin)
        if (listening && previousFocus != null && node == previousFocus && !userActiveRecently() &&
            SystemClock.uptimeMillis() - lastMoveAt > USER_ACTIVE_MS
        ) return
        // Oyna sarlavhasidan keyin birinchi element sarlavhani kesib yubormasin
        val afterWindow = SystemClock.uptimeMillis() - lastWindowAnnounce < 700L
        sayNode(node, pkg, !afterWindow, event.eventTime, hint = hint) { text ->
            val now = SystemClock.uptimeMillis()
            if (text == lastFocusText && now - lastFocusAt < 150L) {
                false
            } else {
                lastFocusText = text
                lastFocusAt = now
                true
            }
        }
    }

    /**
     * Oyna hodisalari odatda 2-6 tadan ketma-ket keladi (tizim oynalari ham). Ilgari har biri nutqni to'xtatib qayta
     * boshlardi va butun ekran daraxtini aylanib chiqardi: shu telefonni qotirib qo'ygan asosiy sabab edi.
     * Endi kutib turib, faqat oxirgi hodisa uchun bir marta ishlaymiz.
     */
    private fun onWindowChanged(event: AccessibilityEvent) {
        // Oyna almashdi: eski oynaning navbatdagi tarjima so'rovlari bekor qilinadi (ortiqcha AI token sarflanmasin)
        AutoTranslator.cancelPending()
        val text = event.text.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(", ")
        if (text.isNotEmpty()) pendingWindowText = text
        main.removeCallbacks(windowTask)
        main.postDelayed(windowTask, speed.windowDebounceMs)
    }

    private fun handleWindowChange() {
        if (suspended || !::speech.isInitialized) return
        val text = pendingWindowText
        pendingWindowText = ""
        val root = rootInActiveWindow
        val windowType = try {
            root?.window?.type
        } catch (_: Throwable) {
            null
        }
        val now = SystemClock.uptimeMillis()
        if (text.isNotEmpty() && (text != lastWindowText || (!listening && now - lastWindowAnnounce > 1000L))) {
            lastWindowText = text
            lastWindowAnnounce = now
            if (windowTitles) {
                Earcons.play(this, Earcons.Kind.WINDOW)
                sayTextAuto(text, true, 0L, false, true)
            }
        }
        if (windowType != AccessibilityWindowInfo.TYPE_INPUT_METHOD && !listening) maybeInitialFocus(root)
        schedulePrefetch(PREFETCH_DELAY_MS, true)
    }

    private fun onAnnouncement(event: AccessibilityEvent) {
        if (screenOffMuted() || listening) return
        val text = event.text.mapNotNull { it?.toString() }.joinToString(" ").trim()
        if (text.isNotEmpty()) sayTextAuto(text, false, event.eventTime, true, true)
    }

    // ------------------------------------------------------------------ ovozli xabar eshitilayotganda (eshitish rejimi)

    /** Suhbat ilovasimi (Telegram, WhatsApp va h.k.): ilova profilidagi "chat" qiymati standart ro'yxatdan ustun turadi. */
    private fun isChatPackage(pkg: String?): Boolean = ProfileStore.get(pkg)?.chat ?: CHAT_PACKAGES.contains(pkg)

    /** Foydalanuvchi so'nggi paytda ekranga tekkanmi: u bergan buyruq (yurish, bosish) ovozli xabar paytida ham aytiladi. */
    private fun userActiveRecently(windowMs: Long = USER_ACTIVE_MS): Boolean =
        SystemClock.uptimeMillis() - lastTouchAt < windowMs

    /** Boshqa ilova ovoz ijro eta boshladi yoki to'xtadi (PlaybackMonitor). */
    private fun onPlaybackChanged(active: Boolean) {
        playbackActive = active
        main.removeCallbacks(listenStart)
        if (active) {
            // Qisqa tovushlar (xabar yuborildi signali) hisobga olinmaydi: faqat biroz davom etgan ijro
            main.postDelayed(listenStart, LISTEN_DEBOUNCE_MS)
        } else {
            endListening()
        }
    }

    /**
     * Suhbat ilovasida ovozli xabar (yoki video) eshitila boshladi: aytilayotgan nutq darhol to'xtatiladi va ijro tugaguncha
     * avtomatik nutq (suhbat rejimi, e'lonlar, holat o'zgarishi, takroriy fokus hodisalari) o'chiriladi. Foydalanuvchining
     * o'z buyruqlari (surish, teginish) esa avvalgidek aytiladi. Boshqa ilovalarda (musiqa, video) hech narsa o'zgarmaydi.
     */
    private fun beginListening() {
        if (listening || !playbackActive || suspended || !::speech.isInitialized) return
        if (!isChatPackage(lastPackage)) return
        listening = true
        speech.stop()
        Diag.blackBox.add("listen", 1)
    }

    private fun endListening() {
        if (!listening) return
        listening = false
        Diag.blackBox.add("listen", 0)
    }

    /** Matn raqamlarsiz va kichik harfda: ijro vaqti (0:05, 0:06) o'zgarib tursa ham bir xil xabar deb topiladi. */
    private fun chatKey(text: String): String =
        text.lowercase().filter { !it.isDigit() }.replace(Regex("\\s+"), " ").trim()

    private fun seenRecently(text: String): Boolean {
        val key = chatKey(text)
        val now = SystemClock.uptimeMillis()
        var i = 0
        while (i < recentChat.size) {
            if (now - recentChat[i].second > CHAT_DEDUP_MS) recentChat.removeAt(i) else i++
        }
        return recentChat.any { it.first == key }
    }

    private fun markSeen(text: String) {
        val key = chatKey(text)
        recentChat.removeAll { it.first == key }
        recentChat.add(Pair(key, SystemClock.uptimeMillis()))
        while (recentChat.size > 24) recentChat.removeAt(0)
    }

    /** Tafsilotlardagi "Ekran o'chiq bo'lganda ham gapirish" o'chiq bo'lsa, ekran o'chiqligida avtomatik xabarlar o'qilmaydi. */
    private fun screenOffMuted(): Boolean {
        if (speakScreenOff) return false
        return try {
            !(getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        } catch (_: Throwable) {
            false
        }
    }

    private fun onNotification(event: AccessibilityEvent, pkg: String?) {
        if (ProfileStore.get(pkg)?.notify == false) return
        if (screenOffMuted()) return
        // Ovozli xabar eshitilayotganda suhbat ilovasining bildirishnomasi o'qilmaydi (qo'ng'iroq va boshqa ilovalar o'qilaveradi)
        if (listening && isChatPackage(pkg)) return
        val text = event.text.mapNotNull { it?.toString() }.joinToString(" ").trim()
        if (text.isEmpty()) return
        if (Prefs.privateNotifications(this) && SensitiveText.looksSensitive(text) && !AudioRoute.headsetConnected(this)) {
            // Tasdiqlash kodi karnayda o'qilmaydi: xotirada 2 daqiqa saqlanadi, ishora bilan o'qiladi
            pendingPrivate = text
            pendingPrivateAt = SystemClock.uptimeMillis()
            main.removeCallbacks(clearPrivate)
            main.postDelayed(clearPrivate, 120_000L)
            speak(getString(R.string.msg_private_notification), false, auto = true)
        } else {
            sayTextAuto(text, false, event.eventTime, true, true)
        }
    }

    /** Ilova profili ("echo:off/on") umumiy sozlamadan ustun turadi. */
    private fun echoModeFor(pkg: String?): String {
        val global = Prefs.echoMode(this)
        return when (ProfileStore.get(pkg)?.echo) {
            false -> "off"
            true -> if (global == "off") "chars" else global
            null -> global
        }
    }

    private fun lastWordBefore(text: String, end: Int): String {
        var i = end
        while (i > 0 && (text[i - 1].isLetterOrDigit() || text[i - 1] == '\'')) i--
        return text.substring(i, end)
    }

    /** Yozilgan matn e'loni (8.5): harflar, so'zlar yoki ikkalasi. Parol maydonida belgilar aytilmaydi. */
    private fun onTextChanged(event: AccessibilityEvent, pkg: String?) {
        val mode = echoModeFor(pkg)
        if (mode == "off") return
        if (event.isPassword) {
            speak(getString(R.string.msg_hidden_char), true)
            return
        }
        val cur = event.text.firstOrNull()?.toString() ?: return
        val from = event.fromIndex.coerceAtLeast(0)
        if (event.addedCount in 1..20 && event.removedCount == 0) {
            val end = (from + event.addedCount).coerceAtMost(cur.length)
            if (from >= end) return
            val added = cur.substring(from, end)
            if (mode == "chars" || mode == "both") speak(CharNames.speakable(added), true, event.eventTime)
            // so'z tugadi (probel yoki belgi yozildi): oxirgi yozilgan so'zni aytamiz
            if ((mode == "words" || mode == "both") && added.length == 1 && !added[0].isLetterOrDigit() && added[0] != '\'') {
                val word = lastWordBefore(cur, from)
                if (word.isNotEmpty()) speak(word, mode == "words", event.eventTime)
            }
        } else if (event.removedCount in 1..20 && event.addedCount == 0 && mode != "words") {
            val before = event.beforeText?.toString().orEmpty()
            val end = (from + event.removedCount).coerceAtMost(before.length)
            if (from < end) {
                speak(getString(R.string.msg_deleted, CharNames.speakable(before.substring(from, end))), true, event.eventTime)
            }
        }
    }

    /** Harf, so'z, qator yoki xatboshi bo'yicha yurganda o'tilgan bo'lakni aytadi. */
    private fun onTraversed(event: AccessibilityEvent) {
        // Parol maydonida harf/so'z bo'yicha yurganda ham matn aytilmaydi ("parollarni ko'rsatish" yoqilgan bo'lsa ham)
        if (event.isPassword || event.source?.isPassword == true) {
            speak(getString(R.string.msg_hidden_char), true, event.eventTime)
            return
        }
        val text = (event.source?.text ?: event.source?.contentDescription ?: event.text.firstOrNull())?.toString() ?: return
        val from = event.fromIndex.coerceIn(0, text.length)
        val to = event.toIndex.coerceIn(from, text.length)
        if (to <= from) return
        val piece = text.substring(from, to)
        val named = CharNames.unitName(piece)
        sayContent(if (named != piece) named else piece.trim(), true, event.eventTime)
    }

    private fun onClicked(event: AccessibilityEvent) {
        val src = event.source ?: return
        // Bosishdan keyin fokusdagi elementning holati o'zgargan bo'lsa (tanlandi, belgilandi, yoqildi), o'zgarish aytiladi
        if (scheduleStateCheck(src)) return
        // Fokus eslab qolinmagan bo'lsa: eski usul, faqat belgilanadigan elementlar uchun
        if (!src.isCheckable) return
        main.postDelayed({
            try {
                src.refresh()
                describer.stateOf(src)?.let { speak(it, false) }
            } catch (ex: Throwable) {
                swallowed("ReaderService.onClicked", ex)
            }
        }, 150L)
    }

    private fun onContentChanged(event: AccessibilityEvent, pkg: String?) {
        // Fokusdagi elementning holati yoki tavsifi o'zgargan bo'lsa (tanlandi, belgilandi), o'zgarish aytiladi
        if ((event.contentChangeTypes and STATE_CHANGE_TYPES) != 0) scheduleStateCheck(event.source)
        // Ovozli xabar eshitilayotganda ilova mazmunni tez-tez yangilaydi (ijro vaqti, chiziq): bular o'qilmaydi
        if (listening) return
        // Ba'zi ilovalar bunday hodisani sekundiga o'nlab marta yuboradi: har biri uchun tugunni so'rash tizimni sekinlashtiradi
        val t = SystemClock.uptimeMillis()
        if (t - lastContentAt < speed.contentGapMs) return
        lastContentAt = t
        val src = event.source ?: return
        // Ro'yxat aylantirilganda yoki yangi mazmun chiqqanda yangi matnlar oldindan tarjima qilinadi
        schedulePrefetch(PREFETCH_DELAY_MS)
        val region = src.liveRegion
        if (region != View.ACCESSIBILITY_LIVE_REGION_NONE) {
            if (screenOffMuted()) return
            val assertive = region == View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
            sayNode(src, pkg, assertive, event.eventTime, true) { text ->
                val now = SystemClock.uptimeMillis()
                if (text == lastLiveText && now - lastLiveAt < 1000L) {
                    false
                } else {
                    lastLiveText = text
                    lastLiveAt = now
                    true
                }
            }
            return
        }
        if (!safeMode && ProfileStore.get(pkg)?.sensitive != true &&
            (ProfileStore.get(pkg)?.chat ?: CHAT_PACKAGES.contains(pkg))
        ) readNewChatMessage(src, pkg)
    }

    /**
     * Suhbat rejimi: foydalanuvchi ekranga tegmayotgan paytda kelgan yangi xabar avtomatik o'qiladi.
     * Taxminiy usul: ro'yxatdagi oxirgi ko'rinadigan element o'zgarsa, uni o'qiydi.
     */
    private fun readNewChatMessage(src: AccessibilityNodeInfo, pkg: String?) {
        if (screenOffMuted() || chatPending || listening) return
        if (SystemClock.uptimeMillis() - lastTouchAt < 2500L) return
        var list: AccessibilityNodeInfo? = src
        var guard = 0
        while (list != null && list.collectionInfo == null && !list.isScrollable && guard++ < 8) list = list.parent
        if (list == null) return
        // Foydalanuvchining fokusi shu ro'yxat ichida bo'lsa, u aynan shu yerda yurib o'qiyapti (Telegram chatlar ro'yxati,
        // xabarlar tarixi): ro'yxatning boshqa yacheykasi matni uning o'qishiga qo'shib aytilmaydi.
        val focused = cachedFocus()
        if (focused != null && SystemClock.uptimeMillis() - lastFocusedAt < CHAT_FOCUS_IDLE_MS && isInside(focused, list)) return
        var last: AccessibilityNodeInfo? = null
        // Eng yangi xabar ko'rinishda eng pastdagi: bolalar tartibi teskari bo'lsa, boshidan qidiramiz
        val order: IntProgression =
            if (navigator.isReversedContainer(list)) (0 until list.childCount) else (list.childCount - 1 downTo 0)
        for (i in order) {
            val c = list.getChild(i) ?: continue
            if (c.isVisibleToUser) {
                last = c
                break
            }
        }
        if (last == null) return
        val node: AccessibilityNodeInfo = last
        val dt = detailsFor(pkg)
        val d = describeNode(node, dt)
        if (d.text.isBlank() || d.text == lastChatText || seenRecently(d.text)) return
        if (d.missing.isEmpty()) {
            lastChatText = d.text
            markSeen(d.text)
            sayContent(d.text, false, auto = true)
            return
        }
        // Yangi xabar boshqa tilda: tarjimasini kutib, keyin o'qiymiz (kutish paytida takror ishga tushmaydi)
        chatPending = true
        schedulePrefetch(80L, true)
        waitForTranslation(d.missing) {
            chatPending = false
            val text = try {
                node.refresh()
                describer.describe(node, dt)
            } catch (_: Throwable) {
                d.text
            }
            if (text.isNotBlank() && text != lastChatText && !seenRecently(text) && !listening) {
                lastChatText = text
                markSeen(text)
                sayContent(text, false, auto = true)
            }
        }
    }

    private fun maybeInitialFocus(root: AccessibilityNodeInfo?) {
        if (suspended || root == null) return
        if (root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) != null) return
        // Fokus maxsus elementda (Telegram) bo'lsa, tizim uni findFocus bilan ko'rsatmaydi: oyna hodisasidan keyin
        // fokus ekranning eng tepasidagi elementga sakrab ketmasligi kerak
        if (focusSourceInWindow(root)) return
        val first = navigator.firstUnit(root) ?: return
        focus(first)
    }

    /** Oxirgi fokus manbasi hali shu oynada ko'rinib turibdimi. */
    private fun focusSourceInWindow(root: AccessibilityNodeInfo): Boolean {
        val last = lastFocusedSource ?: return false
        if (SystemClock.uptimeMillis() - lastFocusedAt > 120_000L) return false
        return try {
            if (!last.refresh() || !last.isVisibleToUser) return false
            val w = last.window ?: return false
            w.id == root.window?.id
        } catch (_: Throwable) {
            false
        }
    }

    // ------------------------------------------------------------------ ishoralar

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onGesture(gestureId: Int): Boolean {
        val t0 = SystemClock.uptimeMillis()
        val handled = handleGesture(gestureId)
        noteDuration("gesture", gestureId, SystemClock.uptimeMillis() - t0)
        return handled
    }

    private fun handleGesture(gestureId: Int): Boolean {
        if (suspended) return false
        Diag.blackBox.add("gesture", gestureId)
        // TalkBack kabi: bir barmoqli ikki marta bosishni xizmatning o'zi bajaradi (tizim bunday ishoralarni faqat shu rejimda beradi)
        if (gestureId == GESTURE_DOUBLE_TAP_ID || gestureId == GESTURE_DOUBLE_TAP_HOLD_ID) {
            return try {
                activateByDoubleTap(gestureId == GESTURE_DOUBLE_TAP_HOLD_ID)
                true
            } catch (t: Throwable) {
                Diag.log.log(ErrorCode.FOCUS_NODE_FAILED, mapOf("exc" to t.javaClass.simpleName, "gesture" to gestureId))
                false
            }
        }
        Tutorial.listener?.invoke(gestureId)
        val action = GestureMap.actionFor(this, gestureId)
        if (action == GestureAction.NONE) return false
        return try {
            // TalkBack kabi: slayder yoki tanlagich fokusda bo'lsa, yuqoriga/pastga surish qiymatni o'zgartiradi
            if (!adjustByGesture(gestureId, action)) perform(action)
            true
        } catch (t: Throwable) {
            Diag.log.log(ErrorCode.FOCUS_NODE_FAILED, mapOf("exc" to t.javaClass.simpleName, "gesture" to gestureId))
            false
        }
    }

    /**
     * TalkBack kabi: slayder (yorqinlik, ovoz balandligi va h.k.) yoki tanlagich fokusda bo'lsa, bir barmoq bilan yuqoriga
     * surish qiymatni oshiradi, pastga surish kamaytiradi; \"o'ngga, so'ng chapga\" (oldinga aylantirish) oshiradi,
     * \"chapga, so'ng o'ngga\" (orqaga aylantirish) kamaytiradi. Yuqoriga/pastga surish faqat \"Elementlar\" o'qish
     * boshqaruvida qiymatni o'zgartiradi (boshqa boshqaruvda tanlangan boshqaruv bo'yicha yuradi, TalkBack kabi), VoiceOver
     * profilida esa har doim. Ishora boshqa amalga (masalan, Bosh ekran) biriktirilgan yoki fokusda sozlanadigan element
     * bo'lmasa, ishora odatdagidek ishlaydi. Ikki barmoqli aylantirishga tegilmaydi: u ro'yxat va xabarnoma soyasini suradi.
     * Qaytaradi: ishora qiymatni o'zgartirishga sarflandi.
     */
    private fun adjustByGesture(gestureId: Int, action: GestureAction): Boolean {
        val up: Boolean
        val vertical: Boolean
        when (gestureId) {
            AccessibilityService.GESTURE_SWIPE_UP -> {
                up = true
                vertical = true
            }
            AccessibilityService.GESTURE_SWIPE_DOWN -> {
                up = false
                vertical = true
            }
            AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT -> {
                up = true
                vertical = false
            }
            AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT -> {
                up = false
                vertical = false
            }
            else -> return false
        }
        val valueGesture = if (vertical) {
            when (action) {
                GestureAction.NEXT, GestureAction.PREV -> {
                    // Tezlik yoki Tafsilotlar rejimi unutib qoldirilsa, elementlar rejimiga qaytariladi (moveFocus bilan bir xil)
                    if (navigator.adjusterExpired()) navigator.select(Navigator.Mode.ELEMENTS)
                    navigator.mode == Navigator.Mode.ELEMENTS
                }
                GestureAction.MODE_NEXT, GestureAction.MODE_PREV -> Prefs.gestureProfile(this) == "voiceover"
                else -> false
            }
        } else {
            action == (if (up) GestureAction.SCROLL_FWD else GestureAction.SCROLL_BACK)
        }
        if (!valueGesture) return false
        // Tez tekshiruv (tizimga murojaat qilmasdan): fokusda sozlanadigan element bo'lmasa, ishora darhol odatdagidek ishlaydi
        val cached = cachedFocus()
        if (cached != null && !Adjustable.maybeAdjustable(cached)) return false
        val cur = currentFocus() ?: return false
        return adjustNode(cur, up, false)
    }

    private fun perform(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> Unit

            // Fokus harakati
            GestureAction.NEXT -> moveFocus(true)
            GestureAction.PREV -> moveFocus(false)
            GestureAction.NEXT_ITEM -> moveFocus(true, true)
            GestureAction.PREV_ITEM -> moveFocus(false, true)
            GestureAction.MODE_NEXT -> announceMode(navigator.cycle(1))
            GestureAction.MODE_PREV -> announceMode(navigator.cycle(-1))
            GestureAction.FIRST_ITEM -> jumpFirst()
            GestureAction.JUMP_LAST -> jumpLast()
            GestureAction.NEXT_WINDOW -> jumpWindow(true)
            GestureAction.PREV_WINDOW -> jumpWindow(false)

            // Faollashtirish va qiymat
            GestureAction.ACTIVATE -> activateFocused(false)
            GestureAction.LONG_PRESS -> activateFocused(true)
            GestureAction.VALUE_UP -> adjustValue(true)
            GestureAction.VALUE_DOWN -> adjustValue(false)

            // Tizim amallari
            GestureAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            GestureAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            GestureAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            GestureAction.OPEN_APPS -> openAppsMenu()
            GestureAction.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            GestureAction.QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            GestureAction.LOCK_SCREEN -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            GestureAction.SCREENSHOT -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            GestureAction.POWER_DIALOG -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            GestureAction.SPLIT_SCREEN -> performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            GestureAction.MEDIA_PLAY_PAUSE -> mediaPlayPause()

            // Aylantirish va ekranni surish
            GestureAction.SCROLL_FWD -> scrollScreen(ScrollDir.DOWN)
            GestureAction.SCROLL_BACK -> scrollScreen(ScrollDir.UP)
            GestureAction.SCROLL_LEFT -> scrollScreen(ScrollDir.LEFT)
            GestureAction.SCROLL_RIGHT -> scrollScreen(ScrollDir.RIGHT)
            GestureAction.SCREEN_SWIPE_UP -> screenSwipe(ScrollDir.DOWN)
            GestureAction.SCREEN_SWIPE_DOWN -> screenSwipe(ScrollDir.UP)
            GestureAction.SCREEN_SWIPE_LEFT -> screenSwipe(ScrollDir.RIGHT)
            GestureAction.SCREEN_SWIPE_RIGHT -> screenSwipe(ScrollDir.LEFT)

            // Matn bilan ishlash
            GestureAction.SELECT_ALL -> selectAllText()
            GestureAction.COPY_TEXT -> copyText()
            GestureAction.CUT_TEXT -> editAction(AccessibilityNodeInfo.ACTION_CUT, R.string.msg_cut_done)
            GestureAction.PASTE_TEXT -> editAction(AccessibilityNodeInfo.ACTION_PASTE, R.string.msg_pasted)

            // Nutq
            GestureAction.STOP_SPEECH -> speech.stop()
            GestureAction.SPEED_UP -> adjustRate(true)
            GestureAction.SPEED_DOWN -> adjustRate(false)
            GestureAction.READ_FROM_TOP -> readFrom(false)
            GestureAction.READ_FROM_NEXT -> readFrom(true)
            GestureAction.REPEAT_LAST ->
                if (lastRaw.isNotEmpty()) speak(lastRaw, true) else speak(getString(R.string.msg_nothing_spoken), true)
            GestureAction.COPY_LAST -> copyLast()
            GestureAction.SAVE_AUDIO -> saveLastAudio()
            GestureAction.STATUS -> speakStatus()
            GestureAction.CURTAIN -> toggleCurtain()

            // Menyular va qidiruv
            GestureAction.GLOBAL_MENU -> showGlobalMenu()
            GestureAction.ACTIONS_MENU -> showActionsMenu(null)
            GestureAction.SCREEN_SEARCH -> openScreen(ScreenSearchActivity::class.java)

            // Til, tarjima, sun'iy intellekt
            GestureAction.TRANSLATE -> translateFocused()
            GestureAction.AUTO_TRANSLATE -> toggleAutoTranslate()
            GestureAction.DESCRIBE_SCREEN -> describeScreen()
            GestureAction.DESCRIBE_IMAGE -> describeImage()
            GestureAction.LINKS_MENU -> showLinksMenu()
            GestureAction.EXPLAIN_BRIDGE -> explainLastBridge()
            GestureAction.ADD_WORD -> addWordToDictionary()
            GestureAction.ADD_LABEL -> addLabelForFocused()

            // Qo'ng'iroq va maxfiylik
            GestureAction.CALL_ANSWER -> callAction(CallActions.Kind.ANSWER)
            GestureAction.CALL_END -> callAction(CallActions.Kind.END)
            GestureAction.CALL_SPEAKER -> callAction(CallActions.Kind.SPEAKER)
            GestureAction.READ_PRIVATE -> readPrivate()

            // Xizmat
            GestureAction.MARK_PROBLEM -> markProblem()
            GestureAction.PRACTICE -> openScreen(TutorialActivity::class.java)
            GestureAction.OPEN_SETTINGS -> openScreen(SettingsActivity::class.java)
            GestureAction.TTS_SETTINGS -> openTtsSettings()
            GestureAction.SYSTEM_ACTIONS -> showSystemActionsMenu()
            GestureAction.DETAILS_MENU -> showDetailsMenu()
            GestureAction.SETTINGS_MENU -> showSettingsLinksMenu()
            GestureAction.OPEN_VOICES -> openScreen(VoicesActivity::class.java)
            GestureAction.OPEN_GESTURES -> openScreen(GesturesActivity::class.java)
            GestureAction.OPEN_DETAILS -> openScreen(DetailsActivity::class.java)
            GestureAction.OPEN_AI -> openScreen(AiSettingsActivity::class.java)
            GestureAction.OPEN_BACKUP -> openScreen(BackupActivity::class.java)
            GestureAction.OPEN_HEALTH -> openScreen(HealthActivity::class.java)
            GestureAction.OPEN_LOG -> openScreen(LogActivity::class.java)
            GestureAction.OPEN_MENU_EDITOR -> openScreen(MenuActivity::class.java)
            GestureAction.SPEED_MODE -> cycleSpeed()
            GestureAction.SUSPEND -> setSuspended(!userSuspended)

            // Matn bo'ylab yurish va harflab o'qish (o'qish boshqaruvini almashtirmasdan)
            GestureAction.NEXT_CHAR -> moveInText(TextGran.CHAR, true)
            GestureAction.PREV_CHAR -> moveInText(TextGran.CHAR, false)
            GestureAction.NEXT_WORD -> moveInText(TextGran.WORD, true)
            GestureAction.PREV_WORD -> moveInText(TextGran.WORD, false)
            GestureAction.NEXT_LINE -> moveInText(TextGran.LINE, true)
            GestureAction.PREV_LINE -> moveInText(TextGran.LINE, false)
            GestureAction.NEXT_PARA -> moveInText(TextGran.PARAGRAPH, true)
            GestureAction.PREV_PARA -> moveInText(TextGran.PARAGRAPH, false)
            GestureAction.SPELL_FOCUSED -> spellFocused()
            GestureAction.SPELL_LAST -> spellLast()
            GestureAction.READING_MENU -> showReadingMenu()
            GestureAction.PITCH_UP -> adjustPitch(true)
            GestureAction.PITCH_DOWN -> adjustPitch(false)
            else -> Unit
        }
    }

    private fun announceMode(mode: Navigator.Mode) = speak(getString(mode.labelRes), true)

    // ------------------------------------------------------------------ faollashtirish, qiymat, oynalar

    /** Elementni yoki eng yaqin bosiladigan ota-onasini bosadi (uzoq bosish ham mumkin). */
    private fun clickNode(node: AccessibilityNodeInfo, longPress: Boolean): Boolean {
        val action = if (longPress) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
        // Eskirgan (ro'yxat qayta chizgan) tugun: avval yangilab olamiz, shunda amal hozirgi holatga qo'llanadi
        try {
            node.refresh()
        } catch (ex: Throwable) {
            swallowed("ReaderService.clickNode", ex)
        }
        var x: AccessibilityNodeInfo? = node
        var guard = 0
        while (x != null && guard++ < 8) {
            // Ro'yxatning o'zi va undan yuqoridagi konteynerlar fokusdagi elementning bosiladigan qismi emas: ular bosilsa,
            // amal "bajarildi" deb qaytadi-yu, kerakli element ochilmaydi. Shunda element markaziga tegiladi (tapCenter).
            if (guard > 1 && isListBoundary(x)) break
            if (supportsClick(x, longPress) && x.performAction(action)) return true
            x = try {
                x.parent
            } catch (_: Throwable) {
                null
            }
        }
        return false
    }

    /**
     * Bosish amalini qo'llaydimi: bayroq (isClickable) yoki amallar ro'yxatida bor. Maxsus (virtual) elementlar ko'pincha
     * faqat amallar ro'yxatida ko'rsatadi, bayroqni qo'ymaydi (TalkBack ham shunday tekshiradi). Ilgari faqat bayroqqa
     * qaralgani uchun bunday elementlar bosilmay, ikki marta bosish "ishlamay qolardi".
     */
    private fun supportsClick(n: AccessibilityNodeInfo, longPress: Boolean): Boolean {
        val flagged = if (longPress) n.isLongClickable else n.isClickable
        if (flagged) return true
        val id = if (longPress) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
        return try {
            n.actionList.any { it.id == id }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Bir barmoqli ikki marta bosish va "ikki marta bosib ushlab turish" (Oʻzbegim bajaradigan rejim). Tartib:
     * 1) navigatsiya paneli tugmasi (Orqaga, Bosh ekran, So'nggilar, Maxsus imkoniyatlar) bo'lsa, tizim amalining o'zi;
     * 2) elementni yoki bosiladigan ota-onasini (ro'yxat elementigacha) bosish;
     * 3) oxirgi chora: element markaziga HAQIQIY tegish (SystemSurfaces.injectTap). Android tizimi ham, amal topilmasa,
     *    fokusdagi elementning markaziga tegadi; Telegram xabarlari kabi maxsus elementlar aynan shunday ochiladi.
     * Ilgari 3-bosqich teginib o'rganish yoqiq paytda oddiy tegish yuborardi: tizim uni bosish emas, teginib o'rganish
     * deb hisoblab, elementni faqat qayta o'qirdi, shuning uchun bu rejimda bosish deyarli ishlamasdi.
     * Qaysi yo'l bilan bajarilgani (matnsiz) jurnalga yoziladi (GESTURE_PATH, what=dt_...).
     */
    private fun activateByDoubleTap(longPress: Boolean) {
        val node = activationTarget()
        if (node == null) {
            logActivation("no_focus", null, longPress)
            speak(getString(R.string.msg_no_focus), true)
            return
        }
        if (!longPress) {
            val global = TouchActions.navigationBarAction(node)
            if (global != null && performGlobalAction(global)) {
                logActivation("global", node, false)
                return
            }
        }
        val how: String? = when {
            clickNode(node, longPress) -> "click"
            tapByTouch(node, longPress) -> "touch"
            else -> null
        }
        if (how != null) {
            logActivation(how, node, longPress)
            return
        }
        logActivation("failed", node, longPress)
        logClickFailed(node, longPress)
        speak(getString(R.string.msg_action_failed), true)
    }

    /** Ichida elementlari bor ro'yxat yoki jadval konteyneri: u bosiladigan element emas, uning elementlari bor. */
    private fun isListContainer(n: AccessibilityNodeInfo): Boolean = try {
        n.childCount > 0 && isListBoundary(n)
    } catch (_: Throwable) {
        false
    }

    /**
     * Ikki marta bosish nishoni: foydalanuvchi eshitgan joriy element (currentFocus). Fokus ro'yxatning o'ziga tushgan
     * bo'lsa (tizim yoki ilova shunday qilgan bo'lishi mumkin) yoki umuman yo'q bo'lsa, foydalanuvchi oxirgi teginib
     * o'rgangan element olinadi. Ikkalasi ham bo'lmasa, mavjud fokus (ro'yxatning o'zi bo'lsa ham) qaytariladi.
     */
    private fun activationTarget(): AccessibilityNodeInfo? {
        val cur = currentFocus()
        if (cur != null && !isListContainer(cur)) return cur
        val hovered = lastHoverUnit
        if (hovered != null) {
            val usable = try {
                hovered.refresh() && hovered.isVisibleToUser && !isListContainer(hovered)
            } catch (_: Throwable) {
                false
            }
            if (usable) return hovered
        }
        return cur
    }

    /**
     * Element markaziga haqiqiy tegish (teginib o'rganish qisqa muddatga o'chiriladi). Tegish asinxron bajariladi: u
     * tugagach (teginib o'rganish qayta yoqilgach) yangi ekranda fokus bo'lmasa, birinchi elementga o'tiladi.
     */
    private fun tapByTouch(node: AccessibilityNodeInfo, longPress: Boolean): Boolean {
        val p = TouchActions.tapPoint(node) ?: return false
        val started = if (::surfaces.isInitialized) {
            surfaces.injectTap(p.x, p.y, longPress)
        } else {
            TouchActions.tapCenter(this, node, longPress)
        }
        if (!started) return false
        main.postDelayed({
            if (suspended || !::speech.isInitialized || listening) return@postDelayed
            try {
                maybeInitialFocus(rootInActiveWindow)
            } catch (ex: Throwable) {
                swallowed("ReaderService.tapByTouch", ex)
            }
        }, if (longPress) TAP_REFOCUS_LONG_MS else TAP_REFOCUS_MS)
        return true
    }

    /** Ikki marta bosish qaysi yo'l bilan bajarilgani yoki nega bajarilmagani: matnsiz (sinf, ilova) jurnalga yoziladi. */
    private fun logActivation(how: String, node: AccessibilityNodeInfo?, longPress: Boolean) {
        try {
            val m = HashMap<String, Any>()
            m["what"] = "dt_$how"
            m["long"] = longPress
            if (node != null) {
                m["cls"] = node.className?.toString()?.substringAfterLast('.') ?: ""
                m["pkg"] = node.packageName?.toString() ?: ""
                m["clk"] = node.isClickable
                m["list"] = isListBoundary(node)
            }
            Diag.log.log(ErrorCode.GESTURE_PATH, m)
        } catch (ex: Throwable) {
            swallowed("ReaderService.logActivation", ex)
        }
    }

    /** Ikki marta bosishni kim bajarishini sozlamaga moslaydi (Tafsilotlar > Bosish va navigatsiya). */
    private fun applyDoubleTapFlag() {
        try {
            val info = serviceInfo ?: return
            val want = Prefs.doubleTapMode(this) == "service"
            val flag = AccessibilityServiceInfo.FLAG_SERVICE_HANDLES_DOUBLE_TAP
            val has = (info.flags and flag) != 0
            if (want == has) return
            info.flags = if (want) info.flags or flag else info.flags and flag.inv()
            serviceInfo = info
        } catch (ex: Throwable) {
            swallowed("ReaderService.applyDoubleTapFlag", ex)
        }
    }

    /** Tafsilotlardagi bosish va navigatsiya sozlamalari o'zgarganda (va xizmat ulanganda) qo'llanadi. */
    fun refreshInputPrefs() {
        navigator.skipUnlabeled = Prefs.skipUnlabeled(this)
        applyDoubleTapFlag()
    }

    private fun activateFocused(longPress: Boolean) {
        val node = currentFocus()
        if (node == null) {
            speak(getString(R.string.msg_no_focus), true)
            return
        }
        if (!clickNode(node, longPress)) {
            logClickFailed(node, longPress)
            speak(getString(R.string.msg_action_failed), true)
        }
    }

    /** Slayder, tanlagich va shunga o'xshash elementlar qiymatini bir pog'ona oshiradi yoki kamaytiradi. */
    /** Ishoraga biriktirilgan "Qiymatni oshirish/kamaytirish" amali: fokusdagi slayder yoki tanlagich (yoki uning ota-onasi). */
    private fun adjustValue(up: Boolean) {
        val start = currentFocus()
        if (start == null) {
            speak(getString(R.string.msg_no_focus), true)
            return
        }
        if (!adjustNode(start, up, true)) speak(getString(R.string.msg_action_failed), true)
    }

    /**
     * start (yoki uning sozlanadigan ota-onasi) slayder yoki tanlagich bo'lsa, qiymatini bir pog'ona o'zgartirib, yangi
     * qiymatni aytadi (TalkBack kabi: tizimning o'z qadami, bo'lmasa ACTION_SET_PROGRESS). Chekkaga yetilsa, chekka
     * ovozi chalinib, joriy qiymat takrorlanadi. Qaytaradi: sozlanadigan element topildi; false bo'lsa, ishora oddiy
     * yurish sifatida davom etadi.
     */
    private fun adjustNode(start: AccessibilityNodeInfo, up: Boolean, explicit: Boolean): Boolean {
        val target = Adjustable.find(start, explicit) ?: return false
        try {
            target.refresh()
        } catch (ex: Throwable) {
            swallowed("ReaderService.adjustNode", ex)
        }
        val before = rangeSpoken(target)
        val result = try {
            Adjustable.step(target, up)
        } catch (t: Throwable) {
            Diag.log.log(ErrorCode.FOCUS_NODE_FAILED, mapOf("exc" to t.javaClass.simpleName, "adjust" to up))
            Adjustable.Step.FAILED
        }
        val seq = ++adjustSeq
        when (result) {
            Adjustable.Step.CHANGED -> scheduleRangeCheck(target, before, seq, 0)
            Adjustable.Step.EDGE -> {
                Earcons.play(this, Earcons.Kind.EDGE)
                announceValue(target, true)
            }
            Adjustable.Step.FAILED -> speak(getString(R.string.msg_action_failed), true)
        }
        return true
    }

    /** Qadamdan keyin yangi qiymat paydo bo'lishini kutadi (ba'zi ilovalar kechroq yangilaydi) va aytadi; faqat oxirgi qadam uchun. */
    private fun scheduleRangeCheck(target: AccessibilityNodeInfo, before: String, seq: Int, attempt: Int) {
        main.postDelayed({
            if (seq != adjustSeq || suspended || !::speech.isInitialized || !::describer.isInitialized) return@postDelayed
            try {
                target.refresh()
                if (rangeSpoken(target) != before || attempt >= 2) {
                    announceValue(target, true)
                } else {
                    scheduleRangeCheck(target, before, seq, attempt + 1)
                }
            } catch (ex: Throwable) {
                swallowed("ReaderService.scheduleRangeCheck", ex)
            }
        }, if (attempt == 0) 130L else 220L)
    }

    /** Sozlanadigan elementning aytiladigan qiymati: slayderda holat (foiz), tanlagichda uning ichidagi matn. */
    private fun rangeSpoken(n: AccessibilityNodeInfo): String {
        try {
            if (n.rangeInfo != null) {
                val st = describer.stateOf(n).orEmpty()
                if (st.isNotBlank()) return st
            }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                if (c.className?.toString()?.endsWith("EditText") == true) {
                    val t = c.text?.toString()?.trim().orEmpty()
                    if (t.isNotEmpty()) return t
                }
            }
            return describer.labelOf(n)
        } catch (_: Throwable) {
            return ""
        }
    }

    /**
     * Yangi qiymatni aytadi. force=false: o'sha qiymat yaqinda aytilgan bo'lsa takrorlanmaydi (o'z qadamimiz natijasi
     * bilan tizim hodisasi qo'shilib ketmasin).
     */
    private fun announceValue(n: AccessibilityNodeInfo, force: Boolean) {
        val text = rangeSpoken(n)
        if (text.isBlank()) return
        val now = SystemClock.uptimeMillis()
        if (!force && text == rangeText && now - rangeAt < RANGE_DEDUP_MS) return
        rangeText = text
        rangeAt = now
        speak(text, true)
    }

    /** Barmoq ekranga tegib turibdimi (slayderni sudrab o'zgartirish): tegish boshlangan, tugamagan va eskirmagan bo'lsa. */
    private fun touchActive(): Boolean = touching && SystemClock.uptimeMillis() - touchStartedAt < TOUCH_MAX_MS

    /** Fokusdagi elementning eslab qolingan nusxasi: tizimga murojaat qilmaydi (har surishda currentFocus() chaqirilmasligi uchun). */
    private fun cachedFocus(): AccessibilityNodeInfo? {
        val now = SystemClock.uptimeMillis()
        val soft = softFocus
        if (soft != null && now - softFocusAt < 120_000L) return soft
        val last = lastFocusedSource
        if (last != null && now - lastFocusedAt < 120_000L) return last
        return null
    }

    /** Element amalini bajaradi; slayder va tanlagich qiymatini o'zgartirishda yangi qiymat ham aytiladi. */
    private fun performNodeAction(node: AccessibilityNodeInfo, id: Int): Boolean = when (id) {
        ACTION_VALUE_UP -> adjustNode(node, true, true)
        ACTION_VALUE_DOWN -> adjustNode(node, false, true)
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ->
            if (Adjustable.isAdjustable(node)) adjustNode(node, true, false) else node.performAction(id)
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD ->
            if (Adjustable.isAdjustable(node)) adjustNode(node, false, false) else node.performAction(id)
        else -> node.performAction(id)
    }

    /** Oynadagi birinchi elementga o'tadi (TalkBack: "Ekrandagi birinchi elementga fokus"). */
    private fun jumpFirst() {
        val root = rootInActiveWindow
        if (root == null) {
            speak(getString(R.string.msg_no_window), true)
            return
        }
        val first = navigator.firstUnit(root)
        if (first != null) focus(first)
    }

    /** Keyingi yoki oldingi oynaning birinchi elementiga o'tadi (holat paneli, ilova, klaviatura, pastki tugmalar). */
    private fun jumpWindow(forward: Boolean) {
        val list = orderedWindows()
        if (list.size < 2) {
            speak(getString(R.string.msg_one_window), true)
            return
        }
        var curId: Int? = null
        try {
            curId = currentFocus()?.window?.id
        } catch (ex: Throwable) {
            swallowed("ReaderService.jumpWindow", ex)
        }
        val idx = list.indexOfFirst { it.id == curId }
        var i = if (idx >= 0) idx else if (forward) -1 else 0
        for (k in list.indices) {
            i = (i + (if (forward) 1 else -1) + list.size) % list.size
            var root: AccessibilityNodeInfo? = null
            try {
                root = list[i].root
            } catch (ex: Throwable) {
                swallowed("ReaderService.jumpWindow", ex)
            }
            if (root == null) continue
            val target = navigator.firstUnit(root) ?: continue
            if (focus(target)) {
                Earcons.play(this, Earcons.Kind.WINDOW)
                return
            }
        }
        speak(getString(R.string.msg_no_window), true)
    }

    // ------------------------------------------------------------------ nutq tezligi va media

    /** Nutq tezligini sozlamalardagi qadam (1, 2, 5, 10 yoki 20 foiz) bilan o'zgartiradi. */
    private fun adjustRate(up: Boolean) {
        val step = Prefs.rateStep(this)
        val delta = if (up) step else -step
        val next = (Prefs.rate(this) + delta).coerceIn(Prefs.RATE_RANGE.first, Prefs.RATE_RANGE.last)
        prefs.edit().putInt(Prefs.RATE, next).apply()
        speak(getString(R.string.msg_rate_set, next), true)
    }

    /** Ovoz tonini xuddi shu qadam bilan o'zgartiradi. */
    private fun adjustPitch(up: Boolean) {
        val step = Prefs.rateStep(this)
        val delta = if (up) step else -step
        val next = (Prefs.pitch(this) + delta).coerceIn(Prefs.PITCH_RANGE.first, Prefs.PITCH_RANGE.last)
        prefs.edit().putInt(Prefs.PITCH, next).apply()
        speak(getString(R.string.msg_pitch_set, next), true)
    }

    private fun mediaPlayPause() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        } catch (_: Throwable) {
            speak(getString(R.string.msg_action_failed), true)
        }
    }

    // ------------------------------------------------------------------ matn tahrirlash

    /** Tahrirlanadigan maydon: avval fokusdagisi, bo'lmasa klaviatura kiritish fokusidagisi. */
    private fun editTarget(): AccessibilityNodeInfo? {
        val f = currentFocus()
        if (f != null && f.isEditable) return f
        val input = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        return if (input != null && input.isEditable) input else null
    }

    private fun selectAllText() {
        val n = editTarget()
        if (n == null) {
            speak(getString(R.string.msg_no_edit), true)
            return
        }
        val args = Bundle()
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, n.text?.length ?: 0)
        val ok = n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
        speak(getString(if (ok) R.string.msg_selected_all else R.string.msg_action_failed), true)
    }

    private fun editAction(action: Int, doneRes: Int) {
        val n = editTarget()
        if (n == null) {
            speak(getString(R.string.msg_no_edit), true)
            return
        }
        val ok = n.performAction(action)
        speak(getString(if (ok) doneRes else R.string.msg_action_failed), true)
    }

    /**
     * Tanlangan matnni, tanlov bo'lmasa fokusdagi elementning matnini nusxalaydi (TalkBack: uch barmoq bilan ikki marta
     * bosish). Parol va maxfiy ilovalarda nusxalanmaydi.
     */
    private fun copyText() {
        val field = editTarget()
        if (field != null && !field.isPassword && field.textSelectionStart >= 0 &&
            field.textSelectionEnd > field.textSelectionStart
        ) {
            val ok = field.performAction(AccessibilityNodeInfo.ACTION_COPY)
            speak(getString(if (ok) R.string.msg_copied else R.string.msg_action_failed), true)
            return
        }
        val node = currentFocus()
        if (node == null) {
            speak(getString(R.string.msg_no_focus), true)
            return
        }
        if (node.isPassword || isSensitiveContext()) {
            speak(getString(R.string.msg_ai_sensitive), true)
            return
        }
        val text = describer.labelOf(node, false)
        if (text.isBlank()) {
            speak(getString(R.string.msg_nothing_spoken), true)
            return
        }
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("O'zbegim", text))
            speak(getString(R.string.msg_copied), true)
        } catch (_: Throwable) {
            speak(getString(R.string.msg_action_failed), true)
        }
    }

    // ------------------------------------------------------------------ menyular

    /** onLongPick: qator uzoq bosilganda (masalan, bandni ko'chirish uchun) qator raqami bilan chaqiriladi. */
    private fun openMenu(
        title: String,
        labels: List<String>,
        returnTo: AccessibilityNodeInfo?,
        onLongPick: ((Int) -> Unit)? = null,
        onPick: (Int) -> Unit,
    ) {
        menuReturn = returnTo
        val shown = menu.show(
            title, labels, onPick,
            onCancel = { main.postDelayed({ restoreMenuFocus() }, 250L) },
            onLongPick = onLongPick,
        )
        if (!shown) {
            speak(getString(R.string.msg_menu_failed), true)
            return
        }
        // Oyna paydo bo'lgach, fokusni menyu ichiga (sarlavhaga) o'tkazamiz; ba'zan oyna kechroq chiqadi
        main.postDelayed({ focusMenuWindow(0) }, 400L)
    }

    private fun focusMenuWindow(attempt: Int) {
        if (!menu.isShowing) return
        try {
            for (w in windows) {
                if (w.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
                val root = w.root ?: continue
                if (root.packageName?.toString() != packageName || root.childCount == 0) continue
                val first = navigator.firstUnit(root) ?: continue
                if (focus(first)) return
            }
        } catch (ex: Throwable) {
            swallowed("ReaderService.focusMenuWindow", ex)
        }
        if (attempt < 3) main.postDelayed({ focusMenuWindow(attempt + 1) }, 300L)
    }

    private fun restoreMenuFocus() {
        val n = menuReturn ?: return
        try {
            n.refresh()
            focus(n)
        } catch (ex: Throwable) {
            swallowed("ReaderService.restoreMenuFocus", ex)
        }
    }

    /**
     * O'zbegim menyusi: hamma amal bir joyda (TalkBack menyusi kabi). Tartibni shu yerning o'zida o'zgartirish mumkin:
     * oxirgi band ("Menyu tartibini o'zgartirish") yoki istalgan bandni uzoq bosish.
     */
    private fun showGlobalMenu() {
        // Foydalanuvchi tanlagan bandlar (o'zi belgilagan tartibda); tanlanmagan bo'lsa, standart ro'yxat
        val actions = GlobalMenu.effective(this)
        val origin = currentFocus()
        val labels = ArrayList<String>(actions.size + 1)
        for (a in actions) labels.add(menuLabel(a))
        labels.add(getString(R.string.menu_reorder_item))
        openMenu(
            getString(R.string.menu_global_title), labels, origin,
            onLongPick = { which ->
                // Bandni uzoq bosish: shu bandni ko'chirish (yangi o'rin tanlanadi)
                if (which in actions.indices) {
                    main.postDelayed({ showMenuReorderDrop(origin, which, null) }, 200L)
                }
            },
        ) { which ->
            if (which == actions.size) {
                // Oxirgi band: menyu tartibini o'zgartirish
                main.postDelayed({ showMenuReorderPick(origin, null) }, 200L)
            } else {
                val chosen = actions.getOrNull(which)
                if (chosen != null) {
                    main.postDelayed({
                        try {
                            restoreMenuFocus()
                            if (chosen == GestureAction.ACTIONS_MENU) showActionsMenu(origin) else perform(chosen)
                        } catch (t: Throwable) {
                            Diag.log.log(ErrorCode.FOCUS_NODE_FAILED, mapOf("exc" to t.javaClass.simpleName, "menu" to chosen.name))
                        }
                    }, 300L)
                }
            }
        }
    }

    // ------------------------------------------------------------------ menyu tartibini menyuning o'zida o'zgartirish

    /**
     * Ko'chiriladigan bandni tanlash. status (oldingi amal natijasi) sarlavhaga qo'shiladi: fokus sarlavhaga tushganda
     * shu aytiladi. Har bir o'zgarish darhol saqlanadi (Sozlamalar > Ishoralar > O'zbegim menyusini sozlash bilan bir xil joyga).
     */
    private fun showMenuReorderPick(origin: AccessibilityNodeInfo?, status: String?) {
        val items = GlobalMenu.effective(this)
        val labels = ArrayList<String>(items.size + 1)
        for ((i, a) in items.withIndex()) labels.add(getString(R.string.menu_reorder_row, i + 1, a.displayName(this)))
        labels.add(getString(R.string.menu_reorder_add))
        val base = getString(R.string.menu_reorder_pick_title)
        val title = if (status.isNullOrEmpty()) base else "$status. $base"
        openMenu(title, labels, origin) { which ->
            main.postDelayed({
                try {
                    if (which == items.size) showMenuAddItem(origin) else showMenuReorderDrop(origin, which, null)
                } catch (t: Throwable) {
                    Diag.log.log(ErrorCode.FOCUS_NODE_FAILED, mapOf("exc" to t.javaClass.simpleName, "menu" to "reorder_pick"))
                }
            }, 200L)
        }
    }

    /** Tanlangan bandning yangi o'rnini tanlash; birinchi qator: bandni menyudan olib tashlash. from: bandning hozirgi o'rni (0 dan). */
    private fun showMenuReorderDrop(origin: AccessibilityNodeInfo?, from: Int, status: String?) {
        val items = GlobalMenu.effective(this)
        val moving = items.getOrNull(from)
        if (moving == null) {
            showMenuReorderPick(origin, null)
            return
        }
        val name = moving.displayName(this)
        val labels = ArrayList<String>(items.size + 1)
        labels.add(getString(R.string.menu_reorder_remove))
        for ((i, a) in items.withIndex()) {
            val row = getString(R.string.menu_reorder_row, i + 1, a.displayName(this))
            labels.add(if (i == from) getString(R.string.menu_reorder_current, row) else row)
        }
        val base = getString(R.string.menu_reorder_drop_title, name, from + 1)
        val title = if (status.isNullOrEmpty()) base else "$status. $base"
        openMenu(title, labels, origin) { which ->
            main.postDelayed({
                try {
                    val now = GlobalMenu.effective(this)
                    if (which == 0) {
                        val rest = now.filter { it != moving }
                        if (rest.isEmpty()) {
                            showMenuReorderPick(origin, getString(R.string.menu_reorder_last_item))
                        } else {
                            Prefs.saveMenuItems(this, rest)
                            showMenuReorderPick(origin, getString(R.string.menu_reorder_removed, name))
                        }
                    } else {
                        val to = which - 1
                        Prefs.saveMenuItems(this, GlobalMenu.moveItem(now, moving, to))
                        showMenuReorderPick(origin, getString(R.string.menu_reorder_moved, name, to + 1))
                    }
                } catch (t: Throwable) {
                    Diag.log.log(ErrorCode.FOCUS_NODE_FAILED, mapOf("exc" to t.javaClass.simpleName, "menu" to "reorder_drop"))
                }
            }, 200L)
        }
    }

    /** Menyuda yo'q bandlardan birini menyuning oxiriga qo'shish. */
    private fun showMenuAddItem(origin: AccessibilityNodeInfo?) {
        val items = GlobalMenu.effective(this)
        val rest = GlobalMenu.pool.filter { it !in items }
        if (rest.isEmpty()) {
            showMenuReorderPick(origin, getString(R.string.menu_add_none))
            return
        }
        openMenu(getString(R.string.menu_add_title), rest.map { it.displayName(this) }, origin) { which ->
            main.postDelayed({
                try {
                    val a = rest.getOrNull(which)
                    if (a == null) {
                        showMenuReorderPick(origin, null)
                    } else {
                        val now = GlobalMenu.effective(this)
                        Prefs.saveMenuItems(this, now + a)
                        showMenuReorderPick(origin, getString(R.string.menu_add_done, a.displayName(this), now.size + 1))
                    }
                } catch (t: Throwable) {
                    Diag.log.log(ErrorCode.FOCUS_NODE_FAILED, mapOf("exc" to t.javaClass.simpleName, "menu" to "reorder_add"))
                }
            }, 200L)
        }
    }

    /** Fokusdagi elementning amallari (maxsus amallar ham: javob berish, o'chirish, ulashish va h.k.). */
    private fun showActionsMenu(target: AccessibilityNodeInfo?) {
        val node = target ?: currentFocus()
        if (node == null) {
            speak(getString(R.string.msg_no_focus), true)
            return
        }
        val entries = actionsOf(node)
        if (entries.isEmpty()) {
            speak(getString(R.string.msg_no_actions), true)
            return
        }
        openMenu(getString(R.string.menu_actions_title), entries.map { it.first }, node) { which ->
            val entry = entries.getOrNull(which)
            if (entry != null) {
                main.postDelayed({
                    try {
                        node.refresh()
                        if (!performNodeAction(node, entry.second)) speak(getString(R.string.msg_action_failed), true)
                    } catch (t: Throwable) {
                        Diag.log.log(ErrorCode.FOCUS_NODE_FAILED, mapOf("exc" to t.javaClass.simpleName, "node_action" to entry.second))
                    }
                }, 300L)
            }
        }
    }

    private fun actionsOf(node: AccessibilityNodeInfo): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        val seen = HashSet<Int>()
        val slider = node.rangeInfo != null
        for (a in node.actionList) {
            val id = a.id
            if (!seen.add(id)) continue
            val custom = a.label?.toString()?.trim().orEmpty()
            val name = if (custom.isNotEmpty()) custom else standardActionName(id, slider)
            if (name != null) out.add(name to id)
        }
        // Slayder oldinga/orqaga aylantirishni bilmasa (faqat ACTION_SET_PROGRESS): qiymatni oshirish va kamaytirishni o'zimiz qo'shamiz
        if (slider && AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id in seen &&
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD !in seen && AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD !in seen
        ) {
            out.add(getString(R.string.act_value_up) to ACTION_VALUE_UP)
            out.add(getString(R.string.act_value_down) to ACTION_VALUE_DOWN)
        }
        return out
    }

    /** Standart amallarning nomi tizim tilida; ro'yxatda ko'rsatilmaydigan ichki amallar (fokus, tanlash va h.k.) uchun null. */
    private fun standardActionName(id: Int, slider: Boolean): String? = when (id) {
        AccessibilityNodeInfo.ACTION_CLICK -> getString(R.string.act_click)
        AccessibilityNodeInfo.ACTION_LONG_CLICK -> getString(R.string.act_long_click)
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> getString(if (slider) R.string.act_value_up else R.string.act_scroll_forward)
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> getString(if (slider) R.string.act_value_down else R.string.act_scroll_backward)
        AccessibilityNodeInfo.ACTION_EXPAND -> getString(R.string.act_expand)
        AccessibilityNodeInfo.ACTION_COLLAPSE -> getString(R.string.act_collapse)
        AccessibilityNodeInfo.ACTION_DISMISS -> getString(R.string.act_dismiss)
        AccessibilityNodeInfo.ACTION_COPY -> getString(R.string.act_copy)
        AccessibilityNodeInfo.ACTION_CUT -> getString(R.string.act_cut)
        AccessibilityNodeInfo.ACTION_PASTE -> getString(R.string.act_paste)
        AccessibilityNodeInfo.AccessibilityAction.ACTION_CONTEXT_CLICK.id -> getString(R.string.act_context_click)
        AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id -> getString(R.string.act_show_on_screen)
        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id -> getString(R.string.act_scroll_up)
        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id -> getString(R.string.act_scroll_down)
        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id -> getString(R.string.act_scroll_left)
        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id -> getString(R.string.act_scroll_right)
        else -> null
    }

    // ------------------------------------------------------------------ ekranda qidirish

    private fun openScreen(cls: Class<*>) {
        try {
            startActivity(Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
            speak(getString(R.string.msg_action_failed), true)
        }
    }

    /** Qidiruv oynasidan chaqiriladi: ekrandagi birinchi mos elementga (joriy fokusdan keyin, so'ng boshidan) o'tadi. */
    fun searchScreen(query: String) {
        val q = query.trim()
        if (q.isEmpty() || suspended || !::speech.isInitialized) return
        // Qidiruv oynasi yopilib, orqadagi ilova faollashishi uchun biroz kutamiz
        main.postDelayed({
            try {
                doSearch(q)
            } catch (t: Throwable) {
                Log.w(TAG, "search", t)
            }
        }, 500L)
    }

    private fun doSearch(q: String) {
        val all = ArrayList<AccessibilityNodeInfo>()
        for (w in orderedWindows()) {
            var root: AccessibilityNodeInfo? = null
            try {
                root = w.root
            } catch (ex: Throwable) {
                swallowed("ReaderService.doSearch", ex)
            }
            if (root == null || root.packageName?.toString() == packageName) continue
            all.addAll(navigator.collect(root))
        }
        if (all.isEmpty()) {
            speak(getString(R.string.msg_no_window), true)
            return
        }
        val cur = currentFocus()
        val start = if (cur == null) 0 else all.indexOfFirst { it == cur } + 1
        for (k in all.indices) {
            val n = all[(start + k) % all.size]
            if (describer.labelOf(n).contains(q, ignoreCase = true) && focus(n)) return
        }
        speak(getString(R.string.msg_search_not_found, q), true)
    }

    // ------------------------------------------------------------------ aylantirish va ekranni surish

    private enum class ScrollDir { UP, DOWN, LEFT, RIGHT }

    private enum class ScrollResult { NONE, DONE, EDGE }

    /** dir: tarkib qaysi tomonga ochiladi (DOWN: pastdagi tarkib ko'rinadi, ya'ni oldinga aylantirish). */
    private fun scrollScreen(dir: ScrollDir) {
        // Qulf ekranida pastga aylantirish = qulfni ochish uchun ekranni yuqoriga surish
        if (dir == ScrollDir.DOWN && isKeyguardLocked() && swipeScreen(dir)) return
        val root = rootInActiveWindow
        if (root == null) {
            if (!swipeScreen(dir)) speak(getString(R.string.msg_no_window), true)
            return
        }
        val pkg = root.packageName?.toString()
        val home = isLauncherPkg(pkg)
        val shade = pkg == "com.android.systemui"
        val result = scrollNodes(currentFocus(), root, dir, home)
        if (result == ScrollResult.DONE) return
        when {
            // Xabarnoma soyasi ochiq: pastga tortish = tezkor sozlamalar, tepaga surish = soyani yopish
            shade && dir == ScrollDir.UP -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            shade && dir == ScrollDir.DOWN -> closeShade()
            // Bosh ekran: pastga tortish = xabarnoma soyasi. Ilovalar menyusi ochiq bo'lsa, scrollNodes ro'yxatni
            // tepaga aylantirgan bo'ladi; ro'yxat tepasida bo'lsa ham soya tushadi (rasmiy tizim amali, barmoq surish emas)
            home && dir == ScrollDir.UP -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            // Bosh ekran: ikki barmoq bilan tepaga surish = ilovalar menyusi (rasmiy amal, so'ng tugma, oxirida barmoq
            // surish). Menyu allaqachon ochiq va ro'yxat oxirida bo'lsa, faqat chekka belgisi chalinadi
            home && dir == ScrollDir.DOWN ->
                if (hasBigVerticalList(root)) Earcons.play(this, Earcons.Kind.EDGE) else openAppsMenu()
            // Bosh ekran sahifalari: chapga va o'ngga (barmoq bilan surgandek)
            home -> if (!swipeScreen(dir)) Earcons.play(this, Earcons.Kind.EDGE)
            // Ilovada aylantiriladigan hech narsa yo'q: pastga tortish = xabarnoma soyasi
            result == ScrollResult.NONE && dir == ScrollDir.UP -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            result == ScrollResult.NONE -> if (!swipeScreen(dir)) Earcons.play(this, Earcons.Kind.EDGE)
            // Ro'yxatning boshida: birinchi marta chekka belgisi, 3 soniya ichida takrorlansa xabarnoma soyasi
            dir == ScrollDir.UP -> {
                val now = SystemClock.uptimeMillis()
                if (now - lastEdgeUpAt < 3000L) {
                    lastEdgeUpAt = 0L
                    performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                } else {
                    lastEdgeUpAt = now
                    Earcons.play(this, Earcons.Kind.EDGE)
                }
            }
            else -> Earcons.play(this, Earcons.Kind.EDGE)
        }
    }

    /** Bosh ekran (launcher) ilovasimi: tizimning standart bosh ekran ilovasi yoki ma'lum launcherlar. */
    private fun isLauncherPkg(pkg: String?): Boolean {
        if (pkg.isNullOrEmpty()) return false
        val now = SystemClock.uptimeMillis()
        if (homePkgAt == 0L || now - homePkgAt > 60_000L) {
            homePkgAt = now
            homePkg = try {
                val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                val found = packageManager.resolveActivity(i, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                found?.activityInfo?.packageName?.takeIf { it != "android" }
            } catch (_: Throwable) {
                null
            }
        }
        if (pkg == homePkg) return true
        return pkg == "com.sec.android.app.launcher" || pkg == "com.google.android.apps.nexuslauncher" ||
            pkg == "com.android.launcher3" || pkg == "com.android.launcher" || pkg == "com.miui.home" ||
            pkg.contains("launcher", ignoreCase = true)
    }

    /** Ekranning kamida 45 foizini egallagan, tepaga/pastga aylanadigan ro'yxat (ochiq ilovalar menyusi) bormi. */
    private fun hasBigVerticalList(root: AccessibilityNodeInfo): Boolean {
        val dm = resources.displayMetrics
        val screen = dm.widthPixels.toLong() * dm.heightPixels
        val up = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
        val down = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
        val r = Rect()
        for (n in collectScrollables(root)) {
            n.getBoundsInScreen(r)
            if (r.width().toLong() * r.height() < screen * 45L / 100L) continue
            val ids = n.actionList.map { it.id }
            if (ids.contains(up) || ids.contains(down)) return true
        }
        return false
    }

    private fun scrollNodes(
        cur: AccessibilityNodeInfo?,
        root: AccessibilityNodeInfo,
        dir: ScrollDir,
        home: Boolean,
    ): ScrollResult {
        var found = false
        var p: AccessibilityNodeInfo? = cur
        var guard = 0
        while (p != null && guard++ < 24) {
            if (p.isScrollable) {
                found = true
                if (tryScroll(p, dir, home)) return ScrollResult.DONE
            }
            p = p.parent
        }
        for (n in collectScrollables(root)) {
            found = true
            if (tryScroll(n, dir, home)) return ScrollResult.DONE
        }
        return if (found) ScrollResult.EDGE else ScrollResult.NONE
    }

    private fun collectScrollables(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>(4)
        var visited = 0
        fun walk(n: AccessibilityNodeInfo) {
            if (++visited > 1500 || out.size >= 12 || !n.isVisibleToUser) return
            if (n.isScrollable) out.add(n)
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                walk(c)
            }
        }
        walk(root)
        return out
    }

    private fun tryScroll(n: AccessibilityNodeInfo, dir: ScrollDir, home: Boolean = false): Boolean {
        val ids = n.actionList.map { it.id }
        val up = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
        val down = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
        val left = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id
        val right = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id
        val wanted = when (dir) {
            ScrollDir.UP -> up
            ScrollDir.DOWN -> down
            ScrollDir.LEFT -> left
            ScrollDir.RIGHT -> right
        }
        // Yo'nalishli amallarni biladigan element: kerakli yo'nalish yo'q bo'lsa, u shu tomonga aylanmaydi (chekka yoki boshqa o'q)
        if (ids.contains(up) || ids.contains(down) || ids.contains(left) || ids.contains(right)) {
            return ids.contains(wanted) && n.performAction(wanted)
        }
        // Eski ilovalar faqat "oldinga/orqaga" aylantirishni biladi: gorizontal faqat gorizontal ko'rinishlarda
        val vertical = dir == ScrollDir.UP || dir == ScrollDir.DOWN
        val cls = n.className?.toString().orEmpty()
        if (!vertical && !cls.contains("Horizontal") && !cls.contains("Pager")) return false
        // Bosh ekran sahifalari (Workspace) faqat yon tomonga aylanadi: vertikal surish sahifani almashtirib yubormasin,
        // ilovalar menyusini ochish uchun barmoq bilan surish ishlatiladi
        if (vertical && (home || isPager(n) || cls.contains("Workspace"))) return false
        val legacy = if (dir == ScrollDir.DOWN || dir == ScrollDir.RIGHT) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return ids.contains(legacy) && n.performAction(legacy)
    }

    private fun screenSwipe(dir: ScrollDir) {
        if (!swipeScreen(dir)) speak(getString(R.string.msg_action_failed), true)
    }

    /** Barmoq bilan ekranni surish (dispatchGesture). dir: tarkib yo'nalishi; barmoq teskari tomonga yuradi. */
    private fun swipeScreen(dir: ScrollDir): Boolean = when (dir) {
        ScrollDir.DOWN -> fingerSwipe(0.5f, 0.80f, 0.5f, 0.30f)
        ScrollDir.UP -> fingerSwipe(0.5f, 0.30f, 0.5f, 0.80f)
        ScrollDir.RIGHT -> fingerSwipe(0.85f, 0.5f, 0.15f, 0.5f)
        ScrollDir.LEFT -> fingerSwipe(0.15f, 0.5f, 0.85f, 0.5f)
    }

    /**
     * Ekranga barmoq bilan surish yuboradi (ekran o'lchamining ulushlarida). Teginib o'rganish yoqiq paytda tizim
     * yuborilgan surishni ham o'zi ushlab olmasligi uchun SystemSurfaces uni qisqa muddatga o'chirib turadi.
     */
    private fun fingerSwipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean = try {
        ::surfaces.isInitialized && surfaces.injectSwipe(x1, y1, x2, y2, 300L)
    } catch (_: Throwable) {
        false
    }

    private fun screenArea(): Long {
        val dm = resources.displayMetrics
        return dm.widthPixels.toLong() * dm.heightPixels.toLong()
    }

    /** Ilovalar menyusini ochadi: rasmiy tizim amali, so'ng «Ilovalar» tugmasi, oxirida barmoq surish (SystemSurfaces). */
    private fun openAppsMenu() {
        if (!::surfaces.isInitialized) return
        surfaces.openApps { ok ->
            if (!ok && !suspended && ::speech.isInitialized) speak(getString(R.string.msg_apps_failed), true)
        }
    }

    /** Xabarnoma soyasini yopadi: Android 12 va yuqorida rasmiy amal, aks holda «orqaga». */
    private fun closeShade() {
        if (Build.VERSION.SDK_INT >= 31 && performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)) return
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    // ------------------------------------------------------------------ aylantirish hodisasi (TYPE_VIEW_SCROLLED)

    private var scrollSource: AccessibilityNodeInfo? = null
    private var ownScrollUntil = 0L
    private val scrollTask = Runnable { handleScrolled() }

    /**
     * Ro'yxat aylantirilganda hodisa soniyasiga o'nlab marta keladi: manbani faqat boshida olamiz, har hodisada esa
     * kutish vaqtini suramiz, shunda ish aylantirish tinchigach bir marta bajariladi. Xizmatning o'z aylantirishlari
     * (edgeScroll, scrollThenFocus) fokusni o'zi boshqaradi, ularga aralashmaymiz.
     */
    private fun onScrolled(event: AccessibilityEvent) {
        if (SystemClock.uptimeMillis() < ownScrollUntil) return
        if (scrollSource == null) scrollSource = event.source
        main.removeCallbacks(scrollTask)
        main.postDelayed(scrollTask, SCROLL_SETTLE_MS)
    }

    /**
     * Aylantirish tugadi: indeks keshlari yangilanadi; fokusdagi element ekrandan chiqib ketgan bo'lsa (ishora yoki
     * barmoq bilan aylantirilgan), fokus ro'yxatning yangi ko'ringan birinchi nuqtasiga (odatda xabar matniga) o'tadi.
     * Fokus hali ko'rinib tursa (masalan, ilovaning o'zi yangi xabar qo'shdi), hech narsa o'zgarmaydi.
     */
    private fun handleScrolled() {
        val source = scrollSource
        scrollSource = null
        if (suspended || !::speech.isInitialized || source == null) return
        try {
            navigator.clearCaches()
            if (SystemClock.uptimeMillis() - lastTouchAt > SCROLL_FOCUS_WINDOW_MS) return
            // Maxsus (virtual) elementlarda refresh() ko'rinib turgan elementga ham false qaytarishi mumkin: ilgari shu sabab
            // fokus ro'yxat boshiga sakrab, elementlar o'tkazib yuborilardi. Endi haqiqiy joriy fokus tekshiriladi.
            val cur = currentFocus()
            val focusGone = cur == null || !cur.isVisibleToUser
            if (!focusGone) return
            if (!source.refresh()) return
            val first = navigator.collect(source).firstOrNull { navigator.matches(it, navigator.mode) } ?: return
            focus(first)
        } catch (ex: Throwable) {
            swallowed("ReaderService.handleScrolled", ex)
        }
    }

    private fun isKeyguardLocked(): Boolean = try {
        (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
    } catch (_: Throwable) {
        false
    }

    /**
     * Elementga a11y fokusini beradi. performAction element allaqachon fokusda bo'lsa yoki maxsus (virtual) element
     * bo'lsa ham false qaytarishi mumkin, shuning uchun holat qayta tekshiriladi: fokusdagi element muvaffaqiyatli
     * hisoblanadi va o'tkazib yuborilmaydi (ilgari shu sababli kanal postlari o'qilmasdan o'tib ketardi).
     * readIfSilent: element allaqachon fokusda bo'lsa, tizim hodisa yubormaydi, shuning uchun o'zimiz o'qib beramiz.
     */
    private fun focus(node: AccessibilityNodeInfo, readIfSilent: Boolean = true): Boolean {
        // Kutish rejimida fokusni boshqa ekran o'quvchi boshqaradi: kechikib kelgan eski vazifalar uni siljitmasin
        if (yielded) return false
        focusAlready = false
        focusSeq++
        var ok = node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        if (!ok) {
            // Tugun eskirgan bo'lishi mumkin (ro'yxat qayta chizilgan): yangilab, holatini tekshiramiz
            try {
                if (node.refresh()) {
                    if (node.isAccessibilityFocused) {
                        ok = true
                        focusAlready = true
                    } else {
                        ok = node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                    }
                }
            } catch (ex: Throwable) {
                swallowed("ReaderService.focus", ex)
            }
        }
        if (ok) {
            Earcons.play(this, Earcons.Kind.FOCUS)
            focusRequest = node
            focusRequestAt = SystemClock.uptimeMillis()
            softFocus = null
            // Fokus hodisasi kelmasa (ba'zi ilovalarda maxsus element jim qoladi), element o'qilmay qolmasligi uchun o'zimiz o'qiymiz
            if (readIfSilent) readIfNoFocusEvent(node, if (focusAlready) 300L else 600L)
        } else {
            // Bu holat takrorlanishi mumkin: jurnal va xotira to'lib ketmasin
            val now = SystemClock.uptimeMillis()
            if (now - lastFocusFailLogAt > 10_000L) {
                lastFocusFailLogAt = now
                Diag.log.log(ErrorCode.FOCUS_NODE_FAILED, focusFailInfo(node))
            }
        }
        return ok
    }

    /** Element allaqachon fokusda bo'lsa tizim hodisa yubormaydi va u o'qilmay qolardi: biroz kutib, o'zimiz o'qiymiz. */
    private fun readIfNoFocusEvent(node: AccessibilityNodeInfo, delayMs: Long = 300L) {
        val before = focusEvents
        val seq = focusSeq
        val pkg = lastPackage
        main.postDelayed({
            if (focusEvents == before && seq == focusSeq && !suspended && ::speech.isInitialized) {
                try {
                    if (speakUnit(node, pkg, SystemClock.uptimeMillis())) markHeard(node)
                } catch (ex: Throwable) {
                    swallowed("ReaderService.readIfNoFocusEvent", ex)
                }
            }
        }, delayMs)
    }

    /** Fokus olmagan elementning matnsiz tavsifi (sinf, ilova, ko'rinishi): sababini topish uchun jurnalga yoziladi. */
    private fun focusFailInfo(node: AccessibilityNodeInfo): Map<String, Any> = try {
        mapOf(
            "why" to "action_failed",
            "cls" to (node.className?.toString()?.substringAfterLast('.') ?: ""),
            "pkg" to (node.packageName?.toString() ?: ""),
            "vis" to node.isVisibleToUser,
            "foc" to node.isAccessibilityFocused,
            "imp" to node.isImportantForAccessibility,
            "win" to (node.window?.type ?: -1),
        )
    } catch (_: Throwable) {
        mapOf("why" to "action_failed")
    }

    /**
     * Joriy element (yurish shu yerdan davom etadi). Uch manba bor va ular har doim ham mos kelmaydi:
     * 1) o'zimiz o'qib bergan, lekin tizim fokus bermagan element (softFocus);
     * 2) tizimning o'z fokusi (findFocus): Telegram'da maxsus elementlar (javob havolasi, tugmalar) uchun uni topa olmaydi
     *    yoki ularning ota-onasini (post konteynerini) qaytaradi;
     * 3) oxirgi fokus hodisasining manbasi (yoki o'zimiz so'ragan fokus): hodisa manbasiz kelganda ham aniq element.
     * Ilgari tizim topmasa eskirgan manba olinardi va yurish bir joyda qotib qolar yoki elementlarni o'tkazib yuborardi.
     */
    private fun currentFocus(): AccessibilityNodeInfo? {
        val now = SystemClock.uptimeMillis()
        val soft = softFocus
        if (soft != null) {
            val alive = try {
                soft.refresh()
                soft.isVisibleToUser
            } catch (_: Throwable) {
                false
            }
            if (alive && now - softFocusAt < 120_000L) return soft
            softFocus = null
        }
        val sys = systemFocus()
        val tracked = trackedFocus()
        if (sys == null) {
            if (tracked != null) logFocusFallback(tracked)
            return tracked
        }
        if (tracked == null) return sys
        if (sys == tracked) return sys
        // Tizim ota-onani (post), biz esa uning ichidagi aniq elementni (tugma, havola) bildirsak, aniqrog'i to'g'ri
        if (isInside(tracked, sys)) return tracked
        return sys
    }

    /** Tizim fokusi: qaysi oynada bo'lsa ham (ilova, holat paneli, pastki tugmalar, klaviatura, qulf ekrani) topadi. */
    private fun systemFocus(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (active != null) return active
        try {
            for (w in windows) {
                val r = w.root ?: continue
                val f = r.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                if (f != null) return f
            }
        } catch (ex: Throwable) {
            swallowed("ReaderService.systemFocus", ex)
        }
        return null
    }

    /** Oxirgi fokus hodisasining manbasi (ko'rinib tursa): ba'zi ilovalarda fokusdagi maxsus element findFocus bilan topilmaydi. */
    private fun trackedFocus(): AccessibilityNodeInfo? {
        val last = lastFocusedSource
        if (last != null && SystemClock.uptimeMillis() - lastFocusedAt < 120_000L) {
            try {
                if (last.refresh() && last.isVisibleToUser) return last
            } catch (ex: Throwable) {
                swallowed("ReaderService.trackedFocus", ex)
            }
            lastFocusedSource = null
        }
        return null
    }

    /**
     * Elementga fokus beradi. Tizim fokus bermasa (ba'zi ilovalarda matnli element yoki maxsus tugma shunday bo'ladi),
     * element o'tkazib yuborilmaydi: o'zimiz o'qib beramiz va yurish shu yerdan davom etadi. Ilgari bunday element jimgina
     * tashlab ketilardi: foydalanuvchi uchun "xabar o'qilmadi" yoki "ikkinchi tugma tushib qoldi" bo'lib ko'rinardi.
     * Yurish natijasi eslab qolinadi (qotib qolishdan himoya uchun, moveFocus).
     */
    private fun focusOrRead(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val ok = focus(node) || readWithoutFocus(node)
        if (ok) {
            lastMoveTarget = node
            lastMoveForward = forward
            lastMoveAt = SystemClock.uptimeMillis()
        }
        return ok
    }

    /** Tizim fokus bermagan, lekin ko'rinib turgan va nomi bor elementni o'zimiz o'qib beramiz. */
    private fun readWithoutFocus(node: AccessibilityNodeInfo): Boolean {
        return try {
            try {
                node.refresh()
            } catch (ex: Throwable) {
                swallowed("ReaderService.readWithoutFocus", ex)
            }
            if (!node.isVisibleToUser) return false
            val pkg = node.packageName?.toString() ?: lastPackage
            if (!sayNode(node, pkg, true, SystemClock.uptimeMillis()) { true }) return false
            val now = SystemClock.uptimeMillis()
            softFocus = node
            softFocusAt = now
            lastFocusedSource = node
            lastFocusedAt = now
            rememberState(node)
            logSoftRead(node)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun logSoftRead(n: AccessibilityNodeInfo) {
        val now = SystemClock.uptimeMillis()
        if (now - lastSoftLogAt < 10_000L) return
        lastSoftLogAt = now
        try {
            Diag.log.log(
                ErrorCode.FOCUS_SOFT_READ,
                mapOf(
                    "cls" to (n.className?.toString()?.substringAfterLast('.') ?: ""),
                    "pkg" to (n.packageName?.toString() ?: ""),
                    "id" to (n.viewIdResourceName?.substringAfterLast('/') ?: ""),
                    "kids" to n.childCount,
                    "click" to n.isClickable,
                    "foc" to n.isAccessibilityFocused,
                )
            )
        } catch (ex: Throwable) {
            swallowed("ReaderService.logSoftRead", ex)
        }
    }

    private fun logStuckFixed(n: AccessibilityNodeInfo) {
        val now = SystemClock.uptimeMillis()
        if (now - lastStuckLogAt < 10_000L) return
        lastStuckLogAt = now
        try {
            Diag.log.log(
                ErrorCode.FOCUS_STUCK_FIXED,
                mapOf(
                    "cls" to (n.className?.toString()?.substringAfterLast('.') ?: ""),
                    "pkg" to (n.packageName?.toString() ?: ""),
                    "id" to (n.viewIdResourceName?.substringAfterLast('/') ?: ""),
                )
            )
        } catch (ex: Throwable) {
            swallowed("ReaderService.logStuckFixed", ex)
        }
    }

    /**
     * Tizim fokus hodisasini yubormagan, lekin o'zimiz o'qib bergan element: ikki marta bosish aynan shuni bosishi uchun
     * joriy element qilib eslab qolinadi. Ilgari tizimning eski fokusidagi element bosilardi: foydalanuvchi bir elementni
     * eshitib, boshqasi bosilar yoki hech narsa bo'lmasdi ("fokus boshqa joyga siljib ketadi").
     */
    private fun markHeard(node: AccessibilityNodeInfo) {
        val now = SystemClock.uptimeMillis()
        softFocus = node
        softFocusAt = now
        lastFocusedSource = node
        lastFocusedAt = now
        rememberState(node)
    }

    /** Ikki marta bosish bajarilmasa: qaysi element ekani jurnalga yoziladi (keyingi safar sababini topish uchun). */
    private fun logClickFailed(n: AccessibilityNodeInfo, longPress: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (now - lastClickFailLogAt < 5_000L) return
        lastClickFailLogAt = now
        try {
            Diag.log.log(ErrorCode.FOCUS_NODE_FAILED, focusFailInfo(n) + mapOf("why" to "click_failed", "long" to longPress))
        } catch (ex: Throwable) {
            swallowed("ReaderService.logClickFailed", ex)
        }
    }

    /** Tizim fokusni topa olmay, eslab qolingan elementga tayanilganda jurnalga yoziladi (20 soniyada bir marta). */
    private fun logFocusFallback(last: AccessibilityNodeInfo) {
        val now = SystemClock.uptimeMillis()
        if (now - lastFocusFallbackLogAt <= 20_000L) return
        lastFocusFallbackLogAt = now
        try {
            Diag.log.log(
                ErrorCode.FOCUS_FALLBACK,
                mapOf(
                    "pkg" to (last.packageName?.toString() ?: ""),
                    "cls" to (last.className?.toString()?.substringAfterLast('.') ?: ""),
                )
            )
        } catch (ex: Throwable) {
            swallowed("ReaderService.logFocusFallback", ex)
        }
    }

    // ------------------------------------------------------------------ holat o'zgarishi (tanlangan, belgilangan...)

    private fun related(a: AccessibilityNodeInfo, b: AccessibilityNodeInfo): Boolean =
        a == b || isInside(a, b) || isInside(b, a)

    /** Fokusdagi elementning hozirgi holati eslab qolinadi: bosish yoki tanlashdan keyin o'zgarish shu bilan solishtiriladi. */
    private fun rememberState(node: AccessibilityNodeInfo) {
        try {
            stateNode = node
            stateText = describer.stateOf(node).orEmpty()
        } catch (_: Throwable) {
            stateNode = null
            stateText = ""
        }
    }

    /**
     * Bosish, uzoq bosish, tanlash yoki holat tavsifi o'zgarishi hodisasidan keyin fokusdagi elementning holati
     * tekshiriladi (checkStateChange). Hodisa boshqa elementga tegishli bo'lsa, hech narsa qilinmaydi.
     * Qaytaradi: tekshiruv rejalashtirildimi.
     */
    private fun scheduleStateCheck(src: AccessibilityNodeInfo?, delayMs: Long = 220L): Boolean {
        val tracked = stateNode ?: return false
        if (src != null && !related(src, tracked)) return false
        if (statePending) return true
        statePending = true
        stateRetry = 0
        main.postDelayed(stateTask, delayMs)
        return true
    }

    /**
     * Holat o'zgargan bo'lsa, yangi holat aytiladi (TalkBack kabi): "tanlangan", "tanlanmagan", "belgilangan",
     * "belgilanmagan", "yoqilgan". Ilgari faqat belgilanadigan (checkable) element bosilganda aytilardi, tanlash va
     * tanlovni olib tashlash esa umuman aytilmasdi (Telegram va Samsung fayl menejerida ko'p tanlash).
     */
    private fun checkStateChange() {
        if (suspended || !::speech.isInitialized || !::describer.isInitialized) return
        val node = stateNode ?: return
        try {
            if (!node.refresh()) return
            // Slayder: qiymati o'zgargan bo'lsa (barmoq bilan sudrash, ilova yoki tizim o'zgartirdi), yangi qiymat aytiladi.
            // Faqat foydalanuvchi ishora qilgan yoki tegib turgan paytda: ijro paytidagi vaqt chizig'i har soniya o'zgaradi.
            // O'z qadamimiz natijasi allaqachon aytilgan bo'lsa, announceValue uni takrorlamaydi.
            if (node.rangeInfo != null) {
                if (!screenOffMuted() && (userActiveRecently(RANGE_ACTIVE_MS) || touchActive())) announceValue(node, false)
                return
            }
            val now = describer.stateOf(node).orEmpty()
            val before = stateText
            if (now == before) {
                // Ba'zi ilovalar holatni bir oz kechroq yangilaydi: yana ikki marta tekshiramiz
                if (stateRetry++ < 2) {
                    statePending = true
                    main.postDelayed(stateTask, 350L)
                }
                return
            }
            stateText = now
            val unchecked = getString(R.string.state_unchecked)
            val checked = getString(R.string.state_checked)
            val selected = getString(R.string.state_selected)
            val said = when {
                now.isNotEmpty() -> now
                before.contains(unchecked) -> ""
                before.contains(checked) -> unchecked
                before.contains(selected) -> getString(R.string.state_not_selected)
                else -> ""
            }
            // Ovozli xabar eshitilayotganda holat matni tez-tez o'zgaradi (ijro vaqti): foydalanuvchi tekkan bo'lsagina aytiladi
            if (said.isNotEmpty() && !screenOffMuted() && !(listening && !userActiveRecently())) speak(said, false)
        } catch (ex: Throwable) {
            swallowed("ReaderService.checkStateChange", ex)
        }
    }

    /** Oynalar o'qish tartibida: yuqoridan pastga (holat paneli, ilova, klaviatura, pastki tugmalar). */
    private fun orderedWindows(): List<AccessibilityWindowInfo> {
        val list = ArrayList<AccessibilityWindowInfo>()
        try {
            for (w in windows) {
                if (w.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) list.add(w)
            }
        } catch (ex: Throwable) {
            swallowed("ReaderService.orderedWindows", ex)
        }
        val r1 = Rect()
        val r2 = Rect()
        list.sortWith(
            Comparator { a, b ->
                a.getBoundsInScreen(r1)
                b.getBoundsInScreen(r2)
                if (r1.top != r2.top) r1.top - r2.top else r1.bottom - r2.bottom
            }
        )
        return list
    }

    /**
     * Holat paneli oynasi (tepadagi soat, batareya, tarmoq belgilari). Oddiy surish ilova chetiga yetganda unga o'tib
     * ketmaydi: ilgari chapga surilganda fokus kutilmaganda shu yerga tushib, ikki marta bosish ilovadagi elementni emas,
     * holat paneli belgisini bosardi. Unga "Oldingi/keyingi oynaga o'tish" ishorasi (jumpWindow) bilan hamon o'tish mumkin.
     */
    private fun isStatusBarWindow(w: AccessibilityWindowInfo): Boolean = try {
        if (w.type != AccessibilityWindowInfo.TYPE_SYSTEM) {
            false
        } else {
            val r = Rect()
            w.getBoundsInScreen(r)
            val limit = resources.displayMetrics.heightPixels / 6
            r.top <= 0 && !r.isEmpty && r.height() <= limit && w.root?.packageName?.toString() == "com.android.systemui"
        }
    } catch (_: Throwable) {
        false
    }

    /** Joriy oynaning chetiga yetilganda qo'shni oynaga (pastki tugmalar, klaviatura) o'tadi; holat paneliga o'tmaydi. */
    private fun crossWindow(from: AccessibilityWindowInfo?, forward: Boolean, mode: Navigator.Mode = navigator.mode): Boolean {
        val ws = orderedWindows()
        if (ws.size < 2) return false
        val fromId = from?.id
        val start = if (fromId == null) -1 else ws.indexOfFirst { it.id == fromId }
        val step = if (forward) 1 else -1
        var i = if (start == -1) (if (forward) 0 else ws.size - 1) else start + step
        while (i in ws.indices) {
            if (isStatusBarWindow(ws[i])) {
                i += step
                continue
            }
            val root = try {
                ws[i].root
            } catch (_: Throwable) {
                null
            }
            if (root != null) {
                val units = navigator.collect(root).filter { navigator.matches(it, mode) }
                val target = if (forward) units.firstOrNull() else units.lastOrNull()
                if (target != null && focus(target)) return true
            }
            i += step
        }
        return false
    }

    /** itemOnly: o'qish boshqaruvi (rejim)dan qat'i nazar elementlar bo'yicha yuradi (TalkBack: o'ngga/chapga surish). */
    private fun moveFocus(forward: Boolean, itemOnly: Boolean = false) {
        val cur = currentFocus()
        // Fokus qaysi oynada bo'lsa, shu oyna ichida yuramiz; fokus yo'q bo'lsa, faol oynadan boshlaymiz. Oyna ildizi tizimdan
        // ikki marta so'raladi (oyna, so'ng ildiz), shuning uchun faqat kerak bo'lganda yuklanadi: tez yo'lda umuman kerak emas.
        var rootLoaded = false
        var rootNode: AccessibilityNodeInfo? = null
        fun windowRoot(): AccessibilityNodeInfo? {
            if (!rootLoaded) {
                rootLoaded = true
                val focusRoot = try {
                    cur?.window?.root
                } catch (_: Throwable) {
                    null
                }
                rootNode = focusRoot ?: rootInActiveWindow
            }
            return rootNode
        }
        if (cur == null && windowRoot() == null) {
            speak(getString(R.string.msg_no_window), true)
            return
        }
        // Tezlik yoki Tafsilotlar rejimi unutib qoldirilsa, yuqoriga/pastga surish harakatlantirmay, qiymatni o'zgartirib turardi
        if (!itemOnly && navigator.adjusterExpired()) navigator.select(Navigator.Mode.ELEMENTS)
        val mode = if (itemOnly) Navigator.Mode.ELEMENTS else navigator.mode
        // Nutq tezligi boshqaruvi: yuqoriga surish tezlashtiradi, pastga surish sekinlashtiradi
        if (mode == Navigator.Mode.RATE) {
            navigator.keepMode()
            adjustRate(!forward)
            return
        }
        // Tafsilotlar boshqaruvi (TalkBack'dagi "Verbosity"): pastga surish darajani oshiradi, yuqoriga kamaytiradi
        if (mode == Navigator.Mode.VERBOSITY) {
            navigator.keepMode()
            adjustVerbosity(forward)
            return
        }
        // Harf, so'z, qator yoki xatboshi rejimi: avval matn ichida yuramiz; tugasa keyingi elementga o'tamiz
        val g = mode.granularity
        if (g != 0 && moveByGranularity(cur, forward, g)) return

        // Tez yo'l: butun daraxtni yig'masdan, faqat keyingi nuqtani qidiramiz (ilgari har surishda butun daraxt aylanardi)
        var fast = try {
            if (cur != null) {
                navigator.nextUnit(cur, forward, mode)
            } else if (forward) {
                windowRoot()?.let { navigator.firstUnit(it) }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
        // Suhbat ilovalarida (Telegram va h.k.) tez yo'l natijasi ro'yxatning to'liq tartibi bilan solishtiriladi: xabar matni
        // yoki tugma tushib qolmasligi kafolatlanadi (farq bo'lsa, ro'yxat tartibidagi aniq qo'shni olinadi)
        val chatList = cur != null && isChatPackage(cur.packageName?.toString())
        if (cur != null && (chatList || isBehind(cur, fast, forward))) fast = crossCheckInList(cur, fast, forward, mode)
        // Qotib qolishdan himoya: oldingi surish shu yo'nalishda aynan shu elementga o'tgan bo'lsa-yu, hozir yana shu element
        // chiqsa, joriy fokus noto'g'ri (eskirgan) aniqlangan bo'ladi. Foydalanuvchi u elementni allaqachon eshitgan,
        // shuning uchun undan keyingisiga o'tamiz. Telegram'da maxsus element (javob havolasi, tugma) fokus hodisasi
        // manbasiz kelganda yurish shunday bir joyda takrorlanib qolardi.
        val moved = lastMoveTarget
        val stuckTarget: AccessibilityNodeInfo? =
            if (moved != null && lastMoveForward == forward && SystemClock.uptimeMillis() - lastMoveAt < STUCK_GUARD_MS) moved else null
        if (fast != null && stuckTarget != null && fast == stuckTarget) {
            val skipped: AccessibilityNodeInfo = fast
            logStuckFixed(skipped)
            fast = try {
                navigator.nextUnit(skipped, forward, mode)
            } catch (_: Throwable) {
                null
            }
            if (chatList) fast = crossCheckInList(skipped, fast, forward, mode)
        }
        // Ro'yxat (aylantiriladigan konteyner) chetida: keyingi element ro'yxatdan tashqarida bo'lsa, avval ro'yxat aylantiriladi.
        // Aks holda Telegram suhbatlarida va chatlar ro'yxatida ko'rinmay turgan elementlarga hech qachon yetib bo'lmasdi.
        if (cur != null && edgeScroll(cur, fast, forward, mode)) return
        if (fast != null && focusFrom(fast, forward, mode)) return

        // Zaxira yo'l (oldingi usul): tez yo'l topmasa (ro'yxat oxiri, uzoq sarlavha va h.k.)
        val root = windowRoot()
        if (root == null) {
            speak(getString(R.string.msg_no_window), true)
            return
        }
        val all = navigator.collect(root)
        val idx = if (cur == null) -1 else all.indexOfFirst { it == cur }
        val step = if (forward) 1 else -1
        var i = if (idx == -1) (if (forward) 0 else all.size - 1) else idx + step
        var failed = 0
        while (i in all.indices && failed < MAX_FOCUS_TRIES) {
            val u = all[i]
            // Eskirgan fokus tufayli yana o'sha elementga kelib qolmaslik uchun u o'tkazib yuboriladi
            if (stuckTarget != null && u == stuckTarget) {
                i += step
                continue
            }
            // Ikkilangan nuqta (bir xil nom, bir joy) o'tkazib yuboriladi; fokus olmagan element esa o'qib beriladi
            if (navigator.matches(u, mode) && !(cur != null && isShadow(cur, u))) {
                if (focusOrRead(u, forward)) return
                failed++
            }
            i += step
        }
        // Ko'rinadigan ro'yxat tugadi: aylantirib, yangi elementga o'tishga urinamiz; bo'lmasa qo'shni oynaga o'tamiz.
        // Menyu ochiq bo'lsa, fokus menyudan tashqariga chiqmaydi.
        if (scrollThenFocus(cur, root, forward, mode)) return
        if (!menu.isShowing && crossWindow(cur?.window, forward, mode)) return
        Earcons.play(this, Earcons.Kind.EDGE)
        speak(getString(if (forward) R.string.msg_last_item else R.string.msg_first_item), true)
    }

    /** start fokus bermasa, undan keyingi mos elementlarga o'tadi (eng ko'pi bilan MAX_FOCUS_TRIES marta). */
    private fun focusFrom(start: AccessibilityNodeInfo, forward: Boolean, mode: Navigator.Mode): Boolean {
        var cand: AccessibilityNodeInfo? = start
        var tries = 0
        while (cand != null && tries++ < MAX_FOCUS_TRIES) {
            if (focusOrRead(cand, forward)) return true
            cand = try {
                navigator.nextUnit(cand, forward, mode)
            } catch (_: Throwable) {
                null
            }
        }
        return false
    }

    private fun isInside(node: AccessibilityNodeInfo, container: AccessibilityNodeInfo): Boolean {
        var p: AccessibilityNodeInfo? = try {
            node.parent
        } catch (_: Throwable) {
            null
        }
        var guard = 0
        while (p != null && guard++ < 48) {
            val q: AccessibilityNodeInfo = p
            if (q == container) return true
            p = try {
                q.parent
            } catch (_: Throwable) {
                null
            }
        }
        return false
    }

    private fun isPager(n: AccessibilityNodeInfo): Boolean =
        n.className?.toString()?.contains("Pager", ignoreCase = true) == true

    private fun canScrollIn(container: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val need = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return container.actionList.any { it.id == need }
    }

    /** Aylantirishdan oldingi ko'rinadigan nuqta: yangi chiqqan elementni topish uchun (langar). */
    private class UnitSnap(val node: AccessibilityNodeInfo, val top: Int, val left: Int, val sig: Int)

    /** Elementning tarkibiy belgisi (sinf, matn): ro'yxat qayta ishlatilgan ko'rinishlarni eskisidan ajratish uchun. */
    private fun unitSig(n: AccessibilityNodeInfo): Int {
        var h = n.className?.hashCode() ?: 0
        h = h * 31 + (n.text?.toString()?.hashCode() ?: 0)
        h = h * 31 + (n.contentDescription?.toString()?.hashCode() ?: 0)
        return h
    }

    private fun snapshotUnits(sc: AccessibilityNodeInfo): List<UnitSnap> {
        val out = ArrayList<UnitSnap>(32)
        try {
            val r = Rect()
            for (u in navigator.collect(sc)) {
                if (out.size >= 48) break
                u.getBoundsInScreen(r)
                out.add(UnitSnap(u, r.top, r.left, unitSig(u)))
            }
        } catch (ex: Throwable) {
            swallowed("ReaderService.snapshotUnits", ex)
        }
        return out
    }

    /**
     * Aylantirishdan keyin ro'yxatda yangi paydo bo'lgan nuqtalar (o'qish tartibida). Eski nuqta: avval ham ko'ringan,
     * belgisi bir xil va o'rni boshqa eskilar bilan bir xil masofaga siljigan. Ro'yxat elementlarni qayta ishlatgani uchun
     * faqat tugun tengligiga ishonib bo'lmaydi.
     */
    private fun freshUnits(now: List<AccessibilityNodeInfo>, before: List<UnitSnap>): List<AccessibilityNodeInfo> {
        if (before.isEmpty()) return now
        val r = Rect()
        val matched = arrayOfNulls<UnitSnap>(now.size)
        val dys = ArrayList<Int>()
        val dxs = ArrayList<Int>()
        for (k in now.indices) {
            val u = now[k]
            val sig = unitSig(u)
            val snap = before.firstOrNull { it.node == u && it.sig == sig } ?: continue
            matched[k] = snap
            u.getBoundsInScreen(r)
            dys.add(snap.top - r.top)
            dxs.add(snap.left - r.left)
        }
        val my = if (dys.isEmpty()) null else dys.sorted()[dys.size / 2]
        val mx = if (dxs.isEmpty()) null else dxs.sorted()[dxs.size / 2]
        val out = ArrayList<AccessibilityNodeInfo>(now.size)
        for (k in now.indices) {
            val u = now[k]
            val snap = matched[k]
            if (snap == null) {
                out.add(u)
                continue
            }
            u.getBoundsInScreen(r)
            val sameY = my == null || abs((snap.top - r.top) - my) <= 24
            val sameX = mx == null || abs((snap.left - r.left) - mx) <= 24
            if (!(sameY && sameX)) out.add(u)
        }
        return out
    }

    /** cur'ni o'z ichiga olgan eng yaqin aylantiriladigan konteyner (sahifa almashtirgichdan tashqari), topilmasa null. */
    private fun scrollParentOf(cur: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var p: AccessibilityNodeInfo? = try {
            cur.parent
        } catch (_: Throwable) {
            null
        }
        var guard = 0
        while (p != null && guard++ < 24) {
            val sc: AccessibilityNodeInfo = p
            if (sc.isScrollable && !isPager(sc)) return sc
            p = try {
                sc.parent
            } catch (_: Throwable) {
                null
            }
        }
        return null
    }

    /** Ko'rinish tartibida a b'dan oldin (-1), keyin (1) yoki bir joyda (0). Qator markazlari yaqin bo'lsa, chapdan o'ngga solishtiriladi. */
    private fun readingCompare(a: Rect, b: Rect): Int {
        val dy = a.centerY() - b.centerY()
        val band = minOf(a.height(), b.height()) / 2
        if (abs(dy) > band) return if (dy < 0) -1 else 1
        val dx = a.centerX() - b.centerX()
        return if (dx < 0) -1 else if (dx > 0) 1 else 0
    }

    /**
     * Tez yo'l natijasi yurish yo'nalishiga teskari tomonda, joriy elementdan butunlay yuqorida (oldinga) yoki pastda (orqaga)
     * turibdimi. Daraxt tartibi ko'rinish tartibiga mos kelmasa shunday bo'ladi (teskari ro'yxat oxiridan keyin ekranning
     * tepasidagi sarlavhaga sakrash). Bunday paytda natija ro'yxat tartibi bilan solishtiriladi.
     */
    private fun isBehind(cur: AccessibilityNodeInfo, fast: AccessibilityNodeInfo?, forward: Boolean): Boolean {
        if (fast == null) return false
        return try {
            val rc = Rect()
            val rf = Rect()
            cur.getBoundsInScreen(rc)
            fast.getBoundsInScreen(rf)
            if (rc.isEmpty || rf.isEmpty) false else if (forward) rf.bottom <= rc.top else rf.top >= rc.bottom
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Tez yo'l (fast) va ro'yxat tartibi (cand) farq qilganda, yurish yo'nalishida joriy elementga yaqinrog'i olinadi.
     * Ilgari doim cand olinardi: u ba'zan yaqin qo'shnini o'tkazib, uzoqdagi elementga (ekran tepasiga) sakrardi. Bittasi
     * teskari tomonda bo'lsa, ikkinchisi olinadi. Ikkalasi ham yo'nalishda bo'lmasa, avvalgidek cand.
     */
    private fun nearerInDirection(
        cur: AccessibilityNodeInfo,
        fast: AccessibilityNodeInfo?,
        cand: AccessibilityNodeInfo,
        forward: Boolean,
    ): AccessibilityNodeInfo {
        if (fast == null) return cand
        val rc = Rect()
        val rf = Rect()
        val rd = Rect()
        cur.getBoundsInScreen(rc)
        fast.getBoundsInScreen(rf)
        cand.getBoundsInScreen(rd)
        if (rc.isEmpty || rf.isEmpty || rd.isEmpty) return cand
        fun ahead(r: Rect): Boolean {
            val c = readingCompare(rc, r)
            return if (forward) c < 0 else c > 0
        }
        val fastAhead = ahead(rf)
        val candAhead = ahead(rd)
        return when {
            fastAhead && !candAhead -> fast
            !fastAhead && candAhead -> cand
            fastAhead && candAhead -> {
                val c = readingCompare(rf, rd)
                if (forward) (if (c <= 0) fast else cand) else (if (c >= 0) fast else cand)
            }
            else -> cand
        }
    }

    /**
     * Tez yo'l (nextUnit) natijasi ro'yxatning to'liq tartibi (collect) bilan solishtiriladi. Ular farq qilsa, tez yo'l biror
     * nuqtani (xabar matni, "fikrlar" yoki "ulashish" tugmasi) tashlab ketgan yoki umuman topmagan: ro'yxat tartibidagi
     * aniq qo'shni olinadi. Ro'yxat chetida (qo'shni yo'q) tez yo'l natijasi o'zgarmaydi: aylantirish shunga tayanadi.
     */
    private fun crossCheckInList(
        cur: AccessibilityNodeInfo,
        fast: AccessibilityNodeInfo?,
        forward: Boolean,
        mode: Navigator.Mode,
    ): AccessibilityNodeInfo? {
        return try {
            val sc = scrollParentOf(cur) ?: return fast
            val step = navigator.listNeighbor(sc, cur, forward, mode)
            if (!step.found) return fast
            val cand = step.node ?: return fast
            if (fast != null && fast == cand) return fast
            logNavDiverged(cur, fast, cand, forward)
            nearerInDirection(cur, fast, cand, forward)
        } catch (_: Throwable) {
            fast
        }
    }

    /** Tugunning matnsiz qisqa tavsifi (sinf, bosiladimi, bolalar soni, tepa chegarasi, yorlig'i bormi): jurnal uchun. */
    private fun navShape(n: AccessibilityNodeInfo?): String {
        if (n == null) return "-"
        return try {
            val r = Rect()
            n.getBoundsInScreen(r)
            val cls = n.className?.toString()?.substringAfterLast('.') ?: ""
            val labeled = if (!n.text.isNullOrBlank() || !n.contentDescription.isNullOrBlank()) 1 else 0
            "$cls:c${if (n.isClickable) 1 else 0}:k${n.childCount}:t${r.top}:l$labeled"
        } catch (_: Throwable) {
            "?"
        }
    }

    private fun logNavDiverged(cur: AccessibilityNodeInfo, fast: AccessibilityNodeInfo?, cand: AccessibilityNodeInfo, forward: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (now - lastNavLogAt < 3_000L) return
        lastNavLogAt = now
        try {
            Diag.log.log(
                ErrorCode.NAV_DIVERGED,
                mapOf("fwd" to forward, "cur" to navShape(cur), "fast" to navShape(fast), "cand" to navShape(cand)),
            )
        } catch (ex: Throwable) {
            swallowed("ReaderService.logNavDiverged", ex)
        }
    }

    /**
     * Aylantirishdan keyin eski fokus elementi hali ko'rinib tursa (ro'yxat kam siljigan), yurish aynan undan davom etadi:
     * ro'yxat tartibidagi qo'shnisi olinadi. Ilgari doim "yangi chiqqan birinchi" nuqta olinardi va oradagi nuqtalar
     * tushib qolishi mumkin edi. Eski element ko'rinmay qolgan bo'lsa (yoki boshqa mazmun ko'rsatsa), null qaytadi.
     */
    private fun anchorSuccessor(
        sc: AccessibilityNodeInfo,
        anchor: AccessibilityNodeInfo?,
        sig: Int,
        forward: Boolean,
        mode: Navigator.Mode,
    ): AccessibilityNodeInfo? {
        if (anchor == null) return null
        return try {
            if (!anchor.refresh() || !anchor.isVisibleToUser || unitSig(anchor) != sig) return null
            val step = navigator.listNeighbor(sc, anchor, forward, mode)
            if (step.found) step.node else null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Fokus ro'yxat ichidagi chekka elementda bo'lsa va keyingi element shu ro'yxatdan tashqarida (pastki panel, sarlavha)
     * yoki umuman yo'q bo'lsa, avval ro'yxatning o'zi aylantiriladi va yangi ko'ringan elementga o'tiladi (TalkBack kabi).
     * Ro'yxat boshqa aylanmasa (oxiriga yetgan bo'lsa), oddiy yurish davom etadi. Qaytaradi: aylantirish boshlandimi.
     * Yo'nalish ko'rinish bo'yicha: oldinga = pastga (tarkib tepaga suriladi), teskari tuzilgan ro'yxatlarda ham.
     */
    private fun edgeScroll(
        cur: AccessibilityNodeInfo,
        target: AccessibilityNodeInfo?,
        forward: Boolean,
        mode: Navigator.Mode,
    ): Boolean {
        var p: AccessibilityNodeInfo? = try {
            cur.parent
        } catch (_: Throwable) {
            null
        }
        var guard = 0
        while (p != null && guard++ < 24) {
            val sc: AccessibilityNodeInfo = p
            // Sahifa almashtirgich (ViewPager) aylantirilmaydi: o'tkir surish bilan sahifa kutilmaganda almashib ketardi
            if (sc.isScrollable && !isPager(sc)) {
                // Keyingi element shu ro'yxat ichida: oddiy yuramiz
                if (target != null && isInside(target, sc)) return false
                if (canScrollIn(sc, forward)) {
                    val action = if (forward) {
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    } else {
                        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    }
                    val before = snapshotUnits(sc)
                    if (sc.performAction(action)) {
                        ownScrollUntil = SystemClock.uptimeMillis() + 1500L
                        focusAfterScroll(sc, before, target, forward, mode, 0, cur, unitSig(cur))
                        return true
                    }
                }
            }
            p = try {
                sc.parent
            } catch (_: Throwable) {
                null
            }
        }
        return false
    }

    /**
     * Aylantirishdan keyin ro'yxatda yangi paydo bo'lgan birinchi elementga (orqaga yurishda oxirgisiga) o'tadi.
     * Ilgari eski fokus topilmasa, ro'yxatdagi eng birinchi element olinardi: fokus ekran tepasiga sakrab ketardi.
     * Natija bo'lmasa, ro'yxatdan tashqaridagi elementga chiqadi.
     */
    private fun focusAfterScroll(
        sc: AccessibilityNodeInfo,
        before: List<UnitSnap>,
        outside: AccessibilityNodeInfo?,
        forward: Boolean,
        mode: Navigator.Mode,
        attempt: Int,
        anchor: AccessibilityNodeInfo? = null,
        anchorSig: Int = 0,
    ) {
        main.postDelayed({
            try {
                sc.refresh()
                // Eski fokus hali ko'rinib tursa, yurish undan davom etadi (oradagi nuqtalar tushib qolmasin)
                val next = anchorSuccessor(sc, anchor, anchorSig, forward, mode)
                if (next != null && focusOrRead(next, forward)) return@postDelayed
                val now = navigator.collect(sc).filter { navigator.matches(it, mode) }
                val fresh = freshUnits(now, before)
                val step = if (forward) 1 else -1
                var i = if (forward) 0 else fresh.size - 1
                var failed = 0
                while (i in fresh.indices && failed < MAX_FOCUS_TRIES) {
                    if (focusOrRead(fresh[i], forward)) return@postDelayed
                    failed++
                    i += step
                }
                // Yangi element hali ko'rinmadi: ro'yxat to'xtashini yana bir bor kutamiz
                if (attempt < 1) {
                    focusAfterScroll(sc, before, outside, forward, mode, attempt + 1, anchor, anchorSig)
                    return@postDelayed
                }
                // Aylantirish natija bermadi: ro'yxatdan tashqaridagi keyingi elementga chiqamiz
                if (outside != null && focusFrom(outside, forward, mode)) return@postDelayed
                Earcons.play(this, Earcons.Kind.EDGE)
                speak(getString(if (forward) R.string.msg_last_item else R.string.msg_first_item), true)
            } catch (ex: Throwable) {
                swallowed("ReaderService.focusAfterScroll", ex)
            }
        }, if (attempt == 0) speed.scrollWaitMs else 260L)
    }

    private fun moveByGranularity(cur: AccessibilityNodeInfo?, forward: Boolean, g: Int): Boolean {
        if (cur == null) return false
        if ((cur.movementGranularities and g) != 0) {
            val args = Bundle()
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, g)
            args.putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, false)
            val action = if (forward) {
                AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
            } else {
                AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY
            }
            // false: matn boshiga yoki oxiriga yetildi (qo'shni elementga o'tiladi)
            return cur.performAction(action, args)
        }
        // Tizim bu elementda matn bo'ylab yurishni qo'llamaydi (masalan, Telegram xabari): o'zimiz matn bo'ylab yuramiz.
        // Ilgari bunday elementlarda "harflar" rejimi harf o'rniga keyingi elementga sakrardi.
        val gran = TextGran.of(g) ?: return false
        return virtualMove(cur, forward, gran)
    }

    /** Elementning o'qiladigan matni: yozuv, contentDescription yoki ichki matnlar (tizim matn maydoni bo'lmasa ham). */
    private fun textOf(node: AccessibilityNodeInfo): String = try {
        describer.labelOf(node).trim()
    } catch (_: Throwable) {
        ""
    }

    private fun cursorKey(node: AccessibilityNodeInfo): String = "${node.windowId}|${node.hashCode()}"

    /** O'zimizning virtual kursor bo'yicha bir bo'lak yuradi va aytadi. false: matn tugadi (yoki matn yo'q). */
    private fun virtualMove(node: AccessibilityNodeInfo, forward: Boolean, gran: TextGran): Boolean {
        if (node.isPassword) return false
        val text = textOf(node)
        if (text.isBlank()) return false
        textCursor.bind(cursorKey(node), text)
        val piece = (if (forward) textCursor.next(gran) else textCursor.prev(gran)) ?: return false
        val spoken = if (gran == TextGran.CHAR) CharNames.unitName(piece) else piece
        if (spoken.isNotBlank()) sayContent(spoken, true)
        return true
    }

    /**
     * Fokusdagi element matni bo'ylab (harf, so'z, qator yoki xatboshi) yuradi: o'qish boshqaruvini almashtirish shart emas.
     * Matn boshiga yoki oxiriga yetilsa shuni aytadi (boshqa elementga o'tmaydi: element almashtirish o'ngga/chapga surish).
     */
    private fun moveInText(gran: TextGran, forward: Boolean) {
        val cur = currentFocus()
        if (cur == null) {
            speak(getString(R.string.msg_no_focus), true)
            return
        }
        if (cur.isPassword) {
            speak(getString(R.string.msg_hidden_char), true)
            return
        }
        if (moveByGranularity(cur, forward, gran.granularity)) return
        val msg = when {
            textOf(cur).isBlank() -> R.string.msg_no_text
            forward -> R.string.msg_text_end
            else -> R.string.msg_text_start
        }
        Earcons.play(this, Earcons.Kind.EDGE)
        speak(getString(msg), true)
    }

    /** Fokusdagi matnni harf nomlari bilan aytadi ("Salom" -> "katta es, a, el, o, em"). */
    private fun spellFocused() {
        val node = currentFocus()
        if (node == null) {
            speak(getString(R.string.msg_no_focus), true)
            return
        }
        if (node.isPassword) {
            speak(getString(R.string.msg_hidden_char), true)
            return
        }
        spellOut(textOf(node))
    }

    /** Oxirgi aytilgan matnni harflab aytadi (TalkBack: "Spell last utterance"). */
    private fun spellLast() {
        if (lastRaw.isBlank()) {
            speak(getString(R.string.msg_nothing_spoken), true)
            return
        }
        spellOut(lastRaw)
    }

    private fun spellOut(text: String) {
        if (text.isBlank()) {
            speak(getString(R.string.msg_no_text), true)
            return
        }
        // speak (sayContent emas): "oxirgi aytilgan" matn harflab o'qilgan natija bilan almashib ketmasin
        speak(Spelling.spell(text.trim()), true)
    }

    /** O'qish boshqaruvini ro'yxatdan bevosita tanlash: "up-then-down" bilan bir necha marta aylantirish shart emas. */
    private fun showReadingMenu() {
        val modes = Navigator.Mode.values().toList()
        val labels = modes.map { m ->
            val name = getString(m.labelRes)
            if (m == navigator.mode) getString(R.string.menu_reading_current, name) else name
        }
        openMenu(getString(R.string.menu_reading_title), labels, currentFocus()) { which ->
            val chosen = modes.getOrNull(which)
            if (chosen != null) {
                navigator.select(chosen)
                main.postDelayed({
                    try {
                        restoreMenuFocus()
                    } catch (ex: Throwable) {
                        swallowed("ReaderService.showReadingMenu", ex)
                    }
                }, 250L)
                // Menyu yopilib, fokus qaytgach e'lon qilinadi (aks holda fokus nutqi uni kesib yuboradi)
                main.postDelayed({ announceMode(chosen) }, 700L)
            }
        }
    }

    private fun findScroller(
        cur: AccessibilityNodeInfo?,
        root: AccessibilityNodeInfo,
        forward: Boolean = true,
    ): AccessibilityNodeInfo? {
        var p = cur?.parent
        var guard = 0
        while (p != null && guard++ < 20) {
            if (p.isScrollable) return p
            p = p.parent
        }
        // Fokus ro'yxatdan tashqarida: kerakli tomonga aylana oladigan eng katta konteyner (kichik tab-qator yoki karusel emas)
        val need = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0L
        val r = Rect()
        findFirst(root) { n ->
            if (n.isScrollable && n.actionList.any { it.id == need }) {
                n.getBoundsInScreen(r)
                val area = r.width().toLong() * r.height().toLong()
                if (area > bestArea) {
                    best = n
                    bestArea = area
                }
            }
            false
        }
        return best ?: findFirst(root) { it.isScrollable }
    }

    private fun scrollThenFocus(
        cur: AccessibilityNodeInfo?,
        root: AccessibilityNodeInfo,
        forward: Boolean,
        mode: Navigator.Mode = navigator.mode,
    ): Boolean {
        val scroller = findScroller(cur, root, forward) ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (!scroller.performAction(action)) return false
        ownScrollUntil = SystemClock.uptimeMillis() + 1500L
        main.postDelayed({
            try {
                scroller.refresh()
                val list = navigator.collect(scroller)
                val curIdx = if (cur == null) -1 else list.indexOfFirst { it == cur }
                val step = if (forward) 1 else -1
                var i = if (curIdx == -1) (if (forward) 0 else list.size - 1) else curIdx + step
                while (i in list.indices) {
                    if (navigator.matches(list[i], mode)) {
                        focusOrRead(list[i], forward)
                        return@postDelayed
                    }
                    i += step
                }
            } catch (ex: Throwable) {
                swallowed("ReaderService.scrollThenFocus", ex)
            }
        }, speed.scrollWaitMs)
        return true
    }

    /** Uzluksiz o'qish: boshidan yoki joriy elementdan keyingilarini ketma-ket aytadi (ekranga tegilsa to'xtaydi). */
    private fun readFrom(fromNext: Boolean) {
        val root = rootInActiveWindow ?: return
        val all = navigator.collect(root)
        val cur = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        val idx = if (cur == null) -1 else all.indexOfFirst { it == cur }
        val start = if (fromNext && idx >= 0) idx + 1 else 0
        val dt = detailsFor(lastPackage)
        var first = true
        var count = 0
        for (i in start until all.size) {
            val t = describer.describe(all[i], dt)
            if (t.isBlank()) continue
            sayContent(t, first)
            first = false
            if (++count >= 80) break
        }
    }

    /** Suhbatda oxirgi ko'rinadigan xabarga (ro'yxatdagi oxirgi elementga) o'tadi. */
    private fun jumpLast() {
        val root = rootInActiveWindow ?: return
        val cur = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        val scroller = findScroller(cur, root)
        val list = navigator.collect(scroller ?: root)
        val last = list.lastOrNull() ?: return
        focus(last)
    }

    // ------------------------------------------------------------------ qo'ng'iroq, yorliq

    private fun callAction(kind: CallActions.Kind) {
        val roots = ArrayList<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots.add(it) }
        for (w in windows) w.root?.let { roots.add(it) }
        val ok = CallActions.click(this, roots, kind)
        speak(getString(if (ok) R.string.msg_call_done else R.string.msg_call_not_found), true)
    }

    /** Fokusdagi elementning identifikatorini yorliqlar fayliga qo'shish uchun tahrirlash ekranini ochadi. */
    private fun addLabelForFocused() {
        val id = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)?.viewIdResourceName
        if (id == null) {
            speak(getString(R.string.msg_no_view_id), true)
            return
        }
        val i = Intent(this, TextFileEditorActivity::class.java)
            .putExtra(TextFileEditorActivity.EXTRA_FILE, UserFiles.LABELS)
            .putExtra(TextFileEditorActivity.EXTRA_TITLE, R.string.btn_labels)
            .putExtra(TextFileEditorActivity.EXTRA_HELP, R.string.help_labels)
            .putExtra(TextFileEditorActivity.EXTRA_APPEND, "$id=")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    // ------------------------------------------------------------------ qo'shimcha imkoniyatlar

    private fun markProblem() {
        Diag.log.log(ErrorCode.USER_MARKED)
        speak(getString(R.string.msg_marked), true)
    }

    private fun readPrivate() {
        val text = pendingPrivate
        if (text == null || SystemClock.uptimeMillis() - pendingPrivateAt > 120_000L) {
            pendingPrivate = null
            speak(getString(R.string.msg_private_none), true)
            return
        }
        pendingPrivate = null
        main.removeCallbacks(clearPrivate)
        speak(text, true)
    }

    /** Oxirgi moslashtirilgan so'z: qanday o'qildi va asli qanday yozilgan (harflab). */
    private fun explainLastBridge() {
        val c = lastChanges.lastOrNull()
        if (c == null) {
            speak(getString(R.string.msg_bridge_none), true)
            return
        }
        val spelled = c.original.map { it.toString() }.joinToString(", ")
        speech.speak(process(getString(R.string.msg_bridge_item, c.spoken, spelled), record = false), true)
    }

    private fun speakStatus() {
        val uz = Locale.getDefault().language == "uz"
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        // Vaqt "Vaqt va batareya" sozlamalaridagi format (12/24) va o'qish uslubi bilan o'qiladi
        val sb = StringBuilder(TimeAnnouncer.timeText(this))
        // Ba'zi qurilmalar BATTERY_PROPERTY_CAPACITY'ni qo'llamaydi va Int.MIN_VALUE qaytaradi
        val levelKnown = level in 0..100
        if (levelKnown) {
            val shown = if (uz) UzNumbers.words(level) else level.toString()
            sb.append(". ").append(getString(R.string.msg_status_battery, shown))
        }
        if (bm.isCharging) sb.append(if (levelKnown) ", " else ". ").append(getString(R.string.msg_charging))
        speak(sb.toString(), true)
    }

    private fun toggleCurtain() {
        setCurtain(curtain == null)
        speak(getString(if (curtain != null) R.string.msg_curtain_on else R.string.msg_curtain_off), true)
    }

    /** Ekran pardasi: qora qatlam. Teginish unga to'siq bo'lmaydi; xavfsizlik (atrofdagilar ko'rmasin) va batareya uchun. */
    private fun setCurtain(on: Boolean) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (on && curtain == null) {
            val v = View(this)
            v.setBackgroundColor(Color.BLACK)
            v.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE
            )
            lp.gravity = Gravity.TOP or Gravity.START
            try {
                wm.addView(v, lp)
                curtain = v
            } catch (ex: Throwable) {
                swallowed("ReaderService.setCurtain", ex)
            }
        } else if (!on) {
            val v = curtain
            if (v != null) {
                try {
                    wm.removeView(v)
                } catch (ex: Throwable) {
                    swallowed("ReaderService.setCurtain", ex)
                }
            }
            curtain = null
        }
    }

    // ------------------------------------------------------------------ onlayn: tarjima va ekran tavsifi

    private var aiBusy = false

    /** Maxfiy ilovada, onlayn yoqilmagan yoki kalit bo'lmaganda, yoki oldingi so'rov tugamagan bo'lsa false. */
    private fun aiPrecheck(): Boolean {
        if (isSensitiveContext()) {
            speak(getString(R.string.msg_ai_sensitive), true)
            return false
        }
        if (!Online.aiReady(this)) {
            speak(getString(R.string.msg_ai_not_ready), true)
            return false
        }
        if (aiBusy) {
            speak(getString(R.string.msg_ai_busy), true)
            return false
        }
        return true
    }

    /** Fokusdagi (yoki oxirgi aytilgan) matnni tanlangan tilga tarjima qilib o'qiydi. Matn faqat shu ishorada yuboriladi. */
    private fun translateFocused() {
        val node = currentFocus()
        if (node != null && node.isPassword) {
            speak(getString(R.string.msg_nothing_to_translate), true)
            return
        }
        var text = if (node != null) describer.labelOf(node, false) else ""
        if (text.isBlank()) text = lastRaw
        if (text.isBlank()) {
            speak(getString(R.string.msg_nothing_to_translate), true)
            return
        }
        if (!aiPrecheck()) return
        aiBusy = true
        aiBusySince = SystemClock.uptimeMillis()
        speak(getString(R.string.msg_ai_working), true)
        val ctx = applicationContext
        val source = Prefs.translateFrom(this)
        val target = Prefs.translateTo(this)
        val payload = text
        Online.async({ AiClient.translate(ctx, payload, source, target) }) { result -> aiFinished(result, "translate", target) }
    }

    /** Ekran rasmini olib, sun'iy intellekt orqali tasvirlaydi (Android 11 va undan yangi). Rasm faqat shu ishorada yuboriladi. */
    private fun describeScreen() {
        if (Build.VERSION.SDK_INT < 30) {
            speak(getString(R.string.msg_ai_needs_api30), true)
            return
        }
        if (!aiPrecheck()) return
        aiBusy = true
        aiBusySince = SystemClock.uptimeMillis()
        speak(getString(R.string.msg_ai_working), true)
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                Executor { r -> main.post(r) },
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val ctx = applicationContext
                        // Tavsif matn tilida: ovoz o'zbekcha o'qisa o'zbekcha, aks holda tizim tilida
                        val lang = if (Prefs.isUzbekText(ctx)) "uz" else Locale.getDefault().language
                        Online.async({
                            val b64 = jpegBase64Of(screenshot, null)
                            if (b64 == null) null else AiClient.describe(ctx, b64, lang)
                        }) { result -> aiFinished(result, "describe") }
                    }

                    override fun onFailure(errorCode: Int) {
                        aiFinished(null, "screenshot")
                    }
                }
            )
        } catch (_: Throwable) {
            aiFinished(null, "screenshot")
        }
    }

    /** Ekran rasmini kichraytirib JPEG qiladi (orqa oqimda). Xatoda null. */
    private fun jpegBase64Of(shot: ScreenshotResult, crop: Rect?): String? {
        val hw = shot.hardwareBuffer
        try {
            val hwBitmap = Bitmap.wrapHardwareBuffer(hw, shot.colorSpace) ?: return null
            val soft = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
            hwBitmap.recycle()
            if (soft == null) return null
            // Rasmga tavsif: faqat fokusdagi element maydoni kesib olinadi (butun ekran emas)
            var base = soft
            if (crop != null) {
                val r = Rect(crop)
                if (r.intersect(0, 0, soft.width, soft.height) && r.width() > 8 && r.height() > 8) {
                    base = Bitmap.createBitmap(soft, r.left, r.top, r.width(), r.height())
                }
            }
            val longSide = maxOf(base.width, base.height)
            val scale = if (longSide > MAX_SHOT_SIDE) MAX_SHOT_SIDE.toFloat() / longSide else 1f
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    base,
                    (base.width * scale).toInt().coerceAtLeast(1),
                    (base.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                base
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            if (scaled !== base) scaled.recycle()
            if (base !== soft) base.recycle()
            soft.recycle()
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Throwable) {
            return null
        } finally {
            try {
                hw.close()
            } catch (ex: Throwable) {
                swallowed("ReaderService.jpegBase64Of", ex)
            }
        }
    }

    /** Onlayn so'rov tugadi (asosiy oqimda): natijani aytadi yoki xatoni xabar qiladi. */
    private fun aiFinished(result: String?, what: String, lang: String = "") {
        aiBusy = false
        if (instance !== this) return
        if (result.isNullOrBlank()) {
            Diag.log.log(ErrorCode.AI_FAILED, mapOf("what" to what))
            speak(getString(if (what == "screenshot") R.string.msg_ai_screenshot_failed else R.string.msg_ai_failed), true)
        } else {
            // O'zbekcha bo'lmagan tarjima o'zbek yozuv qoidalari (kirill-lotin, so'z moslash) bilan o'zgartirilmaydi
            sayContent(result, true, 0L, lang.isNotEmpty() && !lang.startsWith("uz"))
        }
    }

    // ------------------------------------------------------------------ takrorlash, nusxalash, lug'at

    /** Mazmunni (tizim xabarlarini emas) aytadi va "oxirgi aytilgan" sifatida eslab qoladi. */
    private fun sayContent(text: String, interrupt: Boolean, eventTime: Long = 0L, raw: Boolean = false, auto: Boolean = false) {
        lastRaw = text
        if (!auto) {
            val sensitive = isSensitiveContext()
            if (interrupt || sessionRaw.isEmpty()) {
                sessionRaw = text
                sessionSensitive = sensitive
            } else {
                sessionRaw = (sessionRaw + "\n" + text).takeLast(SESSION_MAX_CHARS)
                sessionSensitive = sessionSensitive || sensitive
            }
        }
        speak(text, interrupt, eventTime, raw, auto)
    }

    /**
     * O'zbegim menyusi: oxirgi o'qilgan matnni (uzluksiz o'qilgan bo'lsa, butun qismini) Microsoft ovozida MP3 qilib saqlaydi.
     * Fayl telefonning "Music/Ozbegim" papkasiga tushadi. Mahalliy ovozlar (RHVoice, tizim) MP3 bermaydi; maxfiy
     * ilovalarda matn tarmoqqa chiqmaydi.
     */
    private fun saveLastAudio() {
        val raw = if (sessionRaw.isNotBlank()) sessionRaw else lastRaw
        if (raw.isBlank()) {
            speak(getString(R.string.msg_audio_none), true)
            return
        }
        if (sessionSensitive || isSensitiveContext()) {
            speak(getString(R.string.msg_audio_sensitive), true)
            return
        }
        if (Prefs.engine(this) != "azure" || !Online.speechReady(this)) {
            speak(getString(R.string.msg_audio_need_online), true)
            return
        }
        if (audioSaving) return
        audioSaving = true
        // Matn eshitilgan ko'rinishda saqlanadi: raqamlar so'z bilan, Rim raqamlari tartib son bilan va h.k.
        val spoken = process(raw, false)
        speak(getString(R.string.msg_audio_saving), true)
        AudioExport.save(applicationContext, spoken) { r ->
            audioSaving = false
            if (instance === this) {
                val msg = if (r.ok) {
                    var m = getString(R.string.msg_audio_saved, r.name, r.where)
                    if (r.truncatedTo > 0) m += " " + getString(R.string.msg_audio_truncated, r.truncatedTo)
                    m
                } else {
                    getString(if (r.reason == "auth") R.string.msg_audio_auth else R.string.msg_audio_failed)
                }
                speak(msg, true)
            }
        }
    }

    private fun copyLast() {
        if (lastRaw.isEmpty() || ProfileStore.get(lastPackage)?.sensitive == true) {
            speak(getString(R.string.msg_nothing_spoken), true)
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("", lastRaw))
        speak(getString(R.string.msg_copied), true)
    }

    /** Noto'g'ri o'qilgan so'zni lug'atga qo'shish: oxirgi moslashtirilgan yoki oxirgi aytilgan so'z (9.5). */
    private fun addWordToDictionary() {
        // Maxfiy ilova (bank va h.k.) ekranidagi so'z lug'at fayliga (u eksport qilinadi) tushmasin
        if (ProfileStore.get(lastPackage)?.sensitive == true) {
            speak(getString(R.string.msg_no_word), true)
            return
        }
        val word = lastChanges.lastOrNull()?.original
            ?: lastRaw.split(' ', ',', '.', ':', ';', '\n').lastOrNull { w -> w.any { it.isLetter() } }
        if (word == null) {
            speak(getString(R.string.msg_no_word), true)
            return
        }
        val i = Intent(this, TextFileEditorActivity::class.java)
            .putExtra(TextFileEditorActivity.EXTRA_FILE, UserFiles.DICTIONARY)
            .putExtra(TextFileEditorActivity.EXTRA_TITLE, R.string.btn_dictionary)
            .putExtra(TextFileEditorActivity.EXTRA_HELP, R.string.help_dictionary)
            .putExtra(TextFileEditorActivity.EXTRA_APPEND, word.lowercase() + "=")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    // ------------------------------------------------------------------ jismoniy klaviatura

    /** Jismoniy klaviatura buyruqlari (8.3): Alt + yo'nalish tugmalari va harflar. */
    private fun keyboardCommand(code: Int): Boolean {
        if (suspended || !::speech.isInitialized) return false
        if (code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_DPAD_CENTER) {
            val node = currentFocus()
            if (node != null) clickNode(node, false)
            return true
        }
        // Alt bilan birga bosiladigan qisqa tugmalar (jismoniy klaviatura)
        val action = when (code) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> GestureAction.NEXT_ITEM
            KeyEvent.KEYCODE_DPAD_LEFT -> GestureAction.PREV_ITEM
            KeyEvent.KEYCODE_DPAD_DOWN -> GestureAction.NEXT
            KeyEvent.KEYCODE_DPAD_UP -> GestureAction.PREV
            KeyEvent.KEYCODE_R -> GestureAction.READ_FROM_NEXT
            KeyEvent.KEYCODE_T -> GestureAction.READ_FROM_TOP
            KeyEvent.KEYCODE_S -> GestureAction.STOP_SPEECH
            KeyEvent.KEYCODE_H -> GestureAction.STATUS
            KeyEvent.KEYCODE_B -> GestureAction.BACK
            KeyEvent.KEYCODE_O -> GestureAction.REPEAT_LAST
            KeyEvent.KEYCODE_M -> GestureAction.GLOBAL_MENU
            KeyEvent.KEYCODE_A -> GestureAction.ACTIONS_MENU
            KeyEvent.KEYCODE_N -> GestureAction.NOTIFICATIONS
            KeyEvent.KEYCODE_Q -> GestureAction.QUICK_SETTINGS
            KeyEvent.KEYCODE_C -> GestureAction.CURTAIN
            KeyEvent.KEYCODE_Y -> GestureAction.TRANSLATE
            KeyEvent.KEYCODE_F -> GestureAction.FIRST_ITEM
            KeyEvent.KEYCODE_L -> GestureAction.JUMP_LAST
            KeyEvent.KEYCODE_W -> GestureAction.NEXT_WINDOW
            else -> null
        }
        if (action == null) return false
        try {
            perform(action)
        } catch (ex: Throwable) {
            swallowed("ReaderService.keyboardCommand", ex)
        }
        return true
    }

    // ------------------------------------------------------------------ to'xtatish (favqulodda chiqish)

    /** Ikkala ovoz tugmasini 3 soniya bosib turish: ekran o'quvchini to'xtatadi yoki davom ettiradi. */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        return try {
            handleKey(event)
        } catch (_: Throwable) {
            false
        }
    }

    private fun handleKey(event: KeyEvent): Boolean {
        if (yielded) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        if (down && (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            // Ovoz tugmasi bosildi: keyingi soniyalarda tizim ovoz paneli o'qishni to'xtatmaydi
            lastVolumeKeyAt = SystemClock.uptimeMillis()
        }
        if (event.action == KeyEvent.ACTION_UP && swallowedKeys.remove(event.keyCode)) return true
        if (down && event.isAltPressed && keyboardCommand(event.keyCode)) {
            swallowedKeys.add(event.keyCode)
            return true
        }
        if (down && event.repeatCount > 0) return false
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> volUp = down
            KeyEvent.KEYCODE_VOLUME_DOWN -> volDown = down
            else -> return false
        }
        main.removeCallbacks(toggleSuspend)
        if (volUp && volDown) main.postDelayed(toggleSuspend, 3000L)
        return false // tugmalar odatdagidek ishlashda davom etadi
    }

    // ------------------------------------------------------------------ boshqa ekran o'quvchilar bilan kelishuv

    /**
     * Boshqa ekran o'quvchi (TalkBack, Samsung Voice Assistant va h.k.) yoqildi: O'zbegim kutish rejimiga o'tadi.
     * Xizmat tizimning "yoqilgan xizmatlar" ro'yxatidan o'zini o'chira olmaydi (buning uchun adb orqali beriladigan maxsus
     * ruxsat kerak), shuning uchun u yoqiq qoladi, lekin: hech narsa o'qimaydi, ishora va tugmalarni ushlamaydi,
     * teginib o'rganishni bo'shatadi va hodisalarni olmaydi. Boshqa ekran o'quvchi o'chirilsa, evaluateOtherReader
     * xizmatni avtomatik qayta ishga tushiradi (leaveStandby).
     */
    private fun enterStandby(other: String) {
        if (yielded) return
        // E'lon nutq o'chirilishidan oldin aytiladi va boshqa ekran o'quvchi gapirib turganda ham eshitiladi
        speak(getString(R.string.msg_other_reader_started), true)
        yielded = true
        standbyAt = SystemClock.uptimeMillis()
        // Kutilayotgan ishlar bekor qilinadi, ekran ustidagi hamma narsa bo'shatiladi
        main.removeCallbacks(hoverRunnable)
        main.removeCallbacks(windowTask)
        main.removeCallbacks(stateTask)
        main.removeCallbacks(prefetchTask)
        main.removeCallbacks(listenStart)
        main.removeCallbacks(toggleSuspend)
        prefetchPending = false
        statePending = false
        hoverNode = null
        stateNode = null
        softFocus = null
        focusRequest = null
        lastMoveTarget = null
        lastHoverUnit = null
        touching = false
        menu.dismiss()
        menuReturn = null
        setCurtain(false)
        navigator.clearCaches()
        applyStandbyFlags(true)
        Diag.blackBox.add("yield", 1)
        Log.i(TAG, "standby: other reader $other")
    }

    /**
     * Kutish rejimidan chiqish. otherStopped: sababi boshqa ekran o'quvchining o'chirilishi (e'lon shuni aytadi);
     * aks holda (foydalanuvchi "o'zim to'xtash" sozlamasini o'chirdi) oddiy "davom etmoqda" aytiladi.
     * Foydalanuvchi O'zbegimni oldindan o'zi to'xtatgan bo'lsa, u to'xtatilgan holda qoladi va gapirmaydi.
     */
    private fun leaveStandby(otherStopped: Boolean) {
        if (!yielded) return
        yielded = false
        applyStandbyFlags(false)
        if (!userSuspended) setTouchExploration(true)
        scheduleSurfaceCheck()
        lastFocusText = ""
        Diag.blackBox.add("yield", 0)
        Log.i(TAG, "standby ended")
        if (userSuspended) return
        speak(getString(if (otherStopped) R.string.msg_other_reader_stopped else R.string.msg_resumed), true)
    }

    /**
     * Kutish rejimida xizmatning ishora, teginib o'rganish, tugma va hodisa bayroqlari olib tashlanadi (boshqa ekran
     * o'quvchi ularni to'liq o'zi boshqarishi uchun). Chiqishda avvalgi bayroqlar aynan o'zi qaytariladi.
     */
    private fun applyStandbyFlags(on: Boolean) {
        try {
            val info = serviceInfo ?: return
            if (on) {
                if (standbyFlags == NO_FLAGS) standbyFlags = info.flags
                var f = info.flags
                f = f and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
                f = f and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
                if (Build.VERSION.SDK_INT >= 29) f = f and AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES.inv()
                if (Build.VERSION.SDK_INT >= 31) f = f and AccessibilityServiceInfo.FLAG_REQUEST_2_FINGER_PASSTHROUGH.inv()
                info.flags = f
                info.eventTypes = 0
            } else {
                if (standbyFlags != NO_FLAGS) {
                    info.flags = standbyFlags
                    standbyFlags = NO_FLAGS
                }
                info.eventTypes = NEEDED_EVENTS
            }
            serviceInfo = info
        } catch (ex: Throwable) {
            swallowed("ReaderService.applyStandbyFlags", ex)
        }
    }

    /** Boshqa ekran o'quvchi bor-yo'qligi tekshiriladi va kerak bo'lsa, kutish rejimiga o'tiladi yoki undan chiqiladi. */
    private fun evaluateOtherReader() {
        if (!::speech.isInitialized) return
        val allowed = Prefs.yieldToOtherReaders(this)
        val other = if (allowed) OtherReaders.find(this) else null
        if (other != null) {
            enterStandby(other)
        } else if (yielded) {
            leaveStandby(allowed)
        }
    }

    /** Ketma-ket o'zgarishlar (xizmat yoqilib-o'chishi) bir marta ko'riladi: e'lon takror-takror aytilmasligi uchun. */
    private fun scheduleReaderCheck() {
        main.removeCallbacks(readerCheck)
        main.postDelayed(readerCheck, READER_DEBOUNCE_MS)
    }

    private fun startReaderWatch() {
        readerWatcher?.stop()
        readerWatcher = OtherReaders.Watcher(this, main) { scheduleReaderCheck() }.also { it.start() }
        main.removeCallbacks(readerPoll)
        main.postDelayed(readerPoll, READER_POLL_MS)
    }

    private fun stopReaderWatch() {
        readerWatcher?.stop()
        readerWatcher = null
        main.removeCallbacks(readerPoll)
        main.removeCallbacks(readerCheck)
    }

    private fun setSuspended(on: Boolean) {
        if (on == userSuspended) return
        if (on) {
            speak(getString(R.string.msg_suspended), true)
            userSuspended = true
            setTouchExploration(false)
        } else {
            userSuspended = false
            setTouchExploration(true)
            speak(getString(R.string.msg_resumed), true)
        }
        Log.i(TAG, "suspended=$userSuspended")
    }

    /**
     * Ko'p barmoqli ishoralar yoqilganda tizim ikki barmoq bilan surishni o'zi ushlab oladi (jurnalda 25-ishora), shuning
     * uchun qulf ekranini ikki barmoq bilan tepaga surib ochib bo'lmasdi, ro'yxatlar ham aylanmasdi. Bu bayroq (Android 12+)
     * ikki barmoq bilan surishni ilovaga o'tkazib yuboradi, ikki va uch barmoq bilan bosish ishoralari esa saqlanadi.
     */
    private fun updatePassthrough() {
        if (Build.VERSION.SDK_INT < 31 || yielded) return
        val want = when (Prefs.twoFingerPass(this)) {
            "always" -> true
            "off" -> false
            else -> isKeyguardLocked()
        }
        if (want == passthroughOn) return
        try {
            val info = serviceInfo ?: return
            val mask = AccessibilityServiceInfo.FLAG_REQUEST_2_FINGER_PASSTHROUGH
            info.flags = if (want) info.flags or mask else info.flags and mask.inv()
            serviceInfo = info
            passthroughOn = want
            Diag.blackBox.add("pass", if (want) 1 else 0)
        } catch (ex: Throwable) {
            swallowed("ReaderService.updatePassthrough", ex)
        }
    }

    private fun scheduleSurfaceCheck() {
        main.removeCallbacks(surfaceTask)
        main.postDelayed(surfaceTask, SURFACE_CHECK_MS)
    }

    /** Ekran yoqilishi, o'chishi va qulf ochilishi: ikki barmoqli ishorani o'tkazib yuborish holati qayta tekshiriladi. */
    private fun registerSurfaceReceiver() {
        if (surfaceReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                scheduleSurfaceCheck()
            }
        }
        try {
            val f = IntentFilter()
            f.addAction(Intent.ACTION_SCREEN_ON)
            f.addAction(Intent.ACTION_SCREEN_OFF)
            f.addAction(Intent.ACTION_USER_PRESENT)
            registerReceiver(r, f)
            surfaceReceiver = r
        } catch (ex: Throwable) {
            swallowed("ReaderService.registerSurfaceReceiver", ex)
        }
    }

    private fun unregisterSurfaceReceiver() {
        val r = surfaceReceiver ?: return
        surfaceReceiver = null
        try {
            unregisterReceiver(r)
        } catch (ex: Throwable) {
            swallowed("ReaderService.unregisterSurfaceReceiver", ex)
        }
    }

    /** Faqat kerakli hodisa turlarini olish: qolganlari ishlatilmaydi, lekin har biri asosiy oqimni band qiladi. */
    private fun applyEventMask() {
        try {
            val info = serviceInfo ?: return
            info.eventTypes = NEEDED_EVENTS
            serviceInfo = info
        } catch (ex: Throwable) {
            swallowed("ReaderService.applyEventMask", ex)
        }
    }

    /**
     * Yorlig'i topilmagan ("Nomsiz") element: sababini aniqlash uchun matnsiz tuzilma jurnalga yoziladi (sinf, ilova,
     * bolalar belgilari). Har 15 soniyada ko'pi bilan bitta yozuv.
     */
    private fun logUnlabeled(n: AccessibilityNodeInfo) {
        val now = SystemClock.uptimeMillis()
        if (now - lastUnlabeledLogAt < 15_000L) return
        lastUnlabeledLogAt = now
        try {
            val sb = StringBuilder()
            for (i in 0 until minOf(n.childCount, 6)) {
                val c = n.getChild(i) ?: continue
                sb.append(c.className?.toString()?.substringAfterLast('.') ?: "?")
                sb.append(':')
                sb.append(if (c.text.isNullOrBlank()) '-' else 't')
                sb.append(if (c.contentDescription.isNullOrBlank()) '-' else 'd')
                sb.append(if (c.isClickable) 'c' else '-')
                sb.append(if (c.isVisibleToUser) 'v' else '-')
                sb.append(' ')
            }
            Diag.log.log(
                ErrorCode.NODE_UNLABELED,
                mapOf(
                    "cls" to (n.className?.toString()?.substringAfterLast('.') ?: ""),
                    "pkg" to (n.packageName?.toString() ?: ""),
                    "id" to (n.viewIdResourceName?.substringAfterLast('/') ?: ""),
                    "n" to n.childCount,
                    "kids" to sb.toString().trim(),
                    "lab" to (n.labeledBy != null),
                )
            )
        } catch (ex: Throwable) {
            swallowed("ReaderService.logUnlabeled", ex)
        }
    }

    /** Zichlik (dpi) va shrift masshtabi jurnalga yoziladi: "interfeys kichrayib ketdi" shikoyatini aniqlash uchun. */
    private fun logDisplayInfo(reason: String) {
        try {
            val cfg = resources.configuration
            val dpi = cfg.densityDpi
            val fs = cfg.fontScale
            val changed = (lastDensity != 0 && lastDensity != dpi) || (lastFontScale != 0f && lastFontScale != fs)
            Diag.blackBox.add("dpi", dpi)
            if (reason == "connected" || changed) {
                Diag.log.log(
                    ErrorCode.DISPLAY_INFO,
                    mapOf(
                        "why" to reason,
                        "dpi" to dpi,
                        "font" to fs,
                        "swdp" to cfg.smallestScreenWidthDp,
                        "pass" to Prefs.twoFingerPass(this),
                    )
                )
            }
            lastDensity = dpi
            lastFontScale = fs
        } catch (ex: Throwable) {
            swallowed("ReaderService.logDisplayInfo", ex)
        }
    }

    /**
     * Davriy salomatlik tekshiruvi (har 30 soniyada): xizmat soatlab, kunlab ishlaganda qotib qolgan holatlarni tuzatadi.
     * Mikrofon "band" holati qotib qolsa (ovozli xabardan keyin), nutq dvigateli tayyor bo'lmay qolsa, tizim sensorli
     * o'rganish rejimini yo'qotsa, onlayn so'rov javobsiz qolsa va xavfsiz rejimdan keyin barqaror ishlansa.
     */
    private fun healthTick() {
        if (!::speech.isInitialized) return
        val now = SystemClock.uptimeMillis()
        speech.ensureAlive()
        if (aiBusy && now - aiBusySince > AI_STUCK_MS) {
            aiBusy = false
            Diag.log.log(ErrorCode.AI_FAILED, mapOf("what" to "stuck"))
        }
        if (micActive && now - micSince > MIC_RECHECK_MS && now - micCheckedAt > MIC_RECHECK_MS) {
            micCheckedAt = now
            try {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (realRecordingCount(am.activeRecordingConfigurations) == 0) applyMicState(false)
            } catch (ex: Throwable) {
                swallowed("ReaderService.healthTick", ex)
            }
        }
        if (!suspended) {
            val info = serviceInfo
            if (info != null && (info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE) == 0) {
                setTouchExploration(true)
                Diag.blackBox.add("fix_te", 1)
            }
        }
        if (safeMode && CrashGuard.recentCount(this) == 0) {
            safeMode = false
            Diag.blackBox.add("safe_exit", 1)
        }
        if (lastHoverUnit != null && now - lastTouchAt > 60_000L) lastHoverUnit = null
    }

    // ------------------------------------------------------------------ ovoz balandligi

    /** Ovoz tugmasi bosilganidan beri 3,5 soniya o'tmagan va tafsilotlarda "o'qishni to'xtatmaslik" yoqiq. */
    private fun volumeGuardActive(): Boolean {
        val at = lastVolumeKeyAt
        if (at == 0L || SystemClock.uptimeMillis() - at > VOLUME_GUARD_MS) return false
        return Prefs.volumeKeep(this)
    }

    private fun isVolumeUi(event: AccessibilityEvent, pkg: String?): Boolean {
        if (pkg != null && pkg.contains("systemui", ignoreCase = true)) return true
        val cls = event.className?.toString() ?: return false
        return cls.contains("volume", ignoreCase = true)
    }

    /** Ovoz paneli: hozirgi o'qish to'xtamaydi, fokus panelga o'tmaydi. Nutq jim bo'lsa, panel matni (masalan, "Media ovozi 7") aytiladi. */
    private fun onVolumeUi(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_ANNOUNCEMENT
        ) return
        if (speech.isBusy()) return
        val now = SystemClock.uptimeMillis()
        if (now - lastVolumeSpokenAt < 700L) return
        val text = event.text.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(", ")
        if (text.isEmpty()) return
        lastVolumeSpokenAt = now
        speak(text, false, auto = true)
    }

    private fun setTouchExploration(enabled: Boolean) {
        val info = serviceInfo ?: return
        val mask = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        // Kutish rejimida teginib o'rganishni boshqa ekran o'quvchi boshqaradi: uni hech qachon o'zimiz yoqmaymiz
        val on = enabled && !yielded
        info.flags = if (on) info.flags or mask else info.flags and mask.inv()
        serviceInfo = info
    }

    // ------------------------------------------------------------------ tafsilotlar, tezlik, yangi menyu bandlari

    /** preset: bu qiymat tafsilot darajasiga (past, o'rta, yuqori) kiradimi; qo'lda o'zgartirilsa, faqat shunda daraja "maxsus" bo'ladi. */
    private class DetailToggle(val key: String, val titleRes: Int, val def: Boolean, val preset: Boolean = true)

    private val detailToggles = listOf(
        DetailToggle(Prefs.DET_TYPE_BTN, R.string.det_type_btn, false),
        DetailToggle(Prefs.DET_TYPE, R.string.det_type, true),
        DetailToggle(Prefs.DET_POSITION, R.string.det_position, false),
        DetailToggle(Prefs.DET_HINTS, R.string.det_hints, false),
        DetailToggle(Prefs.DET_WINDOW_TITLES, R.string.det_window_titles, true, preset = false),
        DetailToggle(Prefs.DET_SCREEN_OFF, R.string.det_screen_off, true, preset = false),
        DetailToggle(Prefs.DET_VOLUME_KEEP, R.string.det_volume_keep, true, preset = false),
        DetailToggle(Prefs.DET_EMOJI, R.string.det_emoji, true, preset = false),
        DetailToggle(Prefs.DET_STICKERS, R.string.det_stickers, true, preset = false),
        DetailToggle(Prefs.DET_STICKER_SHORT, R.string.det_sticker_short, false, preset = false),
        DetailToggle(Prefs.DET_PHONE, R.string.det_phone, true, preset = false),
        DetailToggle(Prefs.DET_SCI_UNITS, R.string.det_sci_units, true, preset = false),
        DetailToggle(Prefs.DET_SCI_PHYSICS, R.string.det_sci_physics, true, preset = false),
        DetailToggle(Prefs.DET_SCI_MATH, R.string.det_sci_math, true, preset = false),
        DetailToggle(Prefs.DET_SCI_INFO, R.string.det_sci_info, true, preset = false),
    )

    private fun stateLabel(on: Boolean): String = getString(if (on) R.string.state_on else R.string.state_off)

    private fun presetLabel(name: String): String = getString(
        when (name) {
            "short" -> R.string.details_low
            "full" -> R.string.details_high
            "custom" -> R.string.details_custom
            else -> R.string.details_medium
        }
    )

    private fun speedLabel(level: String): String = getString(
        when (level) {
            "normal" -> R.string.speed_normal
            "turbo" -> R.string.speed_turbo
            else -> R.string.speed_fast
        }
    )

    /** Bosh menyudagi bandlarning yorlig'i: holatga bog'liq bandlarda joriy holat ko'rsatiladi. */
    private fun menuLabel(a: GestureAction): String = when (a) {
        GestureAction.AUTO_TRANSLATE -> getString(R.string.menu_auto_tr, stateLabel(Prefs.autoTranslate(this)))
        GestureAction.DETAILS_MENU -> getString(R.string.menu_details_item, presetLabel(Prefs.verbosityPreset(this)))
        else -> a.displayName(this)
    }

    /** Tafsilotlar menyusi (TalkBack'dagi "Verbosity"): daraja va har bir imkoniyatni shu yerning o'zida yoqish yoki o'chirish. */
    private fun showDetailsMenu() {
        val origin = currentFocus()
        val labels = ArrayList<String>()
        labels.add(getString(R.string.details_level_row, presetLabel(Prefs.verbosityPreset(this))))
        for (t in detailToggles) labels.add(getString(t.titleRes) + ": " + stateLabel(prefs.getBoolean(t.key, t.def)))
        labels.add(getString(R.string.details_open_settings))
        openMenu(getString(R.string.menu_details_title), labels, origin) { which ->
            main.postDelayed({
                try {
                    restoreMenuFocus()
                    when {
                        which == 0 -> adjustVerbosity(true)
                        which == labels.size - 1 -> openScreen(DetailsActivity::class.java)
                        which in 1..detailToggles.size -> toggleDetail(detailToggles[which - 1])
                    }
                } catch (t: Throwable) {
                    Diag.log.log(ErrorCode.FOCUS_NODE_FAILED, mapOf("exc" to t.javaClass.simpleName, "menu" to "details"))
                }
            }, 300L)
        }
    }

    private fun toggleDetail(t: DetailToggle) {
        val now = !prefs.getBoolean(t.key, t.def)
        // Qo'lda o'zgartirilsa daraja "maxsus" bo'ladi (TalkBack kabi). Darajaga kirmaydigan qiymatlar (oyna sarlavhalari,
        // ekran o'chiq, ovoz balandligi, emoji, stikerlar) darajani o'zgartirmaydi: Tafsilotlar ekrani ham shunday ishlaydi.
        val edit = prefs.edit().putBoolean(t.key, now)
        if (t.preset) edit.putString(Prefs.VERBOSITY, "custom")
        edit.apply()
        speak(getString(R.string.msg_detail_state, getString(t.titleRes), stateLabel(now)), true)
    }

    /** Past -> o'rta -> yuqori -> maxsus -> past. forward=false: teskari yo'nalishda. */
    private fun adjustVerbosity(forward: Boolean) {
        val order = listOf("short", "normal", "full", "custom")
        val found = order.indexOf(Prefs.verbosityPreset(this))
        val cur = if (found < 0) 1 else found
        val name = order[(cur + (if (forward) 1 else -1) + order.size) % order.size]
        if (name == "custom") prefs.edit().putString(Prefs.VERBOSITY, "custom").apply() else Prefs.applyPreset(this, name)
        speak(getString(R.string.msg_details_level, presetLabel(name)), true)
    }

    /** Javob tezligi rejimi: oddiy -> tezkor -> eng tezkor -> oddiy. */
    private fun cycleSpeed() {
        val order = listOf("normal", "fast", "turbo")
        val next = order[(order.indexOf(Prefs.speedLevel(this)) + 1).mod(order.size)]
        prefs.edit().putString(Prefs.SPEED, next).apply()
        speak(getString(R.string.msg_speed_set, speedLabel(next)), true)
    }

    /** Tizimning "Matnni nutqqa aylantirish" sozlamalari (TalkBack menyusidagi shu nomli band kabi). */
    private fun openTtsSettings() {
        val candidates = listOf(Intent("com.android.settings.TTS_SETTINGS"), Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        for (i in candidates) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (ex: Throwable) {
                swallowed("ReaderService.openTtsSettings", ex)
            }
        }
        openScreen(SettingsActivity::class.java)
    }

    /** "Tizim amallari" ichki menyusi: orqaga, bosh ekran, so'nggi ilovalar, bildirishnomalar va h.k. */
    private fun showSystemActionsMenu() {
        val actions = GlobalMenu.systemActions
        val origin = currentFocus()
        openMenu(getString(R.string.menu_system_title), actions.map { it.displayName(this) }, origin) { which ->
            val chosen = actions.getOrNull(which)
            if (chosen != null) {
                main.postDelayed({
                    try {
                        restoreMenuFocus()
                        perform(chosen)
                    } catch (t: Throwable) {
                        Diag.log.log(ErrorCode.FOCUS_NODE_FAILED, mapOf("exc" to t.javaClass.simpleName, "menu" to chosen.name))
                    }
                }, 300L)
            }
        }
    }

    /** "Sozlamalar bo'limlari" ichki menyusi: har bir band O'zbegim sozlamalarining tegishli bo'limini to'g'ridan-to'g'ri ochadi. */
    private fun showSettingsLinksMenu() {
        val actions = GlobalMenu.settingsLinks
        val origin = currentFocus()
        openMenu(getString(R.string.menu_settings_title), actions.map { it.displayName(this) }, origin) { which ->
            val chosen = actions.getOrNull(which)
            if (chosen != null) {
                main.postDelayed({
                    try {
                        restoreMenuFocus()
                        perform(chosen)
                    } catch (t: Throwable) {
                        Diag.log.log(ErrorCode.FOCUS_NODE_FAILED, mapOf("exc" to t.javaClass.simpleName, "menu" to chosen.name))
                    }
                }, 300L)
            }
        }
    }

    // ------------------------------------------------------------------ havolalar va rasmga tavsif

    private class LinkItem(val label: String, val open: () -> Unit)

    private val urlPattern = Regex("(?:https?://|www\\.)[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    /**
     * Elementning matnidagi havolalarni yig'adi: bosiladigan bo'laklar (ClickableSpan) va oddiy matndagi manzillar.
     * Parol maydoni o'qilmaydi.
     */
    private fun linksOf(node: AccessibilityNodeInfo, out: MutableList<LinkItem>) {
        if (node.isPassword) return
        val t = node.text ?: return
        val s = t.toString()
        val covered = ArrayList<IntRange>(2)
        if (t is android.text.Spanned) {
            val spans = t.getSpans(0, t.length, android.text.style.ClickableSpan::class.java)
            for (sp in spans) {
                val a = t.getSpanStart(sp)
                val b = t.getSpanEnd(sp)
                if (a < 0 || b <= a || b > s.length) continue
                val label = s.substring(a, b).trim().ifEmpty { s.take(40) }
                covered.add(a until b)
                out.add(LinkItem(label) { sp.onClick(View(applicationContext)) })
            }
        }
        for (m in urlPattern.findAll(s)) {
            if (covered.any { m.range.first in it }) continue
            val url = m.value.trimEnd('.', ',', ')', ';', ':', '!', '?')
            out.add(LinkItem(url) { openUrl(url) })
        }
    }

    private fun openUrl(url: String) {
        val u = if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
            speak(getString(R.string.msg_action_failed), true)
        }
    }

    private fun collectWindowLinks(out: MutableList<LinkItem>) {
        val root = rootInActiveWindow ?: return
        var visited = 0
        fun walk(n: AccessibilityNodeInfo) {
            if (++visited > 400 || out.size >= 30 || !n.isVisibleToUser) return
            linksOf(n, out)
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                walk(c)
            }
        }
        walk(root)
    }

    /** "Mavjud havolalarga o'tish": fokusdagi matndagi havolalar, bo'lmasa ekrandagi hamma havola. Tanlangan havola ochiladi. */
    private fun showLinksMenu() {
        val links = ArrayList<LinkItem>()
        val cur = currentFocus()
        if (cur != null) linksOf(cur, links)
        if (links.isEmpty()) collectWindowLinks(links)
        if (links.isEmpty()) {
            speak(getString(R.string.msg_no_links), true)
            return
        }
        val shown = links.take(30)
        openMenu(getString(R.string.menu_links_title), shown.map { it.label }, cur) { which ->
            val item = shown.getOrNull(which)
            if (item != null) {
                main.postDelayed({
                    try {
                        restoreMenuFocus()
                        item.open()
                    } catch (t: Throwable) {
                        Diag.log.log(ErrorCode.FOCUS_NODE_FAILED, mapOf("exc" to t.javaClass.simpleName, "menu" to "links"))
                        speak(getString(R.string.msg_action_failed), true)
                    }
                }, 300L)
            }
        }
    }

    /**
     * Rasmga tavsif berish (TalkBack'dagi "Describe image"): fokusdagi elementning maydoni kesib olinib, sun'iy intellektga
     * yuboriladi; element maydoni noma'lum bo'lsa butun ekran. Rasm faqat shu amal bajarilganda yuboriladi.
     */
    private fun describeImage() {
        if (Build.VERSION.SDK_INT < 30) {
            speak(getString(R.string.msg_ai_needs_api30), true)
            return
        }
        if (!aiPrecheck()) return
        var area: Rect? = null
        val node = currentFocus()
        if (node != null && !node.isPassword) {
            try {
                val r = Rect()
                node.getBoundsInScreen(r)
                if (r.width() > 8 && r.height() > 8) area = r
            } catch (ex: Throwable) {
                swallowed("ReaderService.describeImage", ex)
            }
        }
        val crop = area
        aiBusy = true
        aiBusySince = SystemClock.uptimeMillis()
        speak(getString(R.string.msg_ai_working), true)
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                Executor { r -> main.post(r) },
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val ctx = applicationContext
                        // Tavsif matn tilida: ovoz o'zbekcha o'qisa o'zbekcha, aks holda tizim tilida
                        val lang = if (Prefs.isUzbekText(ctx)) "uz" else Locale.getDefault().language
                        Online.async({
                            val b64 = jpegBase64Of(screenshot, crop)
                            if (b64 == null) null else AiClient.describe(ctx, b64, lang)
                        }) { result -> aiFinished(result, "describe") }
                    }

                    override fun onFailure(errorCode: Int) {
                        aiFinished(null, "screenshot")
                    }
                }
            )
        } catch (_: Throwable) {
            aiFinished(null, "screenshot")
        }
    }

    // ------------------------------------------------------------------ nutq

    /** Matnni til quvuridan o'tkazib (uch yozuv, til ko'prigi), nutq boshqaruvchisiga beradi. */
    /**
     * auto: foydalanuvchi so'ramagan avtomatik xabar (bildirishnoma, jonli matn, oyna sarlavhasi). Mikrofon band
     * bo'lganda (ovozli xabar yozilayotganda) faqat shular o'qilmaydi; bosib o'qiganlar past ovozda aytiladi.
     */
    private fun speak(text: String, interrupt: Boolean, eventTime: Long = 0L, raw: Boolean = false, auto: Boolean = false) {
        // Kutish rejimida (boshqa ekran o'quvchi ishlayapti) O'zbegim hech narsa aytmaydi: ikki ovoz aralashmasligi uchun
        if (yielded) return
        // Har bir "to'xtatib aytish" tarjimani kutayotgan eski matnni bekor qiladi
        if (interrupt) speakToken++
        speech.speak(if (raw) text else process(text), interrupt, eventTime, isSensitiveContext(), auto)
    }

    /** Maxfiy ilova (bank va h.k.): matn hech qachon tarmoqqa chiqmaydi (onlayn ovoz, tarjima, tavsif). */
    private fun isSensitiveContext(): Boolean = ProfileStore.get(lastPackage)?.sensitive == true

    private fun process(text: String, record: Boolean = true): String {
        if (safeMode) return text // xavfsiz rejim: asl matn
        return try {
            // Matn tili: Tafsilotlar > Matn tili (ovoz paketi tili yoki tizim tili, yoki foydalanuvchi tanlovi)
            val uz = Prefs.isUzbekText(this)
            val base = if (uz) {
                Prefs.pipelineOptions(this, Prefs.engineReadsCyrillic(this, speech.effectiveEngine))
            } else {
                // Matn o'zbekcha emas: o'zbekcha raqam so'zlari, fan qisqartmalari va Rim raqamlari qo'llanmaydi
                Prefs.otherLangOptions(this)
            }
            // Emoji: o'zbekcha nom bilan o'qish faqat matn o'zbekcha bo'lganda; boshqa tilda ovoz dvigateliga qoldiriladi
            val emojiMode = Prefs.emojiMode(this)
            val withPkg = base.copy(
                packageName = lastPackage,
                emoji = if (uz || emojiMode == EmojiMode.SKIP) emojiMode else EmojiMode.KEEP,
                emojiWord = getString(R.string.emoji_word),
            )
            val opts = if (ProfileStore.get(lastPackage)?.bridge == BridgeMode.OFF) withPkg.copy(bridge = BridgeMode.OFF) else withPkg
            val r = ContentPipeline.process(text, opts)
            if (record && r.changes.isNotEmpty()) lastChanges = r.changes
            r.text
        } catch (t: Throwable) {
            if (bridgeErrors++ < 5) Diag.log.log(ErrorCode.LANG_BRIDGE_FAILED, mapOf("exc" to t.javaClass.simpleName))
            text
        }
    }
}
