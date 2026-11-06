package uz.ekranoquvchi.mvp.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import uz.ekranoquvchi.mvp.diag.swallowed
import uz.ekranoquvchi.mvp.util.Prefs

/** Tovush signallari (8.4, 18.2): nutqsiz qisqa tonlar, kod bilan hosil qilinadi (fayl kerak emas). Standartda o'chiq. */
object Earcons {
    enum class Kind { FOCUS, EDGE, WINDOW }

    private const val RATE = 48000
    private val cache = HashMap<Kind, ShortArray>()

    private fun tone(parts: List<Pair<Int, Int>>): ShortArray {
        var total = 0
        for (p in parts) total += RATE * p.second / 1000
        val out = ShortArray(total)
        var pos = 0
        for (p in parts) {
            val n = RATE * p.second / 1000
            for (i in 0 until n) {
                // 4 ms boshlanish va tugash silliqligi: "chiqillash" bo'lmasligi uchun
                val env = minOf(1.0, minOf(i, n - i) / (RATE * 0.004))
                out[pos + i] = (Math.sin(2.0 * Math.PI * p.first * i / RATE) * env * 9000.0).toInt().toShort()
            }
            pos += n
        }
        return out
    }

    private fun build(kind: Kind): ShortArray = when (kind) {
        Kind.FOCUS -> tone(listOf(1200 to 18))
        Kind.EDGE -> tone(listOf(330 to 70, 260 to 90))
        Kind.WINDOW -> tone(listOf(660 to 50, 880 to 70))
    }

    // Har bir signal turi uchun bitta AudioTrack bir marta yaratiladi va qayta ishlatiladi. Ilgari har signalda yangi
    // AudioTrack ochilardi: ekranda tez surilganda tizim aralashtirgichi to'lib, "AudioTrack allocation failed" chiqardi
    // va signallar to'xtab qolardi. Endi bir vaqtda uchtadan ortiq trek bo'lmaydi.
    private val tracks = HashMap<Kind, AudioTrack>()

    private fun newTrack(pcm: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }

    fun play(context: Context, kind: Kind) {
        if (!Prefs.earcons(context)) return
        val pcm = cache.getOrPut(kind) { build(kind) }
        try {
            val old = tracks[kind]
            if (old != null) {
                var ok: Boolean
                try {
                    old.stop()
                    ok = old.reloadStaticData() == AudioTrack.SUCCESS
                    if (ok) old.play()
                } catch (_: Throwable) {
                    ok = false
                }
                if (ok) return
                // Qayta ishlatib bo'lmadi: eskisi bo'shatilib, yangisi yaratiladi
                try {
                    old.release()
                } catch (ex: Throwable) {
                    swallowed("Earcons.play", ex)
                }
                tracks.remove(kind)
            }
            val fresh = newTrack(pcm)
            tracks[kind] = fresh
            fresh.play()
        } catch (ex: Throwable) {
            swallowed("Earcons.play", ex)
        }
    }

    /** Xizmat to'xtaganda treklar bo'shatiladi. */
    fun release() {
        for (t in tracks.values) {
            try {
                t.release()
            } catch (ex: Throwable) {
                swallowed("Earcons.release", ex)
            }
        }
        tracks.clear()
    }
}
