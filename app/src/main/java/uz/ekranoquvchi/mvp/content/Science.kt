package uz.ekranoquvchi.mvp.content

/**
 * Fan qisqartmalari guruhlari. Har biri Tafsilotlarda alohida yoqiladi va o'chiriladi:
 *  - UNITS: o'lchov birliklari (uzunlik, og'irlik, hajm, vaqt, harorat): kg, km, sm, l, min, km/h, °C;
 *  - PHYSICS: fizik birliklar: N, J, W, V, A, Ω, Hz, Pa, K, T, eV, kWh;
 *  - MATH: matematik belgilar: + = × ÷ − ≠ ≈ ≤ ≥ √ π ∞, daraja (x²), yunon harflari, sin, cos, log;
 *  - INFO: informatika: KB, MB, GB, TB, Mbit/s, px.
 * Yangi fan qo'shish: shu enum'ga qiymat, `ScienceOptions`ga bayroq, lug'atga yozuvlar, Prefs va Tafsilotlarga tugma.
 */
enum class Science { UNITS, PHYSICS, MATH, INFO }

/**
 * Fan qisqartmalarini o'qish sozlamalari. Yoqilgan fanning qisqartmasi o'zbekcha so'z bilan o'qiladi
 * ("5 kg" -> "besh kilogramm"). O'chirilgan fanning qisqartmasi aytilmaydi ("5 kg" -> "besh"), emoji sozlamasi kabi.
 */
data class ScienceOptions(
    val units: Boolean = true,
    val physics: Boolean = true,
    val math: Boolean = true,
    val info: Boolean = true,
) {
    fun enabled(s: Science): Boolean = when (s) {
        Science.UNITS -> units
        Science.PHYSICS -> physics
        Science.MATH -> math
        Science.INFO -> info
    }
}
