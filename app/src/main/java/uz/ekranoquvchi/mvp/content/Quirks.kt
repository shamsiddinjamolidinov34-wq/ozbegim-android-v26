package uz.ekranoquvchi.mvp.content

/**
 * "Quirks" registri (FR-22, 12.2): ishlab chiqaruvchiga xos muammolar va aylanma yo'llar. Maslahat matni resursda
 * ("quirk_<kalit>") turadi va tizim tilida ko'rsatiladi; bu yerda faqat qaysi ishlab chiqaruvchiga qaysi kalit mos kelishi bor.
 */
object Quirks {
    private val RULES: List<Pair<Regex, String>> = listOf(
        Regex("xiaomi|redmi|poco") to "xiaomi",
        Regex("huawei|honor") to "huawei",
        Regex("oppo|realme|oneplus") to "oppo",
        Regex("vivo|iqoo") to "vivo",
        Regex("samsung") to "samsung",
        Regex("tecno|infinix|itel|transsion") to "tecno",
    )

    /** Ishlab chiqaruvchi nomlariga mos maslahat kalitlari ("xiaomi", "samsung" va h.k.). */
    fun keysFor(vararg names: String): List<String> {
        val key = names.joinToString(" ").lowercase()
        return RULES.filter { it.first.containsMatchIn(key) }.map { it.second }
    }
}
