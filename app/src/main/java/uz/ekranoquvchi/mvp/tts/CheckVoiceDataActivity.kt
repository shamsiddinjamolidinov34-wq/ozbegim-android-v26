package uz.ekranoquvchi.mvp.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech

/**
 * Tizim sozlamalari dvigatelni tanlaganda "ovoz ma'lumotlari bormi" deb so'raydi (ACTION_CHECK_TTS_DATA).
 * O'zbegim yuklab olinadigan ma'lumot saqlamaydi (ovozni boshqa dvigatel beradi), shuning uchun o'zbekcha tilni
 * "bor" deb javob beradi; haqiqiy til tekshiruvi OzbegimTtsService.onIsLanguageAvailable orqali orqa dvigatelga yo'naltiriladi.
 */
class CheckVoiceDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val available = ArrayList<String>()
        available.add("uzb-UZB")
        val result = Intent()
        result.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, available)
        result.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, ArrayList<String>())
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, result)
        finish()
    }
}
