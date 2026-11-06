package uz.ekranoquvchi.mvp.content

enum class BridgeMode { ADAPT, OFF }

/** Kirill yozuvi bilan nima qilinadi. AUTO: dvigatel kirillni o'qiy olmasa lotinga o'tkazadi. */
enum class ScriptMode { AUTO, LATIN, KEEP }

/** Ş Ç Ö Ğ harflari. AUTO: ishonchli turkcha belgilar (ı İ ü) bo'lsa tegmaydi. */
enum class NewLatinMode { AUTO, UZ, OFF }

data class PipelineOptions(
    val bridge: BridgeMode = BridgeMode.ADAPT,
    val scriptMode: ScriptMode = ScriptMode.AUTO,
    val newLatin: NewLatinMode = NewLatinMode.AUTO,
    val engineReadsCyrillic: Boolean = false,
    val apostrophe: Char = '\'',
    val normalize: Boolean = true,
    val packageName: String? = null,
    /** Rim raqamlarini o'zbekcha tartib sonlarga aylantirish ("XIX asr"): faqat o'zbekcha tizim tilida. */
    val romanNumerals: Boolean = true,
    /** Emoji: KEEP (tegilmaydi), READ (o'zbekcha nom bilan o'qiladi), SKIP (aytilmaydi). */
    val emoji: EmojiMode = EmojiMode.KEEP,
    /** Matn faqat emojidan iborat bo'lib, hammasi olib tashlansa, aytiladigan so'z (fokus jim qolmasligi uchun). */
    val emojiWord: String? = null,
    /** O'zbekiston telefon raqamlari guruh-guruh, har guruhdan keyin nuqta bilan o'qiladi ("+998. 94. 773. 99. 10."). */
    val phoneGroups: Boolean = false,
    /** Fan qisqartmalari (birliklar, fizika, matematika, informatika): har biri o'zbekcha so'z bilan o'qiladi yoki aytilmaydi. */
    val science: ScienceOptions = ScienceOptions(),
    /**
     * O'zbekcha matn: raqam, sana, pul va fan qisqartmalari o'zbekcha so'zga aylanadi (ovoz dvigatellari buni o'zbekcha
     * qila olmaydi). false: matn boshqa tilda (tizim tili, ovoz paketi yoki foydalanuvchi tanlovi), bunday so'zlar
     * qo'llanmaydi va ovoz dvigateli matnni o'zi o'qiydi (inglizcha ovozga o'zbekcha "yuz yigirma" aytilmasligi uchun).
     */
    val uzbekText: Boolean = true,
) {
    fun convertsCyrillic(): Boolean = when (scriptMode) {
        ScriptMode.LATIN -> true
        ScriptMode.KEEP -> false
        ScriptMode.AUTO -> !engineReadsCyrillic
    }
}

data class Change(val original: String, val spoken: String, val kind: String)

data class PipelineResult(val text: String, val changes: List<Change>)

/**
 * Mazmunni tayyorlash quvuri (monografiya 16.1): normalizatsiya -> bo'laklash -> yozuvni aniqlash ->
 * moslashtirish. Tarmoqsiz, Android'siz: oddiy JVM'da sinaladi. Xato bo'lsa chaqiruvchi asl matnni o'qiydi.
 * Parollar, kodlar, URL, elektron pochta va raqamli tokenlar o'zgartirilmaydi.
 */
object ContentPipeline {
    private val TOKEN = Regex(
        """https?://\S+|www\.\S+|[\p{L}\p{N}._%+\-]+@[\p{L}\p{N}\-]+(?:\.[\p{L}\p{N}\-]+)+|""" +
            """[\p{L}\p{N}\p{M}]+(?:['\u02BB\u02BC\u2018\u2019`\u00B4\u2032\-][\p{L}\p{N}\p{M}]+)*|\s+|.""",
        // "." boshqa qator ajratgich belgilarga ham (U+2028, U+2029, U+0085) mos kelsin: aks holda ular matndan jimgina yo'qolardi
        RegexOption.DOT_MATCHES_ALL
    )

    fun process(input: String, opts: PipelineOptions = PipelineOptions()): PipelineResult {
        if (input.isEmpty()) return PipelineResult(input, emptyList())
        var text = TextNormalizer.normalize(input)
        if (opts.emoji != EmojiMode.KEEP) text = EmojiSpeech.apply(text, opts.emoji, opts.emojiWord)
        if (opts.normalize) {
            val sensitive = SensitiveText.looksSensitive(text)
            // Telefon raqamlari: guruhlarga ajratilib, har guruhdan keyin to'xtash bilan o'qiladi (Tafsilotlardagi tanlov)
            if (opts.phoneGroups) text = PhoneFormat.apply(text)
            // Rim raqamlari ("XIX asr") raqam-harf tekshiruvidan oldin: matnda raqam bo'lmasa ham ishlaydi
            if (!sensitive && opts.romanNumerals) text = RomanNumerals.normalize(text)
            if (opts.uzbekText) {
                // Fan qisqartmalari ("5 kg", "220 V", "2+3=5"): raqamlar so'zga aylanishidan oldin; kod va parolga tegilmaydi
                if (!sensitive) text = ScienceAbbr.apply(text, opts.science)
                text = SpeechNormalizer.normalize(text, sensitive)
            }
        }
        val turkish = opts.newLatin == NewLatinMode.AUTO && UzScripts.looksTurkish(text)
        val out = StringBuilder(text.length + 8)
        val changes = ArrayList<Change>(4)
        for (m in TOKEN.findAll(text)) {
            val tok = m.value
            if (isPlainWord(tok)) out.append(processWord(tok, opts, turkish, changes)) else out.append(tok)
        }
        return PipelineResult(out.toString(), changes)
    }

    private fun isPlainWord(tok: String): Boolean {
        if (!tok[0].isLetter()) return false
        if (tok.startsWith("http", ignoreCase = true) || tok.startsWith("www.", ignoreCase = true)) return false
        if (tok.contains('@')) return false
        for (c in tok) if (c.isDigit()) return false
        return true
    }

    private fun processWord(w0: String, o: PipelineOptions, turkish: Boolean, changes: MutableList<Change>): String {
        var w = UzScripts.unifyApostrophes(TextNormalizer.repairMixedScript(w0))
        when (TextNormalizer.dominantScript(w)) {
            Script.CYRILLIC -> {
                val russian = UzScripts.hasRussianOnlyLetters(w)
                val convert = o.convertsCyrillic() || (russian && o.bridge == BridgeMode.ADAPT)
                if (!convert) return w
                val latin = UzScripts.cyrillicToLatin(w)
                if (o.bridge == BridgeMode.ADAPT && !UzScripts.hasUzbekOnlyLetters(w)) {
                    changes.add(Change(w0, latin, "ru"))
                }
                return UzScripts.applyApostrophe(latin, o.apostrophe)
            }
            Script.LATIN -> {
                if (o.newLatin != NewLatinMode.OFF && !turkish && UzScripts.hasNewLatinLetters(w)) {
                    w = UzScripts.newLatinToCurrent(w)
                }
                if (o.bridge == BridgeMode.ADAPT) {
                    val adapted = BridgeDictionary.lookup(w.lowercase(), o.packageName)
                    if (adapted != null) {
                        val spoken = matchCase(w, adapted)
                        changes.add(Change(w0, spoken, "en"))
                        return spoken
                    }
                }
                return UzScripts.applyApostrophe(w, o.apostrophe)
            }
            else -> return w
        }
    }

    private fun matchCase(orig: String, adapted: String): String {
        val letters = orig.filter { it.isLetter() }
        return when {
            letters.length > 1 && letters.all { it.isUpperCase() } -> adapted.uppercase()
            orig.first().isUpperCase() -> adapted.substring(0, 1).uppercase() + adapted.substring(1)
            else -> adapted
        }
    }
}
