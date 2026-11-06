package uz.ekranoquvchi.mvp.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import uz.ekranoquvchi.mvp.diag.swallowed

/**
 * Boshqa ilova ovoz ijro etayotganini kuzatadi (masalan, Telegram'da ovozli xabar eshitilayotgan payt).
 *
 * O'zbegimning o'z tovushlari (nutq, ovoz effektlari, onlayn ovoz) USAGE_ASSISTANCE_ACCESSIBILITY bilan ijro etiladi
 * va hisobga olinmaydi, shuning uchun xizmat o'zini jim qilib qo'ymaydi. Qo'ng'iroq, signal va bildirishnoma tovushlari
 * ham hisobga olinmaydi: faqat media, o'yin, ovozli aloqa va noma'lum maqsadli ijro "faol ijro" hisoblanadi.
 *
 * onChange asosiy oqimda (handler) chaqiriladi va faqat holat o'zgarganda: true - ijro boshlandi, false - to'xtadi.
 */
class PlaybackMonitor(
    private val context: Context,
    private val handler: Handler,
    private val onChange: (Boolean) -> Unit,
) {
    private var callback: AudioManager.AudioPlaybackCallback? = null
    private var last = false

    fun start() {
        stop()
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val cb = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                report(external(configs))
            }
        }
        try {
            am.registerAudioPlaybackCallback(cb, handler)
            callback = cb
            report(external(am.activePlaybackConfigurations))
        } catch (_: Throwable) {
            callback = null
        }
    }

    fun stop() {
        val cb = callback ?: return
        callback = null
        try {
            (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.unregisterAudioPlaybackCallback(cb)
        } catch (ex: Throwable) {
            swallowed("PlaybackMonitor.stop", ex)
        }
        last = false
    }

    private fun report(active: Boolean) {
        if (active == last) return
        last = active
        try {
            onChange(active)
        } catch (ex: Throwable) {
            swallowed("PlaybackMonitor.report", ex)
        }
    }

    /** Faol ijrolar orasida o'zimizniki bo'lmagan "tinglanadigan" ijro bormi. */
    private fun external(configs: List<AudioPlaybackConfiguration>?): Boolean {
        if (configs == null) return false
        for (c in configs) {
            try {
                val usage = c.audioAttributes.usage
                if (usage == AudioAttributes.USAGE_MEDIA ||
                    usage == AudioAttributes.USAGE_GAME ||
                    usage == AudioAttributes.USAGE_UNKNOWN ||
                    usage == AudioAttributes.USAGE_VOICE_COMMUNICATION
                ) return true
            } catch (ex: Throwable) {
                swallowed("PlaybackMonitor.external", ex)
            }
        }
        return false
    }
}
