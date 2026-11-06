package uz.ekranoquvchi.mvp.core

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import uz.ekranoquvchi.mvp.R
import uz.ekranoquvchi.mvp.ReaderService
import uz.ekranoquvchi.mvp.diag.Diag
import uz.ekranoquvchi.mvp.diag.ErrorCode
import uz.ekranoquvchi.mvp.diag.swallowed
import uz.ekranoquvchi.mvp.tts.EngineChoice

/**
 * "Afzal ko'rilgan mexanizm"ni O'zbegim xizmati orqali avtomatik tanlash (tizim sozlamalarida bitta tugma bosish o'rniga).
 *
 * Qadamlar (har 350 ms da holat tekshiriladi, eng ko'pi bilan 30 soniya):
 *  1) "Matnni nutqqa aylantirish" sozlamalari ochiladi;
 *  2) ekrandan kerakli dvigatelning nomi (EngineChoice.engineLabel: Sozlamalar aynan shu nomni ko'rsatadi, shuning uchun
 *     tizim tilidan qat'i nazar topiladi) qidirilib, qatori bosiladi;
 *  3) nom ko'rinmasa, hozirgi dvigatel qatori bosiladi (ba'zi telefonlarda ro'yxat shu orqali ochiladi), keyin sahifa aylantiriladi;
 *  4) tanlovdan keyin chiqadigan ogohlantirish oynasida OK (android:id/button1) bosiladi;
 *  5) tizim sozlamasi haqiqatan o'zgarganini tekshiradi (EngineChoice.systemDefault), muvaffaqiyatda sozlamalardan chiqib,
 *     natijani ovoz bilan aytadi; bo'lmasa sozlamalarni ochiq qoldirib, qo'lda tanlash yo'lini aytadi.
 *
 * Faqat tizim sozlamalari oynalarida ish bajaradi (paket nomida "settings" bor), boshqa ilovalarga tegmaydi.
 * Xizmat nutqiga o'zgartirish kiritilmaydi: bu yerdagi e'lonlar ReaderService.announce orqali aytiladi.
 */
object TtsDefaultSetup {
    private const val FIRST_TICK_MS = 900L
    private const val TICK_MS = 350L
    private const val TOTAL_MS = 30_000L
    private const val PICK_GRACE_MS = 12_000L
    private const val MAX_NODES = 400
    private const val MAX_SCROLLS = 6
    private const val BACK_STEP_MS = 450L
    private const val MAX_BACKS = 4
    private const val OK_BUTTON_ID = "android:id/button1"
    private const val SETTINGS_ACTION = "com.android.settings.TTS_SETTINGS"

    private class Run(
        val app: Context,
        val target: String,
        val targetLabel: String,
        val currentLabel: String,
        val onFinished: (Boolean) -> Unit,
    ) {
        val startedAt: Long = SystemClock.uptimeMillis()
        var pickedAt = 0L
        var openedPicker = false
        var scrolls = 0
    }

    private val main = Handler(Looper.getMainLooper())
    private var run: Run? = null
    private val tickTask = Runnable { tick() }

    fun isRunning(): Boolean = run != null

    /** true: jarayon boshlandi. false: allaqachon ketyapti yoki sozlamalarni ochib bo'lmadi. Asosiy oqimdan chaqiriladi. */
    fun start(context: Context, target: String, onFinished: (Boolean) -> Unit): Boolean {
        if (run != null) return false
        val app = context.applicationContext
        try {
            Diag.init(app)
        } catch (ex: Throwable) {
            swallowed("TtsDefaultSetup.start", ex)
        }
        val current = EngineChoice.systemDefault(app)
        val currentLabel = if (current != null && current != target) EngineChoice.engineLabel(app, current) else ""
        val r = Run(app, target, EngineChoice.engineLabel(app, target), currentLabel, onFinished)
        try {
            app.startActivity(Intent(SETTINGS_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
            return false
        }
        run = r
        say(app.getString(R.string.tts_msg_auto_started))
        main.removeCallbacks(tickTask)
        main.postDelayed(tickTask, FIRST_TICK_MS)
        return true
    }

    fun cancel() {
        run = null
        main.removeCallbacks(tickTask)
    }

    private fun say(text: String) {
        try {
            ReaderService.instance?.announce(text)
        } catch (ex: Throwable) {
            swallowed("TtsDefaultSetup.say", ex)
        }
    }

    private fun log(step: String) {
        try {
            Diag.log.log(ErrorCode.TTS_DEFAULT_SETUP, mapOf("step" to step))
        } catch (ex: Throwable) {
            swallowed("TtsDefaultSetup.log", ex)
        }
    }

    // ------------------------------------------------------------------ jarayon

    private fun tick() {
        val r = run ?: return
        val svc = ReaderService.instance
        if (svc == null) {
            finish(r, false, "service_gone")
            return
        }
        if (EngineChoice.systemDefault(r.app) == r.target) {
            finish(r, true, "ok")
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - r.startedAt > TOTAL_MS) {
            finish(r, false, "timeout")
            return
        }
        try {
            step(svc, r, now)
        } catch (t: Throwable) {
            log("exc_" + t.javaClass.simpleName)
        }
        if (run === r) main.postDelayed(tickTask, TICK_MS)
    }

    private fun step(svc: AccessibilityService, r: Run, now: Long) {
        val roots = settingsRoots(svc)
        if (roots.isEmpty()) return
        // Tanlov bosilgan: ogohlantirish oynasi bo'lsa OK bosiladi, o'zgarishni keyingi tick tekshiradi
        if (r.pickedAt > 0L) {
            for (root in roots) {
                if (clickPositive(root)) break
            }
            if (now - r.pickedAt > PICK_GRACE_MS) finish(r, false, "no_effect")
            return
        }
        for (root in roots) {
            val node = findLabeled(root, r.targetLabel) ?: continue
            val click = clickableOf(node) ?: continue
            if (click.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                r.pickedAt = now
                log("picked")
                return
            }
        }
        if (!r.openedPicker && r.currentLabel.isNotEmpty()) {
            for (root in roots) {
                val node = findLabeled(root, r.currentLabel) ?: continue
                val click = clickableOf(node) ?: continue
                if (click.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    r.openedPicker = true
                    log("picker_opened")
                    return
                }
            }
        }
        if (r.scrolls < MAX_SCROLLS) {
            for (root in roots) {
                if (scrollOnce(root)) {
                    r.scrolls += 1
                    return
                }
            }
        }
    }

    private fun finish(r: Run, ok: Boolean, why: String) {
        if (run !== r) return
        run = null
        main.removeCallbacks(tickTask)
        log("end_" + why)
        if (ok) {
            // Muvaffaqiyat: sozlamalardan chiqamiz (orqaga), keyin natija aytiladi: e'lon oyna nomi bilan qirqilmasin
            goBack(0) {
                say(r.app.getString(R.string.tts_msg_done))
                r.onFinished(true)
            }
        } else {
            // Muvaffaqiyatsiz: sozlamalar ochiq qoladi, foydalanuvchi qo'lda tanlaydi
            say(r.app.getString(R.string.tts_msg_failed))
            r.onFinished(false)
        }
    }

    /** Tizim sozlamalari oynasida bo'lsa, orqaga qaytadi (ilovamizga yetguncha, eng ko'pi bilan MAX_BACKS marta). */
    private fun goBack(count: Int, done: () -> Unit) {
        val svc = ReaderService.instance
        if (svc == null || count >= MAX_BACKS) {
            done()
            return
        }
        val root = try {
            svc.rootInActiveWindow
        } catch (_: Throwable) {
            null
        }
        if (root == null || !isSettings(root)) {
            done()
            return
        }
        svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        main.postDelayed({ goBack(count + 1, done) }, BACK_STEP_MS)
    }

    // ------------------------------------------------------------------ tugunlarni qidirish

    private fun isSettings(n: AccessibilityNodeInfo): Boolean {
        val p = n.packageName?.toString() ?: return false
        return p.contains("settings", ignoreCase = true)
    }

    /** Faol oyna va (oynalar ro'yxati ochiq bo'lsa) boshqa oynalar ichidan tizim sozlamalariga tegishlilari (ogohlantirish oynasi ham). */
    private fun settingsRoots(svc: AccessibilityService): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>(2)
        val active = svc.rootInActiveWindow
        if (active != null && isSettings(active)) out.add(active)
        try {
            val wins = svc.windows
            if (wins != null) {
                for (w in wins) {
                    val root = w.root ?: continue
                    if (isSettings(root) && !out.contains(root)) out.add(root)
                }
            }
        } catch (ex: Throwable) {
            swallowed("TtsDefaultSetup.settingsRoots", ex)
        }
        return out
    }

    private fun norm(s: String): String =
        s.trim().lowercase()
            .replace('\u2018', '\'').replace('\u2019', '\'').replace('\u02BB', '\'').replace('\u02BC', '\'').replace('`', '\'')

    /**
     * Matni nomga aynan teng bo'lgan ko'rinadigan tugunni topadi (avval matn, keyin tavsif). "Ichida bor" bo'yicha
     * qidirilmaydi: aks holda dvigatel nomi bilan boshlanadigan "sozlamalar" tugmasi bosilib qolardi.
     */
    private fun findLabeled(root: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        if (label.isEmpty()) return null
        val want = norm(label)
        var byText: AccessibilityNodeInfo? = null
        var byDesc: AccessibilityNodeInfo? = null
        var visited = 0
        fun walk(n: AccessibilityNodeInfo) {
            if (byText != null || ++visited > MAX_NODES) return
            if (n.isVisibleToUser) {
                val t = n.text?.toString()
                if (t != null && norm(t) == want) {
                    byText = n
                    return
                }
                if (byDesc == null) {
                    val d = n.contentDescription?.toString()
                    val cls = n.className?.toString().orEmpty()
                    if (d != null && norm(d) == want && !cls.contains("Image")) byDesc = n
                }
            }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                walk(c)
                if (byText != null) return
            }
        }
        walk(root)
        return byText ?: byDesc
    }

    /** Tugunning o'zi yoki eng yaqin bosiladigan ota-onasi (qator). */
    private fun clickableOf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth <= 6) {
            if (n.isClickable && n.isEnabled) return n
            n = n.parent
            depth += 1
        }
        return null
    }

    private fun clickPositive(root: AccessibilityNodeInfo): Boolean {
        val list = try {
            root.findAccessibilityNodeInfosByViewId(OK_BUTTON_ID)
        } catch (_: Throwable) {
            null
        }
        if (list != null) {
            for (n in list) {
                if (n.isVisibleToUser && n.isEnabled && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
        }
        return false
    }

    private fun scrollOnce(root: AccessibilityNodeInfo): Boolean {
        var found: AccessibilityNodeInfo? = null
        var visited = 0
        fun walk(n: AccessibilityNodeInfo) {
            if (found != null || ++visited > MAX_NODES) return
            if (n.isScrollable) {
                found = n
                return
            }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                walk(c)
                if (found != null) return
            }
        }
        walk(root)
        val f = found ?: return false
        return f.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }
}
