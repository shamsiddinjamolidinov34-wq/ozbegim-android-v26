package uz.ekranoquvchi.mvp.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import uz.ekranoquvchi.mvp.R

/** Tizim sozlamalaridagi "Namunani tinglash" uchun tilga mos namuna matn qaytaradi (ACTION_GET_SAMPLE_TEXT). */
class GetSampleTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lang = (intent?.getStringExtra("language") ?: "").lowercase()
        val sample = when {
            lang.startsWith("ru") -> getString(R.string.tts_sample_ru)
            lang.startsWith("en") -> getString(R.string.tts_sample_en)
            else -> getString(R.string.tts_sample_uz)
        }
        val result = Intent()
        result.putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, sample)
        setResult(TextToSpeech.LANG_AVAILABLE, result)
        finish()
    }
}
