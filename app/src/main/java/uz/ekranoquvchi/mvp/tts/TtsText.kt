package uz.ekranoquvchi.mvp.tts

import android.content.Context
import uz.ekranoquvchi.mvp.R
import uz.ekranoquvchi.mvp.content.ContentPipeline
import uz.ekranoquvchi.mvp.content.EmojiMode
import uz.ekranoquvchi.mvp.util.Prefs
import java.util.Locale

/**
 * Tizim dvigateli orqali kelgan matnni O'zbegimning o'z qoidalari bo'yicha tayyorlaydi (ekran o'quvchidagi bilan bir xil
 * quvur: ContentPipeline). O'zbekcha so'rovda: uch yozuv (lotin, kirill, yangi lotin), raqamlar, Rim raqamlari,
 * telefon raqamlari, emoji, til ko'prigi. Boshqa tillarda matn deyarli o'zgarishsiz qoladi. Tarmoqqa hech narsa chiqmaydi.
 */
internal object TtsText {
    fun prepare(c: Context, text: String, locale: Locale, backendPackage: String?): String {
        if (text.isEmpty()) return text
        return try {
            val uz = locale.language == "uz"
            val base = if (uz) {
                Prefs.pipelineOptions(c, Prefs.engineReadsCyrillic(c, backendPackage))
            } else {
                // So'ralgan til o'zbekcha emas: o'zbekcha raqam so'zlari va fan qisqartmalari qo'llanmaydi
                Prefs.otherLangOptions(c)
            }
            val emojiMode = Prefs.emojiMode(c)
            val opts = base.copy(
                emoji = if (uz || emojiMode == EmojiMode.SKIP) emojiMode else EmojiMode.KEEP,
                emojiWord = c.getString(R.string.emoji_word),
            )
            ContentPipeline.process(text, opts).text
        } catch (_: Throwable) {
            text
        }
    }
}
