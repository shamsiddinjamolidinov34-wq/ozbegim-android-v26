package uz.ekranoquvchi.mvp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import uz.ekranoquvchi.mvp.R
import uz.ekranoquvchi.mvp.diag.Diag
import uz.ekranoquvchi.mvp.diag.swallowed
import uz.ekranoquvchi.mvp.tts.EngineChoice
import uz.ekranoquvchi.mvp.tts.TtsDefaultEngine
import uz.ekranoquvchi.mvp.util.Prefs
import uz.ekranoquvchi.mvp.util.SystemUtil
import java.util.Locale

/**
 * O'zbegim tizim nutq dvigateli sozlamalari. Android "Matnni nutqqa aylantirish" sozlamalarida dvigatel yonidagi
 * sozlamalar tugmasi shu sahifani ochadi (res/xml/tts_engine.xml). Bu yerda: holat, "afzal ko'rilgan mexanizm" qilish tugmasi,
 * ovozni beruvchi dvigatel tanlovi, O'zbegim qoidalarini yoqish/o'chirish, namunani eshitish va tezkor yoqish uchun ADB buyrug'i.
 */
class TtsEngineActivity : AppCompatActivity() {
    private lateinit var container: LinearLayout
    private var sampleTts: TextToSpeech? = null
    private var sampleGen = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        title = getString(R.string.tts_engine_settings_title)
        container = findViewById(R.id.container)
        Diag.init(this)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    override fun onDestroy() {
        sampleGen++
        try {
            sampleTts?.shutdown()
        } catch (ex: Throwable) {
            swallowed("TtsEngineActivity.onDestroy", ex)
        }
        sampleTts = null
        super.onDestroy()
    }

    private fun build() {
        container.removeAllViews()
        addText(getString(R.string.tts_act_intro))

        val backend = EngineChoice.backendPackage(this)
        val backendLabel = if (backend != null) EngineChoice.engineLabel(this, backend) else getString(R.string.tts_act_backend_none_short)
        val status = if (EngineChoice.isDefault(this)) {
            getString(R.string.tts_act_status_on, backendLabel)
        } else {
            val def = EngineChoice.systemDefault(this)
            val defLabel = if (def != null) EngineChoice.engineLabel(this, def) else getString(R.string.tts_act_unknown)
            getString(R.string.tts_act_status_off, defLabel)
        }
        addText(status)
        addButton(TtsEngineUi.buttonText(this)) { TtsEngineUi.press(this) { build() } }

        // Ovoz beruvchi dvigatel
        addText(getString(R.string.tts_act_backend_title))
        val engines = EngineChoice.installedEngines(this)
        if (engines.isEmpty()) addText(getString(R.string.tts_act_backend_none))
        val explicit = Prefs.ttsBackend(this)
        addButton(mark(explicit.isEmpty(), getString(R.string.tts_act_backend_auto))) { saveBackend("", "") }
        for (e in engines) {
            val label = EngineChoice.engineLabel(this, e)
            addButton(mark(explicit == e, getString(R.string.tts_act_backend_choose, label))) { saveBackend(e, label) }
        }
        addButton(getString(R.string.btn_voices)) { startActivity(Intent(this, VoicesActivity::class.java)) }

        // O'zbegim qoidalari
        val rulesOn = Prefs.ttsRules(this)
        addButton(getString(if (rulesOn) R.string.tts_act_rules_on else R.string.tts_act_rules_off)) {
            Prefs.get(this).edit().putBoolean(Prefs.TTS_RULES, !rulesOn).apply()
            build()
        }

        addButton(getString(R.string.tts_act_sample)) { playSample() }
        addButton(getString(R.string.btn_tts_settings)) { SystemUtil.openTtsSettings(this) }

        // Tezkor yoqish
        addText(getString(R.string.tts_act_adb_title))
        if (TtsDefaultEngine.canWriteDirectly(this)) {
            addText(getString(R.string.tts_act_adb_granted))
        } else {
            addText(getString(R.string.tts_act_adb_text))
            addText(TtsDefaultEngine.adbCommand(this))
            addButton(getString(R.string.tts_act_adb_copy)) { copyAdb() }
        }
    }

    private fun mark(selected: Boolean, text: String): String =
        if (selected) getString(R.string.tts_act_current_mark, text) else text

    private fun saveBackend(pkg: String, label: String) {
        Prefs.setTtsBackend(this, pkg)
        val msg = if (pkg.isEmpty()) getString(R.string.tts_act_backend_auto) else getString(R.string.tts_act_backend_saved, label)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        build()
    }

    private fun copyAdb() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("adb", TtsDefaultEngine.adbCommand(this)))
            Toast.makeText(this, R.string.tts_act_adb_copied, Toast.LENGTH_SHORT).show()
        } catch (ex: Throwable) {
            swallowed("TtsEngineActivity.copyAdb", ex)
        }
    }

    /** Namuna O'zbegim dvigateli orqali aytiladi: butun zanjir (tizim xizmati, matn qoidalari, orqa dvigatel) tekshiriladi. */
    private fun playSample() {
        sampleGen++
        val gen = sampleGen
        try {
            sampleTts?.shutdown()
        } catch (ex: Throwable) {
            swallowed("TtsEngineActivity.playSample", ex)
        }
        val listener = TextToSpeech.OnInitListener { status ->
            runOnUiThread { if (gen == sampleGen) onSampleReady(status) }
        }
        sampleTts = try {
            TextToSpeech(this, listener, packageName)
        } catch (_: Throwable) {
            null
        }
        if (sampleTts == null) Toast.makeText(this, R.string.tts_act_sample_failed, Toast.LENGTH_LONG).show()
    }

    private fun onSampleReady(status: Int) {
        val t = sampleTts ?: return
        if (status != TextToSpeech.SUCCESS) {
            Toast.makeText(this, R.string.tts_act_sample_failed, Toast.LENGTH_LONG).show()
            return
        }
        try {
            val r = t.setLanguage(Locale("uz", "UZ"))
            if (r < TextToSpeech.LANG_AVAILABLE) {
                Toast.makeText(this, R.string.tts_act_sample_failed, Toast.LENGTH_LONG).show()
                return
            }
            t.speak(getString(R.string.tts_sample_uz), TextToSpeech.QUEUE_FLUSH, null, "tts-engine-sample")
        } catch (_: Throwable) {
            Toast.makeText(this, R.string.tts_act_sample_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun addText(text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 17f
        tv.setPadding(0, 16, 0, 16)
        tv.isFocusable = true
        container.addView(tv)
    }

    private fun addButton(text: String, action: () -> Unit) {
        val b = Button(this)
        b.text = text
        b.setOnClickListener { action() }
        container.addView(b)
    }
}
