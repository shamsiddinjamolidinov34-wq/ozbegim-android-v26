package uz.ekranoquvchi.mvp.core

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction

/**
 * Sozlanadigan elementlar: qiymati surish bilan o'zgartiriladigan slayder (SeekBar, Material va Compose slayderlari)
 * hamda tanlagich (NumberPicker, vaqt va sana tanlagichining g'ildiraklari).
 *
 * TalkBack'da bunday element fokusda bo'lsa, bir barmoq bilan yuqoriga surish qiymatni oshiradi, pastga surish kamaytiradi;
 * "oldinga aylantirish" va "orqaga aylantirish" ishoralari ham shuni bajaradi. Ilgari O'zbegim yuqoriga va pastga surishni
 * doim keyingi va oldingi elementga o'tish deb hisoblardi: ekran yorqinligi 50 foizda turganda pastga surilsa, qiymat
 * kamaymasdi, fokus esa boshqa elementga ketardi.
 */
object Adjustable {

    /** Bir qadam natijasi: qiymat o'zgardi, chekkaga yetilgan (boshqa tomonga o'zgara olmaydi) yoki amal bajarilmadi. */
    enum class Step { CHANGED, EDGE, FAILED }

    private const val FORWARD = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
    private const val BACKWARD = AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
    private val SET_PROGRESS: Int = AccessibilityAction.ACTION_SET_PROGRESS.id

    /**
     * ACTION_SET_PROGRESS bilan qadam diapazonning shuncha ulushi (1/20, 1/10, 1/5, 1/2): oddiy slayderda birinchi qadam
     * (5 foiz, tizimning o'z qadami bilan bir xil) yetadi; qadamli (diskret) slayder kichik qadamni eng yaqin
     * pog'onaga yumaloqlab, qiymatni o'zgartirmay qo'yishi mumkin, shunda qadam kattalashtirib qayta uriniladi.
     */
    private val DIVISORS = floatArrayOf(20f, 10f, 5f, 2f)

    private fun actionIds(n: AccessibilityNodeInfo): Set<Int> {
        val out = HashSet<Int>()
        for (a in n.actionList) out.add(a.id)
        return out
    }

    /** Tanlagich: NumberPicker va uning o'xshashlari (Samsung "SeslNumberPicker" ham). */
    fun isPicker(n: AccessibilityNodeInfo): Boolean {
        val cls = n.className?.toString().orEmpty()
        return cls.contains("NumberPicker", ignoreCase = true) ||
            cls.contains("WheelPicker", ignoreCase = true) ||
            cls.contains("WheelView", ignoreCase = true)
    }

    /**
     * Element o'zi sozlanadimi: yoqilgan va qiymat oraliqli (RangeInfo) element qadam amaliga (oldinga/orqaga aylantirish)
     * yoki ACTION_SET_PROGRESS'ga ega; yoki tanlagich qadam amaliga ega. Faqat ko'rsatadigan (progress) chiziq va oddiy
     * aylantiriladigan ro'yxat sozlanadigan hisoblanmaydi: ular odatdagidek yuriladi.
     */
    fun isAdjustable(n: AccessibilityNodeInfo): Boolean {
        if (!n.isEnabled) return false
        // Arzon rad etish: oddiy elementlar uchun amallar ro'yxati (nusxa) umuman yig'ilmaydi
        val hasRange = n.rangeInfo != null
        if (!hasRange && !isPicker(n)) return false
        val ids = actionIds(n)
        val canStep = ids.contains(FORWARD) || ids.contains(BACKWARD)
        return if (hasRange) canStep || ids.contains(SET_PROGRESS) else canStep
    }

    /**
     * Tez dastlabki tekshiruv (tizimga murojaat qilmaydi): element sozlanadigan yoki tanlagichning ichki elementi
     * (kiritish maydoni, tugma) bo'lishi mumkinmi. Yo'q bo'lsa, yuqoriga/pastga surish darhol odatdagidek ishlaydi.
     */
    fun maybeAdjustable(n: AccessibilityNodeInfo): Boolean = isAdjustable(n) || isPickerPart(n)

    private fun isPickerPart(n: AccessibilityNodeInfo): Boolean {
        val cls = n.className?.toString().orEmpty()
        return cls.endsWith("EditText") || cls.endsWith("Button")
    }

    /**
     * Fokusdagi elementga tegishli sozlanadigan elementni topadi: o'zi, yoki tanlagichning ichki elementi (kiritish
     * maydoni, ortirish va kamaytirish tugmalari) bo'lsa, tanlagichning o'zi. Ichidagi bolalar qidirilmaydi: ro'yxat
     * qatoridagi (masalan, Telegram xabaridagi) slayder yuqoriga va pastga yurishni to'smasligi uchun.
     * explicit: foydalanuvchi qiymatni o'zgartirish amalini o'zi tanlagan (ishoraga yoki menyuga biriktirgan): bu holda
     * 4 darajagacha har qanday sozlanadigan ota-ona ham qabul qilinadi.
     */
    fun find(start: AccessibilityNodeInfo?, explicit: Boolean = false): AccessibilityNodeInfo? {
        if (start == null) return null
        if (isAdjustable(start)) return start
        // Ota-onasiga murojaat (tizimga so'rov) faqat tanlagichning ichki elementi bo'lishi mumkin bo'lgan tugunlar uchun
        if (!explicit && !isPickerPart(start)) return null
        val limit = if (explicit) 4 else 1
        var p: AccessibilityNodeInfo? = try {
            start.parent
        } catch (_: Throwable) {
            null
        }
        var depth = 0
        while (p != null && depth++ < limit) {
            if (isAdjustable(p) && (explicit || isPicker(p))) return p
            p = try {
                p.parent
            } catch (_: Throwable) {
                null
            }
        }
        return null
    }

    /** Slayder qiymati up tomonga yana o'zgara olmaydigan chekkadami (eng ko'p yoki eng kam). Qiymat oralig'i bo'lmasa, false. */
    fun atEdge(n: AccessibilityNodeInfo, up: Boolean): Boolean {
        val r = n.rangeInfo ?: return false
        val span = r.max - r.min
        if (span <= 0f) return false
        val eps = span * 1e-4f
        return if (up) r.current >= r.max - eps else r.current <= r.min + eps
    }

    /**
     * Qiymatni bir pog'ona o'zgartiradi. Avval tizimning o'z qadami (SeekBar: diapazonning 1/20 qismi, NumberPicker: bir
     * birlik), bo'lmasa ACTION_SET_PROGRESS (Compose va o'zi chizadigan slayderlar). n.refresh() oldindan chaqirilgan
     * bo'lishi kerak: chekka eskirgan qiymat bo'yicha aniqlanmasligi uchun.
     */
    fun step(n: AccessibilityNodeInfo, up: Boolean): Step {
        if (atEdge(n, up)) return Step.EDGE
        val ids = actionIds(n)
        val want = if (up) FORWARD else BACKWARD
        val other = if (up) BACKWARD else FORWARD
        if (ids.contains(want) && n.performAction(want)) return Step.CHANGED
        val r = n.rangeInfo
        if (r != null && ids.contains(SET_PROGRESS) && setProgress(n, r, up)) return Step.CHANGED
        // Tanlagich chekkasida (aylanib o'tmaydigan) kerakli amal bo'lmaydi, teskarisi esa bor: bu chekka
        if (r == null && !ids.contains(want) && ids.contains(other)) return Step.EDGE
        return Step.FAILED
    }

    private fun setProgress(n: AccessibilityNodeInfo, r: AccessibilityNodeInfo.RangeInfo, up: Boolean): Boolean {
        val span = r.max - r.min
        if (span <= 0f) return false
        for (div in DIVISORS) {
            val delta = span / div
            val value = (r.current + (if (up) delta else -delta)).coerceIn(r.min, r.max)
            if (value == r.current) return false
            val args = Bundle()
            args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value)
            if (n.performAction(SET_PROGRESS, args)) return true
        }
        return false
    }
}
