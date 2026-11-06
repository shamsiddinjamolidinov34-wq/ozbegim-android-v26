package uz.ekranoquvchi.mvp.content

/**
 * Fan qisqartmalarini o'qish yoki o'qimaslik (Tafsilotlar > Fan qisqartmalari). Har bir fan alohida yoqiladi:
 * o'lchov birliklari (kg, km, sm, °C), fizika (V, A, N, Hz), matematika (+ = × √ π x²), informatika (MB, GB, px).
 * Yoqilgan fanning qisqartmasi o'zbekcha so'z bilan o'qiladi ("5 kg" -> "5 kilogramm", "2+3=5" -> "2 plyus 3 teng 5"),
 * o'chirilganniki aytilmaydi ("5 kg" -> "5"), emoji sozlamasi kabi. Raqamlar keyin SpeechNormalizer'da so'zga aylanadi,
 * shuning uchun bu bosqich undan OLDIN ishlaydi. Havola, elektron pochta, parol va tasdiqlash kodiga tegilmaydi.
 * Tarmoqsiz, Android'siz: oddiy JVM'da sinaladi.
 */
object ScienceAbbr {
    private val PROTECT = Regex(
        """(?:https?://|www\.)\S+|[\p{L}\p{N}._%+\-]+@[\p{L}\p{N}\-]+(?:\.[\p{L}\p{N}\-]+)+""",
        RegexOption.IGNORE_CASE
    )
    private val SPACES = Regex(" {2,}")

    /** SpeechNormalizer uchun: o'lchov birligining aytilishi (o'nlik kasr "1,105 kilometr" minglik deb o'qilmasligi uchun). */
    val spokenUnitWords: List<String> get() = ScienceUnits.firstWords

    private fun mayApply(t: String): Boolean = t.any { it in '0'..'9' } || t.indexOf('(') >= 0 || ScienceMath.mayApply(t)

    fun apply(text: String, o: ScienceOptions = ScienceOptions()): String {
        if (text.isEmpty() || !mayApply(text)) return text
        val bare = text.trim()
        if (bare == "+" || bare == "=") {
            // Hisoblagich tugmasi: "+" yoki "=" yakka yorliq. O'chirilgan bo'lsa fokus jim qolmasligi uchun asl yorliq aytiladi.
            return if (o.math) (if (bare == "+") "plyus" else "teng") else text
        }
        val converted = segments(text) { seg ->
            ScienceMath.apply(ScienceUnits.apply(ScienceUnits.applyParen(seg, o), o), o.math)
        }
        if (converted == text) return text
        val lead = text.takeWhile { it.isWhitespace() }
        val trail = text.takeLastWhile { it.isWhitespace() }
        val out = lead + SPACES.replace(converted.replace(ScienceUnits.MARK, ""), " ").trim() + trail
        // Hammasi olib tashlansa (masalan, matn faqat "+" bo'lsa), fokus jim qolmasligi uchun asl matn aytiladi
        return if (out.isBlank() && text.isNotBlank()) text else out
    }

    /** Havola va elektron pochtani o'tkazib, qolgan bo'laklarga `f` ni qo'llaydi. */
    private fun segments(text: String, f: (String) -> String): String {
        var m = PROTECT.find(text) ?: return f(text)
        val sb = StringBuilder(text.length + 24)
        var last = 0
        while (true) {
            sb.append(f(text.substring(last, m.range.first))).append(m.value)
            last = m.range.last + 1
            m = m.next() ?: break
        }
        sb.append(f(text.substring(last)))
        return sb.toString()
    }
}
