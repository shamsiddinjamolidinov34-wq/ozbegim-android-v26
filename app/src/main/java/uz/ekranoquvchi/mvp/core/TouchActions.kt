package uz.ekranoquvchi.mvp.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Ikki marta bosishda ishlatiladigan yordamchi amallar (ReaderService.activateByDoubleTap): navigatsiya paneli tugmalari va
 * element markaziga barmoq bilan tegish. ReaderService kattalashib ketmasligi uchun alohida faylga ajratilgan.
 */
object TouchActions {
    private const val SYSTEM_UI = "com.android.systemui"

    /**
     * Navigatsiya paneli (SystemUI) tugmasi bo'lsa (Orqaga, Bosh ekran, So'nggilar, Maxsus imkoniyatlar), unga mos tizim amali;
     * aks holda null. Bunday tugmani bosish ba'zan o'tmasligi mumkin, tizim amalining o'zi esa har doim ishlaydi.
     */
    fun navigationBarAction(node: AccessibilityNodeInfo): Int? {
        if (node.packageName?.toString() != SYSTEM_UI) return null
        val id = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
        return when (id) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recent_apps" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "accessibility_button" -> AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_BUTTON
            else -> null
        }
    }

    /**
     * Elementning oynada ko'rinadigan qismining markazi (ekran pikselida). Element oynadan qisman chiqib turgan bo'lsa
     * (ro'yxat chetida), markaz ko'rinmas joyga tushmasin: ko'rinadigan qismining markazi olinadi. Ko'rinmasa null.
     */
    fun tapPoint(node: AccessibilityNodeInfo): PointF? {
        val r = Rect()
        try {
            node.getBoundsInScreen(r)
        } catch (_: Throwable) {
            return null
        }
        if (r.isEmpty) return null
        val w = Rect()
        val hasWindow = try {
            node.window?.getBoundsInScreen(w)
            !w.isEmpty
        } catch (_: Throwable) {
            false
        }
        if (hasWindow && !r.intersect(w)) return null
        return PointF(r.exactCenterX(), r.exactCenterY())
    }

    /**
     * Bosiladigan tugun topilmaganda: element markaziga barmoq bilan tegadi. DIQQAT: teginib o'rganish yoqiq paytda tizim
     * xizmat yuborgan bir barmoqli tegishni ham o'zi ushlab oladi va uni bosish emas, "teginib o'rganish" deb hisoblaydi
     * (element faqat qayta o'qiladi). Shuning uchun ReaderService buning o'rniga SystemSurfaces.injectTap'dan foydalanadi
     * (u teginib o'rganishni qisqa muddatga o'chirib turadi); bu usul faqat zaxira.
     */
    fun tapCenter(service: AccessibilityService, node: AccessibilityNodeInfo, longPress: Boolean): Boolean {
        val p = tapPoint(node) ?: return false
        return try {
            val path = Path()
            path.moveTo(p.x, p.y)
            val stroke = GestureDescription.StrokeDescription(path, 0L, if (longPress) 600L else 50L)
            service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        } catch (_: Throwable) {
            false
        }
    }
}
