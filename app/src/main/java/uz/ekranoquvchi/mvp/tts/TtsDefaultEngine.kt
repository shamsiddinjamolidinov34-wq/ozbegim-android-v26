package uz.ekranoquvchi.mvp.tts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import uz.ekranoquvchi.mvp.ReaderService
import uz.ekranoquvchi.mvp.core.TtsDefaultSetup
import uz.ekranoquvchi.mvp.diag.swallowed
import uz.ekranoquvchi.mvp.util.Prefs

/**
 * Dvigatelni tizimning "Afzal ko'rilgan mexanizm"i qilib o'rnatish.
 *
 * Android oddiy ilovaga bu sozlamani o'zgartirishga ruxsat bermaydi (Settings.Secure.tts_default_synth faqat
 * WRITE_SECURE_SETTINGS bilan yoziladi). Shuning uchun uch yo'l, tezdan sekinga:
 *  1) DIRECT: WRITE_SECURE_SETTINGS berilgan bo'lsa (kompyuterdan bir marta: adb shell pm grant ...), sozlama bir zumda yoziladi;
 *  2) AUTOMATION: O'zbegim ekran o'quvchi xizmati yoqiq bo'lsa, u tizim sozlamalarini o'zi ochib, "Afzal ko'rilgan mexanizm"dan
 *     tanlaydi va ogohlantirishda OK'ni bosadi (core/TtsDefaultSetup);
 *  3) NEED_SERVICE: xizmat o'chiq bo'lsa, sozlamalar ochiladi va foydalanuvchi qo'lda tanlaydi (matn bilan yo'l-yo'riq beriladi).
 */
object TtsDefaultEngine {
    enum class Outcome { ALREADY, DIRECT, AUTOMATION, NEED_SERVICE, FAILED }

    const val WRITE_PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"

    fun canWriteDirectly(c: Context): Boolean = try {
        c.checkSelfPermission(WRITE_PERMISSION) == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /** Kompyuterdan bir marta bajariladigan buyruq: shundan keyin tugma sozlamalarni ochmasdan, bir zumda ishlaydi. */
    fun adbCommand(c: Context): String = "adb shell pm grant " + c.packageName + " " + WRITE_PERMISSION

    private fun writeDirect(c: Context, pkg: String): Boolean = try {
        Settings.Secure.putString(c.contentResolver, EngineChoice.DEFAULT_SYNTH_KEY, pkg)
        EngineChoice.systemDefault(c) == pkg
    } catch (_: Throwable) {
        false
    }

    /**
     * target dvigatelni afzal ko'rilgan mexanizm qilishga urinadi. onFinished faqat AUTOMATION natijasida,
     * jarayon tugagach (asosiy oqimda) chaqiriladi; boshqa natijalar qaytarilgan qiymatdan ma'lum bo'ladi.
     * O'zbegimni tanlashdan oldin hozirgi dvigatel eslab qolinadi: keyin shunga qaytarish mumkin.
     */
    fun select(c: Context, target: String, onFinished: (Boolean) -> Unit): Outcome {
        val app = c.applicationContext
        val current = EngineChoice.systemDefault(app)
        if (current == target) return Outcome.ALREADY
        if (target == app.packageName && current != null) Prefs.setTtsPrevDefault(app, current)
        if (canWriteDirectly(app) && writeDirect(app, target)) return Outcome.DIRECT
        if (ReaderService.instance == null) {
            openSettings(c)
            return Outcome.NEED_SERVICE
        }
        return if (TtsDefaultSetup.start(app, target, onFinished)) Outcome.AUTOMATION else Outcome.FAILED
    }

    /** Tizimning "Matnni nutqqa aylantirish" sozlamalarini ochadi (qo'lda tanlash uchun). */
    fun openSettings(c: Context) {
        try {
            c.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
            try {
                c.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (ex: Throwable) {
                swallowed("TtsDefaultEngine.openSettings", ex)
            }
        }
    }
}
