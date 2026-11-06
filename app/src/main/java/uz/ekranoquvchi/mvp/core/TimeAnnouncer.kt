package uz.ekranoquvchi.mvp.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import uz.ekranoquvchi.mvp.R
import uz.ekranoquvchi.mvp.ReaderService
import uz.ekranoquvchi.mvp.content.UzNumbers
import uz.ekranoquvchi.mvp.diag.swallowed
import uz.ekranoquvchi.mvp.util.Prefs
import java.util.Calendar
import java.util.Locale

/**
 * Vaqt va batareya e'loni (Sozlamalar > Tafsilotlar > Vaqt va batareya).
 *
 * Vaqt: har 30 yoki 60 daqiqada, soatning aniq chegarasida (14:00, 14:30). Format 24 yoki 12 soatlik, o'qish uslubi ikkita:
 *  - "past": "Soat o'n bir, soat o'n birdan o'ttiz daqiqa o'tdi";
 *  - "digital": "Soat o'n bir, soat o'n bir o'ttiz".
 * Ikki yo'l bir vaqtda ishlaydi: qurilma uyg'oq bo'lsa, Handler aniq vaqtda ishga tushiradi; qurilma uxlayotgan
 * (ekran o'chiq) bo'lsa, AlarmManager uni uyg'otadi. Qaysi biri birinchi kelsa, e'lon bir marta aytiladi.
 * Aniq alarm ruxsati (USE_EXACT_ALARM / SCHEDULE_EXACT_ALARM) bo'lmasa, alarm noaniqroq (bir necha daqiqagacha
 * kechikishi mumkin); ekran yoqiq paytida Handler baribir aniq vaqtda aytadi.
 *
 * Jadval uzilmaydi: har bir signal (takroriy bo'lsa ham) keyingisini rejalashtiradi; ekran yoqilganda va batareya
 * hodisalarida jadval tekshiriladi, kechikkan chegara bo'lsa darhol aytiladi, jadval yo'qolgan bo'lsa qayta tuziladi.
 *
 * Batareya: har 10 foizda yoki foydalanuvchi belgilagan qadamda (masalan, har 2 foizda: 98, 96, 94...).
 * Kuzatish xizmat ishlayotganda ACTION_BATTERY_CHANGED orqali bo'ladi (ekran o'chiq paytida ham).
 *
 * Matn o'qilayotgan paytda e'lon kelsa, "wait" rejimida o'qish tugashi kutiladi, "interrupt" rejimida darhol aytiladi.
 */
object TimeAnnouncer {
    const val ACTION_TICK = "uz.ekranoquvchi.mvp.TIME_TICK"
    private const val REQUEST_CODE = 7301

    /** Signal chegaradan bir necha soniya oldin kelsa ham shu chegaraga tegishli hisoblanadi (soat sinxronlanishi, yaxlitlash). */
    private const val EARLY_TOLERANCE_MS = 5_000L

    private val main = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null
    private var prefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var tick: Runnable? = null

    /** Hozir rejalashtirilgan chegara (millisekund) va oxirgi aytilgan chegara. */
    private var scheduledAt = 0L
    private var lastFiredAt = 0L
    private var prevLevel = -1

    fun start(context: Context) {
        val app = context.applicationContext
        registerReceiver(app)
        registerPrefListener(app)
        lastFiredAt = 0L
        reschedule(app)
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        cancelSchedule(app)
        try {
            receiver?.let { app.unregisterReceiver(it) }
        } catch (ex: Throwable) {
            swallowed("TimeAnnouncer.stop", ex)
        }
        receiver = null
        try {
            prefListener?.let { Prefs.get(app).unregisterOnSharedPreferenceChangeListener(it) }
        } catch (ex: Throwable) {
            swallowed("TimeAnnouncer.stop", ex)
        }
        prefListener = null
        prevLevel = -1
        lastFiredAt = 0L
    }

    // ------------------------------------------------------------------ vaqt

    private fun intervalMinutes(app: Context): Int = when (Prefs.timeMode(app)) {
        "30" -> 30
        "60" -> 60
        else -> 0
    }

    /** now dan keyingi eng yaqin chegara (qat'iy keyin): 30 daqiqada :00 va :30, 60 daqiqada :00. */
    fun nextTrigger(interval: Int, now: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val minute = cal.get(Calendar.MINUTE)
        cal.add(Calendar.MINUTE, interval - (minute % interval))
        return cal.timeInMillis
    }

    /**
     * now ga tegishli chegara: now dan sal keyingi (EARLY_TOLERANCE_MS) vaqtni oldingi chegaraga yaxlitlaydi.
     * Signal biroz erta kelsa, o'sha chegara; kechiksa (noaniq alarm), eng so'nggi o'tgan chegara.
     */
    fun boundaryFor(interval: Int, now: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now + EARLY_TOLERANCE_MS
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.MINUTE, -(cal.get(Calendar.MINUTE) % interval))
        return cal.timeInMillis
    }

    /** Jadvalni qaytadan tuzadi (sozlama, vaqt yoki vaqt zonasi o'zgarganda; har e'londan keyin). */
    fun reschedule(context: Context) {
        val app = context.applicationContext
        cancelSchedule(app)
        val interval = intervalMinutes(app)
        if (interval == 0) return
        val now = System.currentTimeMillis()
        // Soat orqaga surilgan bo'lsa, eski "oxirgi aytilgan" vaqt jadvalni kelajakka itarib yubormasin
        if (lastFiredAt > now + EARLY_TOLERANCE_MS) lastFiredAt = 0L
        var at = nextTrigger(interval, now)
        // Aytilgan chegara qayta rejalashtirilmaydi: signal sal erta kelsa ham jadval keyingisiga o'tadi
        if (at <= lastFiredAt) at = nextTrigger(interval, lastFiredAt)
        val trigger = at
        scheduledAt = trigger
        val r = Runnable { fire(app, trigger) }
        tick = r
        main.postDelayed(r, maxOf(0L, trigger - now))
        scheduleAlarm(app, trigger)
    }

    private fun cancelSchedule(app: Context) {
        tick?.let { main.removeCallbacks(it) }
        tick = null
        scheduledAt = 0L
        try {
            val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(alarmIntent(app))
        } catch (ex: Throwable) {
            swallowed("TimeAnnouncer.cancelSchedule", ex)
        }
    }

    private fun alarmIntent(app: Context): PendingIntent {
        val i = Intent(app, TimeAlarmReceiver::class.java).setAction(ACTION_TICK)
        return PendingIntent.getBroadcast(app, REQUEST_CODE, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun scheduleAlarm(app: Context, at: Long) {
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmIntent(app)
        try {
            val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: Throwable) {
            // Ruxsat yo'q yoki tizim rad etdi: noaniq alarmga o'tamiz
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (ex: Throwable) {
                swallowed("TimeAnnouncer.scheduleAlarm", ex)
            }
        }
    }

    /** AlarmManager signali keldi (TimeAlarmReceiver). */
    fun onAlarm(context: Context) {
        val app = context.applicationContext
        val interval = intervalMinutes(app)
        if (interval == 0) return
        fire(app, boundaryFor(interval, System.currentTimeMillis()))
    }

    /**
     * Chegara keldi (Handler yoki alarm). Keyingi e'lon HAR HOLDA rejalashtiriladi: ilgari takroriy signal
     * (bir chegara ikki marta kelsa) jadvalni butunlay uzib qo'yar edi va e'lonlar to'xtab qolardi.
     */
    private fun fire(app: Context, boundary: Long) {
        val duplicate = boundary <= lastFiredAt
        if (!duplicate) lastFiredAt = boundary
        reschedule(app)
        if (duplicate || intervalMinutes(app) == 0) return
        // Erta kelsa chegara vaqti aytiladi (14:29 emas, 14:30), kechiksa haqiqiy vaqt
        val spoken = maxOf(System.currentTimeMillis(), boundary)
        say(app, timeText(app, calendarAt(spoken)), true)
    }

    /**
     * Jadval sog'lomligini tekshiradi (ekran yoqilganda, batareya hodisalarida): jadval yo'qolgan bo'lsa qayta tuzadi;
     * chegara o'tib ketgan, lekin hali aytilmagan bo'lsa (qurilma uxlagan) darhol aytadi.
     */
    private fun ensureScheduled(app: Context) {
        if (intervalMinutes(app) == 0) return
        val at = scheduledAt
        if (tick == null || at == 0L) {
            reschedule(app)
        } else if (System.currentTimeMillis() >= at) {
            fire(app, at)
        }
    }

    private fun calendarAt(ms: Long): Calendar {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ms
        return cal
    }

    /**
     * Vaqtni tanlangan format (12/24) va uslub (past/digital) bilan o'qiladigan matnga aylantiradi.
     * O'zbekcha tizim tilida raqamlar so'z bilan; boshqa tilda "14:30" ko'rinishi (dvigatel o'z tilida o'qiydi).
     */
    fun timeText(app: Context, cal: Calendar = Calendar.getInstance()): String {
        val h24 = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val twelve = Prefs.timeFormat(app) == "12"
        val hour = if (twelve) (if (h24 % 12 == 0) 12 else h24 % 12) else h24
        val uz = Locale.getDefault().language == "uz"
        if (!uz) {
            val t = String.format(Locale.getDefault(), "%d:%02d", hour, m)
            return if (twelve) {
                app.getString(R.string.tb_say_12, app.getString(partOfDay(h24)), t)
            } else {
                app.getString(R.string.msg_status_time, t)
            }
        }
        val sentence = uzSentence(app, hour, m, Prefs.timeStyle(app))
        return if (twelve) app.getString(R.string.tb_say_part, app.getString(partOfDay(h24)), sentence) else sentence
    }

    /**
     * "past": "Soat o'n bir, soat o'n birdan o'ttiz daqiqa o'tdi"; "digital": "Soat o'n bir, soat o'n bir o'ttiz".
     * Aniq soatda (daqiqa 0) faqat "Soat o'n bir".
     */
    private fun uzSentence(app: Context, hour: Int, minute: Int, style: String): String {
        val h = UzNumbers.words(hour)
        if (minute == 0) return app.getString(R.string.msg_status_time, h)
        return if (style == "digital") {
            app.getString(R.string.tb_say_digital, h, UzNumbers.time(hour, minute))
        } else {
            app.getString(R.string.tb_say_past, h, UzNumbers.ablative(hour), UzNumbers.words(minute))
        }
    }

    private fun partOfDay(h: Int): Int = when (h) {
        in 4..5 -> R.string.tb_part_dawn
        in 6..11 -> R.string.tb_part_morning
        in 12..17 -> R.string.tb_part_day
        in 18..21 -> R.string.tb_part_evening
        else -> R.string.tb_part_night
    }

    // ------------------------------------------------------------------ batareya

    private fun levelOf(i: Intent): Int {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level < 0 || scale <= 0) -1 else level * 100 / scale
    }

    private fun isCharging(i: Intent): Boolean = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0

    fun batteryText(app: Context, level: Int, charging: Boolean): String {
        val uz = Locale.getDefault().language == "uz"
        val shown = if (uz) UzNumbers.words(level) else level.toString()
        val sb = StringBuilder(app.getString(R.string.msg_status_battery, shown))
        if (charging) sb.append(", ").append(app.getString(R.string.msg_charging))
        return sb.toString()
    }

    private fun onBattery(app: Context, i: Intent) {
        // Batareya hodisalari tez-tez keladi: vaqt jadvalining sog'lomligini shu bilan ham tekshiramiz
        ensureScheduled(app)
        val level = levelOf(i)
        if (level < 0) return
        val prev = prevLevel
        prevLevel = level
        if (prev < 0 || level == prev) return
        val mode = Prefs.batteryMode(app)
        if (mode == "off") return
        val step = if (mode == "custom") Prefs.batteryStep(app) else 10
        // Chegarani kesib o'tish: qadam karralisiga (masalan, 98, 96, ... yoki 90, 80, ...) yetganda aytiladi;
        // o'qish oralig'ida bir necha foiz sakrasa ham bir marta
        val announce = if (level < prev) {
            val m = (prev - 1) / step * step
            m > 0 && level <= m
        } else {
            val m = (prev / step + 1) * step
            level >= m
        }
        if (announce || (level == 100 && prev < 100)) {
            say(app, batteryText(app, level, isCharging(i)), true)
        }
    }

    // ------------------------------------------------------------------ umumiy

    private fun registerReceiver(app: Context) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val i = intent ?: return
                when (i.action) {
                    Intent.ACTION_BATTERY_CHANGED -> onBattery(app, i)
                    Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                        // Soat yoki vaqt zonasi o'zgardi: eski "oxirgi aytilgan" vaqt endi ma'nosiz
                        lastFiredAt = 0L
                        reschedule(app)
                    }
                    Intent.ACTION_SCREEN_ON -> ensureScheduled(app)
                }
            }
        }
        try {
            val f = IntentFilter()
            f.addAction(Intent.ACTION_BATTERY_CHANGED)
            f.addAction(Intent.ACTION_TIME_CHANGED)
            f.addAction(Intent.ACTION_TIMEZONE_CHANGED)
            f.addAction(Intent.ACTION_SCREEN_ON)
            val sticky = app.registerReceiver(r, f)
            receiver = r
            if (sticky != null) prevLevel = levelOf(sticky)
        } catch (ex: Throwable) {
            swallowed("TimeAnnouncer.registerReceiver", ex)
        }
    }

    private fun registerPrefListener(app: Context) {
        if (prefListener != null) return
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Prefs.TIME_MODE) {
                lastFiredAt = 0L
                reschedule(app)
            }
        }
        try {
            Prefs.get(app).registerOnSharedPreferenceChangeListener(l)
            prefListener = l
        } catch (ex: Throwable) {
            swallowed("TimeAnnouncer.registerPrefListener", ex)
        }
    }

    private fun say(app: Context, text: String, respectBusy: Boolean) {
        val svc = ReaderService.instance ?: return
        val wait = respectBusy && Prefs.timeBusy(app) == "wait"
        svc.announceTimed(text, wait)
    }

    /** Sozlamalar ekranidagi "hozir ayt" tugmalari: darhol aytiladi. */
    fun speakTimeNow(context: Context) {
        val app = context.applicationContext
        say(app, timeText(app), false)
    }

    fun speakBatteryNow(context: Context) {
        val app = context.applicationContext
        val i = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = levelOf(i)
        if (level < 0) return
        say(app, batteryText(app, level, isCharging(i)), false)
    }
}
