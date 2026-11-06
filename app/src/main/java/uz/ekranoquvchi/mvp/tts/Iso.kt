package uz.ekranoquvchi.mvp.tts

import java.util.Locale
import uz.ekranoquvchi.mvp.diag.swallowed

/**
 * TextToSpeechService tizimdan til va davlatni ISO 639-2/3 uch harfli kodda oladi ("uzb", "UZB", "rus"),
 * TextToSpeech esa Locale bilan ishlaydi. Bu yordamchi ikkisini bir-biriga o'tkazadi.
 */
internal object Iso {
    private val langMap: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        for (code in Locale.getISOLanguages()) {
            try {
                m[Locale(code).getISO3Language().lowercase()] = code
            } catch (ex: Throwable) {
                swallowed("Iso.langMap", ex)
            }
        }
        m
    }

    private val countryMap: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        for (code in Locale.getISOCountries()) {
            try {
                m[Locale("", code).getISO3Country().uppercase()] = code
            } catch (ex: Throwable) {
                swallowed("Iso.countryMap", ex)
            }
        }
        m
    }

    /** Uch (yoki ikki) harfli kodlardan Locale. Til noma'lum bo'lsa null. */
    fun toLocale(lang: String?, country: String?, variant: String?): Locale? {
        val l = (lang ?: "").trim().lowercase()
        if (l.isEmpty()) return null
        val l2 = if (l.length == 2) l else (langMap[l] ?: return null)
        val c = (country ?: "").trim()
        val c2 = when {
            c.isEmpty() -> ""
            c.length == 2 -> c.uppercase()
            else -> countryMap[c.uppercase()] ?: ""
        }
        return Locale(l2, c2, (variant ?: "").trim())
    }

    fun iso3Language(locale: Locale): String = try {
        locale.getISO3Language()
    } catch (_: Throwable) {
        locale.language
    }

    fun iso3Country(locale: Locale): String = try {
        locale.getISO3Country()
    } catch (_: Throwable) {
        locale.country
    }
}
