package uz.ekranoquvchi.mvp.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import uz.ekranoquvchi.mvp.R
import uz.ekranoquvchi.mvp.ReaderService
import uz.ekranoquvchi.mvp.core.TimeAnnouncer
import uz.ekranoquvchi.mvp.diag.swallowed
import uz.ekranoquvchi.mvp.util.Prefs
import java.util.Locale

/**
 * Sozlamalar > Tafsilotlar > Vaqt va batareya: soatni har 30 yoki 60 daqiqada aytish (12 yoki 24 soatlik, ikki o'qish uslubidan
 * biri), o'qish paytida kelsa nima qilish, batareya quvvatini har 10 foizda yoki o'zim belgilagan qadamda aytish.
 */
class TimeBatteryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.tb_title)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, TimeBatteryFragment())
                .commit()
        }
    }
}

class TimeBatteryFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = requireContext()
        // Sozlamalar xizmat bilan bir xil (qurilma-himoyalangan) faylga yoziladi
        preferenceManager.preferenceDataStore = SharedPrefsStore(ctx.applicationContext, Prefs.get(ctx))
        setPreferencesFromResource(R.xml.preferences_time_battery, rootKey)

        // O'qish uslubi o'zbekcha so'zlar bilan o'qiladigan (tizim tili o'zbekcha) holatda ko'rsatiladi
        findPreference<Preference>(Prefs.TIME_STYLE)?.isVisible = Locale.getDefault().language == "uz"

        findPreference<Preference>("time_test")?.setOnPreferenceClickListener {
            TimeAnnouncer.speakTimeNow(requireContext())
            true
        }
        findPreference<Preference>("battery_test")?.setOnPreferenceClickListener {
            TimeAnnouncer.speakBatteryNow(requireContext())
            true
        }
        findPreference<Preference>("time_exact_alarm")?.setOnPreferenceClickListener {
            openExactAlarmSettings()
            true
        }
        // Surgich ekran o'quvchi bilan qiyin bo'lishi mumkin: aniq son yozish oynasi ham bor (nutq tezligidagi kabi)
        findPreference<Preference>("battery_step_exact")?.setOnPreferenceClickListener {
            NumberDialog.show(
                requireContext(), R.string.tb_battery_step, Prefs.BATTERY_STEP, 1..50, 10,
                onSaved = { enableCustomBattery(Prefs.batteryStep(requireContext())) }
            ) {
                refreshStep()
            }
            true
        }
        // Surgich: qadam o'zgarishi bilan e'lon ham yoqiladi (ilgari alohida "O'zim belgilagan foiz qadamida" tanlanmasa ishlamas edi)
        findPreference<SeekBarPreference>(Prefs.BATTERY_STEP)?.setOnPreferenceChangeListener { _, newValue ->
            enableCustomBattery((newValue as? Int) ?: Prefs.batteryStep(requireContext()))
            true
        }
        refreshStep()
    }

    /**
     * Foydalanuvchi qadamni belgilasa, batareya e'loni "o'zim belgilagan foiz qadamida" rejimiga o'tadi va bu aytiladi.
     * Ilgari qadam yozilsa ham rejim "O'chiq" yoki "Har 10 foiz" qolib ketar, shu sabab 2 foiz ishlamas edi.
     */
    private fun enableCustomBattery(step: Int) {
        val ctx = context ?: return
        if (Prefs.batteryMode(ctx) == "custom") return
        val list = findPreference<ListPreference>(Prefs.BATTERY_MODE)
        if (list != null) {
            list.value = "custom"
        } else {
            Prefs.get(ctx).edit().putString(Prefs.BATTERY_MODE, "custom").apply()
        }
        ReaderService.instance?.announce(getString(R.string.tb_battery_custom_on, step))
    }

    override fun onResume() {
        super.onResume()
        refreshExactAlarm()
        refreshStep()
    }

    private fun refreshStep() {
        val step = Prefs.batteryStep(requireContext())
        findPreference<SeekBarPreference>(Prefs.BATTERY_STEP)?.value = step
        findPreference<Preference>("battery_step_exact")?.summary = getString(R.string.tb_battery_step_exact_summary, step)
    }

    private fun refreshExactAlarm() {
        val p = findPreference<Preference>("time_exact_alarm") ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            p.isVisible = false
            return
        }
        val am = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        p.summary = getString(if (am.canScheduleExactAlarms()) R.string.tb_exact_on else R.string.tb_exact_off)
    }

    private fun openExactAlarmSettings() {
        val uri = Uri.parse("package:" + requireContext().packageName)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, uri))
                return
            }
        } catch (ex: Throwable) {
            swallowed("TimeBatteryActivity.openExactAlarmSettings", ex)
        }
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
        } catch (ex: Throwable) {
            swallowed("TimeBatteryActivity.openExactAlarmSettings", ex)
        }
    }
}
