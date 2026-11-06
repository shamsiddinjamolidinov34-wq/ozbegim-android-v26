package uz.ekranoquvchi.mvp.tts

import android.content.Context
import android.content.Intent
import android.provider.Settings
import uz.ekranoquvchi.mvp.content.EngineProfiles
import uz.ekranoquvchi.mvp.diag.swallowed
import uz.ekranoquvchi.mvp.util.Prefs

/**
 * Tizim nutq dvigateli ("Afzal ko'rilgan mexanizm") bilan bog'liq aniqlashlar.
 *
 * Muhim qoida: O'zbegimning o'z dvigateli (OzbegimTtsService) hech qachon o'zining ovoz beruvchi dvigateli (backend)
 * bo'la olmaydi, aks holda u o'ziga o'zi ulanib, cheksiz aylanishga tushardi. Shu sababli haqiqiy ovozni beruvchi
 * dvigatel har doim telefondagi BOSHQA dvigatellar ichidan tanlanadi.
 */
object EngineChoice {
    /**
     * Tizimning "afzal ko'rilgan mexanizm" kaliti (Settings.Secure). Ommaviy konstanta emas, lekin uni hamma ilova
     * o'qiy oladi (TextToSpeech.getDefaultEngine ham shuni o'qiydi). Yozish uchun WRITE_SECURE_SETTINGS kerak.
     */
    const val DEFAULT_SYNTH_KEY = "tts_default_synth"

    private const val TTS_ACTION = "android.intent.action.TTS_SERVICE"

    /** Tizimning hozirgi afzal ko'rilgan nutq dvigateli (paket nomi); o'qib bo'lmasa null. */
    fun systemDefault(c: Context): String? = try {
        val v = Settings.Secure.getString(c.contentResolver, DEFAULT_SYNTH_KEY)
        if (v == null || v.trim().isEmpty()) null else v.trim()
    } catch (_: Throwable) {
        null
    }

    /** O'zbegimning o'zi hozir tizimning afzal ko'rilgan mexanizmimi. */
    fun isDefault(c: Context): Boolean = systemDefault(c) == c.packageName

    /** Telefonda o'rnatilgan nutq dvigatellari (paket nomlari), O'zbegimning o'zisiz. */
    @Suppress("DEPRECATION")
    fun installedEngines(c: Context): List<String> {
        return try {
            val list = c.packageManager.queryIntentServices(Intent(TTS_ACTION), 0)
            val out = ArrayList<String>()
            for (ri in list) {
                val p = ri.serviceInfo?.packageName ?: continue
                if (p != c.packageName && !out.contains(p)) out.add(p)
            }
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /** Dvigatelning tizim sozlamalarida ko'rinadigan nomi (Sozlamalar ham shu nomni ko'rsatadi). */
    @Suppress("DEPRECATION")
    fun engineLabel(c: Context, pkg: String): String {
        try {
            val pm = c.packageManager
            val list = pm.queryIntentServices(Intent(TTS_ACTION).setPackage(pkg), 0)
            val first = list.firstOrNull()
            if (first != null) {
                val s = first.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (s.isNotEmpty()) return s
            }
            val app = pm.getApplicationInfo(pkg, 0)
            val a = pm.getApplicationLabel(app).toString().trim()
            if (a.isNotEmpty()) return a
        } catch (ex: Throwable) {
            swallowed("EngineChoice.engineLabel", ex)
        }
        return pkg
    }

    /**
     * Haqiqiy ovoz beruvchi dvigatel. Tartib: foydalanuvchi qo'lda tanlagani; ekran o'quvchi uchun tanlangan dvigatel
     * (RHVoice yoki yuklab olingan ovoz paketi); O'zbegim afzal ko'rilgan bo'lishidan oldingi tizim dvigateli;
     * ishonchli ro'yxat (RHVoice, Google, Samsung); boshqa istalgan o'rnatilgan dvigatel. Hech biri yo'q bo'lsa null.
     */
    fun backendPackage(c: Context): String? {
        val installed = installedEngines(c)
        if (installed.isEmpty()) return null
        val explicit = Prefs.ttsBackend(c)
        if (explicit.isNotEmpty() && installed.contains(explicit)) return explicit
        when (Prefs.engine(c)) {
            "rhvoice" -> if (installed.contains(EngineProfiles.RHVOICE)) return EngineProfiles.RHVOICE
            "custom" -> {
                val p = Prefs.enginePackage(c)
                if (p.isNotEmpty() && installed.contains(p)) return p
            }
            "auto" -> {
                val p = Prefs.autoEngine(c)
                if (p.isNotEmpty() && installed.contains(p)) return p
            }
        }
        val prev = Prefs.ttsPrevDefault(c)
        if (prev.isNotEmpty() && installed.contains(prev)) return prev
        for (p in EngineProfiles.TRUSTED) if (installed.contains(p)) return p
        return installed.firstOrNull()
    }

    /** exclude'dan boshqa birinchi o'rnatilgan dvigatel (asosiy dvigatel ishga tushmasa, zaxira sifatida). */
    fun alternative(c: Context, exclude: String?): String? {
        val installed = installedEngines(c)
        for (p in EngineProfiles.TRUSTED) if (p != exclude && installed.contains(p)) return p
        return installed.firstOrNull { it != exclude }
    }
}
