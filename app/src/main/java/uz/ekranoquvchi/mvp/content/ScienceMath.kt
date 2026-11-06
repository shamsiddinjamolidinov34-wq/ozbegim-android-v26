package uz.ekranoquvchi.mvp.content

/**
 * Matematik belgilar: + = < > * ^, x² x³, × ÷ − ≠ ≈ ≤ ≥ ± √ ∞ π, oddiy kasrlar (½), yunon harflari, sin cos log.
 * Fan yoqilgan bo'lsa belgi o'zbekcha so'z bilan o'qiladi ("2+3=5" -> "2 plyus 3 teng 5"), o'chirilgan bo'lsa aytilmaydi.
 *
 * Ehtiyotkorlik: + = < > faqat raqam, yakka harf yoki qavs oralig'ida ("2+3", "x = 5", "a < b"): "Sozlamalar > Ovoz",
 * "C++", "a=>b", "id=name" o'zgarmaydi. Funksiya nomi faqat qo'llanish joyida ("sin(x)", "cos 60"): "log in" o'zgarmaydi.
 * Tarmoqsiz, Android'siz: oddiy JVM'da sinaladi. O'zbekcha aytilishlar taxminiy, lingvist bilan tasdiqlanishi kerak.
 */
internal object ScienceMath {
    private const val APOS = "'ʻʼ‘’`´′"

    private val SYMBOLS: Map<Char, String> = mapOf(
        '−' to "minus", // −
        '×' to "ko'paytirish", // ×
        '⋅' to "ko'paytirish", // ⋅
        '∙' to "ko'paytirish", // ∙
        '÷' to "bo'lish", // ÷
        '≠' to "teng emas", // ≠
        '≈' to "taxminan teng", // ≈
        '≤' to "kichik yoki teng", // ≤
        '≥' to "katta yoki teng", // ≥
        '±' to "plyus minus", // ±
        '√' to "kvadrat ildiz", // √
        '∛' to "kub ildiz", // ∛
        '∞' to "cheksizlik", // ∞
        '∑' to "yig'indi", // ∑
        '∫' to "integral", // ∫
        '∆' to "delta", // ∆
        '∈' to "tegishli", // ∈
        '∉' to "tegishli emas", // ∉
        '∪' to "birlashma", // ∪
        '∩' to "kesishma", // ∩
        '∅' to "bo'sh to'plam", // ∅
        '∀' to "har qanday", // ∀
        '∃' to "mavjud", // ∃
        '⇒' to "demak", // ⇒
        '⇔' to "teng kuchli", // ⇔
        '∠' to "burchak", // ∠
        '⊥' to "perpendikulyar", // ⊥
        '∥' to "parallel", // ∥
        '≡' to "aynan teng", // ≡
        '‰' to "promille", // ‰
    )

    private val GREEK: Map<Char, String> = mapOf(
        'α' to "alfa", 'β' to "beta", 'γ' to "gamma", 'δ' to "delta", 'ε' to "epsilon", 'ζ' to "zeta", 'η' to "eta",
        'θ' to "teta", 'κ' to "kappa", 'λ' to "lambda", 'μ' to "mu", 'ν' to "nyu", 'ξ' to "ksi", 'π' to "pi", 'ρ' to "ro",
        'σ' to "sigma", 'ς' to "sigma", 'τ' to "tau", 'υ' to "upsilon", 'φ' to "fi", 'χ' to "xi", 'ψ' to "psi", 'ω' to "omega",
    )

    private val FRACTIONS: Map<Char, String> = mapOf(
        '¼' to "to'rtdan bir", '½' to "ikkidan bir", '¾' to "to'rtdan uch",
        '⅓' to "uchdan bir", '⅔' to "uchdan ikki", '⅛' to "sakkizdan bir",
    )

    private val FUNCTIONS: Map<String, String> = mapOf(
        "arcsin" to "arksinus", "arccos" to "arkkosinus", "arctg" to "arktangens", "arctan" to "arktangens",
        "sin" to "sinus", "cos" to "kosinus", "tg" to "tangens", "tan" to "tangens", "ctg" to "kotangens", "cot" to "kotangens",
        "log" to "logarifm", "lg" to "o'nli logarifm", "ln" to "natural logarifm", "lim" to "limit",
    )

    private val FUNCTION_HINTS = arrayOf("sin", "cos", "tg", "tan", "ctg", "cot", "log", "lg", "ln", "lim")

    private const val SUPERS = "⁰¹²³⁴⁵⁶⁷⁸⁹"

    // Operand chegarasi: raqam, yopuvchi qavs, daraja belgisi, o'qilgan birlik belgisi yoki yakka harf
    // (so'rov qatoridagi "?q=", "&a=" kabi harf operand emas).
    private const val L_OP = """(?<=\d|[)\]²³⁣]|(?<![\p{L}?&/\\])\p{L})"""
    private const val R_STRICT = """(?=\d|[(\[√]|\p{L}(?!\p{L}))"""
    private const val R_LOOSE = """(?=\d|[(\[√]|\p{L})"""

    // Probel ikki tomonda ham bo'lishi yoki ikkalasida ham bo'lmasligi shart: "A+ ball", "5+ yil" amal emas.
    private const val SP = """[  ]"""
    private val PLUS_RE = Regex(L_OP + """(?:\+|$SP\+$SP)""" + R_LOOSE)
    private val PLUS_SUFFIX_RE = Regex("""(?<=\d)\+(?![\p{L}\p{N}+(\[])""")
    private val EQ_RE = Regex(L_OP + """(?:=(?![=<>])|$SP=$SP(?![=<>]))""" + R_LOOSE)
    private val CMP2_RE = Regex(L_OP + """(?:<=|>=|!=|$SP(?:<=|>=|!=)$SP)""" + R_STRICT)
    private val CMP_RE = Regex(L_OP + """(?:[<>]|$SP[<>]$SP)""" + R_STRICT)
    private val STAR_RE = Regex("""(?<=\d)[  ]?\*[  ]?(?=\d)""")

    // x², a³, 10⁶, (a+b)², x⁻¹ va x^2, x^(-1), x^n. "Salom²" kabi izoh belgisi tegilmaydi.
    // Asos: son, qavs, yakka harf yoki ikki harf ("mc²"); "Salom²" kabi so'zdan keyingi izoh belgisi tegilmaydi.
    private val SUPER_RE = Regex("""(?<=\d|[)\]]|(?<!\p{L})\p{L}\p{L}?)(⁻?)([⁰¹²³⁴-⁹]+)""")
    private val CARET_RE = Regex("""(?<=\d|[)\]]|(?<!\p{L})\p{L}\p{L}?)\^(?:\((-?\d{1,3}|\p{L})\)|(-?\d{1,3})(?!\d)|(\p{L})(?!\p{L}))""")

    private val FRACTION_RE = Regex("(\\d?)([" + FRACTIONS.keys.joinToString("") + "])")
    private val SYMBOL_RE = Regex("""[  ]?[${SYMBOLS.keys.joinToString("")}][  ]?""")
    private val GREEK_RE = Regex("""(?<!\p{IsGreek})[Ͱ-Ͽ](?!\p{IsGreek})""")
    private val FUNC_RE = Regex(
        """(?<![\p{L}\p{N}_])(${FUNCTIONS.keys.sortedByDescending { it.length }.joinToString("|")})""" +
            """(?=\(|\d|[²³]|$SP(?:\d|[√π(]|\p{L}(?![\p{L}$APOS])))"""
    )

    /** Tezkor yo'l: oddiy matnda (belgi va funksiya nomi yo'q) hech qanday regex ishlamaydi. */
    fun mayApply(t: String): Boolean {
        for (c in t) {
            if (c == '=' || c == '+' || c == '<' || c == '>' || c == '^' || c == '*' || c == '°') return true
            if (c in SYMBOLS || c in FRACTIONS || c in SUPERS || c in 'Ͱ'..'Ͽ') return true
        }
        for (h in FUNCTION_HINTS) if (t.contains(h)) return true
        return false
    }

    private fun power(neg: Boolean, n: Int): String =
        if (!neg && n == 2) " kvadrat " else if (!neg && n == 3) " kub " else
            " ning " + (if (neg) "minus " else "") + UzNumbers.ordinal(n) + " darajasi "

    /** @param on yoqilgan bo'lsa belgi so'zga aylanadi, aks holda aytilmaydi (probelga almashadi). */
    fun apply(s: String, on: Boolean): String {
        var t = s
        fun say(spoken: String): String = if (on) spoken else " "
        fun rep(re: Regex, f: (MatchResult) -> String) {
            t = re.replace(t) { f(it) }
        }

        if (t.indexOf('+') >= 0) {
            rep(PLUS_RE) { say(" plyus ") }
            rep(PLUS_SUFFIX_RE) { say(" plyus") }
        }
        if (t.indexOf('=') >= 0) rep(EQ_RE) { say(" teng ") }
        if (t.indexOf('=') >= 0 || t.indexOf('<') >= 0 || t.indexOf('>') >= 0) {
            rep(CMP2_RE) { r ->
                say(
                    when (r.value.trim()) {
                        "<=" -> " kichik yoki teng "
                        ">=" -> " katta yoki teng "
                        else -> " teng emas "
                    }
                )
            }
        }
        if (t.indexOf('<') >= 0 || t.indexOf('>') >= 0) rep(CMP_RE) { r -> say(if (r.value.trim() == "<") " kichik " else " katta ") }
        if (t.indexOf('*') >= 0) rep(STAR_RE) { say(" ko'paytirish ") }

        if (t.indexOf('^') >= 0) {
            rep(CARET_RE) { r ->
                val raw = r.groupValues[1].ifEmpty { r.groupValues[2] }
                if (raw.isEmpty()) {
                    say(" ning " + r.groupValues[3] + " darajasi ")
                } else {
                    val neg = raw.startsWith("-")
                    val digits = raw.trimStart('-')
                    val n = digits.toIntOrNull()
                    if (n == null) say(" ning " + (if (neg) "minus " else "") + digits + " darajasi ")
                    else if (n == 1 && !neg) " " else say(power(neg, n))
                }
            }
        }
        if (t.any { it in SUPERS }) {
            rep(SUPER_RE) { r ->
                val neg = r.groupValues[1].isNotEmpty()
                val n = r.groupValues[2].map { SUPERS.indexOf(it) }.joinToString("").toIntOrNull()
                if (n == null) r.value else if (n == 1 && !neg) " " else say(power(neg, n))
            }
        }
        if (t.any { it in FRACTIONS }) {
            rep(FRACTION_RE) { r ->
                val whole = r.groupValues[1]
                val spoken = FRACTIONS.getValue(r.groupValues[2][0])
                if (on) (if (whole.isNotEmpty()) "$whole butun " else "") + spoken + " " else " $whole "
            }
        }
        if (t.any { it in SYMBOLS }) rep(SYMBOL_RE) { r -> say(" " + SYMBOLS.getValue(r.value.trim()[0]) + " ") }
        if (t.any { it in 'Ͱ'..'Ͽ' }) {
            rep(GREEK_RE) { r ->
                val name = GREEK[r.value[0].lowercaseChar()] ?: return@rep r.value
                say(" $name ")
            }
        }
        if (FUNCTION_HINTS.any { t.contains(it) }) {
            rep(FUNC_RE) { r -> if (on) FUNCTIONS.getValue(r.value) + " " else "" }
        }
        return t
    }
}
