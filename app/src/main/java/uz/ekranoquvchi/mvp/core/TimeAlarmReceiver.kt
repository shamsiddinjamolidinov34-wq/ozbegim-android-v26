package uz.ekranoquvchi.mvp.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Tizim signali (AlarmManager) kelganda vaqt e'lonini ishga tushiradi: qurilma uxlayotgan bo'lsa ham uyg'otadi. */
class TimeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        TimeAnnouncer.onAlarm(context)
    }
}
