package uz.ekranoquvchi.mvp.core

import android.content.Context
import uz.ekranoquvchi.mvp.util.Prefs

/**
 * O'zbegim menyusi (TalkBack'dagi "TalkBack menyusi" o'rnida): hamma amal bir joyda, ishorani yodlash shart emas.
 * Menyu pastga, so'ng o'ngga surish bilan ochiladi. Tez-tez kerak bo'ladiganlar tepada ("Mavjud havolalarga o'tish" ikkinchi
 * o'rinda). Bandlar tartibini menyuning o'zida o'zgartirish mumkin (oxirgi band yoki bandni uzoq bosish). Ishoralarga biriktirib
 * bo'ladigan har qanday amalni shu yerdan ham bajarish mumkin.
 * Nutq tezligi bu yerda yo'q: u o'qish boshqaruvlarida (yuqoriga/pastga surish) va sozlamalarda o'zgaradi.
 * Tizim amallari (orqaga, bosh ekran, so'nggi ilovalar va h.k.) alohida "Tizim amallari" ichki menyusida;
 * O'zbegim sozlamalarining bo'limlari esa "Sozlamalar bo'limlari" ichki menyusida.
 * Foydalanuvchi menyudagi bandlarni o'zi tanlashi mumkin (Sozlamalar > Ishoralar > O'zbegim menyusini sozlash):
 * bandlarni qo'shish yoki olib tashlash. Hech narsa tanlanmagan bo'lsa, standart ro'yxat ko'rsatiladi.
 */
object GlobalMenu {
    /** Standart ro'yxat. */
    val actions: List<GestureAction> = listOf(
        GestureAction.ACTIONS_MENU,
        GestureAction.LINKS_MENU,
        GestureAction.READING_MENU,
        GestureAction.SPELL_FOCUSED,
        GestureAction.SPELL_LAST,
        GestureAction.AUTO_TRANSLATE,
        GestureAction.READ_FROM_TOP,
        GestureAction.READ_FROM_NEXT,
        GestureAction.SCREEN_SEARCH,
        GestureAction.DESCRIBE_IMAGE,
        GestureAction.DESCRIBE_SCREEN,
        GestureAction.TRANSLATE,
        GestureAction.DETAILS_MENU,
        GestureAction.OPEN_SETTINGS,
        GestureAction.SETTINGS_MENU,
        GestureAction.TTS_SETTINGS,
        GestureAction.SYSTEM_ACTIONS,
        GestureAction.STATUS,
        GestureAction.REPEAT_LAST,
        GestureAction.COPY_LAST,
        GestureAction.SAVE_AUDIO,
        GestureAction.COPY_TEXT,
        GestureAction.SELECT_ALL,
        GestureAction.CUT_TEXT,
        GestureAction.PASTE_TEXT,
        GestureAction.CURTAIN,
        GestureAction.MEDIA_PLAY_PAUSE,
        GestureAction.CALL_ANSWER,
        GestureAction.CALL_END,
        GestureAction.CALL_SPEAKER,
        GestureAction.READ_PRIVATE,
        GestureAction.EXPLAIN_BRIDGE,
        GestureAction.ADD_WORD,
        GestureAction.ADD_LABEL,
        GestureAction.MARK_PROBLEM,
        GestureAction.FIRST_ITEM,
        GestureAction.JUMP_LAST,
        GestureAction.NEXT_WINDOW,
        GestureAction.PREV_WINDOW,
        GestureAction.PRACTICE,
        GestureAction.SUSPEND,
    )

    /** "Tizim amallari" ichki menyusi: ilgari bosh menyuda alohida turgan tizim amallari. */
    val systemActions: List<GestureAction> = listOf(
        GestureAction.BACK,
        GestureAction.HOME,
        GestureAction.RECENTS,
        GestureAction.OPEN_APPS,
        GestureAction.NOTIFICATIONS,
        GestureAction.QUICK_SETTINGS,
        GestureAction.LOCK_SCREEN,
        GestureAction.SCREENSHOT,
        GestureAction.POWER_DIALOG,
        GestureAction.SPLIT_SCREEN,
    )

    /** "Sozlamalar bo'limlari" ichki menyusi: har bir band O'zbegim sozlamalarining tegishli bo'limini to'g'ridan-to'g'ri ochadi. */
    val settingsLinks: List<GestureAction> = listOf(
        GestureAction.OPEN_SETTINGS,
        GestureAction.OPEN_VOICES,
        GestureAction.OPEN_DETAILS,
        GestureAction.OPEN_GESTURES,
        GestureAction.OPEN_AI,
        GestureAction.TTS_SETTINGS,
        GestureAction.OPEN_BACKUP,
        GestureAction.OPEN_HEALTH,
        GestureAction.OPEN_LOG,
        GestureAction.OPEN_MENU_EDITOR,
    )

    /** Menyuga qo'shish mumkin bo'lgan qo'shimcha amallar (standart ro'yxatda yo'q). */
    private val extras: List<GestureAction> = listOf(
        GestureAction.STOP_SPEECH,
        GestureAction.SPEED_UP,
        GestureAction.SPEED_DOWN,
        GestureAction.PITCH_UP,
        GestureAction.PITCH_DOWN,
        GestureAction.SPEED_MODE,
    )

    /** Menyuni sozlashda tanlash mumkin bo'lgan hamma band: avval standart ro'yxat, so'ng qolganlari. */
    val pool: List<GestureAction> = (actions + settingsLinks + systemActions + extras).distinct()

    /** Hozir ko'rsatiladigan bandlar: foydalanuvchi tanlagan ro'yxat (o'zi belgilagan tartibda); saqlanmagan yoki bo'sh bo'lsa, standart ro'yxat. */
    fun effective(c: Context): List<GestureAction> {
        // Bir martalik yangilanish belgisi har holda olinadi: keyinroq foydalanuvchi o'zi belgilagan tartib buzilmasin
        val linksUpgrade = Prefs.takeMenuUpgrade(c, "links_second")
        val chosen = Prefs.menuItems(c) ?: return actions
        var valid = chosen.filter { it in pool }
        if (valid.isEmpty()) return actions
        var changed = false
        // Menyuni oldin sozlagan foydalanuvchida yangi "MP3 qilib saqlash" bandi ko'rinmay qolmasligi uchun bir marta qo'shiladi
        // (keyin uni menyu muharririda olib tashlash mumkin: qayta qo'shilmaydi).
        if (GestureAction.SAVE_AUDIO !in valid && Prefs.takeMenuUpgrade(c, "save_audio")) {
            valid = valid + GestureAction.SAVE_AUDIO
            changed = true
        }
        // "Mavjud havolalarga o'tish" bandi bir marta ikkinchi o'ringa ko'chiriladi (ilgari to'qqizinchi edi). Keyin foydalanuvchi
        // uni menyuning o'zida xohlagan joyga o'tkaza oladi va qayta ko'chirilmaydi.
        if (linksUpgrade && GestureAction.LINKS_MENU in valid) {
            val moved = moveItem(valid, GestureAction.LINKS_MENU, 1)
            if (moved != valid) {
                valid = moved
                changed = true
            }
        }
        if (changed) Prefs.saveMenuItems(c, valid)
        return valid
    }

    /** item'ni ro'yxatda newIndex o'ringa (0 dan) ko'chiradi; boshqa bandlarning tartibi saqlanadi. Band ro'yxatda bo'lmasa, o'zgarishsiz. */
    fun moveItem(list: List<GestureAction>, item: GestureAction, newIndex: Int): List<GestureAction> {
        val from = list.indexOf(item)
        if (from < 0) return list
        val out = ArrayList(list)
        out.removeAt(from)
        out.add(newIndex.coerceIn(0, out.size), item)
        return out
    }
}
