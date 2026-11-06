package uz.ekranoquvchi.mvp.content

/**
 * Emoji bilan nima qilinadi: KEEP (tegilmaydi, ovoz dvigateli o'zi hal qiladi), READ (o'zbekcha nomi bilan o'qiladi),
 * SKIP (umuman aytilmaydi).
 */
enum class EmojiMode { KEEP, READ, SKIP }

/**
 * Emoji va stikerlarni o'qish yoki o'qimaslik (Tafsilotlar sozlamasi). Tarmoqsiz, Android'siz: oddiy JVM'da sinaladi.
 * Yoqilganda eng ko'p uchraydigan emojilar o'zbekcha nom bilan o'qiladi ("😂" -> "kulgidan yig'layotgan yuz"), bir xil
 * emoji ketma-ket kelsa bir marta ("... 3 marta"). Nomi noma'lum emoji o'zgarishsiz qoladi. O'chirilganda hamma emoji
 * (teri rangi, bayroq, kombinatsiyalar bilan) matndan olib tashlanadi.
 * Oddiy matndagi "(c)", "™" kabi belgilar emoji hisoblanmaydi: ular faqat emoji ko'rinishi so'ralganda (U+FE0F) emoji bo'ladi.
 */
object EmojiSpeech {
    private const val ZWJ = 0x200D
    private const val VS16 = 0xFE0F
    private const val VS15 = 0xFE0E
    private const val KEYCAP = 0x20E3

    /** Odatiy ko'rinishi emoji bo'lgan BMP belgilar (juft-juft: dan, gacha). */
    private val BMP_EMOJI = intArrayOf(
        0x231A, 0x231B, 0x23E9, 0x23EC, 0x23F0, 0x23F0, 0x23F3, 0x23F3, 0x25FD, 0x25FE, 0x2614, 0x2615, 0x2648, 0x2653,
        0x267F, 0x267F, 0x2693, 0x2693, 0x26A1, 0x26A1, 0x26AA, 0x26AB, 0x26BD, 0x26BE, 0x26C4, 0x26C5, 0x26CE, 0x26CE,
        0x26D4, 0x26D4, 0x26EA, 0x26EA, 0x26F2, 0x26F3, 0x26F5, 0x26F5, 0x26FA, 0x26FA, 0x26FD, 0x26FD, 0x2705, 0x2705,
        0x270A, 0x270B, 0x2728, 0x2728, 0x274C, 0x274C, 0x274E, 0x274E, 0x2753, 0x2755, 0x2757, 0x2757, 0x2795, 0x2797,
        0x27B0, 0x27B0, 0x27BF, 0x27BF, 0x2B1B, 0x2B1C, 0x2B50, 0x2B50, 0x2B55, 0x2B55,
    )

    /** Odatda matn ko'rinishidagi, lekin chatlarda deyarli doim emoji sifatida yoziladigan belgilar (U+FE0F bo'lmasa ham). */
    private val COMMON_SYMBOLS = intArrayOf(
        0x2600, 0x2604, 0x260E, 0x260E, 0x2611, 0x2611, 0x2618, 0x2618, 0x261D, 0x261D, 0x2620, 0x2620, 0x2622, 0x2623,
        0x2626, 0x2626, 0x262A, 0x262A, 0x262E, 0x262F, 0x2638, 0x263A, 0x2640, 0x2640, 0x2642, 0x2642, 0x2660, 0x2660,
        0x2663, 0x2663, 0x2665, 0x2666, 0x2668, 0x2668, 0x267B, 0x267B, 0x267E, 0x267E, 0x2692, 0x2692, 0x2694, 0x2697,
        0x2699, 0x2699, 0x269B, 0x269C, 0x26A0, 0x26A0, 0x26A7, 0x26A7, 0x26B0, 0x26B1, 0x26C8, 0x26C8, 0x26CF, 0x26CF,
        0x26D1, 0x26D1, 0x26D3, 0x26D3, 0x26E9, 0x26E9, 0x26F0, 0x26F1, 0x26F4, 0x26F4, 0x26F7, 0x26F9, 0x2702, 0x2702,
        0x2708, 0x2709, 0x270C, 0x270D, 0x270F, 0x270F, 0x2712, 0x2712, 0x2714, 0x2714, 0x2716, 0x2716, 0x271D, 0x271D,
        0x2721, 0x2721, 0x2733, 0x2734, 0x2744, 0x2744, 0x2747, 0x2747, 0x2763, 0x2764, 0x27A1, 0x27A1,
    )

    /** Faqat emoji ko'rinishi so'ralganda (U+FE0F) emoji bo'ladigan belgilar: (c), (r), ™, strelkalar va h.k. */
    private val VS16_ONLY = intArrayOf(
        0x00A9, 0x00A9, 0x00AE, 0x00AE, 0x203C, 0x203C, 0x2049, 0x2049, 0x2122, 0x2122, 0x2139, 0x2139, 0x2194, 0x2199,
        0x21A9, 0x21AA, 0x2328, 0x2328, 0x23CF, 0x23CF, 0x23ED, 0x23EF, 0x23F1, 0x23F2, 0x23F8, 0x23FA, 0x24C2, 0x24C2,
        0x25AA, 0x25AB, 0x25B6, 0x25B6, 0x25C0, 0x25C0, 0x25FB, 0x25FC, 0x2934, 0x2935, 0x2B05, 0x2B07, 0x3030, 0x3030,
        0x303D, 0x303D, 0x3297, 0x3297, 0x3299, 0x3299,
    )

    /** Qo'shimcha tekislikdagi emoji (regional belgilar - bayroqlar - alohida qayta ishlanadi). */
    private val SUPPLEMENTARY = intArrayOf(
        0x1F004, 0x1F004, 0x1F0CF, 0x1F0CF, 0x1F170, 0x1F171, 0x1F17E, 0x1F17F, 0x1F18E, 0x1F18E, 0x1F191, 0x1F19A,
        0x1F201, 0x1F202, 0x1F21A, 0x1F21A, 0x1F22F, 0x1F22F, 0x1F232, 0x1F23A, 0x1F250, 0x1F251, 0x1F300, 0x1F64F,
        0x1F680, 0x1F6FF, 0x1F7E0, 0x1F7EB, 0x1F7F0, 0x1F7F0, 0x1F90C, 0x1F9FF, 0x1FA70, 0x1FAFF,
    )

    private fun inRanges(cp: Int, r: IntArray): Boolean {
        var i = 0
        while (i < r.size) {
            if (cp >= r[i] && cp <= r[i + 1]) return true
            i += 2
        }
        return false
    }

    private fun isEmojiBase(cp: Int, vs16: Boolean): Boolean {
        if (cp >= 0x1F000) return inRanges(cp, SUPPLEMENTARY)
        if (cp < 0x00A9) return false
        if (inRanges(cp, BMP_EMOJI) || inRanges(cp, COMMON_SYMBOLS)) return true
        return vs16 && inRanges(cp, VS16_ONLY)
    }

    /** Tez dastlabki tekshiruv: oddiy matnda hech qachon emoji bo'lmaydigan belgilar to'plami bilan. */
    private fun mayContain(s: String): Boolean {
        for (c in s) {
            if (c in '\u2190'..'\u2BFF' || c in '\uD83C'..'\uD83E') return true
            if (c == '\u200D' || c == '\uFE0F' || c == '\u20E3' || c == '\u203C' || c == '\u2049' || c == '\u2122') return true
            if (c == '\u2139' || c == '\u00A9' || c == '\u00AE' || c == '\u3030' || c == '\u303D' || c == '\u3297' || c == '\u3299') return true
        }
        return false
    }

    /**
     * Matndagi birinchi emojini qoldirib, qolganlarini olib tashlaydi (stikerni qisqa o'qish uchun). Emoji yo'q bo'lsa,
     * matn o'zgarmaydi.
     */
    fun keepFirst(text: String): String {
        if (!mayContain(text)) return text
        val out = StringBuilder(text.length)
        var i = 0
        var kept = false
        while (i < text.length) {
            val end = clusterEnd(text, i)
            if (end > i) {
                if (!kept) {
                    out.append(text, i, end)
                    kept = true
                }
                i = end
            } else {
                val cp = text.codePointAt(i)
                out.appendCodePoint(cp)
                i += Character.charCount(cp)
            }
        }
        return out.toString().replace(Regex("\\s{2,}"), " ").trim()
    }

    /** Matnda emoji bormi (stiker tavsifini aniqlash uchun). */
    fun containsEmoji(s: String): Boolean {
        if (!mayContain(s)) return false
        var i = 0
        while (i < s.length) {
            if (clusterEnd(s, i) > i) return true
            i += Character.charCount(s.codePointAt(i))
        }
        return false
    }

    /**
     * text[i] emoji (yoki emoji ketma-ketligi: teri rangi, ZWJ bilan birlashtirilgan, bayroq, tugma) boshi bo'lsa, uning
     * oxirgi indeksidan keyingi o'rnini qaytaradi; emoji bo'lmasa i.
     */
    private fun clusterEnd(text: String, i: Int): Int {
        val n = text.length
        val cp = text.codePointAt(i)
        var j = i + Character.charCount(cp)
        if (cp in 0x1F1E6..0x1F1FF) {
            if (j < n) {
                val c2 = text.codePointAt(j)
                if (c2 in 0x1F1E6..0x1F1FF) j += Character.charCount(c2)
            }
            return j
        }
        if (cp in 0x30..0x39 || cp == 0x23 || cp == 0x2A) {
            var k = j
            if (k < n && text[k] == '\uFE0F') k++
            return if (k < n && text[k] == '\u20E3') k + 1 else i
        }
        val vs16 = j < n && text[j] == '\uFE0F'
        if (!isEmojiBase(cp, vs16)) return i
        while (j < n) {
            val c = text.codePointAt(j)
            val len = Character.charCount(c)
            if (c == VS16 || c == VS15 || c == KEYCAP || c in 0x1F3FB..0x1F3FF || c in 0xE0020..0xE007F) {
                j += len
            } else if (c == ZWJ) {
                val k = j + len
                if (k < n) {
                    val c2 = text.codePointAt(k)
                    val after = k + Character.charCount(c2)
                    if (isEmojiBase(c2, after < n && text[after] == '\uFE0F')) {
                        j = after
                        continue
                    }
                }
                j += len
                break
            } else {
                break
            }
        }
        return j
    }

    /** Teri rangi va ko'rinish belgilarisiz kalit: bir xil emoji turli teri rangida bir nom bilan o'qiladi. */
    private fun keyOf(cluster: String): String {
        val sb = StringBuilder(cluster.length)
        var i = 0
        while (i < cluster.length) {
            val cp = cluster.codePointAt(i)
            i += Character.charCount(cp)
            if (cp == VS16 || cp == VS15 || cp in 0x1F3FB..0x1F3FF) continue
            sb.appendCodePoint(cp)
        }
        return sb.toString()
    }

    private fun nameOf(key: String): String? {
        NAMES[key]?.let { return it }
        val first = key.codePointAt(0)
        // Bayroq: ikkita regional belgi
        if (first in 0x1F1E6..0x1F1FF) {
            val len = Character.charCount(first)
            if (key.length > len) {
                val second = key.codePointAt(len)
                if (second in 0x1F1E6..0x1F1FF) {
                    val code = "" + ('A' + (first - 0x1F1E6)) + ('A' + (second - 0x1F1E6))
                    val country = COUNTRIES[code]
                    return if (country != null) "$country bayrog'i" else "bayroq"
                }
            }
            return null
        }
        // Tugma: "1️⃣" - raqamning o'zi (ro'yxat raqami kabi)
        if (key.endsWith("\u20E3")) {
            return when (val base = key[0]) {
                '#' -> "panjara"
                '*' -> "yulduzcha"
                else -> if (base in '0'..'9') base.toString() else null
            }
        }
        // Hudud bayroqlari (teg belgilari bilan): umumiy nom
        if (first == 0x1F3F4 && hasTagChars(key)) return "bayroq"
        // ZWJ ketma-ketligi: qismlarning nomlari birga ("ayol noutbuk"); bitta qismning nomi noma'lum bo'lsa, emoji tegilmaydi
        if (key.indexOf(ZWJ.toChar()) >= 0) {
            val parts = key.split(ZWJ.toChar().toString())
            val names = ArrayList<String>(parts.size)
            for (p in parts) {
                if (p.isEmpty()) continue
                names.add(NAMES[p] ?: return null)
            }
            return if (names.isEmpty()) null else names.joinToString(" ")
        }
        return null
    }

    private fun hasTagChars(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (cp in 0xE0020..0xE007F) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /** text dagi emoji mode bo'yicha o'qiladi yoki olib tashlanadi. silentWord: SKIP rejimida matn faqat emojidan iborat bo'lsa aytiladigan so'z. */
    fun apply(text: String, mode: EmojiMode, silentWord: String? = null): String {
        if (mode == EmojiMode.KEEP || !mayContain(text)) return text
        val n = text.length
        val out = StringBuilder(n + 16)
        var i = 0
        var pendingSpace = false
        var runKey: String? = null
        var runName = ""
        var runCount = 0

        fun flushRun() {
            if (runKey == null) return
            val spoken = if (runCount >= 2) "$runName $runCount marta" else runName
            if (out.isNotEmpty() && !out[out.length - 1].isWhitespace()) out.append(' ')
            out.append(spoken)
            pendingSpace = true
            runKey = null
            runCount = 0
        }

        while (i < n) {
            val end = clusterEnd(text, i)
            if (end > i) {
                if (mode == EmojiMode.SKIP) {
                    i = end
                    continue
                }
                val cluster = text.substring(i, end)
                val key = keyOf(cluster)
                val name = if (key.isEmpty()) null else nameOf(key)
                if (name == null) {
                    // Nomi noma'lum emoji: o'zgarishsiz qoldiriladi (ovoz dvigateli o'zi hal qiladi)
                    flushRun()
                    if (pendingSpace) {
                        out.append(' ')
                        pendingSpace = false
                    }
                    out.append(cluster)
                } else if (key == runKey) {
                    runCount++
                } else {
                    flushRun()
                    runKey = key
                    runName = name
                    runCount = 1
                }
                i = end
                continue
            }
            flushRun()
            val cp = text.codePointAt(i)
            if (pendingSpace) {
                if (!Character.isWhitespace(cp)) out.append(' ')
                pendingSpace = false
            }
            out.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        flushRun()
        val result = out.toString()
        // Butun matn emojidan iborat bo'lib, hammasi olib tashlansa: fokus jim qolmasligi uchun qisqa so'z aytiladi
        return if (mode == EmojiMode.SKIP && silentWord != null && result.isBlank() && text.isNotBlank()) silentWord else result
    }

    /** Davlat kodi -> o'zbekcha nomi (bayroqlar uchun). */
    private val COUNTRIES: Map<String, String> = mapOf(
        "UZ" to "O'zbekiston", "RU" to "Rossiya", "US" to "Amerika Qo'shma Shtatlari", "GB" to "Buyuk Britaniya",
        "TR" to "Turkiya", "KZ" to "Qozog'iston", "KG" to "Qirg'iziston", "TJ" to "Tojikiston", "TM" to "Turkmaniston",
        "AF" to "Afg'oniston", "CN" to "Xitoy", "DE" to "Germaniya", "FR" to "Fransiya", "IT" to "Italiya",
        "ES" to "Ispaniya", "JP" to "Yaponiya", "KR" to "Janubiy Koreya", "KP" to "Shimoliy Koreya", "IN" to "Hindiston",
        "PK" to "Pokiston", "SA" to "Saudiya Arabistoni", "AE" to "Birlashgan Arab Amirliklari", "EG" to "Misr",
        "UA" to "Ukraina", "BY" to "Belarus", "AZ" to "Ozarbayjon", "GE" to "Gruziya", "AM" to "Armaniston",
        "CA" to "Kanada", "BR" to "Braziliya", "AR" to "Argentina", "MX" to "Meksika", "AU" to "Avstraliya",
        "IR" to "Eron", "IQ" to "Iroq", "IL" to "Isroil", "PS" to "Falastin", "SY" to "Suriya", "JO" to "Iordaniya",
        "LB" to "Livan", "KW" to "Quvayt", "QA" to "Qatar", "OM" to "Ummon", "YE" to "Yaman", "MA" to "Marokash",
        "DZ" to "Jazoir", "TN" to "Tunis", "LY" to "Liviya", "SD" to "Sudan", "NG" to "Nigeriya", "ZA" to "Janubiy Afrika",
        "PL" to "Polsha", "NL" to "Niderlandiya", "BE" to "Belgiya", "CH" to "Shveytsariya", "AT" to "Avstriya",
        "SE" to "Shvetsiya", "NO" to "Norvegiya", "FI" to "Finlyandiya", "DK" to "Daniya", "PT" to "Portugaliya",
        "GR" to "Gretsiya", "CZ" to "Chexiya", "HU" to "Vengriya", "RO" to "Ruminiya", "BG" to "Bolgariya",
        "RS" to "Serbiya", "HR" to "Xorvatiya", "BA" to "Bosniya va Gertsegovina", "AL" to "Albaniya", "XK" to "Kosovo",
        "MK" to "Shimoliy Makedoniya", "ME" to "Chernogoriya", "SK" to "Slovakiya", "SI" to "Sloveniya", "LT" to "Litva",
        "LV" to "Latviya", "EE" to "Estoniya", "MD" to "Moldova", "ID" to "Indoneziya", "MY" to "Malayziya",
        "SG" to "Singapur", "TH" to "Tailand", "VN" to "Vyetnam", "PH" to "Filippin", "BD" to "Bangladesh",
        "LK" to "Shri-Lanka", "NP" to "Nepal", "MN" to "Mongoliya", "TW" to "Tayvan", "HK" to "Gonkong",
        "NZ" to "Yangi Zelandiya", "CU" to "Kuba", "CO" to "Kolumbiya", "CL" to "Chili", "PE" to "Peru",
        "VE" to "Venesuela", "IE" to "Irlandiya", "IS" to "Islandiya", "EU" to "Yevropa Ittifoqi",
    )

    /** Emoji -> o'zbekcha nom. Har qator: emoji, bo'sh joy, nom. Teri rangi va U+FE0F siz yoziladi. */
    private val NAMES: Map<String, String> by lazy {
        val m = HashMap<String, String>(512)
        for (line in TABLE.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val sp = t.indexOf(' ')
            if (sp <= 0) continue
            // Kalit ham teri rangi va U+FE0F siz saqlanadi: jadvalga tasodifan tushgan ko'rinish belgisi moslikni buzmasin
            m[keyOf(t.substring(0, sp))] = t.substring(sp + 1).trim()
        }
        m
    }

    private const val TABLE = """
😀 kulib turgan yuz
😃 og'zi ochiq kulgan yuz
😄 ko'zlari kulgan yuz
😁 tishlari ko'ringan yuz
😆 qattiq kulgan yuz
😅 terlab kulgan yuz
😂 kulgidan yig'layotgan yuz
🤣 yerda dumalab kulayotgan yuz
😊 tabassumli yuz
🙂 sal tabassumli yuz
🙃 teskari yuz
😉 ko'z qisgan yuz
😇 farishta yuz
🥰 sevgi bilan kulgan yuz
😍 ko'zlari yurak bo'lgan yuz
🤩 ko'zlari yulduz bo'lgan yuz
😘 havo o'pichi yuborayotgan yuz
😗 o'pich yuz
😚 ko'zi yumuq o'pich yuz
😙 tabassumli o'pich yuz
😋 mazali yuz
😛 tilini chiqargan yuz
😜 ko'z qisib tilini chiqargan yuz
🤪 telba yuz
😝 ko'zi yumuq tilini chiqargan yuz
🤑 pulga berilgan yuz
🤗 quchoqlayotgan yuz
🤭 og'zini kafti bilan yopgan yuz
🤫 jim bo'l degan yuz
🤔 o'ylayotgan yuz
🤐 og'zi yopiq yuz
🤨 qoshini ko'targan yuz
😐 befarq yuz
😑 ifodasiz yuz
😶 og'zi yo'q yuz
😏 kinoyali yuz
😒 norozi yuz
🙄 ko'zini aylantirgan yuz
😬 tishini ko'rsatgan yuz
😌 yengil tortgan yuz
😔 xafa yuz
😪 uyqusirayotgan yuz
🤤 so'lagi oqayotgan yuz
😴 uxlayotgan yuz
😷 niqobli yuz
🤒 isitmali yuz
🤕 boshi bog'langan yuz
🤢 ko'ngli aynigan yuz
🤮 qusayotgan yuz
🤧 aksa urayotgan yuz
🥵 issiqdan qizargan yuz
🥶 sovqotgan yuz
🥴 boshi aylangan yuz
😵 boshi aylanib ketgan yuz
🤯 boshi portlagan yuz
🤠 kovboy shlyapali yuz
🥳 bayramona yuz
😎 quyosh ko'zoynakli yuz
🤓 ko'zoynakli yuz
🧐 monokl taqqan yuz
😕 hayron yuz
😟 xavotirli yuz
🙁 sal xafa yuz
☹ xafa yuz
☺ tabassumli yuz
😮 og'zi ochiq yuz
😯 jim qolgan yuz
😲 hayratda qolgan yuz
😳 qizarib ketgan yuz
🥺 yolvorayotgan yuz
😦 og'zi ochiq xavotirli yuz
😧 iztirobli yuz
😨 qo'rqqan yuz
😰 sovuq terlagan yuz
😥 xafa bo'lib yengil tortgan yuz
😢 yig'layotgan yuz
😭 hiqillab yig'layotgan yuz
😱 qo'rquvdan qichqirayotgan yuz
😖 cho'chigan yuz
😣 sabr qilayotgan yuz
😞 ruhi tushgan yuz
😓 terlagan xafa yuz
😩 charchagan yuz
😫 holdan toygan yuz
🥱 esnayotgan yuz
😤 burnidan bug' chiqarayotgan yuz
😡 jahldor yuz
😠 g'azabli yuz
🤬 so'kinayotgan yuz
😈 shoxli kulgan shayton
👿 shoxli g'azabli shayton
💀 bosh suyagi
☠ bosh suyagi va suyaklar
💩 axlat
🤡 masxaraboz
👹 devning yuzi
👺 jin yuzi
👻 arvoh
👽 o'zga sayyoralik
👾 kosmik hayvon
🤖 robot
🙈 ko'zini yopgan maymun
🙉 quloqlarini yopgan maymun
🙊 og'zini yopgan maymun
💋 o'pich izi
💌 sevgi xati
💘 o'q tekkan yurak
💝 bantli yurak
💖 yaltiroq yurak
💗 kattalashayotgan yurak
💓 urayotgan yurak
💞 aylanayotgan yuraklar
💕 ikki yurak
💟 yurakli bezak
❣ yurakli undov
💔 singan yurak
❤ qizil yurak
🧡 to'q sariq yurak
💛 sariq yurak
💚 yashil yurak
💙 ko'k yurak
💜 binafsha yurak
🤎 jigarrang yurak
🖤 qora yurak
🤍 oq yurak
♥ yurak
💯 yuz ball
💢 g'azab belgisi
💥 portlash
💫 boshi aylanish belgisi
💦 suv tomchilari
💨 shamol
💣 bomba
💬 xabar belgisi
💭 o'y belgisi
💤 uyqu belgisi
👋 qo'l silkitayotgan kaft
🤚 ko'tarilgan qo'l orqasi
🖐 ochiq kaft
✋ to'xtash kafti
🖖 vulkan salomi
👌 yaxshi belgisi
🤌 barmoqlari qisilgan qo'l
🤏 sal qisilgan barmoqlar
✌ g'alaba belgisi
🤞 barmoqlari kesishgan qo'l
🤟 sevaman belgisi
🤘 shox belgisi
🤙 qo'ng'iroq qiling belgisi
👈 chapga ko'rsatayotgan barmoq
👉 o'ngga ko'rsatayotgan barmoq
👆 tepaga ko'rsatayotgan barmoq
🖕 o'rta barmoq
👇 pastga ko'rsatayotgan barmoq
☝ barmog'ini ko'targan qo'l
👍 yoqdi
👎 yoqmadi
✊ musht
👊 musht zarbasi
🤛 chapga musht
🤜 o'ngga musht
👏 qarsak
🙌 qo'llarini ko'targan odam
👐 ochiq kaftlar
🤲 duo uchun ochilgan kaftlar
🤝 qo'l siqish
🙏 duo qilayotgan qo'llar
✍ yozayotgan qo'l
💅 tirnoq bo'yash
🤳 selfi
💪 kuchli qo'l
👀 ko'zlar
👁 ko'z
👅 til
👄 og'iz
🧠 miya
👶 chaqaloq
👦 o'g'il bola
👧 qiz bola
👨 erkak
👩 ayol
👴 chol
👵 kampir
🙋 qo'l ko'targan odam
🤦 peshonasiga urgan odam
🤷 yelka qisgan odam
👪 oila
👫 qo'l ushlashgan yigit va qiz
💏 o'pishayotgan juftlik
💑 yurakli juftlik
🕺 raqsga tushayotgan erkak
💃 raqsga tushayotgan ayol
🚶 yurayotgan odam
🏃 yugurayotgan odam
👮 politsiyachi
👷 quruvchi
🧕 ro'mol o'ragan ayol
👳 salla o'ragan erkak
🎅 Qorbobo
🤰 homilador ayol
👼 farishta chaqaloq
🐶 it
🐕 it
🐱 mushuk
🐈 mushuk
🐭 sichqon
🐹 hamster
🐰 quyon
🐇 quyon
🦊 tulki
🐻 ayiq
🐼 panda
🐨 koala
🐯 yo'lbars
🦁 sher
🐮 sigir
🐄 sigir
🐷 cho'chqa
🐸 baqa
🐵 maymun yuzi
🐒 maymun
🐔 tovuq
🐧 pingvin
🐦 qush
🐤 jo'ja
🦆 o'rdak
🦅 burgut
🦉 boyqush
🦇 ko'rshapalak
🐺 bo'ri
🐗 yovvoyi cho'chqa
🐴 ot
🐎 ot
🦄 yagona shoxli ot
🐝 asalari
🐛 qurt
🦋 kapalak
🐌 shilliqqurt
🐞 xonqizi
🐜 chumoli
🕷 o'rgimchak
🦂 chayon
🐢 toshbaqa
🐍 ilon
🦎 kaltakesak
🐙 sakkizoyoq
🦀 qisqichbaqa
🐠 baliq
🐟 baliq
🐡 shar baliq
🦈 akula
🐬 delfin
🐳 kit
🐋 kit
🐊 timsoh
🐘 fil
🦒 jirafa
🦓 zebra
🦏 karkidon
🐪 tuya
🐫 ikki o'rkachli tuya
🐑 qo'y
🐐 echki
🐓 xo'roz
🦃 kurka
🕊 kaptar
🐿 olmaxon
🦔 tipratikan
🌹 atirgul
🥀 so'lgan gul
🌷 lola
🌸 gul
💐 guldasta
🌺 gibiskus
🌻 kungaboqar
🌼 gul
🌿 o'tlar
🍀 to'rt bargli yonca
🍃 shamolda uchayotgan barg
🍂 to'kilgan barglar
🍁 chinor bargi
🌱 ko'chat
🌳 daraxt
🌲 igna bargli daraxt
🌴 palma
🌵 kaktus
🌾 bug'doy boshoq
🍄 qo'ziqorin
🌍 Yer shari
🌎 Yer shari
🌏 Yer shari
🌐 globus
🌕 to'lin oy
🌙 yarim oy
🌛 yarim oy
🌝 yuzli to'lin oy
🌞 yuzli quyosh
⭐ yulduz
🌟 yaltiroq yulduz
✨ yaltirash
⚡ chaqmoq
🔥 olov
🌈 kamalak
☀ quyosh
🌤 quyoshli bulut
⛅ bulutli quyosh
☁ bulut
🌧 yomg'irli bulut
⛈ momaqaldiroqli bulut
🌩 chaqmoqli bulut
❄ qor parchasi
☃ qorbobo
⛄ qorbobo
☔ yomg'irli soyabon
☂ soyabon
💧 tomchi
🌊 to'lqin
🌪 girdob
🌫 tuman
🍎 qizil olma
🍏 yashil olma
🍐 nok
🍊 apelsin
🍋 limon
🍌 banan
🍉 tarvuz
🍇 uzum
🍓 qulupnay
🍒 olcha
🍑 shaftoli
🍍 ananas
🥭 mango
🥝 kivi
🍅 pomidor
🍆 baqlajon
🥑 avokado
🥕 sabzi
🌽 makkajo'xori
🥔 kartoshka
🍞 non
🥐 kruassan
🧀 pishloq
🍳 qovurilgan tuxum
🥞 blin
🥓 bekon
🍗 tovuq oyog'i
🍖 go'sht
🍕 pitsa
🍔 burger
🍟 fri kartoshka
🌭 xot-dog
🌮 tako
🍝 makaron
🍜 lag'mon
🍚 guruch
🍛 guruchli taom
🍰 tort
🎂 tug'ilgan kun torti
🍦 muzqaymoq
🍨 muzqaymoq
🍩 ponchik
🍪 pechenye
🍫 shokolad
🍬 konfet
🍭 lolipop
🍯 asal
☕ issiq ichimlik
🍵 choy
🍶 sake
🍺 pivo
🍻 pivo kosalari
🥂 qadahlar
🍷 sharob
🥤 stakan
🥛 sut
🍼 sut shishasi
🍾 shampan
⚽ futbol to'pi
🏀 basketbol to'pi
🏈 amerika futboli
⚾ beysbol
🎾 tennis
🏐 voleybol
🏆 kubok
🥇 oltin medal
🥈 kumush medal
🥉 bronza medal
🏅 medal
🎯 nishon
🎮 o'yin pulti
🎲 zar
♟ shaxmat piyodasi
🎵 nota
🎶 notalar
🎤 mikrofon
🎧 quloqchin
🎸 gitara
🎹 pianino
🥁 baraban
🎁 sovg'a
🎈 shar
🎉 bayram konfettisi
🎊 konfeti to'pi
🎀 lenta
🎄 yolka
🎃 qovoq chiroq
🎬 kino
🎥 kino kamerasi
🎨 rassom palitrasi
🚗 mashina
🚕 taksi
🚙 jip
🚌 avtobus
🚎 trolleybus
🏎 poyga mashinasi
🚑 tez yordam
🚒 o't o'chirish mashinasi
🚓 politsiya mashinasi
🚚 yuk mashinasi
🚲 velosiped
🛵 skuter
🏍 mototsikl
✈ samolyot
🚀 raketa
🚁 vertolyot
🚆 poyezd
🚇 metro
🚢 kema
⛵ yelkanli qayiq
⚓ langar
🏠 uy
🏡 bog'li uy
🏢 bino
🏥 kasalxona
🏦 bank
🏫 maktab
🕌 masjid
🕋 Ka'ba
⛪ cherkov
🌅 quyosh chiqishi
🌃 kechki shahar
🌉 kechki ko'prik
🗺 xarita
🏝 orol
⛰ tog'
🏔 qorli tog'
🌋 vulqon
📱 mobil telefon
☎ telefon
📞 telefon go'shagi
💻 noutbuk
🖥 kompyuter
⌨ klaviatura
🖱 sichqoncha
📷 fotoapparat
📸 chaqnash bilan fotoapparat
📹 videokamera
📺 televizor
📻 radio
⏰ budilnik
⌚ soat
⏳ qum soati
⌛ qum soati
🔔 qo'ng'iroq
🔕 o'chirilgan qo'ng'iroq
📢 karnay
📣 megafon
💡 lampochka
🔋 batareya
🔌 elektr vilkasi
🔍 lupa
🔎 lupa
🔒 qulf
🔓 ochiq qulf
🔑 kalit
🔨 bolg'a
🛠 asboblar
⚙ tishli g'ildirak
💰 pul xaltasi
💵 dollar
💳 bank kartasi
💎 olmos
📚 kitoblar
📖 ochiq kitob
📕 yopiq kitob
📝 yozuv
✏ qalam
🖊 ruchka
📌 knopka
📎 skrepka
✂ qaychi
📅 kalendar
📆 kalendar
📊 diagramma
📈 o'sayotgan grafik
📉 tushayotgan grafik
✉ xat
📧 elektron xat
📨 kelayotgan xat
📩 xat
📦 quti
📫 pochta qutisi
🎒 ryukzak
👑 toj
👓 ko'zoynak
🕶 quyosh ko'zoynagi
👔 galstukli ko'ylak
👕 futbolka
👖 jinsi shim
👗 ko'ylak
👟 krossovka
👠 poshnali tufli
🎓 bitiruvchi shlyapasi
💄 lab bo'yog'i
💍 uzuk
🌂 yopiq soyabon
🧳 chamadon
🧸 ayiqcha o'yinchoq
🕯 sham
🛒 xarid aravachasi
🚪 eshik
🛏 karavot
🚽 hojatxona
🚿 dush
🛁 vanna
🧼 sovun
🧴 krem
🧹 supurgi
🧺 savat
💊 dori
💉 shprits
🩺 stetoskop
🌡 termometr
🧪 probirka
🔬 mikroskop
🔭 teleskop
📡 sun'iy yo'ldosh antennasi
💾 disket
💿 disk
✅ tasdiq belgisi
✔ belgi
☑ belgilangan katak
❌ bekor belgisi
❎ bekor katagi
❓ savol belgisi
❔ savol belgisi
❗ undov belgisi
❕ undov belgisi
‼ ikkita undov
⁉ undov va savol
⚠ ogohlantirish belgisi
🚫 taqiqlangan
⛔ kirish taqiqlangan
🔞 o'n sakkizdan kichiklarga mumkin emas
➕ qo'shish belgisi
➖ ayirish belgisi
➗ bo'lish belgisi
✖ ko'paytirish belgisi
♻ qayta ishlash belgisi
⭕ katta doira
🔴 qizil doira
🟠 to'q sariq doira
🟡 sariq doira
🟢 yashil doira
🔵 ko'k doira
🟣 binafsha doira
⚫ qora doira
⚪ oq doira
🟤 jigarrang doira
🔶 katta to'q sariq romb
🔷 katta ko'k romb
🔺 qizil uchburchak
🔻 pastga qaragan qizil uchburchak
⬆ tepaga strelka
⬇ pastga strelka
⬅ chapga strelka
➡ o'ngga strelka
↗ yuqori o'ngga strelka
↩ chapga qaytuvchi strelka
🔄 aylanma strelkalar
🔁 takrorlash
▶ o'ynatish
⏸ pauza
⏹ to'xtatish
⏩ tez oldinga
⏪ tez orqaga
🆗 OK belgisi
🆕 yangi belgisi
🆓 bepul belgisi
🔝 yuqori belgisi
🔜 tez orada belgisi
🔙 orqaga belgisi
🔛 yoqilgan belgisi
🆘 yordam chaqiruvi
ℹ axborot belgisi
☮ tinchlik belgisi
☯ in va yan
✝ xoch
☪ yarim oy va yulduz
✡ Dovud yulduzi
♾ cheksizlik
🔱 uch uchli nayza
🏁 marra bayrog'i
🚩 qizil bayroqcha
🏳 oq bayroq
🏴 qora bayroq
🎌 kesishgan bayroqlar
🏳‍🌈 kamalak bayrog'i
"""
}
