package uz.ekranoquvchi.mvp.ui

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import uz.ekranoquvchi.mvp.R
import uz.ekranoquvchi.mvp.content.ContentPipeline
import uz.ekranoquvchi.mvp.content.EngineProfiles
import uz.ekranoquvchi.mvp.diag.Diag
import uz.ekranoquvchi.mvp.diag.swallowed
import uz.ekranoquvchi.mvp.tts.EngineChoice
import uz.ekranoquvchi.mvp.util.Prefs
import uz.ekranoquvchi.mvp.util.SystemUtil

/**
 * Ovoz tanlash. Telefonda o'rnatilgan har qanday nutq dvigatelining (RHVoice, Google, Samsung va boshqalar) yuklab
 * olingan ovoz paketlaridan foydalanish mumkin: dvigatel tanlanadi, uning ovozlari ro'yxatda chiqadi (avval o'zbekcha,
 * xohlansa hamma tillar). Tanlangan ovoz kirill va lotinni qanday o'qishi ham shu yerda belgilanadi: matn baribir
 * O'zbegimning o'z qoidalari bo'yicha tayyorlanadi (raqamlar, Rim raqamlari, telefon raqamlari, yangi lotin, til ko'prigi),
 * faqat kirill lotinga o'tkaziladimi yoki o'zgarishsiz qoladimi, ovoz imkoniyatiga bog'liq.
 * RHVoice ovozlari (Islom, Dilnavoz, Sevinch) ilovaga qo'shilmagan (litsenziya: CC BY-NC-SA 4.0); ular RHVoice orqali o'rnatiladi.
 */
class VoicesActivity : AppCompatActivity() {
    private lateinit var container: LinearLayout

    /** Dvigatellar va ovozlar ro'yxati shu yerda quriladi: dvigatel almashganda faqat shu qism yangilanadi. */
    private lateinit var voicesBox: LinearLayout
    private var tts: TextToSpeech? = null
    private var engineName: String? = null
    private var showAll = false
    private var engineGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        title = getString(R.string.btn_voices)
        container = findViewById(R.id.container)
        Diag.init(this)

        addText(getString(R.string.voices_intro))
        addText(getString(R.string.voices_license))
        addButton(getString(R.string.btn_install_rhvoice)) { SystemUtil.openStorePage(this, EngineProfiles.RHVOICE) }
        addButton(getString(R.string.btn_open_rhvoice)) { launchRhvoice() }
        addButton(getString(R.string.btn_tts_settings)) { SystemUtil.openTtsSettings(this) }
        addButton(getString(R.string.pref_tts_engine)) { startActivity(Intent(this, TtsEngineActivity::class.java)) }
        addButton(getString(R.string.btn_default_voice)) { saveVoice("", null) }
        addScriptSection()
        voicesBox = LinearLayout(this)
        voicesBox.orientation = LinearLayout.VERTICAL
        container.addView(voicesBox)
        addAzureSection()
        startTts(initialListEngine())
    }

    /** Tanlangan ovoz paketi qaysi yozuvni o'qiy oladi: matn shunga qarab tayyorlanadi (kirill lotinga o'tkaziladimi). */
    private fun addScriptSection() {
        addText(getString(R.string.voice_reads_intro))
        addButton(getString(R.string.voice_reads_auto)) { saveReads("auto") }
        addButton(getString(R.string.voice_reads_latin)) { saveReads("latin") }
        addButton(getString(R.string.voice_reads_both)) { saveReads("both") }
    }

    private fun saveReads(value: String) {
        Prefs.get(this).edit().putString(Prefs.VOICE_READS, value).apply()
        Toast.makeText(this, R.string.voice_saved, Toast.LENGTH_SHORT).show()
    }

    /** Microsoft Azure ovozlari (onlayn): Madina, Sardor, shuningdek ruscha va inglizcha. Tanlansa, dvigatel "azure" bo'ladi. */
    private fun addAzureSection() {
        addText(getString(R.string.azure_voices_intro))
        val labels = resources.getStringArray(R.array.azure_voice_entries)
        val ids = resources.getStringArray(R.array.azure_voice_values)
        for (i in ids.indices) {
            val id = ids[i]
            val label = if (i < labels.size) labels[i] else id
            addButton(getString(R.string.voice_choose, label)) { saveAzureVoice(id, label) }
        }
        addButton(getString(R.string.btn_back_to_local)) { backToLocal() }
    }

    private fun saveAzureVoice(id: String, label: String) {
        Prefs.get(this).edit()
            .putString(Prefs.AZURE_VOICE, id)
            .putString(Prefs.ENGINE, EngineProfiles.AZURE)
            .apply()
        Toast.makeText(this, getString(R.string.azure_voice_saved, label), Toast.LENGTH_LONG).show()
    }

    private fun backToLocal() {
        Prefs.get(this).edit().putString(Prefs.ENGINE, "auto").apply()
        Toast.makeText(this, R.string.local_voice_restored, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        engineGeneration++
        try {
            tts?.shutdown()
        } catch (ex: Throwable) {
            swallowed("VoicesActivity.onDestroy", ex)
        }
        tts = null
        super.onDestroy()
    }

    private fun addText(text: String, parent: LinearLayout = container) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 17f
        tv.setPadding(0, 16, 0, 16)
        tv.isFocusable = true
        parent.addView(tv)
    }

    private fun addButton(text: String, parent: LinearLayout = container, action: () -> Unit) {
        val b = Button(this)
        b.text = text
        b.setOnClickListener { action() }
        parent.addView(b)
    }

    private fun launchRhvoice() {
        val intent = packageManager.getLaunchIntentForPackage(EngineProfiles.RHVOICE)
        if (intent == null) {
            Toast.makeText(this, R.string.rhvoice_missing, Toast.LENGTH_LONG).show()
        } else {
            startActivity(intent)
        }
    }

    /** Ovozlar ro'yxati qaysi dvigateldan boshlanadi: hozir tanlangani (RHVoice yoki boshqa o'rnatilgan dvigatel), aks holda tizim standarti. */
    private fun initialListEngine(): String? {
        val chosen = when (Prefs.engine(this)) {
            "rhvoice" -> if (SystemUtil.isInstalled(this, EngineProfiles.RHVOICE)) EngineProfiles.RHVOICE else null
            "custom" -> Prefs.enginePackage(this).takeIf { it.isNotEmpty() && it != packageName && SystemUtil.isInstalled(this, it) }
            else -> null
        }
        if (chosen != null) return chosen
        // O'zbegimning o'z dvigateli tizim standarti bo'lsa, ro'yxat uning ortidagi haqiqiy dvigateldan boshlanadi
        return if (EngineChoice.isDefault(this)) EngineChoice.backendPackage(this) else null
    }

    /** engine == null: tizim standart dvigateli. Dvigatel almashganda eski ro'yxat tozalanib, yangisi quriladi. */
    private fun startTts(engine: String?) {
        val gen = ++engineGeneration
        try {
            tts?.shutdown()
        } catch (ex: Throwable) {
            swallowed("VoicesActivity.startTts", ex)
        }
        tts = null
        engineName = engine
        voicesBox.removeAllViews()
        val listener = TextToSpeech.OnInitListener { status ->
            runOnUiThread { if (gen == engineGeneration) showVoices(status) }
        }
        tts = try {
            if (engine == null) TextToSpeech(this, listener) else TextToSpeech(this, listener, engine)
        } catch (_: Throwable) {
            null
        }
        if (tts == null) addText(getString(R.string.health_tts_failed), voicesBox)
    }

    private fun showVoices(status: Int) {
        val t = tts
        voicesBox.removeAllViews()
        if (status != TextToSpeech.SUCCESS || t == null) {
            addText(getString(R.string.health_tts_failed), voicesBox)
            return
        }
        if (engineName == null) engineName = t.defaultEngine
        val engines: List<TextToSpeech.EngineInfo> = try {
            t.engines.orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
        val currentLabel = engines.firstOrNull { it.name == engineName }?.label ?: engineName.orEmpty()
        addText(getString(R.string.voices_engine_now, currentLabel), voicesBox)

        // Telefonda o'rnatilgan boshqa dvigatellar (yuklab olingan ovoz paketlari)
        val others = engines.filter { it.name != engineName && it.name != packageName }
        if (others.isNotEmpty()) addText(getString(R.string.voices_engines_intro), voicesBox)
        for (e in others) {
            val engine = e.name
            addButton(getString(R.string.engine_choose, e.label ?: engine), voicesBox) { startTts(engine) }
        }

        val all: List<Voice> = try {
            t.voices?.toList().orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
        val shown = if (showAll) {
            all.sortedBy { it.name }
        } else {
            all.filter { it.locale.language == "uz" || it.locale.language == "uzb" }.sortedBy { it.name }
        }
        if (shown.isEmpty()) addText(getString(R.string.voices_none), voicesBox)
        for (v in shown) {
            addText(if (showAll) v.name + " (" + v.locale.toLanguageTag() + ")" else v.name, voicesBox)
            addButton(getString(R.string.voice_listen, v.name), voicesBox) { listen(v) }
            addButton(getString(R.string.voice_choose, v.name), voicesBox) { saveVoice(v.name, engineName, v.locale.language) }
        }
        addButton(getString(if (showAll) R.string.voices_show_uz else R.string.voices_show_all), voicesBox) {
            showAll = !showAll
            showVoices(TextToSpeech.SUCCESS)
        }
    }

    /**
     * Namuna ovoz. Matn amaldagi qoidalar bilan tayyorlanadi: "Ovoz yozuvi" sozlamasi "lotin va kirill" bo'lsa, namunadagi
     * kirill so'z o'zgarishsiz yuboriladi. Ovoz uni o'qiy olmasa, "faqat lotin"ni tanlang.
     */
    private fun listen(v: Voice) {
        val t = tts ?: return
        try {
            t.setVoice(v)
            val opts = Prefs.pipelineOptions(this, Prefs.engineReadsCyrillic(this, engineName))
            val sample = ContentPipeline.process(getString(R.string.voice_sample), opts).text
            t.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "sample")
        } catch (ex: Throwable) {
            swallowed("VoicesActivity.listen", ex)
        }
    }

    /** Tizimning standart dvigateli nomi (tanlangan ovoz shunisidan bo'lsa, dvigatel "system" deb saqlanadi). */
    private fun systemDefaultEngine(): String? = try {
        tts?.defaultEngine
    } catch (_: Throwable) {
        null
    }

    private fun saveVoice(name: String, engine: String?, lang: String = "") {
        val e = Prefs.get(this).edit().putString(Prefs.VOICE, name)
        if (name.isEmpty()) {
            e.remove(Prefs.VOICE_LANG).remove(Prefs.VOICE_LANG_FOR)
        } else {
            // Ovoz tili: Tafsilotlar > Matn tili "Avtomatik" bo'lsa, matn shu tilga qarab tayyorlanadi
            e.putString(Prefs.VOICE_LANG, lang).putString(Prefs.VOICE_LANG_FOR, name)
            // Ilgari "rhvoice" qolib ketsa, tizim dvigatelidan tanlangan ovoz hech qachon qo'llanmasdi.
            // Boshqa o'rnatilgan dvigatelning ovozi tanlansa, dvigatel "custom" bo'lib, paket nomi alohida saqlanadi.
            when {
                engine == EngineProfiles.RHVOICE -> e.putString(Prefs.ENGINE, "rhvoice")
                engine == null || engine == systemDefaultEngine() -> e.putString(Prefs.ENGINE, "system")
                else -> e.putString(Prefs.ENGINE, "custom").putString(Prefs.ENGINE_PACKAGE, engine)
            }
            // Yangi ovoz tanlansa, yozuv imkoniyati dvigatel profiliga qaytadi (kerak bo'lsa, yuqoridagi tugmalar bilan o'zgartiriladi)
            e.putString(Prefs.VOICE_READS, "auto")
        }
        e.apply()
        Toast.makeText(this, R.string.voice_saved, Toast.LENGTH_SHORT).show()
    }
}
