package uz.ekranoquvchi.mvp.ui

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import uz.ekranoquvchi.mvp.R
import uz.ekranoquvchi.mvp.core.GestureAction
import uz.ekranoquvchi.mvp.core.GlobalMenu
import uz.ekranoquvchi.mvp.core.displayName
import uz.ekranoquvchi.mvp.util.Prefs

/**
 * O'zbegim menyusini sozlash: menyuda (pastga, so'ng o'ngga surish) qaysi bandlar ko'rinishini tanlash. Belgilangan
 * bandlar menyuda chiqadi; belgisi olib tashlansa, band menyudan yo'qoladi. Har bir o'zgarish darhol saqlanadi.
 * Bandlar menyudagi hozirgi tartibda ko'rsatiladi (avval menyudagilar, so'ng qolganlari); belgi qo'yish yoki olib tashlash
 * tartibni buzmaydi: yangi band oxiriga qo'shiladi. Tartibni o'zgartirish menyuning o'zida (oxirgi band yoki bandni uzoq bosish).
 * "Standart ro'yxatga qaytarish" hamma narsani dastlabki holatga keltiradi.
 */
class MenuActivity : AppCompatActivity() {
    private lateinit var container: LinearLayout
    private val boxes = LinkedHashMap<GestureAction, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        title = getString(R.string.menu_editor_title)
        container = findViewById(R.id.container)
        render()
    }

    private fun addText(text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 17f
        tv.setPadding(0, 16, 0, 8)
        tv.isFocusable = true
        container.addView(tv)
    }

    private fun render() {
        container.removeAllViews()
        boxes.clear()

        addText(getString(R.string.menu_editor_intro))
        addText(getString(R.string.menu_editor_order_hint))

        val reset = Button(this)
        reset.setText(R.string.menu_editor_reset)
        reset.setOnClickListener {
            Prefs.saveMenuItems(this, null)
            render()
        }
        container.addView(reset)

        val current = GlobalMenu.effective(this)
        val selected = current.toSet()
        val ordered = current + GlobalMenu.pool.filter { it !in selected }
        for (a in ordered) {
            val box = CheckBox(this)
            box.text = a.displayName(this)
            // Holat avval o'rnatiladi, tinglovchi keyin: render paytida keraksiz saqlash bo'lmasin
            box.isChecked = a in selected
            box.setOnCheckedChangeListener { _, _ -> save() }
            boxes[a] = box
            container.addView(box)
        }
    }

    /** Belgilanganlar menyudagi hozirgi tartibini saqlaydi; yangi belgilanganlar oxiriga qo'shiladi. */
    private fun save() {
        val checked = boxes.filter { it.value.isChecked }.keys
        val current = GlobalMenu.effective(this)
        val kept = current.filter { it in checked }
        val added = GlobalMenu.pool.filter { it in checked && it !in current }
        Prefs.saveMenuItems(this, kept + added)
    }
}
