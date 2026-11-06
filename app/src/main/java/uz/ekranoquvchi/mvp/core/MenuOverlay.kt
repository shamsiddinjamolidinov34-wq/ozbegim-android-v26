package uz.ekranoquvchi.mvp.core

import android.accessibilityservice.AccessibilityService
import android.view.ContextThemeWrapper
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import uz.ekranoquvchi.mvp.R
import uz.ekranoquvchi.mvp.diag.swallowed

/**
 * Ro'yxatli menyu oynasi (TalkBack'dagi "TalkBack menyusi" va "Amallar" menyusi kabi). Oyna xizmatning maxsus
 * imkoniyatlar qatlamida (TYPE_ACCESSIBILITY_OVERLAY) ochiladi: ilova fokusni yo'qotmaydi, ruxsat ham kerak emas.
 * Qatorlarni O'zbegim odatdagi usulda o'qiydi (teginib yoki surib), ikki marta bosib tanlanadi.
 * Qatorni uzoq bosish (ikki marta bosib ushlab turish) ixtiyoriy onLongPick bilan ushlanadi: menyu bandini ko'chirish uchun.
 * Xato bo'lsa (ba'zi qurilmalarda oyna ochilmasligi mumkin) show() false qaytaradi va xizmat bu haqda aytadi.
 */
class MenuOverlay(private val service: AccessibilityService) {
    private var dialog: AlertDialog? = null

    val isShowing: Boolean
        get() = dialog?.isShowing == true

    /**
     * @param onPick tanlangan qator raqami bilan chaqiriladi (oyna allaqachon yopilgan).
     * @param onCancel "Yopish" tugmasi yoki Orqaga bilan yopilganda chaqiriladi.
     * @param onLongPick qator uzoq bosilganda qator raqami bilan chaqiriladi (oyna allaqachon yopilgan); null bo'lsa, ishlatilmaydi.
     * @return menyu ochilgan bo'lsa true.
     */
    fun show(
        title: String,
        labels: List<String>,
        onPick: (Int) -> Unit,
        onCancel: () -> Unit,
        onLongPick: ((Int) -> Unit)? = null,
    ): Boolean {
        dismiss()
        return try {
            val themed = ContextThemeWrapper(service, R.style.Theme_Reader)
            val d = AlertDialog.Builder(themed)
                .setTitle(title)
                .setItems(labels.toTypedArray()) { dlg, which ->
                    dlg.dismiss()
                    onPick(which)
                }
                .setNegativeButton(R.string.btn_close) { dlg, _ -> dlg.cancel() }
                .setOnCancelListener { onCancel() }
                .create()
            d.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            d.setOnDismissListener { if (dialog === d) dialog = null }
            d.show()
            dialog = d
            if (onLongPick != null) {
                d.listView?.setOnItemLongClickListener { _, _, position, _ ->
                    d.dismiss()
                    onLongPick(position)
                    true
                }
            }
            true
        } catch (_: Throwable) {
            dialog = null
            false
        }
    }

    fun dismiss() {
        val d = dialog
        dialog = null
        try {
            d?.dismiss()
        } catch (ex: Throwable) {
            swallowed("MenuOverlay.dismiss", ex)
        }
    }
}
