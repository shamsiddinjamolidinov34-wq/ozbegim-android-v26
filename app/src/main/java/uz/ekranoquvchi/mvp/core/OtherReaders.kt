package uz.ekranoquvchi.mvp.core

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import uz.ekranoquvchi.mvp.diag.swallowed

/**
 * Boshqa ekran o'quvchilarni (TalkBack, Samsung Voice Assistant, Jieshuo va h.k.) aniqlaydi.
 *
 * Ikkita ikki ekran o'quvchi bir vaqtda ishlasa, ikkalasi ham barmoq ishoralarini ushlashga urinadi va ikki ovoz
 * aralashib gapiradi. Shuning uchun O'zbegim boshqa ekran o'quvchi yoqilganda kutish rejimiga o'tadi (ReaderService.enterStandby).
 *
 * Aniqlash usuli:
 *  1) tizimning yoqilgan maxsus imkoniyat xizmatlari ro'yxati: "teginib o'rganish"ni so'ray oladigan (yoki so'ragan)
 *     har qanday xizmat ekran o'quvchi hisoblanadi. Shu sababli noma'lum yangi ekran o'quvchilar ham aniqlanadi;
 *     faqat gapiradigan, lekin ishora ushlamaydigan xizmatlar (masalan, "Select to Speak") hisobga olinmaydi;
 *  2) zaxira: sozlamalardagi yoqilgan xizmatlar qatori ichida ma'lum ekran o'quvchining paketi bor-yo'qligi
 *     (tizim ro'yxati bo'sh qaytsa yoki xizmat hali bog'lanmagan bo'lsa ham ishlaydi).
 * O'zbegimning o'zi (paket nomi bo'yicha) hech qachon hisobga olinmaydi.
 */
object OtherReaders {
    /** Ma'lum ekran o'quvchilar paketlari (zaxira usul uchun; asosiy usul ularga bog'liq emas). */
    private val KNOWN = setOf(
        "com.google.android.marvin.talkback",
        "com.android.talkback",
        "com.samsung.android.accessibility.talkback",
        "com.samsung.android.app.talkback",
        "com.nirenr.talkman",
        "com.dianming.phoneapp",
    )

    /** Yoqilgan boshqa ekran o'quvchining paket nomi; yo'q bo'lsa null. */
    fun find(ctx: Context): String? {
        val own = ctx.packageName
        try {
            val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val list = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (list != null) {
                for (info in list) {
                    val pkg = packageOf(info) ?: continue
                    if (pkg == own) continue
                    if (isScreenReader(info, pkg)) return pkg
                }
            }
        } catch (ex: Throwable) {
            swallowed("OtherReaders.find", ex)
        }
        try {
            val raw = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            if (!raw.isNullOrEmpty()) {
                for (part in raw.split(':')) {
                    val pkg = ComponentName.unflattenFromString(part.trim())?.packageName ?: continue
                    if (pkg != own && pkg in KNOWN) return pkg
                }
            }
        } catch (ex: Throwable) {
            swallowed("OtherReaders.find", ex)
        }
        return null
    }

    private fun packageOf(info: AccessibilityServiceInfo): String? {
        val fromResolve = info.resolveInfo?.serviceInfo?.packageName
        if (!fromResolve.isNullOrEmpty()) return fromResolve
        return info.id?.substringBefore('/')?.takeIf { it.isNotEmpty() }
    }

    private fun isScreenReader(info: AccessibilityServiceInfo, pkg: String): Boolean {
        if (pkg in KNOWN) return true
        val canExplore = (info.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION) != 0
        val explores = (info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE) != 0
        return canExplore || explores
    }

    /**
     * Tizim sozlamalaridagi yoqilgan xizmatlar o'zgarganda (foydalanuvchi TalkBack'ni yoqdi yoki o'chirdi) onChange
     * chaqiriladi. Qo'shimcha ruxsat kerak emas: bu sozlamalarni har bir ilova o'qiy oladi va o'zgarishini kuzata oladi.
     */
    class Watcher(private val ctx: Context, handler: Handler, private val onSettingsChanged: () -> Unit) {
        private val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                onSettingsChanged()
            }
        }
        private var registered = false

        fun start() {
            if (registered) return
            try {
                val cr = ctx.contentResolver
                cr.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer)
                cr.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED), false, observer)
                registered = true
            } catch (ex: Throwable) {
                swallowed("OtherReaders.start", ex)
            }
        }

        fun stop() {
            if (!registered) return
            registered = false
            try {
                ctx.contentResolver.unregisterContentObserver(observer)
            } catch (ex: Throwable) {
                swallowed("OtherReaders.stop", ex)
            }
        }
    }
}
