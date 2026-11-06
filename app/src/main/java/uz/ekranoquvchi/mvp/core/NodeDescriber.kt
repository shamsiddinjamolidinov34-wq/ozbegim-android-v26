package uz.ekranoquvchi.mvp.core

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import uz.ekranoquvchi.mvp.R
import uz.ekranoquvchi.mvp.content.DescOrder
import uz.ekranoquvchi.mvp.content.Details
import uz.ekranoquvchi.mvp.content.LabelStore
import uz.ekranoquvchi.mvp.content.StickerText
import kotlin.math.roundToInt
import uz.ekranoquvchi.mvp.diag.swallowed

/**
 * Element tavsifi: nom, tur va holat (masalan: "Wi-Fi, o'tkazgich, yoqilgan"). Parol matni hech qachon aytilmaydi.
 * Nimalar aytilishini Details (tafsilotlar) belgilaydi: standartda tugma va rasm turi aytilmaydi, faqat nomi o'qiladi.
 */
class NodeDescriber(private val ctx: Context) {

    /**
     * Avtomatik tarjima: yorliq matnini (keshdagi) tarjimasi bilan almashtiradi. null bo'lsa matnga tegilmaydi.
     * Parol maydoni va foydalanuvchining o'z yorliqlari (labels.txt) hech qachon tarjima qilinmaydi.
     */
    @Volatile
    var translate: ((String) -> String)? = null

    /** Yorlig'i yo'q ("Nomsiz") element topilganda chaqiriladi: sababini aniqlash uchun matnsiz tuzilma jurnalga yoziladi. */
    @Volatile
    var onUnlabeled: ((AccessibilityNodeInfo) -> Unit)? = null

    /** Yengil rejim (tezlashtirish): yorlig'i yo'q konteynerning ichki matnlarini kamroq yig'adi. */
    @Volatile
    var light: Boolean = false

    private class RoleInfo(val text: String, val buttonImage: Boolean, val essential: Boolean)

    /** hint: hodisaning o'z matni; elementning o'zida va ichida yorliq topilmasa, shu aytiladi (bosh ekrandagi ilova nomi). */
    fun describe(node: AccessibilityNodeInfo, details: Details = Details.MEDIUM, hint: String? = null): String {
        val rawLabel = labelOf(node, true, hint)
        // Stikerlarni o'qish o'chirilgan bo'lsa: tavsifdan stiker so'zi va emojilar olinadi (hech narsa qolmasa, qisqa "stiker")
        // Qisqa o'qish yoqilgan bo'lsa: stiker so'zi va faqat birinchi emoji qoladi (to'liq tavsif aytilmaydi)
        val label = when {
            !details.readStickers -> StickerText.quiet(
                rawLabel, node.viewIdResourceName, node.className?.toString(), ctx.getString(R.string.sticker_word)
            )
            details.stickerShort -> StickerText.short(rawLabel, node.viewIdResourceName, node.className?.toString())
            else -> rawLabel
        }
        val cls = node.className?.toString().orEmpty()
        val blank = label.isBlank()
        val role = roleOf(node)

        var showRole = when {
            role == null -> false
            role.essential -> true
            role.buttonImage -> details.typeButtonImage
            else -> details.typeOther
        }
        // Yorlig'i yo'q element: nomi yo'q, shuning uchun tur (tugma, rasm) kerakli ma'lumot bo'ladi
        if (role != null && blank) showRole = true
        // Nomning o'zida tur so'zi bo'lsa ("Orqaga tugmasi"), uni takrorlamaymiz
        if (role != null && showRole && !blank && label.contains(role.text, ignoreCase = true)) showRole = false

        val name = when {
            !blank -> label
            isActionable(node) || cls.endsWith("ImageView") -> {
                try {
                    onUnlabeled?.invoke(node)
                } catch (ex: Throwable) {
                    swallowed("NodeDescriber.describe", ex)
                }
                ctx.getString(R.string.unlabeled)
            }
            else -> ""
        }
        val roleText = if (showRole && role != null) role.text else ""
        val state = stateOf(node).orEmpty()

        val ordered = when (details.order) {
            DescOrder.NAME_TYPE_STATE -> listOf(name, roleText, state)
            DescOrder.TYPE_NAME_STATE -> listOf(roleText, name, state)
            DescOrder.STATE_NAME_TYPE -> listOf(state, name, roleText)
        }
        val parts = ArrayList<String>(5)
        for (p in ordered) if (p.isNotBlank()) parts.add(p)
        if (details.position) positionOf(node)?.let { parts.add(it) }
        if (details.hints) hintOf(node)?.let { parts.add(it) }
        return parts.joinToString(", ")
    }

    /** translated=false: ilovaning asl matni (nusxalash va qo'lda tarjima uchun), tarjimasiz. */
    fun labelOf(node: AccessibilityNodeInfo, translated: Boolean = true, hint: String? = null): String {
        if (node.isPassword) return (node.hintText ?: node.contentDescription)?.toString().orEmpty()
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        // TalkBack kabi: ilova ekran o'quvchi uchun yozgan contentDescription ko'rinadigan matndan ustun turadi. Ilgari
        // matn birinchi olinardi va tugma o'rniga uning ostidagi yozuv ("Video") aytilardi. Tahrirlash maydonida esa
        // yozilgan matn birinchi o'rinda.
        if (!node.isEditable && desc.isNotEmpty()) return tr(desc, translated)
        if (text.isNotEmpty()) return tr(text, translated)
        if (desc.isNotEmpty()) return tr(desc, translated)
        val hintText = node.hintText?.toString()?.trim().orEmpty()
        if (hintText.isNotEmpty()) return tr(hintText, translated)
        val tip = node.tooltipText?.toString()?.trim().orEmpty()
        if (tip.isNotEmpty()) return tr(tip, translated)
        // Yorlig'i yo'q element: foydalanuvchining maxsus yorlig'i (labels.txt)
        val custom = LabelStore.lookup(node.viewIdResourceName)
        if (custom != null) return custom
        // 1) "yorliq beruvchi" element (labelFor): ilova qo'ygan aniq bog'lanish ichidagi tasodifiy matndan ustun
        try {
            val labelNode = node.labeledBy
            if (labelNode != null) {
                val lt = spokenOwn(labelNode)
                if (lt.isNotEmpty()) return tr(lt, translated)
            }
        } catch (ex: Throwable) {
            swallowed("NodeDescriber.labelOf", ex)
        }
        // 2) ichidagi oddiy (alohida nuqta bo'lmagan) matnlar
        val rolled = rollUp(node, translated, false)
        if (rolled.isNotEmpty()) return rolled
        // 3) hodisaning o'z matni (Telegram posti kabi: matn hodisada bor, tugunda yo'q bo'lishi mumkin). U ichidagi
        // "fikrlar", "ulashish" tugmalarining matnidan ustun: aks holda post matni o'rniga faqat tugmalar o'qilardi.
        val h = hint?.trim().orEmpty()
        if (h.isNotEmpty() && trustHint(node)) return tr(h, translated)
        // 4) ichidagi bosiladigan bolalarning matni ham (bosh ekrandagi ilova belgisi: nom bolada turadi)
        val deep = rollUp(node, translated, true)
        if (deep.isNotEmpty()) return deep
        return ""
    }

    /** Tugunning o'z yorlig'i: contentDescription matndan ustun (tahrirlash maydonida matn birinchi). */
    private fun spokenOwn(c: AccessibilityNodeInfo): String {
        val t = c.text?.toString()?.trim().orEmpty()
        val d = c.contentDescription?.toString()?.trim().orEmpty()
        return if (!c.isEditable && d.isNotEmpty()) d else if (t.isNotEmpty()) t else d
    }

    private fun tr(s: String, translated: Boolean): String {
        if (!translated) return s
        val f = translate ?: return s
        return f(s)
    }

    /**
     * Hodisaning o'z matniga (hint) ishonish mumkinmi. ViewGroup hodisa matniga barcha bolalarining matnini qo'shib yuboradi:
     * ro'yxat elementi bo'lmagan ko'p bolali konteyner (bosh ekran sahifasi, papka) uchun bu qo'shni elementlar nomlarini
     * aralashtirib o'qishga olib kelardi. Ro'yxat elementi (Telegram posti, sozlamalar qatori) o'z hodisa matni bilan
     * o'qiladi: u hamma vaqt shu yacheykaning o'ziga tegishli.
     */
    private fun trustHint(node: AccessibilityNodeInfo): Boolean = node.childCount <= 1 || isListItem(node)

    /**
     * Yorlig'i yo'q konteyner: ichidagi (alohida fokus nuqtasi bo'lmagan) matnlarni birlashtiradi. Faqat shu elementning
     * o'ziga tegishli tugunlar olinadi: ro'yxat/jadval konteyneri, ichidagi mustaqil ro'yxatlar va konteyner chegarasidan
     * tashqaridagi tugunlar (qo'shni yacheykalar) hech qachon qo'shilmaydi.
     */
    private fun rollUp(root: AccessibilityNodeInfo, translated: Boolean, includeActionable: Boolean = false): String {
        // Ro'yxat yoki jadvalning o'zi yacheykalar matnidan nom olmaydi: har bir yacheyka alohida fokus nuqtasi
        if (isListBoundary(root)) return ""
        val maxParts = if (includeActionable) 2 else if (light) 4 else 8
        val rootBounds = Rect()
        root.getBoundsInScreen(rootBounds)
        val checkBounds = !rootBounds.isEmpty
        val childBounds = Rect()
        // Bosiladigan, yorlig'li bolalar soni: fokusga tushmaydigan konteynerda ikkitadan ko'p bo'lsa, bu bitta element emas,
        // elementlar guruhi (bosh ekran sahifasi): ularning nomlari konteyner nomi bo'lib qolmasligi kerak
        var labeledActionable = 0
        val maxDepth = if (light) 3 else 6
        // Chuqur daraxtda tizimga ortiqcha murojaat qilmaslik uchun ko'riladigan tugunlar soni cheklangan
        var budget = if (light) 40 else 90
        val parts = ArrayList<String>(4)
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (parts.size >= maxParts || depth > maxDepth || budget <= 0) return
            for (i in 0 until n.childCount) {
                if (budget-- <= 0) return
                val c = n.getChild(i) ?: continue
                if (!c.isVisibleToUser || (!includeActionable && isActionable(c))) continue
                // Ichidagi mustaqil ro'yxat va konteyner chegarasidan tashqaridagi tugun bu elementga tegishli emas
                if (isListBoundary(c)) continue
                if (checkBounds) {
                    c.getBoundsInScreen(childBounds)
                    if (!childBounds.isEmpty && !rootBounds.contains(childBounds.centerX(), childBounds.centerY())) continue
                }
                val own = spokenOwn(c)
                if (includeActionable && own.isNotEmpty() && isActionable(c)) labeledActionable++
                if (own.isNotEmpty()) parts.add(tr(own, translated))
                walk(c, depth + 1)
            }
        }
        walk(root, 0)
        if (includeActionable && labeledActionable >= 2 && !isActionable(root) && !root.isFocusable && !root.isScreenReaderFocusable) return ""
        return parts.joinToString(", ")
    }

    private fun roleOf(n: AccessibilityNodeInfo): RoleInfo? {
        val cls = n.className?.toString().orEmpty()
        val switchLike = cls.contains("Switch") || cls.endsWith("ToggleButton")
        return when {
            n.isPassword -> RoleInfo(ctx.getString(R.string.role_password), buttonImage = false, essential = true)
            n.isEditable || cls.endsWith("EditText") ->
                RoleInfo(ctx.getString(R.string.role_edit), buttonImage = false, essential = true)
            // Belgi katagi va o'tkazgich turi oldingi versiyada ham har qanday darajada aytilardi
            cls.endsWith("CheckBox") -> RoleInfo(ctx.getString(R.string.role_checkbox), buttonImage = false, essential = true)
            switchLike -> RoleInfo(ctx.getString(R.string.role_switch), buttonImage = false, essential = true)
            cls.endsWith("RadioButton") -> RoleInfo(ctx.getString(R.string.role_radio), buttonImage = false, essential = false)
            cls.endsWith("SeekBar") || cls.endsWith("Slider") || (n.rangeInfo != null && Adjustable.isAdjustable(n)) ->
                RoleInfo(ctx.getString(R.string.role_seekbar), buttonImage = false, essential = false)
            cls.endsWith("Spinner") -> RoleInfo(ctx.getString(R.string.role_spinner), buttonImage = false, essential = false)
            cls.endsWith("Button") -> RoleInfo(ctx.getString(R.string.role_button), buttonImage = true, essential = false)
            n.isHeading -> RoleInfo(ctx.getString(R.string.role_heading), buttonImage = false, essential = false)
            cls.endsWith("ImageView") ->
                if (n.isClickable) {
                    RoleInfo(ctx.getString(R.string.role_button), buttonImage = true, essential = false)
                } else {
                    RoleInfo(ctx.getString(R.string.role_image), buttonImage = true, essential = false)
                }
            n.collectionInfo != null -> RoleInfo(ctx.getString(R.string.role_list), buttonImage = false, essential = false)
            hasLink(n) -> RoleInfo(ctx.getString(R.string.role_link), buttonImage = false, essential = false)
            else -> null
        }
    }

    fun stateOf(n: AccessibilityNodeInfo): String? {
        val parts = ArrayList<String>(3)
        val custom = if (Build.VERSION.SDK_INT >= 30) n.stateDescription?.toString() else null
        if (!custom.isNullOrBlank()) {
            // Ilovaning o'z holat matni bor: u to'liq (TalkBack ham faqat shuni aytadi), "tanlangan" qayta qo'shilmaydi
            parts.add(custom)
        } else {
            if (n.isCheckable) {
                val cls = n.className?.toString().orEmpty()
                val sw = cls.contains("Switch") || cls.endsWith("ToggleButton")
                val id = when {
                    n.isChecked && sw -> R.string.state_on
                    n.isChecked -> R.string.state_checked
                    sw -> R.string.state_off
                    else -> R.string.state_unchecked
                }
                parts.add(ctx.getString(id))
            }
            // Tanlangan: fayllar, audio va xabarlarni ko'plab tanlashda holat shu yerda bo'ladi. Ro'yxat elementining
            // tanlanganligi (collectionItemInfo) ham hisobga olinadi; belgisi qo'yilgan katakda ikkinchi marta aytilmaydi.
            val itemSelected = try {
                n.collectionItemInfo?.isSelected == true
            } catch (_: Throwable) {
                false
            }
            if ((n.isSelected || itemSelected) && !(n.isCheckable && n.isChecked)) {
                parts.add(ctx.getString(R.string.state_selected))
            }
        }
        if (!n.isEnabled) parts.add(ctx.getString(R.string.state_disabled))
        val actions = n.actionList.map { it.id }
        if (actions.contains(AccessibilityNodeInfo.ACTION_COLLAPSE)) {
            parts.add(ctx.getString(R.string.state_expanded))
        } else if (actions.contains(AccessibilityNodeInfo.ACTION_EXPAND)) {
            parts.add(ctx.getString(R.string.state_collapsed))
        }
        // Ilovaning o'z holat matni bor bo'lsa (masalan, "50 foiz"), foiz ikkinchi marta qo'shilmaydi. Foiz yaxlitlanadi:
        // 127 / 255 = 49,8 foiz "49" emas, "50" deb aytiladi (ilgari kasr qismi tashlab yuborilardi).
        val range = n.rangeInfo
        if (range != null && range.max > range.min && custom.isNullOrBlank()) {
            val pct = ((range.current - range.min) / (range.max - range.min) * 100f).roundToInt().coerceIn(0, 100)
            parts.add(ctx.getString(R.string.state_percent, pct))
        }
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }

    /** Ro'yxatdagi o'rni: "10 tadan 3" (tafsilotlarda yoqilgan bo'lsa). */
    private fun positionOf(n: AccessibilityNodeInfo): String? {
        val item = n.collectionItemInfo ?: return null
        val total = n.parent?.collectionInfo?.rowCount ?: return null
        if (total <= 0) return null
        return ctx.getString(R.string.state_position, item.rowIndex + 1, total)
    }

    /** Foydalanish ko'rsatmasi (TalkBack'dagi "Speak usage hints"): element bilan nima qilish mumkin. */
    private fun hintOf(n: AccessibilityNodeInfo): String? {
        val parts = ArrayList<String>(2)
        if (n.isEditable) {
            parts.add(ctx.getString(R.string.hint_edit))
        } else if (n.isClickable || n.isCheckable) {
            parts.add(ctx.getString(R.string.hint_activate))
        }
        if (Adjustable.isAdjustable(n)) parts.add(ctx.getString(R.string.hint_adjust))
        if (n.isLongClickable) parts.add(ctx.getString(R.string.hint_long_press))
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }
}
