package uz.ekranoquvchi.mvp.core

import android.accessibilityservice.AccessibilityService
import android.content.Context
import uz.ekranoquvchi.mvp.diag.swallowed
import uz.ekranoquvchi.mvp.util.Prefs

/** Ishoraga biriktiriladigan amallar (FR-02, FR-21). Nomlar o'zbekcha (lotin); ekranda tizim tilida ko'rsatiladi (displayName). */
enum class GestureAction(val label: String) {
    NONE("Hech narsa"),

    // Fokus harakati
    NEXT("Keyingi element (tanlangan o'qish boshqaruvi bo'yicha)"),
    PREV("Oldingi element (tanlangan o'qish boshqaruvi bo'yicha)"),
    NEXT_ITEM("Keyingi element (rejimdan qat'i nazar)"),
    PREV_ITEM("Oldingi element (rejimdan qat'i nazar)"),
    MODE_NEXT("Keyingi rejim"),
    MODE_PREV("Oldingi rejim"),
    FIRST_ITEM("Birinchi elementga o'tish"),
    JUMP_LAST("Oxirgi elementga o'tish"),
    NEXT_WINDOW("Keyingi oynaga o'tish"),
    PREV_WINDOW("Oldingi oynaga o'tish"),

    // Faollashtirish va qiymat
    ACTIVATE("Faollashtirish (bosish)"),
    LONG_PRESS("Uzoq bosish"),
    VALUE_UP("Slayder qiymatini oshirish"),
    VALUE_DOWN("Slayder qiymatini kamaytirish"),

    // Tizim amallari
    BACK("Orqaga"),
    HOME("Bosh ekran"),
    RECENTS("So'nggi ilovalar"),
    OPEN_APPS("Ilovalar menyusini ochish"),
    NOTIFICATIONS("Bildirishnomalar"),
    QUICK_SETTINGS("Tezkor sozlamalar"),
    LOCK_SCREEN("Ekranni qulflash"),
    SCREENSHOT("Ekran rasmini olish"),
    POWER_DIALOG("O'chirish menyusi"),
    SPLIT_SCREEN("Ekranni ikkiga bo'lish"),
    MEDIA_PLAY_PAUSE("Media: ijro etish yoki to'xtatish"),

    // Aylantirish va ekranni surish
    SCROLL_FWD("Pastga aylantirish"),
    SCROLL_BACK("Yuqoriga aylantirish"),
    SCROLL_LEFT("Chapga aylantirish"),
    SCROLL_RIGHT("O'ngga aylantirish"),
    SCREEN_SWIPE_UP("Ekranni yuqoriga surish (qulfni ochish)"),
    SCREEN_SWIPE_DOWN("Ekranni pastga surish"),
    SCREEN_SWIPE_LEFT("Ekranni chapga surish"),
    SCREEN_SWIPE_RIGHT("Ekranni o'ngga surish"),

    // Matn bilan ishlash
    SELECT_ALL("Hammasini tanlash"),
    COPY_TEXT("Matnni nusxalash"),
    CUT_TEXT("Matnni kesish"),
    PASTE_TEXT("Matnni qo'yish"),

    // Nutq
    STOP_SPEECH("Nutqni to'xtatish"),
    SPEED_UP("Nutqni tezlashtirish"),
    SPEED_DOWN("Nutqni sekinlashtirish"),
    READ_FROM_TOP("Boshidan uzluksiz o'qish"),
    READ_FROM_NEXT("Keyingisidan uzluksiz o'qish"),
    REPEAT_LAST("Oxirgi aytilganni takrorlash"),
    COPY_LAST("Oxirgi aytilganni nusxalash"),
    SAVE_AUDIO("Oxirgi o'qilganni MP3 qilib saqlash"),
    STATUS("Soat va batareya"),
    CURTAIN("Ekran pardasi"),

    // Menyular va qidiruv
    GLOBAL_MENU("O'zbegim menyusi"),
    ACTIONS_MENU("Element amallari menyusi"),
    SCREEN_SEARCH("Ekranda qidirish"),

    // Til, tarjima, sun'iy intellekt
    TRANSLATE("Tarjima qilish (fokusdagi matnni)"),
    AUTO_TRANSLATE("Avtomatik tarjimani yoqish yoki o'chirish"),
    DESCRIBE_SCREEN("Ekranni sun'iy intellekt bilan tasvirlash"),
    DESCRIBE_IMAGE("Rasmga tavsif berish"),
    LINKS_MENU("Mavjud havolalarga o'tish"),
    EXPLAIN_BRIDGE("So'z asli qanday edi"),
    ADD_WORD("So'zni talaffuz lug'atiga qo'shish"),
    ADD_LABEL("Elementga yorliq qo'shish"),

    // Qo'ng'iroq va maxfiylik
    CALL_ANSWER("Qo'ng'iroqqa javob berish"),
    CALL_END("Qo'ng'iroqni tugatish"),
    CALL_SPEAKER("Karnayni yoqish yoki o'chirish"),
    READ_PRIVATE("Maxfiy xabarni o'qish"),

    // Xizmat
    MARK_PROBLEM("Muammo belgisi"),
    PRACTICE("Ishoralar mashqi"),
    OPEN_SETTINGS("O'zbegim sozlamalari"),
    TTS_SETTINGS("Matnni nutqqa aylantirish sozlamalari"),
    SYSTEM_ACTIONS("Tizim amallari"),
    DETAILS_MENU("Tafsilotlar menyusi"),
    SPEED_MODE("Javob tezligi rejimini almashtirish"),
    SUSPEND("To'xtatish yoki davom ettirish"),

    // O'zbegim sozlamalarining bo'limlari (O'zbegim menyusida havola sifatida; ishoraga ham biriktirish mumkin)
    SETTINGS_MENU("O'zbegim sozlamalari bo'limlari (ro'yxat)"),
    OPEN_VOICES("Ovozlar sozlamalari"),
    OPEN_GESTURES("Ishoralarni sozlash"),
    OPEN_DETAILS("Tafsilotlar sozlamalari"),
    OPEN_AI("Sun'iy intellekt va onlayn imkoniyatlar sozlamalari"),
    OPEN_BACKUP("Sozlamalarni eksport va import"),
    OPEN_HEALTH("Tizim tekshiruvi"),
    OPEN_LOG("Xatolar jurnali"),
    OPEN_MENU_EDITOR("O'zbegim menyusini sozlash"),

    // Matn bo'ylab yurish: o'qish boshqaruvini almashtirmasdan, fokusdagi element matnida (masalan, Telegram xabarida)
    NEXT_CHAR("Keyingi harf"),
    PREV_CHAR("Oldingi harf"),
    NEXT_WORD("Keyingi so'z"),
    PREV_WORD("Oldingi so'z"),
    NEXT_LINE("Keyingi qator (gap)"),
    PREV_LINE("Oldingi qator (gap)"),
    NEXT_PARA("Keyingi xatboshi"),
    PREV_PARA("Oldingi xatboshi"),
    SPELL_FOCUSED("Fokusdagi matnni harflab o'qish"),
    SPELL_LAST("Oxirgi aytilganni harflab o'qish"),

    // O'qish boshqaruvini bevosita tanlash va ovoz toni
    READING_MENU("O'qish boshqaruvini tanlash menyusi"),
    PITCH_UP("Ovoz tonini ko'tarish"),
    PITCH_DOWN("Ovoz tonini pasaytirish"),
}

/** Resurs nomi bo'yicha tizim tilidagi matn; resursda bo'lmasa (yoki xato bo'lsa) o'zbekcha lotin matni qaytariladi. */
private fun localized(c: Context, resName: String, fallback: String): String =
    try {
        val id = c.resources.getIdentifier(resName, "string", c.packageName)
        if (id != 0) c.getString(id) else fallback
    } catch (_: Throwable) {
        fallback
    }

/** Amal nomi tizim tilida (resurs "ga_<nom>"); topilmasa o'zbekcha lotin nomi. */
fun GestureAction.displayName(c: Context): String = localized(c, "ga_" + name.lowercase(), label)

class GestureItem(val id: Int, val name: String, val key: String = "") {
    /** Ishora nomi tizim tilida (resurs "gi_<kalit>"); topilmasa o'zbekcha lotin nomi. */
    fun displayName(c: Context): String = if (key.isEmpty()) name else localized(c, "gi_$key", name)
}

/**
 * Ishoralar xaritasi. Standart profil Android TalkBack (9.1 va yangi) bilan mos: bir barmoqli ishoralar, ikki, uch va
 * to'rt barmoqli ishoralar. TalkBack'da yo'q O'zbegim imkoniyatlari (tarjima, tavsif, soat, parda, qo'ng'iroq) bo'sh
 * qolgan ishoralarga va O'zbegim menyusiga joylangan. Hammasini "Ishoralarni sozlash" ekranida o'zgartirish mumkin.
 */
object GestureMap {
    private const val DEFAULTS_VERSION = 3
    private const val KEY_DEFAULTS_VERSION = "gesture_defaults_version"

    val items: List<GestureItem> = listOf(
        GestureItem(AccessibilityService.GESTURE_SWIPE_RIGHT, "Bir barmoq: o'ngga surish", "swipe_right"),
        GestureItem(AccessibilityService.GESTURE_SWIPE_LEFT, "Bir barmoq: chapga surish", "swipe_left"),
        GestureItem(AccessibilityService.GESTURE_SWIPE_UP, "Bir barmoq: yuqoriga surish", "swipe_up"),
        GestureItem(AccessibilityService.GESTURE_SWIPE_DOWN, "Bir barmoq: pastga surish", "swipe_down"),
        GestureItem(AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT, "Yuqoriga, so'ng chapga", "swipe_up_and_left"),
        GestureItem(AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT, "Yuqoriga, so'ng o'ngga", "swipe_up_and_right"),
        GestureItem(AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN, "Yuqoriga, so'ng pastga", "swipe_up_and_down"),
        GestureItem(AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT, "Pastga, so'ng chapga", "swipe_down_and_left"),
        GestureItem(AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT, "Pastga, so'ng o'ngga", "swipe_down_and_right"),
        GestureItem(AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP, "Pastga, so'ng yuqoriga", "swipe_down_and_up"),
        GestureItem(AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP, "Chapga, so'ng yuqoriga", "swipe_left_and_up"),
        GestureItem(AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN, "Chapga, so'ng pastga", "swipe_left_and_down"),
        GestureItem(AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT, "Chapga, so'ng o'ngga", "swipe_left_and_right"),
        GestureItem(AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP, "O'ngga, so'ng yuqoriga", "swipe_right_and_up"),
        GestureItem(AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN, "O'ngga, so'ng pastga", "swipe_right_and_down"),
        GestureItem(AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT, "O'ngga, so'ng chapga", "swipe_right_and_left"),

        GestureItem(AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP, "Ikki barmoq: bir marta bosish", "2_finger_single_tap"),
        GestureItem(AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP, "Ikki barmoq: ikki marta bosish", "2_finger_double_tap"),
        GestureItem(AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP, "Ikki barmoq: uch marta bosish", "2_finger_triple_tap"),
        GestureItem(AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD, "Ikki barmoq: ikki marta bosib ushlab turish", "2_finger_double_tap_and_hold"),
        GestureItem(AccessibilityService.GESTURE_2_FINGER_SWIPE_UP, "Ikki barmoq: yuqoriga surish", "2_finger_swipe_up"),
        GestureItem(AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN, "Ikki barmoq: pastga surish", "2_finger_swipe_down"),
        GestureItem(AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT, "Ikki barmoq: chapga surish", "2_finger_swipe_left"),
        GestureItem(AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT, "Ikki barmoq: o'ngga surish", "2_finger_swipe_right"),

        GestureItem(AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP, "Uch barmoq: bir marta bosish", "3_finger_single_tap"),
        GestureItem(AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP, "Uch barmoq: ikki marta bosish", "3_finger_double_tap"),
        GestureItem(AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP, "Uch barmoq: uch marta bosish", "3_finger_triple_tap"),
        GestureItem(AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD, "Uch barmoq: bir marta bosib ushlab turish", "3_finger_single_tap_and_hold"),
        GestureItem(AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD, "Uch barmoq: ikki marta bosib ushlab turish", "3_finger_double_tap_and_hold"),
        GestureItem(AccessibilityService.GESTURE_3_FINGER_SWIPE_UP, "Uch barmoq: yuqoriga surish", "3_finger_swipe_up"),
        GestureItem(AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN, "Uch barmoq: pastga surish", "3_finger_swipe_down"),
        GestureItem(AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT, "Uch barmoq: chapga surish", "3_finger_swipe_left"),
        GestureItem(AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT, "Uch barmoq: o'ngga surish", "3_finger_swipe_right"),

        GestureItem(AccessibilityService.GESTURE_4_FINGER_SINGLE_TAP, "To'rt barmoq: bir marta bosish", "4_finger_single_tap"),
        GestureItem(AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP, "To'rt barmoq: ikki marta bosish", "4_finger_double_tap"),
        GestureItem(AccessibilityService.GESTURE_4_FINGER_TRIPLE_TAP, "To'rt barmoq: uch marta bosish", "4_finger_triple_tap"),
        GestureItem(AccessibilityService.GESTURE_4_FINGER_SWIPE_UP, "To'rt barmoq: yuqoriga surish", "4_finger_swipe_up"),
        GestureItem(AccessibilityService.GESTURE_4_FINGER_SWIPE_DOWN, "To'rt barmoq: pastga surish", "4_finger_swipe_down"),
        GestureItem(AccessibilityService.GESTURE_4_FINGER_SWIPE_LEFT, "To'rt barmoq: chapga surish", "4_finger_swipe_left"),
        GestureItem(AccessibilityService.GESTURE_4_FINGER_SWIPE_RIGHT, "To'rt barmoq: o'ngga surish", "4_finger_swipe_right"),
    )

    private val byId: Map<Int, GestureItem> = items.associateBy { it.id }

    /**
     * TalkBack-mos profil (standart). Bir barmoq: o'ngga/chapga surish elementlar bo'yicha yuradi; yuqoriga/pastga surish
     * tanlangan o'qish boshqaruvi (sarlavha, so'z, harf va h.k.) bo'yicha; "yuqoriga, so'ng pastga" boshqaruvni almashtiradi;
     * "pastga, so'ng o'ngga" menyuni ochadi. Ikki barmoq bilan surish: aylantirish (Android 12 va yangida tizimning o'zi bajaradi).
     */
    private val TALKBACK: Map<Int, GestureAction> = mapOf(
        AccessibilityService.GESTURE_SWIPE_RIGHT to GestureAction.NEXT_ITEM,
        AccessibilityService.GESTURE_SWIPE_LEFT to GestureAction.PREV_ITEM,
        AccessibilityService.GESTURE_SWIPE_DOWN to GestureAction.NEXT,
        AccessibilityService.GESTURE_SWIPE_UP to GestureAction.PREV,
        AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN to GestureAction.MODE_NEXT,
        AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP to GestureAction.MODE_PREV,
        AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT to GestureAction.HOME,
        AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT to GestureAction.BACK,
        AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP to GestureAction.RECENTS,
        AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN to GestureAction.NOTIFICATIONS,
        AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN to GestureAction.SCREEN_SEARCH,
        AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP to GestureAction.QUICK_SETTINGS,
        AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT to GestureAction.GLOBAL_MENU,
        AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT to GestureAction.ACTIONS_MENU,
        AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT to GestureAction.SCROLL_FWD,
        AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT to GestureAction.SCROLL_BACK,

        AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP to GestureAction.STOP_SPEECH,
        AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP to GestureAction.MEDIA_PLAY_PAUSE,
        AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP to GestureAction.READ_FROM_NEXT,
        AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD to GestureAction.TRANSLATE,
        AccessibilityService.GESTURE_2_FINGER_SWIPE_UP to GestureAction.SCROLL_FWD,
        AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN to GestureAction.SCROLL_BACK,
        AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT to GestureAction.SCROLL_RIGHT,
        AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT to GestureAction.SCROLL_LEFT,

        AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP to GestureAction.GLOBAL_MENU,
        AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP to GestureAction.COPY_TEXT,
        AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP to GestureAction.PASTE_TEXT,
        AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD to GestureAction.SPELL_FOCUSED,
        AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD to GestureAction.DESCRIBE_SCREEN,
        // Matn bo'ylab yurish (o'qish boshqaruvini almashtirmasdan): chapga/o'ngga - harf, yuqoriga/pastga - so'z
        AccessibilityService.GESTURE_3_FINGER_SWIPE_UP to GestureAction.PREV_WORD,
        AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN to GestureAction.NEXT_WORD,
        AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT to GestureAction.PREV_CHAR,
        AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT to GestureAction.NEXT_CHAR,

        AccessibilityService.GESTURE_4_FINGER_SINGLE_TAP to GestureAction.STATUS,
        AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP to GestureAction.CURTAIN,
        AccessibilityService.GESTURE_4_FINGER_TRIPLE_TAP to GestureAction.READ_PRIVATE,
        AccessibilityService.GESTURE_4_FINGER_SWIPE_UP to GestureAction.READ_FROM_TOP,
        AccessibilityService.GESTURE_4_FINGER_SWIPE_DOWN to GestureAction.READ_FROM_NEXT,
        AccessibilityService.GESTURE_4_FINGER_SWIPE_LEFT to GestureAction.CALL_END,
        AccessibilityService.GESTURE_4_FINGER_SWIPE_RIGHT to GestureAction.CALL_ANSWER,
    )

    /**
     * VoiceOver-mos profil (8.3): yuqoriga/pastga surish rejimni almashtiradi, o'ngga/chapga surish rejim ichida yuradi;
     * ikki barmoq bilan surish o'qish, uch barmoq bilan surish aylantirish. Ko'rsatilmagan ishoralar TalkBack profilidan olinadi.
     */
    private val VOICEOVER: Map<Int, GestureAction> = mapOf(
        AccessibilityService.GESTURE_SWIPE_RIGHT to GestureAction.NEXT,
        AccessibilityService.GESTURE_SWIPE_LEFT to GestureAction.PREV,
        AccessibilityService.GESTURE_SWIPE_UP to GestureAction.MODE_PREV,
        AccessibilityService.GESTURE_SWIPE_DOWN to GestureAction.MODE_NEXT,
        AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT to GestureAction.BACK,
        AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT to GestureAction.HOME,
        AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP to GestureAction.RECENTS,
        AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN to GestureAction.NOTIFICATIONS,
        AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT to GestureAction.CALL_SPEAKER,
        AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT to GestureAction.JUMP_LAST,
        AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP to GestureAction.QUICK_SETTINGS,
        AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN to GestureAction.CALL_ANSWER,
        AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT to GestureAction.REPEAT_LAST,
        AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT to GestureAction.COPY_LAST,
        AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN to GestureAction.ADD_WORD,
        AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP to GestureAction.CALL_END,
        AccessibilityService.GESTURE_2_FINGER_SWIPE_UP to GestureAction.READ_FROM_TOP,
        AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN to GestureAction.READ_FROM_NEXT,
        AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT to GestureAction.NONE,
        AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT to GestureAction.NONE,
        AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP to GestureAction.STOP_SPEECH,
        AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP to GestureAction.MARK_PROBLEM,
        AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP to GestureAction.EXPLAIN_BRIDGE,
        AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP to GestureAction.STATUS,
        AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP to GestureAction.STOP_SPEECH,
        AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP to GestureAction.CURTAIN,
        AccessibilityService.GESTURE_3_FINGER_SWIPE_UP to GestureAction.SCROLL_FWD,
        AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN to GestureAction.SCROLL_BACK,
        AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT to GestureAction.SCROLL_FWD,
        AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT to GestureAction.SCROLL_BACK,
        AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD to GestureAction.TRANSLATE,
        AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD to GestureAction.DESCRIBE_SCREEN,
        // Menyular va qidiruv VoiceOver profilida to'rt barmoqli ishoralarga joylangan
        AccessibilityService.GESTURE_4_FINGER_SINGLE_TAP to GestureAction.GLOBAL_MENU,
        AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP to GestureAction.ACTIONS_MENU,
        AccessibilityService.GESTURE_4_FINGER_TRIPLE_TAP to GestureAction.SCREEN_SEARCH,
    )

    /** Tanlangan profilga ko'ra standart amal. */
    fun defaultFor(context: Context, item: GestureItem): GestureAction {
        val tb = TALKBACK[item.id] ?: GestureAction.NONE
        return if (Prefs.gestureProfile(context) == "voiceover") VOICEOVER[item.id] ?: tb else tb
    }

    fun actionFor(context: Context, id: Int): GestureAction {
        val def = byId[id]?.let { defaultFor(context, it) } ?: return GestureAction.NONE
        val stored = Prefs.string(context, Prefs.GESTURE_PREFIX + id) ?: return def
        return try {
            GestureAction.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            def
        }
    }

    fun save(context: Context, id: Int, action: GestureAction) {
        Prefs.get(context).edit().putString(Prefs.GESTURE_PREFIX + id, action.name).apply()
    }

    fun reset(context: Context) {
        val e = Prefs.get(context).edit()
        for (item in items) e.remove(Prefs.GESTURE_PREFIX + item.id)
        e.apply()
    }

    /** 2-versiyadagi standart qiymatlari 3-versiyada o'zgargan ishoralar (faqat shularning eski yozuvlari tozalanadi). */
    private val CHANGED_IN_V3: Map<Int, GestureAction> = mapOf(
        AccessibilityService.GESTURE_3_FINGER_SWIPE_UP to GestureAction.MODE_PREV,
        AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN to GestureAction.MODE_NEXT,
        AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT to GestureAction.MODE_PREV,
        AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT to GestureAction.MODE_NEXT,
        AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD to GestureAction.SCREEN_SEARCH,
    )

    /**
     * Standart ishoralar yangilanganda eski saqlangan qiymatlar yangi standartlar tagida qolib ketmasligi kerak.
     * 1 -> 3: hammasi tozalanadi (TalkBack'ga moslash). 2 -> 3: foydalanuvchining shaxsiy o'zgartirishlari saqlanadi;
     * faqat standarti o'zgargan ishoralarda eski standartga teng (ya'ni foydalanuvchi o'zgartirmagan) yozuv olib tashlanadi.
     */
    fun migrate(context: Context) {
        try {
            val p = Prefs.get(context)
            val version = p.getInt(KEY_DEFAULTS_VERSION, 1)
            if (version >= DEFAULTS_VERSION) return
            val e = p.edit()
            if (version < 2) {
                for (item in items) e.remove(Prefs.GESTURE_PREFIX + item.id)
            } else {
                for ((id, old) in CHANGED_IN_V3) {
                    val key = Prefs.GESTURE_PREFIX + id
                    if (p.getString(key, null) == old.name) e.remove(key)
                }
            }
            e.putInt(KEY_DEFAULTS_VERSION, DEFAULTS_VERSION)
            e.apply()
        } catch (ex: Throwable) {
            swallowed("GestureMap.migrate", ex)
        }
    }
}
