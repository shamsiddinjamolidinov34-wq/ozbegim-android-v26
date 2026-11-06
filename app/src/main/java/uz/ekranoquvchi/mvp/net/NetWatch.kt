package uz.ekranoquvchi.mvp.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import uz.ekranoquvchi.mvp.diag.swallowed

/**
 * Tarmoq qayta paydo bo'lishini kuzatadi. Wi-Fi yoki mobil internet qaytganda onlayn ovoz va avtomatik tarjimadagi
 * "to'xtash-kutish" (xatodan keyingi bir necha soniya yoki daqiqalik to'siq) darhol olib tashlanadi: internet uzilib
 * qayta yonsa, foydalanuvchi bekorga kutmaydi. Ruxsat yoki tizim xizmati bo'lmasa, to'siq oddiy vaqt bo'yicha o'tadi.
 */
object NetWatch {
    private val main = Handler(Looper.getMainLooper())
    private var callback: ConnectivityManager.NetworkCallback? = null

    /** Hozirgi asosiy tarmoq. Har tarmoqning o'z ulanishlar havzasi bor: tarmoq almashgach eski soketlar ishlatilmaydi. */
    @Volatile
    private var current: Network? = null

    /** URL'ni joriy tarmoq orqali ochadi; tarmoq noma'lum yoki ochilmasa, oddiy usulda. */
    fun openConnection(url: URL): HttpURLConnection {
        val net = current
        if (net != null) {
            try {
                return net.openConnection(url) as HttpURLConnection
            } catch (ex: Throwable) {
                swallowed("NetWatch.openConnection", ex)
            }
        }
        return url.openConnection() as HttpURLConnection
    }

    fun start(context: Context) {
        if (callback != null) return
        try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    if (current == network) current = null
                }

                override fun onAvailable(network: Network) {
                    current = network
                    main.post {
                        Online.noteSuccess()
                        AutoTranslator.resetBlock()
                    }
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            callback = cb
        } catch (ex: Throwable) {
            swallowed("NetWatch.start", ex)
        }
    }

    fun stop(context: Context) {
        val cb = callback ?: return
        callback = null
        current = null
        try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(cb)
        } catch (ex: Throwable) {
            swallowed("NetWatch.stop", ex)
        }
    }
}
