# O'zbegim 0.9.1: arxitektura qaydlari

Bu hujjat uch muammoning sababini, kiritilgan tuzatishni va telefonda sinash tartibini bayon qiladi. Kod namunalari
haqiqiy fayllardan olingan.

## Holat (halol ma'lumot)

- 0.9.1 tuzatishlari (5-bo'lim) ham Gradle bilan qurilmadi (kompilyator yo'q) va telefonda sinalmadi. Qavslar balansi, nomlarning
  bir marta e'lon qilinishi va chaqiruvlar qo'lda tekshirildi.
- 5-bo'limdagi Telegram sabablarining bir qismi kod o'qishdan aniq, bir qismi taxmin: yuborilgan jurnalda Telegram yozuvi yo'q edi.
  Qaysi biri taxmin ekani 5.1'da alohida belgilangan.
- Tuzatishlar v12 kodiga kiritildi va qo'lda tekshirildi (qavslar balansi, nomlar, chaqiruvlar).
- Gradle bilan qurilmadi (tahrir muhitida tarmoq yo'q) va telefonda sinalmadi. Qurishda xato chiqsa, xato matnini yuboring.
- Ishora haqidagi bir taxmin (pastda, 3-bo'lim) tekshirilmagan; shu sababli ilovalar menyusi bir necha yo'l bilan ochiladi va
  qaysi yo'l ishlagani Xatolar jurnaliga yoziladi.

## 1. Fokus olish va elementni aniqlash

Teginib o'rganishda tizim barmoq ostidagi tugmani o'zi aniqlaydi va `TYPE_VIEW_HOVER_ENTER` hodisasiga manba qilib beradi
(`event.source`). Hodisada koordinata yo'q, shuning uchun "eng yaqin koordinata" qidiruvi uchun ma'lumot yetishmaydi.
`onMotionEvent` yordam bermaydi: Android hujjatiga ko'ra teginib o'rganish yoqiq bo'lsa, u sensor hodisalarini olmaydi.
Koordinata kerak bo'lsa, keyingi bosqichda `TouchInteractionController` (Android 11+) sinab ko'riladi; telefonsiz buni xavfsiz
qo'shib bo'lmaydi. `findAccessibilityNodeInfosByViewId` ham mos emas: id lar ilovaga xos va ko'pincha yo'q.

Shuning uchun aniqlash yorliq va ahamiyat qoidasiga tayanadi (`core/Navigator.kt`):

```kotlin
fun isActionable(n: AccessibilityNodeInfo): Boolean =
    n.isClickable || n.isLongClickable || n.isCheckable || n.isEditable || (n.isFocusable && n.childCount == 0)

fun isMeaningful(n: AccessibilityNodeInfo): Boolean {
    if (!n.isVisibleToUser) return false
    return hasOwnLabel(n) || isActionable(n) || n.isFocusable || n.isScreenReaderFocusable
}
```

Yangi: teginilgan tugunda ham, ota-onalarida ham yorliq bo'lmasa (jim qolardi), tugun kichik konteyner bo'lsa, uning ichidagi
birinchi ahamiyatli tugun olinadi:

```kotlin
fun unitFor(start: AccessibilityNodeInfo, descendantAreaLimit: Long = 0L): AccessibilityNodeInfo? {
    // ... bosiladigan ota-ona, tugunning o'zi, yorliqli ota-ona ...
    if (descendantAreaLimit > 0L && start.childCount > 0) {
        val r = Rect()
        start.getBoundsInScreen(r)
        if (r.width().toLong() * r.height().toLong() <= descendantAreaLimit) return firstMeaningfulDescendant(start)
    }
    return null
}
```

Chegara ekran maydonining 30 foizi (`ReaderService.processHover`): katta bo'sh joyga tegilganda tasodifiy bola o'qilmasligi uchun.

## 2. Xabarlarning o'tkazib yuborilishi (Telegram kanallari, ro'yxatlar)

Sabab: postning matni konteynerning o'zida turadi, ichida esa tugmalar va ba'zan oddiy matnli bolalar (ko'rishlar soni, vaqt)
bo'ladi. Eski qoida ichida oddiy matnli bola bo'lsa konteynerni fokus nuqtasi deb hisoblamasdi: asosiy matn tushib qolar,
tugmalar va bolalar o'qilardi.

Tuzatish: konteynerning o'z yorlig'i hech qachon tashlab ketilmaydi. Takror o'qilmasligi uchun matni ota-onasining
yorlig'ida to'liq bor bola alohida nuqta bo'lmaydi.

```kotlin
fun isLabeledUnit(n: AccessibilityNodeInfo): Boolean {
    if (!hasOwnLabel(n)) return false
    val count = n.childCount
    if (count == 0) return true
    return !(n.isScrollable || n.collectionInfo != null || count > 12)
}

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
```

Tartib: `collect()` va `nextUnit()` daraxtni chuqurlik bo'yicha (DFS) bosib o'tadi, shuning uchun xabar matni, keyin vaqt, keyin
tugmalar ketma-ket keladi. Ro'yxat qatorlarini bitta obyektga birlashtirish (TalkBack "top-level scroll item") qilinmadi:
bu hamma ro'yxatning o'qilishini o'zgartiradi va telefonsiz tekshirib bo'lmaydi. Hozirgi ketma-ketlik hech narsani
tashlab ketmaydi.

Chuqur joylashgan matn: yorlig'siz konteynerning matnini yig'uvchi `NodeDescriber.rollUp` chuqurligi 6 gacha, soni 8 gacha
(tezkor rejimda 3 va 4), tugunlar byudjeti 90 (tezkor rejimda 40).

Aylantirish (`TYPE_VIEW_SCROLLED`, `ReaderService.onScrolled/handleScrolled`): hodisa soniyasiga o'nlab marta keladi, shuning
uchun har hodisada faqat kutish vaqti suriladi, ish aylantirish tinchigach (160 ms) bir marta bajariladi:

```kotlin
private fun onScrolled(event: AccessibilityEvent) {
    if (SystemClock.uptimeMillis() < ownScrollUntil) return      // xizmatning o'z aylantirishi: fokusni o'zi boshqaradi
    if (scrollSource == null) scrollSource = event.source
    main.removeCallbacks(scrollTask)
    main.postDelayed(scrollTask, SCROLL_SETTLE_MS)
}

private fun handleScrolled() {
    // ... navigator.clearCaches(); foydalanuvchi so'nggi 4 soniyada tekkan bo'lsa ...
    val focusGone = last == null || !last.refresh() || !last.isVisibleToUser
    if (!focusGone) return                                       // fokus ko'rinib tursa, tegmaymiz
    val first = navigator.collect(source).firstOrNull { navigator.matches(it, navigator.mode) } ?: return
    focus(first)
}
```

## 3. Ishoralar va global amallar

### Joriy arxitektura

`accessibility_service_config.xml` teginib o'rganish va ko'p barmoqli ishoralarni so'raydi:

```xml
android:accessibilityFlags="flagDefault|flagReportViewIds|flagRequestTouchExplorationMode|flagRequestFilterKeyEvents|flagRetrieveInteractiveWindows|flagEnableAccessibilityVolume|flagRequestMultiFingerGestures"
android:canPerformGestures="true"
android:canRequestTouchExplorationMode="true"
android:canRetrieveWindowContent="true"
```

Teginib o'rganish yoqiq bo'lganda tizim barcha barmoq harakatini o'zi ushlab oladi va faqat tayyor ishora sifatida
`onGesture(gestureId)` ga beradi. Bosh ekran (launcher) xom barmoq harakatini ko'rmaydi: bu "to'liq intercept"ning o'zi,
alohida kod kerak emas. `onGesture` -> `GestureMap` (ishora -> amal) -> `perform(amal)`.

`onMotionEvent` bu yerda ishora ushlash uchun yaramaydi: u faqat kuzatish uchun (Android 14+) va teginib o'rganish yoqiq
bo'lsa sensor hodisalarini bermaydi.

Ikki barmoqli surishni ilovaning o'ziga o'tkazish kerak bo'lsa (qulf ekrani), `FLAG_REQUEST_2_FINGER_PASSTHROUGH` ishlatiladi
(`ReaderService.updatePassthrough`, sozlama `two_finger_pass`).

### Ilovalar menyusi muammosi

Eski kod bosh ekranda ikki barmoq bilan tepaga surishni `dispatchGesture` bilan yuborilgan barmoq surishi orqali bajarardi.
Taxmin (tekshirilmagan): teginib o'rganish yoqiq paytda tizim xizmatning o'zi yuborgan surishni ham o'z ushlagichidan
o'tkazadi va u launcherga yetmaydi. Taxmin to'g'ri bo'lsa, eski yo'l hech qachon ishlamasdi.

Yangi yo'l (`core/SystemSurfaces.kt`): uch qadam ketma-ket, har biridan keyin ekran o'zgarganmi tekshiriladi:

```kotlin
fun openApps(onDone: (Boolean) -> Unit = {}) {
    // ...
    val before = screenSignature()
    val steps = ArrayList<Step>(3)
    if (Build.VERSION.SDK_INT >= 31) {
        steps.add(Step("global") { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS) })
    }
    steps.add(Step("button") { clickAppsButton() })
    steps.add(Step("swipe") { injectSwipe(0.5f, 0.74f, 0.5f, 0.26f, 220L) })
    runStep(steps, 0, before, onDone)
}
```

Oxirgi qadamda teginib o'rganish 250 ms o'chiriladi va surish tugagach (yoki 2 soniyalik xavfsizlik muddatidan keyin) qayta
yoqiladi:

```kotlin
val teWasOn = touchExplorationOn()
if (teWasOn) { teSuspendedByUs = true; setTouchExploration(false) }
main.postDelayed(restoreTask, settle + durationMs + RESTORE_MARGIN_MS)
main.postDelayed({ svc.dispatchGesture(desc, callback /* onCompleted/onCancelled -> restore */, main) }, settle)
```

Boshqa o'zgarishlar: ikki barmoq bilan pastga tortish bosh ekranda to'g'ridan-to'g'ri `GLOBAL_ACTION_NOTIFICATIONS`;
soyani yopish Android 12+ da `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE`; qulf ekranini ochish va sahifa almashtirish
surishlari ham xavfsiz yuborish yo'lidan o'tadi (`ReaderService.fingerSwipe`). Yangi ishora amali `OPEN_APPS`
("Ilovalar menyusini ochish") istalgan ishoraga biriktirilishi mumkin.

## 4. Telefonda sinash ro'yxati

1. Bosh ekranda ikki barmoq bilan tepaga surish: ilovalar menyusi ochiladimi.
2. Bosh ekranda ikki barmoq bilan pastga tortish: xabarnoma soyasi tushadimi; soya ochiq paytda tepaga surish uni yopadimi.
3. Qulf ekranida ikki barmoq bilan tepaga surish: qulf ochiladimi.
4. Teginib o'rganish tugagandan keyin ishlayaptimi (surishdan keyin ekranga tegib, o'qilishini tekshiring).
5. Telegram kanalida xabarlar bo'ylab o'ngga suring: avval post matni, keyin tugmalar o'qiladimi.
6. Telegram kanalida ikki barmoq bilan aylantirish: fokus yangi ko'ringan birinchi elementga o'tadimi.
7. Ro'yxatlarda (Sozlamalar, Telegram chatlar) yurish avvalgidek tez va takrorsiz ishlayaptimi.

Ishlamagan joy bo'lsa, Xatolar jurnalini yuboring: ilovalar menyusi bo'yicha `GESTURE_PATH` yozuvlari (`apps_ok`,
`apps_step_rejected`, `apps_step_no_effect`, `apps_failed`) qaysi yo'l nima uchun yiqilganini ko'rsatadi.

## 5. 0.9.1: Telegram'da yurish, tanlanganlik va yorliq tartibi

### 5.1 Sabablar

Kod o'qishdan aniq (tekshirilgan):

- `NEEDED_EVENTS` ichida `TYPE_VIEW_SELECTED` va `TYPE_VIEW_LONG_CLICKED` yo'q edi, `onClicked` esa faqat `isCheckable`
  elementlarda ishlardi. Tanlash yoki tanlovni olib tashlash hech qachon aytilmasdi; faqat "belgilangan" tomoni ba'zan
  (element belgilanadigan bo'lib qolganda) eshitilardi.
- `stateOf` belgilanadigan elementda `isSelected`ni, ro'yxat elementining tanlanganligini (`collectionItemInfo.isSelected`)
  hisobga olmasdi.
- `NodeDescriber.labelOf` matnni contentDescription'dan oldin olardi. TalkBack aksincha: ilova ekran o'quvchi uchun yozgan
  contentDescription birinchi.
- `labelOf` ichida `val hint = node.hintText...` parametr `hint`ni yopib qo'ygan edi, shuning uchun hodisaning o'z matni
  (4-qadam) hech qachon aytilmasdi.
- `onFocused` manbasiz hodisada (`event.source == null`) hech narsani eslab qolmasdi va `currentFocus()` tizim topmasa eskirgan
  `lastFocusedSource`ni qaytarardi. Natija: keyingi surish eski elementdan hisoblanadi.
- `focusFrom` fokus bermagan elementni jimgina tashlab, keyingisiga o'tardi.
- `handleScrolled` virtual elementda `refresh()` false qaytarsa, fokusni "yo'qolgan" deb ro'yxat boshiga ko'chirardi.
- `isLabeledUnit` bolalari 12 tadan ko'p konteynerni, uzun matni bo'lsa ham, nuqta deb hisoblamasdi.

Taxmin (jurnalda Telegram yozuvi yo'qligi uchun tasdiqlanmagan):

- Javob tariqasida yozilgan xabardagi javob havolasi virtual element; uning fokus hodisasi manbasiz kelganda joriy element
  eskirgan bo'lib, yurish shu yerda qotadi. Tuzatish shu mexanizmga qaratilgan.
- Ovozli xabar tugmasi ikkita: bosiladigan konteyner va ichidagi bosiladigan belgi, ikkalasi bir xil nom bilan alohida nuqta.
- YouTube'dagi "Video": tugmada ham matn, ham contentDescription bor. Aniq daraxt noma'lum.

### 5.2 Kod

Fokus manbasi (ReaderService.onFocused): manbasiz hodisada o'zimiz so'ragan fokus joriy bo'ladi.

```kotlin
val req = focusRequest
if (req != null && nowMs - focusRequestAt < 1500L) {
    lastFocusedSource = req
    lastFocusedAt = nowMs
    rememberState(req)
}
```

Joriy element (ReaderService.currentFocus): tizim ota-onani, biz esa uning ichidagi elementni bildirsak, aniqrog'i olinadi.

```kotlin
if (sys == null) return tracked
if (tracked == null) return sys
if (sys == tracked) return sys
if (isInside(tracked, sys)) return tracked
return sys
```

Fokus bermagan element o'tkazilmaydi (ReaderService.focusOrRead) va qotib qolishdan himoya (moveFocus):

```kotlin
val ok = focus(node) || readWithoutFocus(node)
...
if (fast != null && stuckTarget != null && fast == stuckTarget) fast = navigator.nextUnit(fast, forward, mode)
```

Ikkilangan nuqta (Navigator.isShadow): yorliq bir xil va maydonning 80 foizi ustma-ust.

```kotlin
val smaller = minOf(areaA, areaB)
return smaller > 0L && areaInter * 10L >= smaller * 8L
```

Yorliq tartibi (NodeDescriber.labelOf): contentDescription, matn, hint, tooltip, maxsus yorliq, labelFor, ichidagi matnlar,
hodisa matni, ichidagi bosiladigan bolalar.

Holat o'zgarishi (ReaderService.checkStateChange): bosish, uzoq bosish, tanlash yoki holat tavsifi o'zgargach fokusdagi element
qayta o'qilmaydi, faqat yangi holat aytiladi; tanlov olib tashlansa "tanlanmagan" yoki "belgilanmagan".

### 5.3 Telefonda sinash ro'yxati

1. Telegram kanalida o'ngga surib to'liq yuring: har post matni, keyin "fikrlar" va "ulashish" tugmalari tushib qolmasdan o'qiladimi.
2. Xuddi shunday chapga surib tepaga yuring.
3. Telegram guruhida o'zingiz javob tariqasida yozgan xabarga yetganda yurish davom etadimi, pastdagi yozish maydoniga o'tiladimi.
4. Yozish maydonida ovozli xabar tugmasi bir marta o'qiladimi.
5. Telegram'da bir nechta audio yoki faylni tanlang: har tanlashda "tanlangan" yoki "belgilangan" aytiladimi; olib tashlaganda
   "tanlanmagan" yoki "belgilanmagan".
6. Samsung "Mening fayllarim"da bir nechta faylni tanlang: shunday tekshiring; keyin ro'yxat bo'ylab yurib, tanlanganlar
   "tanlangan" deb o'qiladimi.
7. YouTube'da video pleyer tugmasini toping: "Video" o'rniga tugmaning nomi aytiladimi.
8. Sozlamalar va chatlar ro'yxatida yurish avvalgidek tez, takrorsiz va to'liq ishlayaptimi (yorliq tartibi o'zgargani uchun).

Ishlamagan joy bo'lsa, Xatolar jurnalini yuboring. Yangi yozuvlar: `FOCUS_SOFT_READ` (tizim fokus bermagan element: sinf, ilova,
ko'rinish identifikatori), `FOCUS_STUCK_FIXED` (qotib qolishdan himoya ishga tushgan joy), `FOCUS_NODE_FAILED`.

### 5.4 v14 hisoboti (0.9.2): xabarlar tushib qolishi va ovozli xabar

Hisobot: Telegram guruh va kanallarida o'ngga/chapga surib yurishda ayrim xabarlar o'qilmaydi, fokus to'g'ridan-to'g'ri
"fikrlar" yoki "ulashish" tugmasiga o'tadi; ovozli xabar eshitilayotganda ekran o'quvchi gapirishda davom etadi.
Bu safar xato jurnali yuborilmadi, shuning uchun sabablar kod o'qilib topilgan taxminlar. Telefonda sinalmagan.

Yurish sabablari (taxmin) va tuzatishlar:

1. Ikkilangan nuqta (isShadow) kaliti. Yorlig'i yo'q konteynerning kaliti ichidagi birinchi ko'rinadigan yorliq edi. Postda
   (o'z yorlig'i tugunda bo'lmasa) u birinchi tugma "fikrlar" bo'lib qolardi: oldinga yurishda post unga, orqaga yurishda
   tugma postga "soya" bo'lib, biri o'tkazib yuborilardi. Endi kalit faqat ichida bitta turli yorliq bo'lsa beriladi,
   bosilmaydigan konteyner yoki ro'yxat elementi ichidagi tugmani yashirmaydi.
2. Bosiladigan ota-ona yutishi. hasActionableAncestor va collect() ro'yxatdan yuqoridagi bosiladigan konteynerni ham
   hisoblardi; undan keyin bosilmaydigan xabar matni nuqta bo'lmay, faqat bosiladigan tugmalar qolardi (aynan hisobotdagi
   belgi). Endi ro'yxat/jadval/aylantiriladigan konteyner chegarasi (isListBoundary) to'xtash joyi. TalkBack'da ham ro'yxat
   elementi mustaqil nuqta.
3. Matni tugunda bo'lmagan xabar konteyneri (isChatHost): faqat suhbat ilovalarida, faqat ro'yxat elementi, yorlig'i yo'q,
   bosilmaydi, ichida faqat bosiladigan bolalar bor. Ichida oddiy matnli bola bo'lsa, u o'zi nuqta va konteyner qo'shilmaydi.
4. Mustaqil tekshiruv (ReaderService.crossCheckInList): suhbat ilovasida tez yo'l (nextUnit) natijasi ro'yxatning to'liq
   tartibi (collect) bilan solishtiriladi; farq bo'lsa ro'yxat tartibidagi qo'shni olinadi va NAV_DIVERGED yoziladi.
5. Aylantirishdan keyin (focusAfterScroll): eski fokus ko'rinib tursa, yurish uning qo'shnisidan davom etadi; aks holda
   eski usul (yangi paydo bo'lgan birinchi nuqta).
6. getChild null qaytarsa bir marta qayta so'raladi (childAt).

Ovozli xabar sababi (taxmin) va tuzatish: ijro paytida ilova mazmunni va fokus hodisalarini tez-tez yuboradi, suhbat rejimi
(readNewChatMessage), holat tekshiruvi, e'lonlar va takroriy fokus hodisasi gapirishni davom ettirardi. PlaybackMonitor
(AudioPlaybackCallback, API 26+) faqat USAGE_MEDIA, GAME, UNKNOWN, VOICE_COMMUNICATION ijrolarini sanaydi: O'zbegimning o'z
tovushlari USAGE_ASSISTANCE_ACCESSIBILITY, shuning uchun o'zini jim qilmaydi. Suhbat ilovasida ijro 450 ms dan ortiq
davom etsa ReaderService.beginListening(): nutq to'xtatiladi va ijro tugaguncha yuqoridagi avtomatik yo'llar o'chadi.
Foydalanuvchi so'nggi 1,5 soniyada ekranga tekkan bo'lsa, uning buyrug'i aytiladi. Boshqa ilovalarda o'zgarish yo'q.

Telefonda sinash ro'yxati:

1. Telegram kanalida o'ngga surib to'liq yuring: har post matni, keyin "fikrlar" va "ulashish" tugmalari ketma-ket o'qiladimi.
2. Xuddi shunday chapga surib tepaga yuring: tugmalar, keyin post matni, keyin oldingi postning tugmalari.
3. Guruhda xuddi shunday yuring, javob tariqasida yozilgan xabarda ham.
4. Ovozli xabarni bosing: nutq darhol to'xtaydimi, xabar oxirigacha jim eshitiladimi, ijro paytida surish bilan yurish ishlaydimi.
5. Musiqa yoki video chalayotganda boshqa ilovaga kelgan bildirishnoma avvalgidek o'qiladimi.
6. Sozlamalar va chatlar ro'yxatida yurish avvalgidek tez va to'liqmi.

Ishlamagan joy bo'lsa, Xatolar jurnalini yuboring. Yangi yozuv: `NAV_DIVERGED` (fwd, cur, fast, cand: sinf, bosiladimi,
bolalar soni, tepa chegarasi, yorlig'i bormi). Bu yozuv bo'lsa, tez yo'l biror nuqtani tashlagan ekani aniq bo'ladi.

## 6. 0.9.3: slayder va tanlagich, emoji va stikerlar, menyu sozlash

Holat: Gradle bilan qurilmadi (kompilyator yo'q), telefonda sinalmadi. Qo'lda tekshirildi: qavslar balansi, XML tuzilishi, hamma
`R.string` nomi to'rt tilda borligi, `GestureAction` nomlari, importlar (hammasi yangi kod uchun alohida skript bilan).

Slayder (hisobot: ekran yorqinligi 50 foizda, bir barmoq bilan pastga surilsa kamaymaydi):

- Sabab (kod o'qilib aniq): standart ishoralarda yuqoriga va pastga surish doim `moveFocus` (keyingi, oldingi element) edi.
  Qiymatni o'zgartiruvchi `adjustValue` faqat `VALUE_UP` va `VALUE_DOWN` amallariga ulangan, ular esa standart ishoralarga
  biriktirilmagan. `scrollScreen` slayderni umuman bilmasdi (`isScrollable` SeekBar'da yolg'on), shuning uchun ISHORALAR.md'dagi
  "o'ngga, so'ng chapga: slayderda qiymatni oshirish" bajarilmasdi.
- `core/Adjustable.kt` (yangi): `isAdjustable` (RangeInfo bor va qadam amali yoki ACTION_SET_PROGRESS bor; yoki NumberPicker qadam
  amali bilan), `find` (o'zi yoki tanlagichning ichki elementi uchun ota-onasi; ichidagi bolalar qidirilmaydi: Telegram xabaridagi slayder
  yurishni to'smasin), `step` (avval ACTION_SCROLL_FORWARD/BACKWARD, bo'lmasa ACTION_SET_PROGRESS qadamlari 1/20, 1/10, 1/5, 1/2),
  `atEdge` (chekka).
- `ReaderService.adjustByGesture`: faqat bir barmoqli yuqoriga, pastga, "o'ngga-chapga", "chapga-o'ngga" ishoralari va ularga
  NEXT, PREV (faqat "Elementlar" boshqaruvida), MODE_NEXT, MODE_PREV (faqat VoiceOver profili) yoki SCROLL_FWD, SCROLL_BACK biriktirilgan
  bo'lsa. Ikki barmoqli aylantirishga tegilmaydi (xabarnoma soyasi va ro'yxat uchun). Tez tekshiruv `cachedFocus` bo'yicha tizimga murojaat
  qilmaydi, shuning uchun oddiy elementlarda yuqoriga/pastga surish sekinlashmaydi.
- `adjustNode`: refresh, qadam, 130 va 350 ms da qiymat tekshiriladi va aytiladi; chekkada `Earcons.Kind.EDGE` va joriy qiymat.
  `checkStateChange` endi slayderni jim tashlamaydi: foydalanuvchi tegib turganda (`touching`) yoki ishoradan keyin 2,5 soniya ichida
  o'zgargan qiymatni aytadi; `announceValue` bir xil matnni 900 ms ichida takrorlamaydi.
- `NodeDescriber`: foiz `roundToInt` (ilgari `toInt` kasrni tashlardi: 127/255 -> 49); ilovaning stateDescription'i bo'lsa foiz qo'shilmaydi;
  `Slider` va sozlanadigan RangeInfo elementlar "slayder" deb tanladi; foydalanish ko'rsatmasi `hint_adjust`. `Navigator` "Boshqaruvlar"
  rejimi sozlanadigan elementlarni ham oladi.

Emoji va stikerlar:

- `content/EmojiSpeech.kt`: emoji topish (Emoji_Presentation, odatda emoji sifatida yoziladigan belgilar, U+FE0F bilan emoji bo'ladigan belgilar,
  teri rangi, ZWJ ketma-ketliklari, bayroqlar, tugmalar), 600 dan ortiq o'zbekcha nom, davlat bayroqlari. `EmojiMode`: KEEP, READ, SKIP.
  `ContentPipeline.process` matnni normallashtirgandan keyin birinchi bosqichda ishlatadi.
- `content/StickerText.kt`: stiker aniqlash (id yoki sinf "sticker", yoki qisqa tavsifda stiker so'zi: boshida yoki emoji bilan) va `quiet`.
  `NodeDescriber.describe` `details.readStickers` o'chiq bo'lsa chaqiradi.
- `Details.readEmoji`, `Details.readStickers`, `Prefs.DET_EMOJI`, `Prefs.DET_STICKERS`, `Prefs.emojiMode`. `detailsFor` ilova profilidagi
  darajaga ularni ko'chiradi.
- Taxmin: Telegram va WhatsApp stiker tavsifi so'z va emojidan iborat deb olindi; haqiqiy daraxt ko'rilmadi.

Menyu:

- `GestureAction` ga `SETTINGS_MENU`, `OPEN_VOICES`, `OPEN_GESTURES`, `OPEN_DETAILS`, `OPEN_AI`, `OPEN_BACKUP`, `OPEN_HEALTH`, `OPEN_LOG`,
  `OPEN_MENU_EDITOR` qo'shildi (nomlar bo'yicha saqlanadi: eski saqlangan ishoralar buzilmaydi).
- `GlobalMenu.effective(ctx)`: `Prefs.MENU_ITEMS` (vergul bilan ajratilgan amal nomlari) yoki standart ro'yxat; bo'sh yoki yaroqsiz bo'lsa
  standart. `ui/MenuActivity`: belgilash katakchalari, har o'zgarish darhol saqlanadi. Eksport va import `menu_items` ni tekshiradi.

v20 (0.9.6):

- Menyu: `GlobalMenu.actions` da `LINKS_MENU` ikkinchi; `effective()` bir martalik `links_second` yangilanishi bilan saqlangan ro'yxatda
  uni ikkinchi o'ringa ko'chiradi (`moveItem`). Tartib foydalanuvchi belgilagan holda saqlanadi. `ReaderService.showGlobalMenu()` oxirgi
  bandi `showMenuReorderPick` -> `showMenuReorderDrop` / `showMenuAddItem`; `MenuOverlay.show(onLongPick)` qatorni uzoq bosishni ushlaydi.
- Ovoz: `Prefs.ENGINE = "custom"` + `ENGINE_PACKAGE` (`SpeechManager.initialEngine`, `customPinned`), `Prefs.VOICE_READS` va
  `Prefs.engineReadsCyrillic` (matn quvuri kirillni lotinga o'tkazish-o'tkazmasligini shu bilan hal qiladi). `VoicesActivity` hamma
  o'rnatilgan dvigatellarni sanaydi (`TextToSpeech.getEngines`).
- Stiker: `Details.stickerShort`, `StickerText.short`, `EmojiSpeech.keepFirst`.
- Til: `GestureAction.displayName(ctx)` / `GestureItem.displayName(ctx)` resursdan (`ga_*`, `gi_*`), `act_*` (element amallari), `quirk_*`.

Telefonda sinash ro'yxati:

1. Tezkor sozlamalarda ekran yorqinligi slayderiga fokus bering: yuqoriga surish oshiradimi, pastga surish kamaytiradimi, har safar yangi
   foiz aytiladimi; eng chekkada chekka ovozi eshitiladimi.
2. Sozlamalar, Tovush: ovoz balandligi slayderi. Soat tanlagichi (vaqt tanlagich g'ildiragi) ham.
3. "Boshqaruvlar" o'qish boshqaruvida slayderga yetib boriladimi.
4. Telegram'da stiker va emojili xabar: Tafsilotlar, "Emoji va stikerlar" tugmalarini o'chirib/yoqib sinang.
5. O'zbegim menyusi, "O'zbegim sozlamalari bo'limlari": har band to'g'ri ekranni ochadimi. "O'zbegim menyusini sozlash" ekranida
   bandni olib tashlang: menyudan yo'qoladimi.
6. Oddiy yuqoriga/pastga surish (slayder bo'lmagan joyda) avvalgidek ishlayaptimi va sekinlashmadimi.

Ishlamagan joy bo'lsa, Xatolar jurnalini yuboring (`FOCUS_NODE_FAILED`, `SLOW_HANDLER`).

## 7. 0.9.4: telefon raqamlari, boshqa ekran o'quvchi bilan kelishuv, yacheykalar izolyatsiyasi, surish tezligi

Holat: Gradle bilan qurilmadi (kompilyator yo'q), telefonda sinalmadi. Qo'lda tekshirildi: o'zgargan joylar asl arxivga nisbatan diff
bo'yicha ko'rib chiqildi (qavslar qo'shilgan satrlarda juft), XML tuzilishi, yangi `R.string` nomlari to'rt tilda borligi va takrorlanmasligi,
yangi kod ishlatgan maydon va funksiyalar e'lon qilinganligi. Telefon raqami regexlari Kotlin faylidan olinib, JVMda (java.util.regex)
30 ga yaqin misolda sinaldi; Androidning ICU regex dvigatelida sinalmadi (ishlatilgan konstruksiyalar ikkalasida bir xil).

Telefon raqamlari:

- `content/PhoneFormat.kt` (yangi): `apply(text)`. Uch qoida: xalqaro (`+?998` va 9 xona, ajratgichlar: probel, chiziqcha, nuqta, qavs),
  mahalliy (2-3-2-2 guruh, kamida bitta probel yoki chiziqcha, operator kodi ro'yxatda), kalit so'zdan keyingi ajratgichsiz 9 xona.
  Natija: guruhlar `UzNumbers.words` bilan so'zga aylantiriladi, orasiga ". ", oxiriga nuqta (agar keyin allaqachon tinish belgisi yoki
  probeldan keyin nuqta bo'lsa, qo'shilmaydi). Xalqaro raqam "plyus to'qqiz yuz to'qson sakkiz" bilan boshlanadi.
  Sabab: `SpeechNormalizer.PLAIN` "+" dan keyingi sonni o'zgartirmaydi, "+998" dvigatelga qolardi; so'zga aylantirish natijani
  dvigatelga bog'liq qilmaydi.
- `PipelineOptions.phoneGroups`; `ContentPipeline.process` uni `SensitiveText` tekshiruvidan keyin, `SpeechNormalizer` dan oldin ishlatadi
  (shunda eski `+...` qoidasi bu raqamlarga tegmaydi). `Details.phoneGroups`, `Prefs.DET_PHONE` (standart `true`), `Prefs.phoneGroups`,
  `preferences_details.xml` ("Raqamlar" bo'limi), `detailToggles` (menyudagi Tafsilotlar). Ilova profilidagi daraja uni bekor qilmaydi.

Boshqa ekran o'quvchi bilan kelishuv:

- `core/OtherReaders.kt` (yangi): `find(ctx)` va `Watcher`. `find`: (1) `AccessibilityManager.getEnabledAccessibilityServiceList`
  ichida o'zimizdan boshqa paketli, `CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION` yoki `FLAG_REQUEST_TOUCH_EXPLORATION_MODE` bor xizmat
  (yoki ma'lum paket); (2) zaxira: `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` qatorida ma'lum ekran o'quvchi paketi.
  `Watcher`: shu sozlama va `ACCESSIBILITY_ENABLED` ustida `ContentObserver` (ruxsat kerak emas).
- `ReaderService`: `suspended` endi hisoblanadigan xususiyat: `userSuspended || yielded` (eski ishlatilishlar ishlayveradi; qo'lda
  to'xtatish `userSuspended`). `evaluateOtherReader` (kuzatuvchi 350 ms kechikish bilan va har 2,5 soniyada) -> `enterStandby` yoki
  `leaveStandby`. Kutish rejimida: e'lon aytiladi, so'ng `speak()` va `focus()` hech narsa qilmaydi, `handleKey` va `updatePassthrough`
  chiqib ketadi, `setTouchExploration` hech qachon yoqmaydi; `applyStandbyFlags(true)` teginib o'rganish, tugmalarni filtrlash,
  ko'p barmoqli ishora, ikki barmoqli o'tkazish bayroqlarini olib tashlaydi va `eventTypes = 0` qiladi; chiqishda avvalgi bayroqlar
  aynan o'zi va `NEEDED_EVENTS` qaytariladi. `onInterrupt` kutish rejimiga o'tgach 5 soniya e'lonni kesmaydi.
  `onServiceConnected` boshqa ekran o'quvchi allaqachon yoniq bo'lsa, kutish rejimida boshlaydi.
- Taxminlar (telefonda tekshirilishi kerak): `serviceInfo` orqali `eventTypes = 0` va bayroqlarni olib tashlash tizim tomonidan
  qabul qilinishi (mavjud "to'xtatish" ham xuddi shunday teginib o'rganishni o'chiradi); TalkBack yoqilganda tizim sozlamalari
  kuzatuvchisi chaqirilishi.
- `Prefs.YIELD_OTHER` (standart `true`), `Prefs.yieldToOtherReaders`, `preferences.xml` ("Boshqa ekran o'quvchilar").

Qo'shni yacheykalar izolyatsiyasi:

- `NodeDescriber.trustHint`: hodisa matni (hint) faqat bolalari 1 tadan oshmagan yoki ro'yxat elementi bo'lgan tugun uchun ishlatiladi.
- `NodeDescriber.rollUp`: ro'yxat/jadval ildizi uchun bo'sh; ichki ro'yxatlar o'tkazib yuboriladi; markazi ildiz chegarasidan tashqarida
  bo'lgan bola o'tkazib yuboriladi; `includeActionable` rejimida fokusga tushmaydigan, bosilmaydigan konteynerda ikkitadan ortiq bosiladigan
  yorliqli bola bo'lsa, natija bo'sh.
- `ReaderService.onHover`: `event.source.childCount > 1` va hodisa matni bir nechta bo'lakdan iborat bo'lsa, birlashtirilgan matn
  zaxira sifatida ishlatilmaydi.
- `ReaderService.readNewChatMessage`: `cachedFocus()` shu ro'yxat ichida va oxirgi fokus 30 soniya ichida (`CHAT_FOCUS_IDLE_MS`)
  bo'lsa, chiqib ketadi.

Surish tezligi:

- `Navigator.listNeighbor` avval `windowedNeighbor` ni ishlatadi: cur turgan ro'yxat elementi `collect(item)`, keyin ko'rinish tartibida
  (teskari konteynerda indeks kamayadi) 6 tagacha ko'rinadigan qo'shni element. Natija `collect(list)` bilan bir xil; cur topilmasa
  yoki indeks aniqlanmasa `null` va to'liq usulga o'tiladi. Chet holat: ro'yxatning o'zi nuqta bo'lsa (bosiladigan ro'yxat), tez variant
  uni qo'shni deb topmaydi (oddiy RecyclerView uchun bu to'g'ri).
- `moveFocus`: oyna ildizi (`cur.window.root`, so'ng `rootInActiveWindow`) lazy `windowRoot()`: tez yo'lda yuklanmaydi.

Telefonda sinash ro'yxati:

1. Tafsilotlar, "Raqamlar" yoqiq: matnda "+998947739910", "+998 94 773 99 10", "94 773 99 10" va "+998. 94. 773. 99. 10." yozib (Telegram,
   Xabarlar, Kontaktlar) ustiga fokus keltiring: hammasi bir xil, guruh-guruh o'qiladimi. O'chirilsa, avvalgidek.
2. TalkBack'ni yoqing: O'zbegim e'lonni aytib jim bo'ladimi, TalkBack ishoralari to'liq ishlaydimi. TalkBack'ni o'chiring: O'zbegim
   ikkinchi e'lonni aytib, ishoralarni qaytaradimi. Sozlamada "o'zim to'xtash" ni o'chirib, qayta sinang.
3. Samsung bosh ekrani: ilovalar orasida va bo'sh joyga tegib yuring: faqat bitta ilova nomi eshitiladimi, papkada nechta nom aytiladi.
4. Telegram chatlar ro'yxati va kanal: o'ngga-chapga surish, to'xtab turib ham: qo'shni yacheyka matni qo'shilib o'qilmaydimi, surish
   avvalgidan tezmi (Xatolar jurnalidagi SLOW_HANDLER va NAV_DIVERGED yozuvlariga qarang).
5. Telegram suhbatida kelgan yangi xabar: fokus kiritish maydonida bo'lsa avtomatik o'qiladimi.

Ishlamagan joy bo'lsa, Xatolar jurnalini yuboring (`SLOW_HANDLER`, `NAV_DIVERGED`, `FOCUS_NODE_FAILED`).

## 8. 0.9.7 (v21): tizim nutq dvigateli va "Afzal ko'rilgan mexanizm"

Maqsad: O'zbegim Android sozlamalarida "Matnni nutqqa aylantirish" > "Afzal ko'rilgan mexanizm" ro'yxatida chiqsin va uni bitta tugma
bilan tanlash mumkin bo'lsin.

### 8.1 Nima uchun bu alohida xizmat

Android'da ro'yxatda chiqish uchun ilova `android.speech.tts.TextToSpeechService` ni e'lon qilishi shart (`AndroidManifest.xml`:
`android.intent.action.TTS_SERVICE` + `category.DEFAULT` + `meta-data android.speech.tts` = `res/xml/tts_engine.xml`). Ekran o'quvchining
o'zi esa ovoz chiqarishda boshqa dvigatelga mijoz sifatida ulanadi (`SpeechManager`). Shuning uchun yangi xizmat ham boshqa dvigatelning
"ustidan" ishlaydi: o'zi sintez qilmaydi.

### 8.2 Oqim (bir so'rov)

1. Tizim `OzbegimTtsService.onSynthesizeText` ni chaqiradi (matn, til uch harfli ISO kodda, tezlik va ton, 100 = odatiy).
2. Til `Iso.toLocale` bilan Locale'ga aylanadi. `TtsText.prepare`: o'zbekcha so'rovda `ContentPipeline` (ekran o'quvchidagi bilan bir xil
   quvur: uch yozuv, raqamlar, Rim raqamlari, telefon, emoji, til ko'prigi); boshqa tillarda faqat telefon raqamlari va emoji. Sozlama:
   `Prefs.TTS_RULES`. Quvur xato bersa, asl matn ishlatiladi.
3. `TextChunker` bilan gap bo'laklariga bo'linadi (120/200 belgi). `TtsBackend.submit`: bo'lak orqa dvigatelga `synthesizeToFile` bilan
   beriladi; 3 tagacha bo'lak oldindan navbatga qo'yiladi (dvigatel keyingisini tayyorlayotganda oldingisi ijro etiladi).
4. Tayyor WAV `WavReader` bilan 16 bitli PCM'ga o'qiladi (8/16/24/32 bit va float qo'llab-quvvatlanadi), kerak bo'lsa `PcmConvert`
   birinchi bo'lak formatiga keltiradi, `SynthesisCallback.start/audioAvailable/done` bilan tizimga qaytariladi. Ijro, audio oqimi va
   ovoz balandligini tizim o'zi boshqaradi.
5. `onStop`: bayroq qo'yiladi va orqa dvigatel to'xtatiladi (`TtsBackend.stopAll`). Bo'lak vaqti 30 soniya; oshsa `ERROR_SYNTHESIS`.

Tillar: `onIsLanguageAvailable`, `onLoadLanguage`, `onGetVoices`, `onIsValidVoiceName`, `onLoadVoice`, `onGetDefaultVoiceNameFor` orqa dvigatel
natijasiga tayanadi (ovoz nomi = til belgisi, masalan `uz-UZ`). O'zbekcha so'rovda ekran o'quvchi uchun tanlangan ovoz (masalan, Islom),
agar orqa dvigatelda bo'lsa, qo'llanadi (`Prefs.voice`).

### 8.3 Orqa dvigatel tanlovi va o'ziga ulanishdan himoya

`EngineChoice.backendPackage`: qo'lda tanlangan (`Prefs.TTS_BACKEND`) > ekran o'quvchi tanlagani (RHVoice, yuklab olingan ovoz paketi,
oldingi safar eslab qolingan) > O'zbegim afzal ko'rilgan bo'lishidan oldingi tizim dvigateli (`Prefs.TTS_PREV_DEFAULT`) > ishonchli ro'yxat >
boshqa istalgan. O'zbegimning o'zi hech qachon tanlanmaydi (aks holda xizmat o'ziga ulanib, cheksiz aylanardi).

Himoya joylari: `SpeechManager.initialEngine` va `onInit` (tizim standarti O'zbegim bo'lsa, haqiqiy dvigatelga o'tadi, `TTS_ENGINE_SWITCHED`
yoziladi; `knownEngines` va `systemDefaultEngine` dan O'zbegimning o'zi chiqarib tashlanadi), `VoicesActivity` (ro'yxat haqiqiy dvigateldan
boshlanadi, O'zbegim "boshqa dvigatellar" ichida ko'rsatilmaydi). Ekran o'quvchining o'z nutqi xizmat orqali o'tmaydi: ikki marta qayta ishlash yo'q.

### 8.4 Bitta tugma: `TtsDefaultEngine.select`

Android oddiy ilovaga `Settings.Secure.tts_default_synth` ni yozishga ruxsat bermaydi (kerak: `WRITE_SECURE_SETTINGS`, manifestda e'lon qilingan,
lekin o'rnatishda berilmaydi). Uch yo'l, tezdan sekinga:

- `DIRECT`: ruxsat kompyuterdan berilgan bo'lsa (`adb shell pm grant uz.ekranoquvchi.mvp android.permission.WRITE_SECURE_SETTINGS`),
  sozlama bir zumda yoziladi va o'qib tekshiriladi.
- `AUTOMATION` (`core/TtsDefaultSetup`): ekran o'quvchi xizmati (`ReaderService.instance`) yoqiq bo'lsa. `com.android.settings.TTS_SETTINGS` ochiladi;
  har 350 ms da (eng ko'pi bilan 30 soniya) faqat paket nomida "settings" bor oynalar ko'riladi. Dvigatel qatori nomi bo'yicha topiladi (nom
  `EngineChoice.engineLabel` dan: Sozlamalar aynan shu nomni ko'rsatadi, shuning uchun tizim tilidan qat'i nazar ishlaydi; faqat to'liq mos
  kelish, "ichida bor" emas, aks holda sozlamalar tugmasi bosilib qolardi). Topilmasa, hozirgi dvigatel qatori bosiladi (ro'yxat shu orqali
  ochiladigan telefonlar uchun), keyin sahifa aylantiriladi. Tanlovdan keyin ogohlantirish oynasida `android:id/button1` (OK) bosiladi.
  Muvaffaqiyat faqat `Settings.Secure` haqiqatan o'zgargach e'lon qilinadi; sozlamalardan orqaga chiqiladi. Muvaffaqiyatsiz bo'lsa
  sozlamalar ochiq qoladi va qo'lda tanlash yo'li aytiladi.
- `NEED_SERVICE`: xizmat o'chiq bo'lsa, sozlamalar ochiladi va yo'l-yo'riq beriladi.

Oldingi dvigatel eslab qolinadi (`Prefs.TTS_PREV_DEFAULT`); yoqilgan holatda tugma shunga qaytaradi (bir xil yo'l). Ovoz beruvchi dvigatel
topilmasa, tugma tanlashni rad etadi va RHVoice o'rnatishni taklif qiladi (aks holda butun telefon jim qolardi).
Ekran o'quvchi nutqiga o'zgartirish kiritilmagan: jarayon e'lonlari `ReaderService.announce` orqali aytiladi, `ReaderService.kt` ga tegilmagan.

### 8.5 Maxfiylik va cheklovlar

- Matn faqat qurilmadagi dvigatelga boradi; tarmoq ishlatilmaydi (Azure/Microsoft ovozi bu yo'lga ulanmagan). Orqa dvigatelning o'zi (masalan,
  Google) tarmoq ishlatishi mumkin: bu shu dvigatelning sozlamasi.
- Android'ning o'zi non-system dvigatelni tanlashda "bu dvigatel barcha matnni ko'rishi mumkin" ogohlantirishini chiqaradi; avtomatik rejimda
  O'zbegim OK ni o'zi bosadi (foydalanuvchi buni bilishi uchun e'lon qilinadi).
- Avtomatik tanlash tizim sozlamalari ekraniga bog'liq: Samsung, Xiaomi va boshqalarda tuzilishi farq qilishi mumkin. Shuning uchun natija
  har doim haqiqiy sozlamadan tekshiriladi va muvaffaqiyatsizlikda qo'lda yo'l aytiladi.
- Kechikish: har bir bo'lak fayl orqali o'tadi, shuning uchun ekran o'quvchining to'g'ridan-to'g'ri nutqidan sal sekinroq (birinchi bo'lak
  tayyor bo'lguncha). Audio formati bo'laklar orasida o'zgarsa, birinchisiga keltiriladi.
- O'zbek tili orqa dvigatelda bo'lmasa (masalan, Google dvigateli o'zbekchani qo'llamasa), `onIsLanguageAvailable` rad etadi: orqa dvigatel
  sifatida RHVoice'ni tanlang.
- Xizmat `directBootAware`: qulf ochilmasdan oldin ishlashi orqa dvigatelning o'ziga ham bog'liq.

### 8.6 Telefonda sinash ro'yxati

1. Sozlamalar > Matnni nutqqa aylantirish: ro'yxatda "O'zbegim" bormi; yonidagi sozlamalar tugmasi "O'zbegim ovoz dvigateli" sahifasini ochadimi.
2. Bosh ekrandagi tugma (xizmat yoqiq, ADB ruxsatisiz): sozlamalar ochilib, O'zbegim tanlanadimi, ogohlantirishda OK bosiladimi, so'ng ilovaga
   qaytib "Tayyor" deyiladimi. Telefon tilini o'zgartirib ham sinang (ruscha, inglizcha).
3. ADB ruxsati bilan: tugma sozlamalarni ochmasdan darhol ishlaydimi.
4. Tugmani yana bosing: oldingi dvigatel qaytadimi.
5. "Namunani eshitish": o'zbekcha namuna O'zbegim dvigateli orqali aytiladimi (RHVoice o'rnatilgan bo'lsin).
6. Boshqa ilova (masalan, kitob o'quvchi yoki TalkBack): matn o'zbekcha qoidalar bilan o'qiladimi (raqam, telefon, kirill). Ovoz
   balandligi, to'xtatish va tezlik ishlaydimi.
7. O'zbegim afzal ko'rilgan bo'lib turganda ekran o'quvchining o'z nutqi va Ovozlar ekrani oldingidek ishlaydimi (o'ziga ulanib qolmasligi).
8. RHVoice o'chirilgan telefonda tugma rad etib, o'rnatishni taklif qilishi.

Ishlamagan joy bo'lsa, Xatolar jurnalini yuboring (`TTS_INIT_FAILED`, `TTS_SERVICE_ERROR`, `TTS_DEFAULT_SETUP`, `TTS_ENGINE_SWITCHED`).

## 9. 0.9.8 (v22): e'lon jadvali, soat o'qish uslubi, raqamlar

### 9.1 Vaqt e'loni jadvali (`TimeAnnouncer`)

Holat: `scheduledAt` (rejalashtirilgan chegara), `lastFiredAt` (oxirgi aytilgan chegara), `tick` (Handler), alarm (AlarmManager).

- `reschedule`: keyingi chegara `nextTrigger(interval, now)`; agar u `lastFiredAt` dan oldin yoki teng bo'lsa, `lastFiredAt` dan keyingisi
  olinadi. Handler va alarm bir chegaraga qo'yiladi.
- `fire(boundary)`: chegara `lastFiredAt` dan katta bo'lmasa takror hisoblanadi, e'lon aytilmaydi, lekin jadval HAR HOLDA qayta tuziladi.
  Ilgari takrorda `return` jadvaldan oldin edi: signal 1 ms erta kelsa, "keyingi chegara" yana shu chegara bo'lib chiqar, takroriy chaqiriq
  jadvalsiz qaytar va zanjir uzilar edi. Erta kelgan signalda chegara vaqti aytiladi (14:29 emas, 14:30), kechiksa haqiqiy vaqt.
- `onAlarm`: chegara `boundaryFor(interval, now)`: now + 5 soniya oldingi chegaraga yaxlitlanadi (erta kelgan va kechikkan alarm uchun).
- `ensureScheduled` (ekran yoqilganda va har batareya hodisasida): jadval yo'q bo'lsa qayta tuzadi; chegara o'tib ketgan, lekin aytilmagan
  bo'lsa darhol aytadi.
- Soat yoki vaqt zonasi o'zgarsa va rejim o'zgarsa `lastFiredAt` nolga qaytadi.
- Manifest: `SCHEDULE_EXACT_ALARM` (Android 12) va `USE_EXACT_ALARM` (Android 13+, o'zi beriladi). Google Play'ga qo'yilsa, ikkinchisini qayta
  ko'rib chiqing (ruxsat faqat alarm/kalendar ilovalari uchun).

Simulyatsiya (Java nusxasi): signal har safar 1 ms erta: eski 1 ta e'lon, yangi 10 ta (5 soatda). Tasodifiy kechikish/ertalik, 300 sinov:
eski 165 (30 daqiqa) va 78 (60 daqiqa) sinovda buzildi, yangi 0. Uyqu rejimi va kechikkan alarm: 48/48. Signallar yo'qolgach ekran yoqilishi:
kechikkan e'lon aytiladi, jadval tiklanadi. Soat orqaga surilishi: e'lonlar to'g'ri chegaralarda davom etadi.

### 9.2 Batareya

Formula o'zgarmadi (`onBattery`): kamayganda `m = (prev - 1) / step * step`, `level <= m`; ortganda `m = (prev / step + 1) * step`,
`level >= m`. Xato sozlamada edi: qadam (`BATTERY_STEP`) faqat `BATTERY_MODE = custom` bo'lsa o'qiladi, qadamni yozish rejimni o'zgartirmas edi.
`TimeBatteryFragment.enableCustomBattery` surgich o'zgarganda va `NumberDialog` "Saqlash" bosilganda (`onSaved`, bekor qilinganda emas) rejimni
`custom` qiladi va aytadi.

### 9.3 Soatni o'qish uslubi

`Prefs.TIME_STYLE`: `past` yoki `digital` (standart `past`). `TimeAnnouncer.timeText`: soat 24 soatlikda `hour = h24`, 12 soatlikda
`h24 % 12` (0 bo'lsa 12). O'zbekcha tilda:
- `past`: `tb_say_past` = "Soat %1$s, soat %2$s %3$s daqiqa o'tdi" (`UzNumbers.words`, `UzNumbers.ablative`, daqiqa so'z bilan);
- `digital`: `tb_say_digital` = "Soat %1$s, soat %2$s" (ikkinchisi `UzNumbers.time`: "o'n bir nol besh");
- daqiqa 0: faqat `msg_status_time` ("Soat o'n bir");
- 12 soatlik: oldiga `tb_say_part` bilan kun qismi.
Boshqa tilda avvalgidek "14:30" ko'rinishi. `ReaderService.speakStatus` ham `timeText` dan foydalanadi.

### 9.4 Normalizator (`SpeechNormalizer`)

Yangi/o'zgargan qoidalar (tartib: ... HASHTAG, THOUSANDS, DECIMAL, GROUPED, ORDINAL, GLUED, PLAIN):
- `THOUSANDS`: `1.050.000`, `1,105` (har guruh 3 raqam; butun qismi 0 bo'lsa yoki foiz/o'lchov oldida bo'lsa tegilmaydi);
- `ORDINAL`: `105-chi`, `20-nchi` -> `UzNumbers.ordinal`;
- `GLUED`: raqamdan keyin har qanday harf (lotin, kirill): `105m`, `105та`; inglizcha `1st/2nd/3rd/4th` va `3a8f` tegilmaydi;
- `PLAIN`: "+" dan keyingi son ham;
- `CUR_BEFORE`/`CUR_AFTER`: guruhlangan miqdor to'liq olinadi, oxirgi raqam ortda qolmaydi.
Ataylab qo'shilmadi: harf+raqam (A105, H264): kod ekanini sonlardan ajratib bo'lmaydi.

## 10. 0.9.9 (v24): ikki marta bosish va oynalararo yurish

Ikki marta bosish (`activateByDoubleTap`) joriy elementni `currentFocus()` dan oladi. Manbalar ustuvorlik tartibida: (1) o'zimiz
o'qib bergan, tizim fokus bermagan element (`softFocus`, 120 soniya); (2) tizim fokusi (`findFocus`); (3) oxirgi fokus hodisasining
manbasi. Qoida: foydalanuvchi eshitgan element bosiladigan element bo'lishi kerak. Shuning uchun tizim fokus hodisasi kelmagan, lekin
o'zimiz o'qib bergan har qanday element `markHeard` bilan `softFocus` va `lastFocusedSource` ga yoziladi.

Bosish tartibi: navigatsiya paneli tugmasi bo'lsa, tizim amali; bo'lmasa `clickNode` (element yoki 8 pog'onagacha ota-onasi;
bosish amali bayroqda yoki amallar ro'yxatida bo'lsa); bo'lmasa `TouchActions.tapCenter` (oynada ko'rinadigan qismning markazi).
Hammasi o'tmasa, `FOCUS_NODE_FAILED` (`why=click_failed`) jurnalga yoziladi.

Oynalararo yurish (`crossWindow`): ilova chetiga yetilganda qo'shni oynaga o'tadi, lekin holat paneli oynasi
(`isStatusBarWindow`: TYPE_SYSTEM, ekran tepasida, balandligi ekranning oltidan biridan kichik, SystemUI) o'tkazib yuboriladi.
Ochiq ishora bilan (`jumpWindow`) unga hamon o'tish mumkin.

Tekshirilmagan: bu o'zgarishlar Gradle bilan qurilmadi va telefonda sinalmadi.

## 11. 0.9.10 (v25): tez yo'l va ro'yxat tartibi

Surishda joriy elementdan keyingi nuqta ikki yo'l bilan topiladi: tez yo'l (`Navigator.nextUnit`, daraxt tartibida) va ro'yxat
tartibi (`Navigator.listNeighbor`, to'liq `collect`). Ikkalasi bir xil qoida bilan nuqta tanlashi shart. Ilgari tez yo'l yorlig'siz
kichik elementni "shovqin" deb tashlar (`isUnlabeledNoise`), `collect` esa qo'shardi, shuning uchun Telegram'da natijalar farq
qilar edi. Endi ro'yxatning keng bolasi shovqin hisoblanmaydi.

Natijalar farq qilsa (`crossCheckInList`), ko'rinish tartibi bo'yicha (`readingCompare`) yurish yo'nalishida joriy elementga yaqinrog'i
olinadi (`nearerInDirection`). Tez yo'l yo'nalishga teskari tomonda bo'lsa (`isBehind`), bu solishtirish chat bo'lmagan ilovalarda
ham bajariladi. Daraxt tartibi bilan ko'rinish tartibi mos kelmaydigan ekranlarda (teskari ro'yxat oxiridagi sarlavha) shunday
sakrashlar bo'lardi.

Bosish (`clickNode`): element yoki uning ota-onasi, lekin ro'yxat chegarasigacha. Ro'yxat va undan yuqoridagi konteynerlar bosilmaydi.

Tekshirilmagan: Gradle bilan qurilmadi, telefonda sinalmadi.

## 12. 0.9.11 (v26): ro'yxat fokus nuqtasi emas, ikki marta bosishning haqiqiy zaxirasi

Fokus nuqtasi qoidasi (`Navigator.isActionable`): ichida elementlari bor (`childCount > 0`) ro'yxat yoki jadval konteyneri
(`isListBoundary`: aylantiriladigan yoki `collectionInfo`li) bosiladigan deb belgilangan bo'lsa ham nuqta EMAS. Bu qoida `unitFor`
(teginib o'rganish), `firstUnit`, `collect` va `isUnit`ga birdan ta'sir qiladi. Ilgari `unitFor` ota-onalar zanjirida
`isActionable`ni `isListBoundary`dan OLDIN tekshirardi, shuning uchun bosiladigan RecyclerView (Telegram) xabarni yutib, o'zi
nuqta bo'lardi; `firstUnit` va `collect` ham uni birinchi nuqta qilardi.

Ikki marta bosish (`activateByDoubleTap`) nishoni `activationTarget()`: `currentFocus()`; u ro'yxat konteyneri bo'lsa yoki yo'q bo'lsa,
oxirgi teginib o'rganilgan element (`lastHoverUnit`). Tartib: navigatsiya tugmasi; `clickNode`; `tapByTouch`.
`tapByTouch` `SystemSurfaces.injectTap` orqali teginib o'rganishni qisqa muddatga
o'chirib, haqiqiy tegish yuboradi (`injectSwipe` bilan bir mexanizm: `injectPath`). Teginib o'rganish yoqiq paytda `dispatchGesture`
bilan yuborilgan oddiy tegish bosish bo'lmaydi, tizim uni teginib o'rganish deb hisoblaydi: eski `tapCenter` shuning uchun
bu rejimda ishlamasdi. Tegish tugagach (1,1 s; uzoq bosishda 1,8 s) yangi ekranda fokus bo'lmasa, birinchi elementga o'tiladi.

Eskirgan fokus himoyasi: `focusSeq` har `focus()` va har fokus hodisasida oshadi. `readIfNoFocusEvent` va `processHover`dagi
kechikkan o'qish so'ralgan paytdagi qiymatni eslab qoladi va shu orada fokus ko'chgan bo'lsa ishlamaydi. `processHover`da tizim
fokus bermagan, lekin o'qib berilgan element `markHeard` bilan joriy element bo'ladi.

Tekshirilmagan: Gradle bilan qurilmadi, telefonda sinalmadi. Kutilgan jurnal: `NODE_UNLABELED` (cls=RecyclerView) yozuvlari
yo'qolishi; `GESTURE_PATH` `dt_*` yozuvlari.

## 13. 0.9.11 (v26, qo'shildi): fan qisqartmalari va matn tili

(Quyidagi fan qisqartmalari bo'limi v25 nusxasidan ko'chirilgan; kod o'zgartirilmagan.)

Matnni o'qishga tayyorlash quvuri (`ContentPipeline.process`) tartibi: `TextNormalizer` -> emoji -> (maxfiy matn belgisi) -> `PhoneFormat`
-> `RomanNumerals` -> **`ScienceAbbr`** -> `SpeechNormalizer` -> yozuv va til ko'prigi. `ScienceAbbr` raqamlar so'zga aylanishidan OLDIN
ishlaydi, shuning uchun "5 kg" -> "5 kilogramm" bo'ladi va son keyin `SpeechNormalizer`da "besh" ga aylanadi. Tasdiqlash kodi va parolga o'xshash
(`SensitiveText`) matnda ishlamaydi.

Tuzilma: `Science` (UNITS, PHYSICS, MATH, INFO), `ScienceOptions` (har fan uchun bayroq), `ScienceUnits` (raqamdan keyingi birliklar
jadvali va regex), `ScienceMath` (belgilar, daraja, yunon harflari, funksiyalar), `ScienceAbbr` (kirish nuqtasi: havola va elektron pochtani
o'tkazib yuboradi, ikkalasini ketma-ket ishlatadi, bo'sh joylarni tozalaydi). Sozlama: `Prefs.DET_SCI_*` -> `Prefs.science(c)` ->
`PipelineOptions.science`; ReaderService (o'zbekcha va boshqa til tarmoqlari) va `TtsText` shuni uzatadi.

Qoidalar:
- Yoqilgan fan: qisqartma o'zbekcha so'zga aylanadi. O'chirilgan fan: qisqartma olib tashlanadi (emoji sozlamasi kabi), son qoladi. Natija bo'sh
  qolsa, asl matn qaytariladi (fokus jim qolmasligi uchun).
- Birlik faqat raqamdan keyin ("5 kg", "5kg"); yakka bosh harfli birlik (A, V, N, J, W, K, T) faqat probel bilan; "L" va yunon harflari bundan mustasno.
  Birlikdan keyin harf, raqam, pastki chiziq yoki apostrof kelsa, yoki yakka harfli birlik chiziqcha bilan harfga ulansa, tegilmaydi.
- Belgilar (+ = < >) operand chegarasida: chap tomonda raqam, qavs, daraja belgisi yoki yakka harf (so'rov qatoridagi `?q=` emas), probel ikki tomonda
  ham bo'lishi yoki ikkalasida ham bo'lmasligi shart. `<`, `>` o'ng tomonda faqat raqam, qavs yoki yakka harf; `+`, `=` o'ng tomonda har qanday harf ("F=ma").
- `MARK` (U+2063): birlik o'qilgach "5 kilogramm" oxiriga qo'yiladi, "5 kg + 3 kg" dagi "+" ni operand deb tanishga yordam beradi; `ScienceAbbr.apply`
  oxirida olib tashlanadi.
- `SpeechNormalizer.THOUSANDS` o'nlik kasrni ("1,105 kilometr") minglik deb o'qimasligi uchun birlik so'zlari ro'yxatini `ScienceAbbr.spokenUnitWords`
  dan oladi. Birlik o'chirilganda "1,105" yolg'iz qoladi va minglik deb o'qiladi (bir va ikki xonali kasrlar to'g'ri o'qiladi).

Matn tili (`content/TextLang`, `Prefs.isUzbekText`). `ContentPipeline.process` quvuri endi `PipelineOptions.uzbekText` ni tekshiradi:
true bo'lsa `ScienceAbbr` va `SpeechNormalizer` ishlaydi, false bo'lsa o'tkazib yuboriladi. `Prefs.pipelineOptions` o'zbekcha matn
uchun, `Prefs.otherLangOptions` boshqa til uchun (`uzbekText=false`, bridge/yozuv/Rim raqamlari o'chiq). ReaderService va
`TtsText` shu ikkitadan birini oladi. Matn tili tanlovi (`Prefs.DET_LANG`: auto, system, uz, other) `TextLang.isUzbek` bilan hal
qilinadi: auto'da `Prefs.voiceLangTag` (onlayn Azure ovozi bo'lsa uning tili, aks holda `VOICE_LANG`, faqat `VOICE_LANG_FOR` joriy
ovozga mos bo'lsa), ovoz yo'q bo'lsa tizim tili. `VOICE_LANG` ni Ovozlar ekrani va `SpeechManager.applyVoice` (`rememberVoiceLang`)
yozadi. `TtsText` so'ralgan til (`locale`) bo'yicha ishlaydi, chunki bu holda til so'rovdan keladi.

Matn tiliga bog'langan joylar: `ReaderService.process`, `SpeechManager.targetLocale`, `autoTranslateActive`, rasm tavsifi tili.
Tizim tiliga bog'liq qolganlar: resurs matnlari (interfeys, element turlari), `TimeAnnouncer` (shablonlar resursda), `TimeBatteryActivity`.

`SystemSurfaces.injectPath`: yuborilayotgan tegishlar soni `injecting` bilan sanaladi; `finishInjection` teginib o'rganishni faqat oxirgisi
tugagach qayta yoqadi, `restoreTask` esa xavfsizlik muddati o'tsa sanagichni nolga tushirib majburan qayta yoqadi.

Tekshirilmagan: Gradle bilan qurilmadi, telefonda sinalmadi.
