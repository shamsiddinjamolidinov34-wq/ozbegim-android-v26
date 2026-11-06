package uz.ekranoquvchi.mvp.ui

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import uz.ekranoquvchi.mvp.R
import uz.ekranoquvchi.mvp.ReaderService
import uz.ekranoquvchi.mvp.content.EngineProfiles
import uz.ekranoquvchi.mvp.diag.swallowed
import uz.ekranoquvchi.mvp.tts.EngineChoice
import uz.ekranoquvchi.mvp.tts.TtsDefaultEngine
import uz.ekranoquvchi.mvp.util.Prefs
import uz.ekranoquvchi.mvp.util.SystemUtil

/**
 * O'zbegimni tizimning "Afzal ko'rilgan mexanizm"i qilishni bitta tugma bilan yoqish va qaytarish (bosh ekran va sozlamalar uchun).
 * Tugma matni holatni aytadi: yoqilgan bo'lsa, bosilganda oldingi dvigatelga qaytariladi.
 * Haqiqiy ish TtsDefaultEngine'da: WRITE_SECURE_SETTINGS bo'lsa bir zumda, bo'lmasa ekran o'quvchi xizmati sozlamalarni o'zi bosadi.
 */
object TtsEngineUi {
    fun isOn(c: Context): Boolean = EngineChoice.isDefault(c)

    fun buttonText(c: Context): String = c.getString(if (isOn(c)) R.string.tts_btn_on else R.string.tts_btn_off)

    /** Tugma bosildi. onChanged: holat o'zgargan bo'lishi mumkin (tugma matnini yangilash uchun), asosiy oqimda. */
    fun press(activity: Activity, onChanged: () -> Unit) {
        if (isOn(activity)) disable(activity, onChanged) else enable(activity, onChanged)
    }

    private fun enable(activity: Activity, onChanged: () -> Unit) {
        // Ovoz beruvchi dvigatel bo'lmasa, O'zbegimni tanlash butun telefonni jim qilib qo'yardi: avval o'rnatish taklif qilinadi
        if (EngineChoice.backendPackage(activity) == null) {
            AlertDialog.Builder(activity)
                .setTitle(R.string.tts_dialog_title)
                .setMessage(R.string.tts_need_backend)
                .setPositiveButton(R.string.btn_install_rhvoice) { _, _ -> SystemUtil.openStorePage(activity, EngineProfiles.RHVOICE) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        val result = TtsDefaultEngine.select(activity, activity.packageName) { _ ->
            if (!activity.isFinishing) onChanged()
        }
        when (result) {
            TtsDefaultEngine.Outcome.ALREADY -> tell(activity, R.string.tts_msg_already)
            TtsDefaultEngine.Outcome.DIRECT -> tell(activity, R.string.tts_msg_done)
            TtsDefaultEngine.Outcome.AUTOMATION -> toast(activity, R.string.tts_msg_auto_started)
            TtsDefaultEngine.Outcome.NEED_SERVICE -> toast(activity, R.string.tts_msg_need_service)
            TtsDefaultEngine.Outcome.FAILED -> toast(activity, R.string.tts_msg_open_failed)
        }
        onChanged()
    }

    private fun disable(activity: Activity, onChanged: () -> Unit) {
        val prev = Prefs.ttsPrevDefault(activity)
        if (prev.isEmpty() || !SystemUtil.isInstalled(activity, prev)) {
            // Oldingi dvigatel noma'lum: tizim sozlamalari ochiladi, foydalanuvchi o'zi tanlaydi
            toast(activity, R.string.tts_msg_pick_manually)
            TtsDefaultEngine.openSettings(activity)
            return
        }
        val result = TtsDefaultEngine.select(activity, prev) { _ ->
            if (!activity.isFinishing) onChanged()
        }
        when (result) {
            TtsDefaultEngine.Outcome.ALREADY, TtsDefaultEngine.Outcome.DIRECT -> tell(activity, R.string.tts_msg_restored)
            TtsDefaultEngine.Outcome.AUTOMATION -> toast(activity, R.string.tts_msg_auto_started)
            TtsDefaultEngine.Outcome.NEED_SERVICE -> toast(activity, R.string.tts_msg_need_service)
            TtsDefaultEngine.Outcome.FAILED -> toast(activity, R.string.tts_msg_open_failed)
        }
        onChanged()
    }

    private fun toast(c: Context, res: Int) {
        Toast.makeText(c, res, Toast.LENGTH_LONG).show()
    }

    /** Xabar ekranda va (xizmat yoqiq bo'lsa) ovoz bilan aytiladi. */
    private fun tell(c: Context, res: Int) {
        toast(c, res)
        try {
            ReaderService.instance?.announce(c.getString(res))
        } catch (ex: Throwable) {
            swallowed("TtsEngineUi.tell", ex)
        }
    }
}
