package uz.ekranoquvchi.mvp.content

/**
 * Matn tili. O'zbekcha matnga O'zbegimning o'z qoidalari qo'llanadi: raqam, sana, pul, fan qisqartmalari, yozuv (lotin,
 * kirill), til ko'prigi, emoji nomlari. Boshqa tildagi matnga tegilmaydi: ovoz dvigateli uni o'z tilida o'qiydi.
 * Qaysi til ekani foydalanuvchi tanloviga bog'liq (Tafsilotlar > Matn tili):
 *  - auto (standart): tanlangan ovoz paketining tili (mahalliy yoki onlayn ovoz); ovoz tanlanmagan bo'lsa tizim tili;
 *  - system: doim tizim tili;
 *  - uz: doim o'zbekcha; other: doim boshqa til.
 * Tarmoqsiz, Android'siz: oddiy JVM'da sinaladi.
 */
object TextLang {
    const val AUTO = "auto"
    const val SYSTEM = "system"
    const val UZ = "uz"
    const val OTHER = "other"
    val ALL: Set<String> = setOf(AUTO, SYSTEM, UZ, OTHER)

    /** "uz", "uz-UZ", "uz_Cyrl_UZ", "uzb", "uz-UZ-MadinaNeural" kabi til yozuvi o'zbekchami. */
    fun isUzbekTag(tag: String?): Boolean {
        if (tag == null) return false
        val t = tag.trim().lowercase()
        if (t.isEmpty()) return false
        val cut = t.indexOfFirst { it == '-' || it == '_' }
        val lang = if (cut < 0) t else t.substring(0, cut)
        return lang == "uz" || lang == "uzb"
    }

    /**
     * @param choice foydalanuvchi tanlovi (noma'lum qiymat "auto" kabi ishlaydi)
     * @param systemTag tizim tili ("uz", "en-US", ...)
     * @param voiceTag tanlangan ovoz paketining tili; null yoki bo'sh bo'lsa ovoz tanlanmagan
     */
    fun isUzbek(choice: String, systemTag: String?, voiceTag: String?): Boolean = when (choice) {
        UZ -> true
        OTHER -> false
        SYSTEM -> isUzbekTag(systemTag)
        else -> if (voiceTag.isNullOrBlank()) isUzbekTag(systemTag) else isUzbekTag(voiceTag)
    }
}
