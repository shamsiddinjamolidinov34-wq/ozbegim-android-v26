package uz.ekranoquvchi.mvp.diag

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

private const val SWALLOW_TAG = "Ozbegim"
private const val SWALLOW_GAP_MS = 1000L
private val swallowedAt = ConcurrentHashMap<String, Long>()

/**
 * Ataylab e'tiborsiz qoldiriladigan (best-effort) istisnolar uchun: ish davom etadi, lekin sababi tizim jurnaliga
 * (logcat, teg "Ozbegim") yoziladi, shunda nosozlik paytida xato qayerda bo'lganini aniqlash mumkin bo'ladi.
 *
 * Istisno matni yozilmaydi (u ekran matnini o'z ichiga olishi mumkin): faqat joy va sinf nomi (CrashGuard bilan bir xil qoida).
 * Tez-tez takrorlanadigan xato jurnalni to'ldirmasligi va ekran o'quvchini sekinlashtirmasligi uchun har bir joydan
 * bir soniyada ko'pi bilan bitta yozuv chiqadi.
 *
 * catch blokida chaqirilgani uchun bu funksiya hech qachon istisno tashlamaydi.
 */
fun swallowed(place: String, t: Throwable) {
    val now = SystemClock.uptimeMillis()
    val last = swallowedAt[place]
    if (last != null && now - last < SWALLOW_GAP_MS) return
    swallowedAt[place] = now
    Log.w(SWALLOW_TAG, place + ": " + t.javaClass.simpleName)
}
