package uz.ekranoquvchi.mvp.content

/**
 * Raqamdan keyingi o'lchov birliklari: "5 kg", "72 km/h", "220 V", "250 MB", "20 °C". Birlik o'zbekcha so'zga aylanadi,
 * yoki (uning fani Tafsilotlarda o'chirilgan bo'lsa) aytilmaydi. Tarmoqsiz, Android'siz: oddiy JVM'da sinaladi.
 *
 * Ehtiyotkorlik: birlik faqat raqamdan keyin tanib olinadi (yakka "kg" yoki "m" so'zga tegilmaydi); yakka harfli birlik
 * so'zga yopishsa ("7 g'isht") yoki chiziqcha bilan harfga ulansa ("3 A-sinf") tegilmaydi; "K" va "T" faqat probel bilan
 * ("300 K"), chunki "10K" ijtimoiy tarmoqda "10 ming" degani.
 *
 * DIQQAT: o'zbekcha aytilishlar taxminiy, lingvist bilan tasdiqlanishi kerak. Hammasi shu jadvalda, o'zgartirish oson.
 */
internal object ScienceUnits {
    /** "Birlik shu yerda turgan" belgisi: "5 kg + 3 kg" dagi "+" ni tanishga yordam beradi. ScienceAbbr oxirida olib tashlaydi. */
    const val MARK = "⁣"

    private const val APOS = "'ʻʼ‘’`´′"

    /** Yozuv: aytilishi, fani, yakka harfmi, faqat probel bilan keladimi. */
    private class Def(val spoken: String, val science: Science, val single: Boolean, val needsSpace: Boolean)

    private class Resolved(val spoken: String, val science: Science, val single: Boolean, val needsSpace: Boolean)

    private val DEFS = HashMap<String, Def>(512)

    private val PREFIX = mapOf(
        "n" to "nano", "µ" to "mikro", "μ" to "mikro", "m" to "milli", "h" to "gekto",
        "k" to "kilo", "M" to "mega", "G" to "giga", "T" to "tera",
    )

    private fun add(form: String, spoken: String, sci: Science, needsSpace: Boolean = false) {
        // Yakka bosh harf ("5A sinf", "12V", "5N") odatda birlik emas: faqat probel bilan ("5 A") tanib olinadi.
        // "L" (litr) va yunon harflari ("5Ω") bundan mustasno.
        val capital = form.length == 1 && form[0] in 'A'..'Z' && form[0] != 'L'
        DEFS[form] = Def(spoken, sci, form.length == 1, needsSpace || capital)
    }

    /** Harf registriga qaramaydi ("kg", "KG", "Kg"): eski normalizator shunday ishlagan. */
    private fun addAnyCase(form: String, spoken: String, sci: Science) {
        var variants = listOf("")
        for (ch in form) variants = variants.flatMap { listOf(it + ch.lowercaseChar(), it + ch.uppercaseChar()) }
        for (v in variants.distinct()) add(v, spoken, sci)
    }

    /** Asosiy birlik va old qo'shimchali shakllari: family("V", "volt", P, "m k M") -> V, mV, kV, MV. */
    private fun family(sym: String, word: String, sci: Science, prefixes: String, bare: Boolean = true) {
        if (bare) add(sym, word, sci)
        for (p in prefixes.split(' ')) add(p + sym, PREFIX.getValue(p) + word, sci)
    }

    init {
        val u = Science.UNITS
        val p = Science.PHYSICS
        val i = Science.INFO
        val micro = "µ μ"

        // Uzunlik, yuza, hajm (m2 -> kvadrat metr, m3 -> kub metr)
        add("m", "metr", u)
        add("dm", "detsimetr", u)
        add("cm", "santimetr", u)
        add("nm", "nanometr", u)
        add("µm", "mikrometr", u)
        add("μm", "mikrometr", u)
        addAnyCase("mm", "millimetr", u)
        addAnyCase("sm", "santimetr", u)
        addAnyCase("km", "kilometr", u)
        val lengths = listOf(
            "m" to "metr", "dm" to "detsimetr", "cm" to "santimetr", "sm" to "santimetr", "mm" to "millimetr", "km" to "kilometr",
        )
        for ((sym, word) in lengths) {
            add(sym + "²", "kvadrat $word", u)
            add(sym + "2", "kvadrat $word", u)
            add(sym + "³", "kub $word", u)
            add(sym + "3", "kub $word", u)
        }
        // Og'irlik
        add("g", "gramm", u)
        add("mg", "milligramm", u)
        add("µg", "mikrogramm", u)
        add("μg", "mikrogramm", u)
        addAnyCase("kg", "kilogramm", u)
        add("t", "tonna", u)
        // Suyuqlik hajmi
        add("l", "litr", u)
        add("L", "litr", u)
        add("ml", "millilitr", u)
        add("mL", "millilitr", u)
        add("cl", "santilitr", u)
        add("dl", "detsilitr", u)
        // Vaqt
        add("s", "sekund", u)
        add("ms", "millisekund", u)
        add("µs", "mikrosekund", u)
        add("μs", "mikrosekund", u)
        add("ns", "nanosekund", u)
        add("s²", "sekund kvadrat", u)
        add("s2", "sekund kvadrat", u)
        add("min", "minut", u)
        add("h", "soat", u)
        // Harorat va burchak: "20 °C" -> "yigirma daraja" (avvalgidek), "90°" -> "to'qson daraja"
        for (c in listOf("°C", "° C", "ºC", "˚C", "℃")) add(c, "daraja", u)
        for (f in listOf("°F", "° F", "ºF", "˚F", "℉")) add(f, "daraja Farengeyt", u)
        add("°", "daraja", u)
        // Ovqat energiyasi
        add("cal", "kaloriya", u)
        for (k in listOf("kcal", "kCal", "Kcal", "KCAL", "kkal", "Kkal", "KKAL")) add(k, "kilokaloriya", u)

        // Fizika
        family("N", "nyuton", p, "m k M")
        family("J", "joul", p, "m k M G")
        family("W", "vatt", p, "m k M G")
        family("Wh", "vatt soat", p, "k M G")
        family("V", "volt", p, "m $micro k M")
        family("A", "amper", p, "m $micro k")
        family("Ω", "om", p, "m k M G")
        family("Ω", "om", p, "m k M G")
        family("Hz", "gers", p, "k M G T")
        family("Pa", "paskal", p, "h k M G")
        family("T", "tesla", p, "m $micro n", bare = false)
        family("mol", "mol", p, "m $micro k")
        family("eV", "elektronvolt", p, "k M G")
        add("K", "kelvin", p, needsSpace = true)
        add("T", "tesla", p, needsSpace = true)
        val others = listOf(
            "bar" to "bar", "mbar" to "millibar", "atm" to "atmosfera", "Wb" to "veber", "cd" to "kandela",
            "lm" to "lyumen", "lx" to "lyuks", "dB" to "detsibel", "mmHg" to "millimetr simob ustuni",
        )
        for ((f, w) in others) add(f, w, p)

        // Informatika (kb, mb, gb eski normalizatorda ham harf registrisiz edi)
        addAnyCase("kb", "kilobayt", i)
        addAnyCase("mb", "megabayt", i)
        addAnyCase("gb", "gigabayt", i)
        addAnyCase("tb", "terabayt", i)
        // Tarmoq tezligi: "100 Mb/s" megabit (megabayt emas), "100 MB/s" megabayt
        val bits = listOf(
            "Kbit" to "kilobit", "kbit" to "kilobit", "Mbit" to "megabit", "Gbit" to "gigabit",
            "Kbps" to "kilobit sekund", "kbps" to "kilobit sekund", "Mbps" to "megabit sekund", "Gbps" to "gigabit sekund",
            "Kb/s" to "kilobit sekund", "Mb/s" to "megabit sekund", "Gb/s" to "gigabit sekund",
            "Kbit/s" to "kilobit sekund", "Mbit/s" to "megabit sekund", "Gbit/s" to "gigabit sekund", "px" to "piksel",
        )
        for ((f, w) in bits) add(f, w, i)
    }

    /**
     * Har bir aytilishning birinchi so'zi: SpeechNormalizer "1,105 kilometr" kabi o'nlik kasrni minglik deb o'qimasligi uchun.
     */
    val firstWords: List<String> = DEFS.values.map { it.spoken.substringBefore(' ') }.distinct()

    private val ALT: String = DEFS.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
    private val DEN_ALT = "$ALT|soat|sek|kun"

    /** Raqam + (probel) + birlik. Birlikdan keyin harf, raqam, pastki chiziq yoki apostrof kelmasligi shart ("7 g'isht" tegilmaydi). */
    private val UNIT_RE = Regex(
        """(?<![\p{L}\p{N}_.,])(\d+(?:[.,]\d+)*)([   ]?)((?:$ALT)(?:[/·](?:$DEN_ALT))?)(?![\p{L}\p{N}_$APOS])"""
    )

    /** Qavs ichidagi birlik: "Tezlik (m/s)", "Massa (kg)". Yakka harfli "(A)", "(V)" variant belgisi bo'lishi mumkin: tegilmaydi. */
    private val PAREN_RE = Regex("""\(((?:$ALT)(?:[/·](?:$DEN_ALT))?)\)""")

    /** "kg" yoki "km/h" kabi shaklni ochadi; jamlangan birlikda fan ustuvorligi: fizika, so'ng informatika, so'ng o'lchov. */
    private fun resolve(form: String): Resolved? {
        DEFS[form]?.let { return Resolved(it.spoken, it.science, it.single, it.needsSpace) }
        val idx = form.indexOfFirst { it == '/' || it == '·' }
        if (idx <= 0) return null
        val a = DEFS[form.substring(0, idx)] ?: return null
        val rest = form.substring(idx + 1)
        val b = DEFS[rest]
        val spokenB = b?.spoken ?: when (rest) {
            "soat" -> "soat"
            "sek" -> "sekund"
            "kun" -> "kun"
            else -> return null
        }
        val sci = when {
            a.science == Science.PHYSICS || b?.science == Science.PHYSICS -> Science.PHYSICS
            a.science == Science.INFO || b?.science == Science.INFO -> Science.INFO
            else -> Science.UNITS
        }
        return Resolved(a.spoken + " " + spokenB, sci, a.single || (b?.single ?: false), a.needsSpace)
    }

    /** "5 kg" -> "5 kilogramm"; fani o'chiq bo'lsa -> "5". */
    fun apply(t: String, o: ScienceOptions): String {
        if (t.none { it in '0'..'9' }) return t
        return UNIT_RE.replace(t) { m ->
            val r = resolve(m.groupValues[3]) ?: return@replace m.value
            if (r.needsSpace && m.groupValues[2].isEmpty()) return@replace m.value
            if (r.single) {
                // "3 A-sinf", "5 m-li": yakka harf chiziqcha bilan so'zga ulangan bo'lsa, bu birlik emas
                val e = m.range.last + 1
                if (e + 1 < t.length && t[e] == '-' && t[e + 1].isLetter()) return@replace m.value
            }
            if (o.enabled(r.science)) m.groupValues[1] + " " + r.spoken + MARK else m.groupValues[1]
        }
    }

    /** "(m/s)" -> "(metr sekund)"; fani o'chiq bo'lsa qavs bilan birga olib tashlanadi. */
    fun applyParen(t: String, o: ScienceOptions): String {
        if (t.indexOf('(') < 0) return t
        return PAREN_RE.replace(t) { m ->
            val form = m.groupValues[1]
            if (DEFS[form]?.single == true) return@replace m.value
            val r = resolve(form) ?: return@replace m.value
            if (r.needsSpace) return@replace m.value
            if (o.enabled(r.science)) "(" + r.spoken + ")" else ""
        }
    }
}
