package uz.ekranoquvchi.mvp.content

/**
 * Stikerlar (Telegram, WhatsApp, klaviatura stikerlari): stiker tavsifi odatda "Sticker" yoki "Stiker" so'zi va uning
 * emojisidan iborat. Foydalanuvchi stikerlarni o'qishni o'chirgan bo'lsa, tavsifdagi stiker so'zi va emojilar aytilmaydi,
 * boshqa matn (yuboruvchi nomi, vaqt) qoladi. Hech narsa qolmasa, element jim qolmasligi uchun qisqa "stiker" so'zi
 * aytiladi. Tarmoqsiz, Android'siz: oddiy JVM'da sinaladi.
 */
object StickerText {
    private val WORD = Regex(
        "(?<![\\p{L}\\p{N}])(?:stickers?|stikers?|stikerlar|\u0441\u0442\u0438\u043A\u0435\u0440(?:\u044B|\u0430|\u043E\u0432|\u043B\u0430\u0440)?|\u043D\u0430\u043A\u043B\u0435\u0439\u043A(?:\u0430|\u0438|\u0443))(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE
    )
    private val SPACES = Regex("\\s+")
    private val COMMAS = Regex("(?:\\s*[,;]\\s*){2,}")

    /** Stiker deb hisoblanadimi: element identifikatori yoki sinfi shunday desa, yoki qisqa tavsif stiker so'zi (va emoji) bilan. */
    fun isSticker(label: String, viewId: String?, className: String?): Boolean {
        if (viewId != null && viewId.contains("sticker", ignoreCase = true)) return true
        if (className != null && className.contains("Sticker", ignoreCase = true)) return true
        if (label.length > 60) return false
        val m = WORD.find(label) ?: return false
        // Tavsifning boshida turgan so'z yoki emoji bilan birga kelgan so'z: oddiy xabar matnida tasodifan uchragan so'z emas
        return m.range.first == 0 || EmojiSpeech.containsEmoji(label)
    }

    /**
     * Stikerni qisqa o'qish: stiker so'zi va faqat birinchi emoji qoladi (to'plam nomi va boshqa emojilar aytilmaydi);
     * boshqa matn (yuboruvchi nomi, vaqt) o'zgarmaydi.
     */
    fun short(label: String, viewId: String?, className: String?): String {
        if (!isSticker(label, viewId, className)) return label
        return EmojiSpeech.keepFirst(label)
    }

    /** Stikerni o'qimaslik: stiker so'zi va emojilar olib tashlanadi; hech narsa qolmasa, qisqa word qaytariladi. */
    fun quiet(label: String, viewId: String?, className: String?, word: String): String {
        if (!isSticker(label, viewId, className)) return label
        var s = WORD.replace(label, " ")
        s = EmojiSpeech.apply(s, EmojiMode.SKIP)
        s = SPACES.replace(s, " ").trim()
        s = COMMAS.replace(s, ", ")
        s = s.trim(',', ';', ':', '-', '.', ' ')
        return if (s.isBlank()) word else s
    }
}
