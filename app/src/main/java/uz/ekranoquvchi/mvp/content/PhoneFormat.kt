package uz.ekranoquvchi.mvp.content

/**
 * O'zbekiston telefon raqamlarini ekran o'quvchi qulay o'qiydigan ko'rinishga keltiradi.
 *
 * Maqsad ko'rinish (har bir guruhdan keyin nuqta): "+998. 94. 773. 99. 10."
 * Nuqtalar nutq dvigatelini har guruhdan keyin to'xtatadi, guruhlar esa sonlar sifatida o'qiladi:
 * "plyus to'qqiz yuz to'qson sakkiz. to'qson to'rt. yetti yuz yetmish uch. to'qson to'qqiz. o'n."
 *
 * Qaysi yozuvlar tanib olinadi:
 *  - xalqaro: "+998947739910", "998 94 773 99 10", "+998 (94) 773-99-10", "+998. 94. 773. 99. 10." (hammasi bir xil);
 *  - mahalliy (9 xona) faqat guruhlangan holda: "94 773 99 10", "(94) 773-99-10", "94-773-99-10" (operator kodi tekshiriladi);
 *  - ajratgichsiz 9 xonali raqam faqat "tel", "telefon", "raqam", "nomer" kabi so'zdan keyin.
 *
 * Faqat o'qish uchun: ekrandagi matn o'zgarmaydi. Noaniq raqamga (summa, kod, hisob raqami) tegilmaydi.
 * Tarmoqsiz va Android'siz: oddiy JVM'da sinaladi.
 */
object PhoneFormat {
    /** Guruhlar orasidagi ajratgichlar: probel, chiziqcha, nuqta, qavs (ko'pi bilan 3 belgi). */
    private const val GAP = """[\s\u00A0\u202F.\-()]{0,3}"""

    /** Mahalliy yozuvda guruhlar orasida kamida bitta probel yoki chiziqcha bo'lishi shart: oddiy son telefon deb olinmasin. */
    private const val LOCAL_GAP = """[\s\u00A0\u202F\-]{1,2}"""

    /** Raqam harf/raqamdan keyin boshlanmasin; "A+998..." va "1+998..." kabi yozuvlarda "+" raqamga tegishli emas. */
    private const val BOUNDARY_BEFORE = """(?<![\p{L}\p{N}_])(?<![\p{L}\p{N}_]\+)"""
    private const val BOUNDARY_AFTER = """(?![\p{L}\p{N}_])(?![.,]\d)"""

    private val INTERNATIONAL = Regex(
        BOUNDARY_BEFORE + """\+?998""" + GAP + """(\d{2})""" + GAP + """(\d{3})""" + GAP + """(\d{2})""" + GAP +
            """(\d{2})""" + BOUNDARY_AFTER
    )

    private val LOCAL = Regex(
        """(?<![\p{L}\p{N}_+])(?<!\p{N}[\s\u00A0\u202F\-.,])\(?(\d{2})\)?""" + LOCAL_GAP + """(\d{3})""" + LOCAL_GAP +
            """(\d{2})""" + LOCAL_GAP + """(\d{2})""" + BOUNDARY_AFTER
    )

    /** "tel", "telefon", "raqam(i)", "nomer", "mobil", "phone", "тел", "телефон", "номер" va undan keyingi ajratgich + 9 xona. */
    private val KEYWORD_BARE = Regex(
        """(?iu)((?:tel(?:efon)?|raqam(?:i)?|nomer|mobil|phone|\u0442\u0435\u043B(?:\u0435\u0444\u043E\u043D)?|\u043D\u043E\u043C\u0435\u0440)""" +
            """\.?[\s\u00A0:\-]{0,3})(\d{9})""" + BOUNDARY_AFTER
    )

    /** O'zbekiston mobil va shahar operator/hudud kodlari (mahalliy yozuvni tanishda tekshiriladi). */
    private val CODES = setOf(
        "20", "33", "50", "55", "61", "62", "65", "66", "67", "69", "70", "71", "72", "73", "74", "75", "76", "77", "78", "79",
        "88", "90", "91", "93", "94", "95", "97", "98", "99",
    )

    /** Ish yuzasidan birinchi tekshiruv: matnda kamida 9 ta raqam bo'lmasa, regexlarga hojat yo'q (tez yo'l). */
    private fun hasEnoughDigits(text: String): Boolean {
        var n = 0
        for (c in text) {
            if (c.isDigit()) {
                if (++n >= 9) return true
            }
        }
        return false
    }

    /** Matndagi barcha o'zbek telefon raqamlarini qulay o'qiladigan ko'rinishga keltiradi; boshqa matn o'zgarmaydi. */
    fun apply(text: String): String {
        if (text.length < 9 || !hasEnoughDigits(text)) return text
        val s1 = text
        val t1 = INTERNATIONAL.replace(s1) { m ->
            spoken(true, m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4], nextChar(s1, m))
        }
        val s2 = t1
        val t2 = LOCAL.replace(s2) { m ->
            val code = m.groupValues[1]
            if (code in CODES) spoken(false, code, m.groupValues[2], m.groupValues[3], m.groupValues[4], nextChar(s2, m)) else m.value
        }
        val s3 = t2
        return KEYWORD_BARE.replace(s3) { m ->
            val d = m.groupValues[2]
            if (d.substring(0, 2) in CODES) {
                m.groupValues[1] + spoken(false, d.substring(0, 2), d.substring(2, 5), d.substring(5, 7), d.substring(7, 9), nextChar(s3, m))
            } else {
                m.value
            }
        }
    }

    /** Raqamdan keyingi belgi (yo'q bo'lsa null): jumla tinish belgisi bo'lsa, oxiriga qo'shimcha nuqta qo'yilmaydi. */
    private fun nextChar(text: String, m: MatchResult): Char? {
        var i = m.range.last + 1
        // Probeldan keyin ham nuqta kelsa ("... 10 ."), ikkinchi nuqta qo'shilmaydi
        while (i < text.length && (text[i] == ' ' || text[i] == '\u00A0')) i++
        return if (i < text.length) text[i] else null
    }

    private fun spoken(international: Boolean, a: String, b: String, c: String, d: String, next: Char?): String {
        val parts = ArrayList<String>(5)
        if (international) parts.add("plyus " + UzNumbers.words(998))
        parts.add(group(a))
        parts.add(group(b))
        parts.add(group(c))
        parts.add(group(d))
        // Har bir guruhdan keyin nuqta: dvigatel to'xtab, keyingi guruhni alohida son sifatida o'qiydi
        val body = parts.joinToString(". ")
        val ends = next != null && ".,;:!?".indexOf(next) >= 0
        return if (ends) body else "$body."
    }

    /** Guruh son sifatida o'qiladi; 0 bilan boshlansa raqamma-raqam ("06" -> "nol olti"). */
    private fun group(d: String): String =
        if (d.length > 1 && d.startsWith("0")) {
            d.map { UzNumbers.words(it - '0') }.joinToString(" ")
        } else {
            UzNumbers.words(d.toLong())
        }
}
