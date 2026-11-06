package uz.ekranoquvchi.mvp.core

import android.graphics.Rect
import android.os.SystemClock
import android.text.Spanned
import android.text.style.URLSpan
import android.view.accessibility.AccessibilityNodeInfo
import uz.ekranoquvchi.mvp.R
import kotlin.math.abs
import uz.ekranoquvchi.mvp.diag.swallowed

private const val MAX_NODES = 1500

/** Bitta surishda keyingi fokus nuqtasini qidirishda ko'riladigan tugunlarning eng ko'p soni. */
private const val MAX_STEPS = 400

/** Ro'yxatdagi qo'shni elementni qidirishda joriy elementdan keyin ko'riladigan ko'rinadigan elementlarning eng ko'p soni. */
private const val NEIGHBOR_ITEMS = 6

/** Nutq tezligi / Tafsilotlar rejimi ishlatilmasa, shuncha vaqtdan keyin elementlar rejimiga qaytadi (ms). */
private const val ADJUSTER_HOLD_MS = 12_000L

/**
 * Foydalanuvchi bosishi, tahrirlashi yoki belgilashi mumkin bo'lgan element.
 *
 * Ichida elementlari bor ro'yxat yoki aylantiriladigan konteyner (RecyclerView, ListView, ViewPager...) bosiladigan deb
 * belgilangan bo'lsa ham fokus nuqtasi EMAS: uning har bir elementi o'z nuqtasi (TalkBack ham ro'yxatning o'ziga fokus bermaydi).
 * Ilgari Telegram'da RecyclerView bosiladigan deb belgilangani uchun u «Nomsiz» nuqta bo'lib chiqardi: teginib o'rganishda
 * xabar o'rniga ro'yxatning o'zi fokus olardi, chapga surish ro'yxatning o'ziga tushardi, ikki marta bosish esa hech narsa
 * ochmasdi (jurnaldagi NODE_UNLABELED / RecyclerView yozuvlari).
 */
fun isActionable(n: AccessibilityNodeInfo): Boolean {
    if (n.childCount > 0 && isListBoundary(n)) return false
    return n.isClickable || n.isLongClickable || n.isCheckable || n.isEditable || (n.isFocusable && n.childCount == 0)
}

/**
 * Ro'yxat, jadval yoki aylantiriladigan konteyner. Uning ichidagi elementlar o'z "dunyosi"da turadi: ro'yxatdan
 * yuqoridagi bosiladigan konteyner ularni yutib yubormaydi (TalkBack'da ham ro'yxat elementi mustaqil fokus nuqtasi).
 * Ilgari shunday konteyner suhbatdagi barcha xabar matnlarini tashlab yuborishi mumkin edi: faqat bosiladigan
 * tugmalar ("fikrlar", "ulashish") qolib, xabarning o'zi o'qilmasdi.
 */
fun isListBoundary(n: AccessibilityNodeInfo): Boolean = n.isScrollable || n.collectionInfo != null

/** n ro'yxat (isListBoundary) konteynerining bevosita bolasimi: ro'yxat elementi (masalan, suhbatdagi xabar). */
fun isListItem(n: AccessibilityNodeInfo): Boolean {
    val p = n.parent ?: return false
    return isListBoundary(p)
}

/** x tugun anc'ning avlodimi (eng ko'pi bilan 8 daraja yuqoriga qaraydi). */
fun descends(x: AccessibilityNodeInfo, anc: AccessibilityNodeInfo): Boolean {
    var p: AccessibilityNodeInfo? = x.parent
    var guard = 0
    while (p != null && guard++ < 8) {
        if (p == anc) return true
        p = p.parent
    }
    return false
}

fun hasLink(n: AccessibilityNodeInfo): Boolean {
    val t = n.text
    return t is Spanned && t.getSpans(0, t.length, URLSpan::class.java).isNotEmpty()
}

fun hasOwnLabel(n: AccessibilityNodeInfo): Boolean =
    !n.text.isNullOrBlank() || !n.contentDescription.isNullOrBlank() || !n.hintText.isNullOrBlank()

/**
 * Yorlig'li fokus nuqtasi: o'z yorlig'i (matn, tavsif yoki maslahat) bor har qanday tugun; faqat ro'yxat, jadval va juda
 * katta konteynerlar bundan mustasno. Konteynerning o'z matni hech qachon tashlab ketilmaydi: Telegram kanalidagi post
 * matni postning o'zida turadi, ichida esa "fikrlar" va "ulashish" tugmalari hamda ba'zan oddiy matnli bolalar
 * (ko'rishlar soni, vaqt) bo'ladi. Ilgari ichida oddiy matnli bola bo'lsa konteyner nuqta hisoblanmasdi va postning
 * asosiy matni o'qilmay qolardi, faqat qo'shimcha tugmalar o'qilardi.
 * Takror o'qilmasligi uchun bolaning matni ota-onasining yorlig'ida to'liq bo'lsa, bola alohida nuqta bo'lmaydi
 * (coveredByLabeledParent).
 */
fun isLabeledUnit(n: AccessibilityNodeInfo): Boolean {
    if (!hasOwnLabel(n)) return false
    val count = n.childCount
    if (count == 0) return true
    if (n.isScrollable || n.collectionInfo != null) return false
    // Ko'p bolali konteyner odatda faqat tartib uchun (asbob paneli): lekin o'z matni yoki uzun yorlig'i bor bo'lsa
    // (Telegram posti: ichida ko'p havola va tugma bo'lishi mumkin) u xabarning o'zi, tashlab ketilmaydi.
    return count <= 12 || !n.text.isNullOrBlank() || labelText(n).length >= 24
}

/**
 * Elementning aytiladigan yorlig'i. TalkBack kabi contentDescription ko'rinadigan matndan ustun turadi (ilova uni aynan
 * ekran o'quvchi uchun yozgan); tahrirlash maydonida esa yozilgan matn birinchi o'rinda. NodeDescriber.labelOf bilan bir xil.
 */
private fun labelText(n: AccessibilityNodeInfo): String {
    val text = n.text?.toString()?.trim().orEmpty()
    val desc = n.contentDescription?.toString()?.trim().orEmpty()
    if (!n.isEditable && desc.isNotEmpty()) return desc
    if (text.isNotEmpty()) return text
    if (desc.isNotEmpty()) return desc
    return n.hintText?.toString()?.trim().orEmpty()
}

/**
 * Yorliq kaliti: o'z yorlig'i, bo'lmasa ichidagi yagona yorliq (yorlig'i yo'q konteyner aytilganda ham ichidagi matn
 * aytiladi). Ikki nuqta bir xil eshitilishini aniqlash uchun. Ichida bir nechta turli yorliqli nuqta bo'lsa
 * (Telegram posti: "fikrlar" va "ulashish" tugmalari) konteynerning kaliti yo'q: u birinchi tugmaning soyasi emas.
 * Ilgari birinchi tugma kalit bo'lib qolardi va yurishda post matni yoki tugmalardan biri tushib qolardi.
 */
private fun shadowKey(n: AccessibilityNodeInfo): String {
    val own = labelText(n)
    if (own.isNotEmpty()) return own.lowercase()
    var budget = 14
    var first = ""
    var distinct = 0
    fun walk(x: AccessibilityNodeInfo, depth: Int) {
        if (depth > 3 || distinct > 1) return
        for (i in 0 until x.childCount) {
            if (budget-- <= 0 || distinct > 1) return
            val c = x.getChild(i) ?: continue
            if (!c.isVisibleToUser) continue
            val l = labelText(c)
            if (l.isNotEmpty()) {
                val k = l.lowercase()
                if (distinct == 0) {
                    first = k
                    distinct = 1
                } else if (k != first) {
                    distinct = 2
                }
            } else {
                walk(c, depth + 1)
            }
        }
    }
    walk(n, 0)
    return if (distinct == 1) first else ""
}

/**
 * Ikki nuqta foydalanuvchiga bir xil eshitiladimi: yorlig'i bir xil va ekranda deyarli bir joyda (ota-ona va bola yoki
 * ustma-ust turgan ikki element). Telegram'dagi ovozli xabar tugmasi shunday ikkita bo'lib ko'rinardi: bosiladigan
 * konteyner va uning ichidagi bosiladigan belgi. Yurishda ikkinchisi o'tkazib yuboriladi.
 * Istisno: bosilmaydigan konteyner va ro'yxat elementi (xabar) o'z ichidagi tugmani yashirmaydi, tugma ham uni.
 */
fun isShadow(a: AccessibilityNodeInfo, b: AccessibilityNodeInfo): Boolean {
    return try {
        val ka = shadowKey(a)
        if (ka.isEmpty() || ka != shadowKey(b)) return false
        if (descends(b, a) && (!isActionable(a) || isListItem(a))) return false
        if (descends(a, b) && (!isActionable(b) || isListItem(b))) return false
        val ra = Rect()
        val rb = Rect()
        a.getBoundsInScreen(ra)
        b.getBoundsInScreen(rb)
        if (ra.isEmpty || rb.isEmpty) return false
        val inter = Rect(ra)
        if (!inter.intersect(rb)) return false
        val areaInter = inter.width().toLong() * inter.height().toLong()
        val areaA = ra.width().toLong() * ra.height().toLong()
        val areaB = rb.width().toLong() * rb.height().toLong()
        val smaller = minOf(areaA, areaB)
        smaller > 0L && areaInter * 10L >= smaller * 8L
    } catch (_: Throwable) {
        false
    }
}

/**
 * Bargli (matnli) tugunning yorlig'i bosilmaydigan, yorlig'li ota-onasining yorlig'ida to'liq bor bo'lsa true:
 * ota-ona uni o'zi o'qiydi, shuning uchun alohida nuqta kerak emas. Faqat ikki daraja yuqoriga qaraydi.
 */
fun coveredByLabeledParent(n: AccessibilityNodeInfo): Boolean {
    if (n.isScreenReaderFocusable || n.childCount != 0) return false
    val own = labelText(n)
    if (own.isEmpty()) return false
    var p: AccessibilityNodeInfo? = n.parent
    var depth = 0
    while (p != null && depth++ < 2) {
        if (p.isScrollable || p.collectionInfo != null || isActionable(p)) return false
        if (isLabeledUnit(p)) {
            val pl = labelText(p)
            if (pl.length >= own.length && pl.contains(own, ignoreCase = true)) return true
        }
        p = p.parent
    }
    return false
}

/**
 * Foydalanuvchi uchun ahamiyatli element: ko'rinadi va (matni yoki tavsifi bor, yoki bosiladi, belgilanadi, tahrirlanadi
 * yoki fokus ola oladi). Teginib o'rganishda tugunning o'zida ham, ota-onalarida ham yorliq bo'lmasa, uning ichidan
 * nuqta qidirishda ishlatiladi.
 */
fun isMeaningful(n: AccessibilityNodeInfo): Boolean {
    if (!n.isVisibleToUser) return false
    return hasOwnLabel(n) || isActionable(n) || n.isFocusable || n.isScreenReaderFocusable
}

/** Daraxtdan birinchi mos tugunni topadi (ko'rinadigan tugunlar orasidan). */
fun findFirst(root: AccessibilityNodeInfo, pred: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
    var visited = 0
    fun walk(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (++visited > MAX_NODES || !n.isVisibleToUser) return null
        if (pred(n)) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            val r = walk(c)
            if (r != null) return r
        }
        return null
    }
    return walk(root)
}

/**
 * Fokus nuqtalari va navigatsiya rejimlari (TalkBack'dagi "o'qish boshqaruvlari"). Rejim ishoralar xaritasiga qarab
 * almashtiriladi; TalkBack profilida o'ngga/chapga surish doim elementlar bo'yicha yuradi, yuqoriga/pastga surish esa
 * tanlangan rejim bo'yicha (monografiya 18.3 B).
 */
class Navigator {
    /** granularity != 0: harf, so'z, qator yoki xatboshi bo'yicha o'qish (o'qish boshqaruvlari, FR-01). */
    enum class Mode(val labelRes: Int, val granularity: Int = 0) {
        ELEMENTS(R.string.mode_elements),
        HEADINGS(R.string.mode_headings),
        CONTROLS(R.string.mode_controls),
        FIELDS(R.string.mode_fields),
        LINKS(R.string.mode_links),
        CHARACTERS(R.string.mode_characters, AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER),
        WORDS(R.string.mode_words, AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD),
        LINES(R.string.mode_lines, AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE),
        PARAGRAPHS(R.string.mode_paragraphs, AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH),

        /** Nutq tezligi: yuqoriga surish tezlashtiradi, pastga surish sekinlashtiradi (TalkBack'dagi "Nutq tezligi"). */
        RATE(R.string.mode_rate),

        /** Tafsilotlar: pastga surish darajani oshiradi, yuqoriga surish kamaytiradi (TalkBack'dagi "Verbosity"). */
        VERBOSITY(R.string.mode_verbosity),
    }

    /** Aka-uka tartibidagi o'rinni eslab qolish: surishda odatda qo'shni elementga o'tiladi (tizimga so'rovlar kamayadi). */
    private val indexHints = HashMap<Int, Int>()

    /**
     * Suhbat ilovasimi (Telegram, WhatsApp va h.k.). Xizmat o'rnatadi. Bunday ilovada xabar konteyneri matni tugunda
     * bo'lmasa ham (u fokus hodisasida keladi) alohida fokus nuqtasi bo'ladi, shuning uchun xabar tushib qolmaydi.
     */
    @Volatile
    var chatApp: (String?) -> Boolean = { false }

    /**
     * Xabarlashuv ilovalarida (Telegram) yorliqsiz, ichi bo'sh bosiladigan "ko'rinmas" tugmalarni o'tkazib yuborish. Ularning matni,
     * tavsifi va maslahati yo'q, ichida bolasi ham yo'q: ekran o'quvchi ularda alohida to'xtab, yurishni ortiqcha qadamlarga to'ldirardi.
     * Tahrirlanadigan maydon, belgilanadigan va sozlanadigan elementlarga hamda boshqa ilovalarga tegilmaydi. Xizmat o'rnatadi.
     */
    @Volatile
    var skipUnlabeled: Boolean = true

    /**
     * getChild vaqtincha null qaytarishi mumkin (tizim keshi yangilanayotganda). Bir marta qayta so'raladi: aks holda
     * element jimgina tushib qolardi (foydalanuvchi uchun "ikkinchi tugma o'tkazib yuborildi").
     */
    private fun childAt(n: AccessibilityNodeInfo, i: Int): AccessibilityNodeInfo? = n.getChild(i) ?: n.getChild(i)

    /** Xotira kam bo'lganda yoki xizmat to'xtaganda keshni tozalash. */
    fun clearCaches() {
        indexHints.clear()
        reversedCache.clear()
    }

    /** Teskari konteyner: bolalari daraxtda pastdan yuqoriga joylashgan ro'yxat (hisob-kitob eslab qolinadi). */
    private val reversedCache = HashMap<Int, Boolean>()

    /**
     * Konteyner bolalari daraxtda pastdan yuqoriga (teskari) joylashganmi. Shunday ro'yxatda o'qish tartibi ko'rinish
     * bo'yicha (yuqoridan pastga) bo'lishi uchun bolalar teskari tartibda ko'riladi. Aks holda "keyingi" xabar tepaga
     * chiqib ketardi. Birinchi ko'rinadigan bolalarning haqiqiy o'rni solishtiriladi.
     */
    fun isReversedContainer(n: AccessibilityNodeInfo): Boolean {
        if (!n.isScrollable) return false
        val count = n.childCount
        if (count < 3) return false
        val key = n.hashCode()
        val known = reversedCache[key]
        if (known != null) return known
        var up = 0
        var down = 0
        try {
            val ra = Rect()
            val rb = Rect()
            var prev: AccessibilityNodeInfo? = null
            for (i in 0 until minOf(count, 16)) {
                val c = n.getChild(i) ?: continue
                if (!c.isVisibleToUser) continue
                val p = prev
                if (p != null) {
                    p.getBoundsInScreen(ra)
                    c.getBoundsInScreen(rb)
                    val dy = rb.centerY() - ra.centerY()
                    val dx = rb.centerX() - ra.centerX()
                    if (abs(dy) > abs(dx) && dy != 0) {
                        if (dy < 0) up++ else down++
                    }
                }
                prev = c
            }
        } catch (ex: Throwable) {
            swallowed("Navigator.isReversedContainer", ex)
        }
        val reversed = up >= 2 && up > down
        if (up + down >= 3) {
            if (reversedCache.size > 64) reversedCache.clear()
            reversedCache[key] = reversed
        }
        return reversed
    }

    /** Bolalar indekslari ko'rinish tartibida: teskari konteynerda kamayib boradi. */
    private fun childOrder(n: AccessibilityNodeInfo): IntProgression {
        val count = n.childCount
        return if (count > 1 && isReversedContainer(n)) ((count - 1) downTo 0) else (0 until count)
    }

    var mode = Mode.ELEMENTS
        private set

    /** Rejim qachon tanlangan yoki oxirgi marta ishlatilgan (uptime, ms): vaqtinchalik rejimlar uchun. */
    private var modeTouchedAt = 0L

    fun cycle(step: Int): Mode {
        val all = Mode.values()
        mode = all[(mode.ordinal + step + all.size) % all.size]
        modeTouchedAt = SystemClock.uptimeMillis()
        return mode
    }

    /** Rejimni to'g'ridan-to'g'ri tanlaydi (o'qish boshqaruvlari menyusi): ko'p marta aylantirish shart emas. */
    fun select(m: Mode): Mode {
        mode = m
        modeTouchedAt = SystemClock.uptimeMillis()
        return m
    }

    /** Nutq tezligi yoki Tafsilotlar kabi "sozlash" rejimida har bir surish rejimni "tirik" saqlaydi. */
    fun keepMode() {
        modeTouchedAt = SystemClock.uptimeMillis()
    }

    /**
     * Nutq tezligi va Tafsilotlar rejimlarida yuqoriga/pastga surish harakatlantirmaydi, qiymatni o'zgartiradi. Foydalanuvchi
     * rejim qolganini unutsa, "yuqoriga-pastga yurib bo'lmayapti" degan holat chiqardi: shuning uchun ma'lum vaqt
     * ishlatilmasa, elementlar rejimiga qaytariladi.
     */
    fun adjusterExpired(now: Long = SystemClock.uptimeMillis()): Boolean =
        (mode == Mode.RATE || mode == Mode.VERBOSITY) && now - modeTouchedAt > ADJUSTER_HOLD_MS

    // ------------------------------------------------------------------ tez navigatsiya
    // Ilgari har bir surishda butun daraxt (1500 tugungacha) tizim orqali bitta-bitta so'ralardi: murakkab ekranlarda
    // bir surish soniyalab davom etardi. Endi faqat keyingi nuqtaga yetguncha bo'lgan tugunlar ko'riladi.

    /** collect(root).firstOrNull() bilan bir xil natija, lekin birinchi nuqta topilishi bilan to'xtaydi. */
    fun firstUnit(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var visited = 0
        fun walk(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (++visited > MAX_NODES || !n.isVisibleToUser) return null
            if (isActionable(n) || n.isScreenReaderFocusable || isLabeledUnit(n) || isChatHost(n)) return n
            for (i in childOrder(n)) {
                val c = n.getChild(i) ?: continue
                val r = walk(c)
                if (r != null) return r
            }
            return null
        }
        return walk(root)
    }

    /**
     * Barmoq tekkan tugunga mos fokus nuqtasi (teginib o'rganish): eng yaqin bosiladigan ota-ona yoki tugunning o'zi;
     * yorlig'i yo'q bo'lsa, yorlig'i bor eng yaqin ota-ona. Qoida collect() bilan bir xil: bosiladigan konteyner
     * ichidagi oddiy matnni o'zi o'qiydi.
     */
    fun unitFor(start: AccessibilityNodeInfo, descendantAreaLimit: Long = 0L): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = start
        var guard = 0
        while (n != null && guard++ < 64) {
            if (isActionable(n)) return n
            // Ro'yxat chegarasidan yuqoriga chiqilmaydi: bosiladigan konteyner butun ro'yxat elementini yutib yubormasin
            if (isListBoundary(n)) break
            n = n.parent
        }
        if (start.isScreenReaderFocusable || hasOwnLabel(start)) return start
        var p: AccessibilityNodeInfo? = start.parent
        guard = 0
        while (p != null && guard++ < 8) {
            if (p.isScreenReaderFocusable || hasOwnLabel(p)) return p
            p = p.parent
        }
        // Na o'zida, na ota-onalarida yorliq yo'q: kichik konteyner bo'lsa, ichidagi birinchi ahamiyatli nuqta
        if (descendantAreaLimit > 0L && start.childCount > 0) {
            val r = Rect()
            start.getBoundsInScreen(r)
            if (r.width().toLong() * r.height().toLong() <= descendantAreaLimit) return firstMeaningfulDescendant(start)
        }
        return null
    }

    /** Teginilgan konteyner ichidagi birinchi ko'rinadigan ahamiyatli (yorlig'i bor yoki bosiladigan) tugun. */
    private fun firstMeaningfulDescendant(start: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var visited = 0
        fun walk(n: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
            if (depth > 4) return null
            for (i in childOrder(n)) {
                if (++visited > 60) return null
                val c = n.getChild(i) ?: continue
                if (!c.isVisibleToUser) continue
                if (isMeaningful(c)) return c
                val r = walk(c, depth + 1)
                if (r != null) return r
            }
            return null
        }
        return walk(start, 0)
    }

    /**
     * cur'dan keyingi (forward) yoki oldingi fokus nuqtasi, joriy rejimga mos. Daraxt tartibi collect() bilan bir xil.
     * Topilmasa (ro'yxat tugadi yoki qadam chegarasi oshdi) null.
     */
    fun nextUnit(cur: AccessibilityNodeInfo, forward: Boolean, m: Mode = mode): AccessibilityNodeInfo? {
        var n = cur
        var steps = 0
        // Bir qidiruv ichida ota-onalarning "bosiladigan" holati takror so'ralmasligi uchun (har so'rov tizimga murojaat)
        val ancestors = HashMap<Int, Boolean>()
        while (steps++ < MAX_STEPS) {
            val next = (if (forward) stepForward(n) else stepBackward(n)) ?: return null
            if (isUnit(next, ancestors) && matches(next, m) && !isShadow(cur, next)) return next
            n = next
        }
        return null
    }

    private fun isUnlabeledNoise(n: AccessibilityNodeInfo): Boolean {
        if (!skipUnlabeled || n.childCount != 0) return false
        if (n.isEditable || n.isCheckable || n.rangeInfo != null) return false
        if (hasOwnLabel(n) || n.labeledBy != null) return false
        if (!chatApp(n.packageName?.toString())) return false
        // Ro'yxatning keng elementi (Telegram xabari: matni tugunda emas, fokus hodisasida keladi) "ko'rinmas tugma" emas.
        // Ilgari tez yo'l uni tashlab ketardi (collect esa qo'shardi): xabar o'tkazib yuborilib, fokus ro'yxatdan tashqariga sakrardi.
        return !isWideListItem(n)
    }

    /** n ro'yxatning bevosita bolasi va ro'yxat kengligining kamida choragini egallaydi: kichik ko'rinmas tugmalardan farqi shu. */
    private fun isWideListItem(n: AccessibilityNodeInfo): Boolean {
        return try {
            val p = n.parent ?: return false
            if (!isListBoundary(p)) return false
            val r = Rect()
            val pr = Rect()
            n.getBoundsInScreen(r)
            p.getBoundsInScreen(pr)
            !r.isEmpty && !pr.isEmpty && r.width() * 4 >= pr.width()
        } catch (_: Throwable) {
            false
        }
    }

    /** collect() qoidasi: bosiladigan tugun doim nuqta; oddiy matn esa bosiladigan ota-onasi bo'lmasagina. */
    private fun isUnit(n: AccessibilityNodeInfo, cache: HashMap<Int, Boolean>): Boolean {
        if (isActionable(n)) return !isUnlabeledNoise(n)
        if (n.isScreenReaderFocusable) return !hasActionableAncestor(n, cache)
        if (isChatHost(n)) return !hasActionableAncestor(n, cache)
        if (!isLabeledUnit(n)) return false
        return !hasActionableAncestor(n, cache) && !coveredByLabeledParent(n)
    }

    /**
     * Tugunning ota-onalaridan biri bosiladigan elementmi. cache: ota-ona kaliti -> "o'zi yoki undan yuqorisi bosiladi".
     * Tekshirilgan zanjirdagi hamma ota-onaga bir xil natija yoziladi, shuning uchun ro'yxat qatorlarida
     * har bir tugun uchun butun zanjirni qayta o'tish kerak bo'lmaydi.
     * Ro'yxat chegarasi (isListBoundary) to'xtash joyi: undan yuqoridagi bosiladigan konteyner ro'yxat elementini yutmaydi.
     */
    private fun hasActionableAncestor(n: AccessibilityNodeInfo, cache: HashMap<Int, Boolean>): Boolean {
        var p = n.parent
        var guard = 0
        var result = false
        val walked = ArrayList<Int>(8)
        while (p != null && guard++ < 64) {
            val key = p.hashCode()
            val known = cache[key]
            if (known != null) {
                result = known
                break
            }
            walked.add(key)
            if (isListBoundary(p)) {
                result = false
                break
            }
            if (isActionable(p)) {
                result = true
                break
            }
            p = p.parent
        }
        for (k in walked) cache[k] = result
        return result
    }

    /**
     * bolaning ota-onasidagi tartib raqami. Avval oxirgi marta topilgan o'rin va uning qo'shnilari tekshiriladi
     * (har tekshiruv bitta tizim chaqiruvi); topilmasa hammasi tekshiriladi. Tekshiruv tenglikka asoslangan,
     * shuning uchun eskirgan eslatma xato natija bermaydi.
     */
    private fun indexIn(parent: AccessibilityNodeInfo, child: AccessibilityNodeInfo): Int {
        val count = parent.childCount
        val key = parent.hashCode()
        val hint = indexHints[key]
        if (hint != null) {
            val candidates = intArrayOf(hint, hint + 1, hint - 1)
            for (cand in candidates) {
                if (cand in 0 until count && parent.getChild(cand) == child) {
                    indexHints[key] = cand
                    return cand
                }
            }
        }
        for (i in 0 until count) {
            if (parent.getChild(i) == child) {
                if (indexHints.size > 64) indexHints.clear()
                indexHints[key] = i
                return i
            }
        }
        return -1
    }

    /** Daraxt tartibida keyingi ko'rinadigan tugun: avval bolalar, keyin aka-uka, keyin ota-onaning aka-ukasi. */
    private fun stepForward(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (i in childOrder(n)) {
            val c = childAt(n, i) ?: continue
            if (c.isVisibleToUser) return c
        }
        var x = n
        var guard = 0
        while (guard++ < 64) {
            val p = x.parent ?: return null
            val idx = indexIn(p, x)
            if (idx >= 0) {
                val after: IntProgression =
                    if (isReversedContainer(p)) (idx - 1 downTo 0) else (idx + 1 until p.childCount)
                for (j in after) {
                    val c = childAt(p, j) ?: continue
                    if (c.isVisibleToUser) return c
                }
            }
            x = p
        }
        return null
    }

    /** Daraxt tartibida oldingi tugun: oldingi aka-ukaning eng oxirgi avlodi, aka-uka bo'lmasa ota-onaning o'zi. */
    private fun stepBackward(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val p = n.parent ?: return null
        val idx = indexIn(p, n)
        if (idx >= 0) {
            val before: IntProgression =
                if (isReversedContainer(p)) (idx + 1 until p.childCount) else (idx - 1 downTo 0)
            for (j in before) {
                val s = childAt(p, j) ?: continue
                if (s.isVisibleToUser) return lastDescendant(s)
            }
        }
        return p
    }

    private fun lastDescendant(start: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var x = start
        var guard = 0
        while (guard++ < 64) {
            var next: AccessibilityNodeInfo? = null
            for (i in childOrder(x).reversed()) {
                val c = childAt(x, i) ?: continue
                if (c.isVisibleToUser) {
                    next = c
                    break
                }
            }
            if (next == null) return x
            x = next
        }
        return x
    }

    fun matches(n: AccessibilityNodeInfo, m: Mode = mode): Boolean = when (m) {
        Mode.ELEMENTS -> true
        Mode.HEADINGS -> n.isHeading
        // Slayder va tanlagich ham boshqaruv (TalkBack'dagi "Boshqaruvlar" kabi)
        Mode.CONTROLS -> n.isClickable || n.isCheckable || n.isEditable || Adjustable.isAdjustable(n)
        Mode.FIELDS -> n.isEditable
        Mode.LINKS -> hasLink(n)
        Mode.CHARACTERS, Mode.WORDS, Mode.LINES, Mode.PARAGRAPHS, Mode.RATE, Mode.VERBOSITY -> true
    }

    /**
     * Suhbat ilovasidagi xabar konteyneri: o'z yorlig'i yo'q, bosilmaydi va ichida faqat bosiladigan bolalar bor
     * (Telegram posti: matn tugunda emas, fokus hodisasida keladi). Ilgari bunday konteyner nuqta hisoblanmasdi va
     * yurish xabar matniga umuman to'xtamasdan to'g'ridan-to'g'ri "fikrlar" va "ulashish" tugmalariga o'tardi.
     * Ichida oddiy matnli bola bo'lsa, u o'zi alohida nuqta: takror o'qilmasligi uchun konteyner nuqta bo'lmaydi.
     */
    private fun isChatHost(n: AccessibilityNodeInfo): Boolean {
        val kids = n.childCount
        if (kids == 0 || kids > 30) return false
        if (hasOwnLabel(n) || isActionable(n) || isListBoundary(n)) return false
        if (!chatApp(n.packageName?.toString())) return false
        if (!isListItem(n)) return false
        var anyVisible = false
        for (i in 0 until kids) {
            val c = n.getChild(i) ?: continue
            if (!c.isVisibleToUser) continue
            anyVisible = true
            if (!isActionable(c) && (hasOwnLabel(c) || hasPlainTextBelow(c, 0))) return false
        }
        return anyVisible
    }

    /** n ostida bosilmaydigan, o'z matni bor ko'rinadigan tugun bormi (eng ko'pi bilan 3 daraja, 12 bola). */
    private fun hasPlainTextBelow(n: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 3) return false
        for (i in 0 until minOf(n.childCount, 12)) {
            val c = n.getChild(i) ?: continue
            if (!c.isVisibleToUser) continue
            if (!isActionable(c) && hasOwnLabel(c)) return true
            if (hasPlainTextBelow(c, depth + 1)) return true
        }
        return false
    }

    /** listNeighbor natijasi: found=false bo'lsa cur ro'yxat nuqtalari orasida topilmadi; node=null bo'lsa qo'shni yo'q. */
    class ListStep(val found: Boolean, val node: AccessibilityNodeInfo?)

    /**
     * Ro'yxat (aylantiriladigan konteyner) ichida cur'dan keyingi yoki oldingi nuqta: ro'yxatning to'liq tartibi (collect)
     * bo'yicha. Tez yo'l (nextUnit) bilan solishtirish uchun: ular farq qilsa, tez yo'l biror nuqtani tashlab ketgan.
     */
    fun listNeighbor(list: AccessibilityNodeInfo, cur: AccessibilityNodeInfo, forward: Boolean, m: Mode = mode): ListStep {
        // Tez yo'l: butun ro'yxat (yuzlab tugun, har biri tizimga murojaat) emas, faqat cur turgan element va uning qo'shnilari.
        // Telegram'da har bir surishda butun ro'yxat aylanardi: surishdagi sekinlik va tutilishning asosiy sababi shu edi.
        val near = windowedNeighbor(list, cur, forward, m)
        if (near != null) return near
        val all = collect(list)
        val idx = all.indexOfFirst { it == cur }
        if (idx < 0) return ListStep(false, null)
        val step = if (forward) 1 else -1
        var i = idx + step
        while (i in all.indices) {
            val c = all[i]
            if (matches(c, m) && !isShadow(cur, c)) return ListStep(true, c)
            i += step
        }
        return ListStep(true, null)
    }

    /**
     * listNeighbor'ning tez varianti: natijasi butun ro'yxat bo'yicha hisoblanganiga teng, lekin faqat cur turgan ro'yxat
     * elementi va undan keyingi (oldingi) bir necha element ko'riladi. cur ro'yxatning bevosita bolasi ichida
     * topilmasa yoki aniq xulosa chiqmasa null: chaqiruvchi to'liq usulga o'tadi.
     */
    private fun windowedNeighbor(list: AccessibilityNodeInfo, cur: AccessibilityNodeInfo, forward: Boolean, m: Mode): ListStep? {
        // cur turgan ro'yxat elementi: ro'yxatning bevosita bolasi
        var item: AccessibilityNodeInfo = cur
        var reached = false
        var guard = 0
        while (guard++ < 24) {
            val p = item.parent ?: break
            if (p == list) {
                reached = true
                break
            }
            item = p
        }
        if (!reached) return null
        val step = if (forward) 1 else -1
        // 1) shu elementning ichidagi qo'shni nuqta
        val own = collect(item)
        val idx = own.indexOfFirst { it == cur }
        if (idx < 0) return null
        var i = idx + step
        while (i in own.indices) {
            val c = own[i]
            if (matches(c, m) && !isShadow(cur, c)) return ListStep(true, c)
            i += step
        }
        // 2) qo'shni elementlar ko'rinish tartibida (teskari konteynerda indeks kamayib boradi: stepForward bilan bir xil)
        val pos = indexIn(list, item)
        if (pos < 0) return null
        val delta = if (forward != isReversedContainer(list)) 1 else -1
        val count = list.childCount
        var j = pos + delta
        var looked = 0
        while (j in 0 until count && looked < NEIGHBOR_ITEMS) {
            val sib = childAt(list, j)
            j += delta
            if (sib == null || !sib.isVisibleToUser) continue
            looked++
            val units = collect(sib)
            val ordered = if (forward) units else units.asReversed()
            for (c in ordered) {
                if (matches(c, m) && !isShadow(cur, c)) return ListStep(true, c)
            }
        }
        return ListStep(true, null)
    }

    /**
     * Fokus nuqtalari daraxt tartibida. Bosiladigan konteyner o'z ichidagi oddiy matnni o'zi o'qiydi
     * (matn alohida nuqta bo'lmaydi), ichidagi bosiladigan elementlar esa alohida nuqta bo'ladi.
     * Ro'yxat chegarasidan o'tilganda "yutish" to'xtaydi (isListBoundary).
     */
    fun collect(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>(64)
        var visited = 0
        fun walk(n: AccessibilityNodeInfo, absorbedIn: Boolean) {
            if (++visited > MAX_NODES || !n.isVisibleToUser) return
            val boundary = isListBoundary(n)
            val absorbed = absorbedIn && !boundary
            val actionable = isActionable(n)
            var childAbsorbed = absorbed
            if (!absorbed) {
                if (actionable || n.isScreenReaderFocusable || isChatHost(n) || (isLabeledUnit(n) && !coveredByLabeledParent(n))) {
                    out.add(n)
                    if (actionable && !boundary) childAbsorbed = true
                }
            } else if (actionable) {
                out.add(n)
            }
            for (i in childOrder(n)) {
                val c = childAt(n, i) ?: continue
                walk(c, childAbsorbed)
            }
        }
        walk(root, false)
        return out
    }
}
