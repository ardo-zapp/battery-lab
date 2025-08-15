package com.jacktor.batterylab.interfaces

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.MainActivity
import com.jacktor.batterylab.MainApp
import com.jacktor.batterylab.R
import com.jacktor.batterylab.fragments.BackupSettingsFragment
import com.jacktor.batterylab.helpers.HistoryHelper
import com.jacktor.batterylab.helpers.ServiceHelper
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.services.OverlayService
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.UPDATE_TEMP_SCREEN_TIME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

interface BackupSettingsInterface {

    fun BackupSettingsFragment.onExportSettings(intent: Intent?) {

        val prefPath = "${requireContext().filesDir?.parent}/shared_prefs/" +
                "${requireContext().packageName}_preferences.xml"
        val prefName = File(prefPath).name

        val pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

        //val tokenPref = pref.getString(TOKEN_PREF, null)

        CoroutineScope(Dispatchers.IO).launch {

            try {

                MainActivity.isOnBackPressed = false

                with(pref) {
                    apply {
                        if (contains(UPDATE_TEMP_SCREEN_TIME)) pref.edit {
                            remove(
                                UPDATE_TEMP_SCREEN_TIME
                            )
                        }
                        //if (contains(TOKEN_PREF)) remove(TOKEN_PREF)
                    }
                }

                val pickerDir = intent?.data?.let {
                    requireContext().let { it1 -> DocumentFile.fromTreeUri(it1, it) }
                }

                pickerDir?.findFile(prefName)?.delete()

                val outputStream = pickerDir?.createFile(
                    "text/xml",
                    prefName
                )?.uri?.let {
                    requireContext().contentResolver?.openOutputStream(it)
                }

                val fileInputStream = FileInputStream(prefPath)
                val buffer = byteArrayOf((1024 * 8).toByte())
                var read: Int

                while (true) {

                    read = fileInputStream.read(buffer)

                    if (read != -1)
                        outputStream?.write(buffer, 0, read)
                    else break
                }

                fileInputStream.close()
                outputStream?.flush()
                outputStream?.close()

                withContext(Dispatchers.Main) {

                    MainActivity.isOnBackPressed = true

                    Toast.makeText(
                        requireContext(),
                        getString(R.string.successful_export_of_settings, prefName),
                        Toast.LENGTH_LONG
                    ).show()

                    //pref.setString(TOKEN_PREF, tokenPref)
                }
            } catch (e: Exception) {

                withContext(Dispatchers.Main) {

                    MainActivity.isOnBackPressed = true

                    Toast.makeText(
                        requireContext(), getString(
                            R.string.error_exporting_settings,
                            e.message ?: e.toString()
                        ), Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun BackupSettingsFragment.onImportSettings(uri: Uri?) {

        val prefPath = "${requireContext().filesDir?.parent}/shared_prefs/" +
                "${requireContext().packageName}_preferences.xml"

        CoroutineScope(Dispatchers.IO).launch {

            try {

                MainActivity.isOnBackPressed = false

                withContext(Dispatchers.Main) {

                    Toast.makeText(
                        requireContext(), R.string.import_settings_3dots,
                        Toast.LENGTH_LONG
                    ).show()

                    if (BatteryLabService.instance != null)
                        ServiceHelper.stopService(requireContext(), BatteryLabService::class.java)

                    if (OverlayService.instance != null)
                        ServiceHelper.stopService(requireContext(), OverlayService::class.java)
                }

                val pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

                val prefArrays: HashMap<String, Any?> = hashMapOf()

                pref.all.forEach {
                    when (it.key) {

                        PreferencesKeys.BATTERY_LEVEL_TO, PreferencesKeys.BATTERY_LEVEL_WITH,
                        PreferencesKeys.DESIGN_CAPACITY, PreferencesKeys.CAPACITY_ADDED,
                        PreferencesKeys.LAST_CHARGE_TIME, PreferencesKeys.PERCENT_ADDED,
                        PreferencesKeys.RESIDUAL_CAPACITY ->
                            prefArrays[it.key] = it.value
                    }
                }

                delay(2.seconds)
                if (File(prefPath).exists()) File(prefPath).delete()

                File(prefPath).createNewFile()

                val fileOutputStream = FileOutputStream(prefPath)
                val inputStream = uri?.let {
                    requireContext().contentResolver?.openInputStream(it)
                }

                val buffer = byteArrayOf((1024 * 8).toByte())
                var read: Int

                while (true) {

                    read = inputStream?.read(buffer) ?: -1

                    if (read != -1)
                        fileOutputStream.write(buffer, 0, read)
                    else break
                }

                inputStream?.close()
                fileOutputStream.flush()
                fileOutputStream.close()

                withContext(Dispatchers.Main) {

                    MainActivity.isOnBackPressed = true

                    MainApp.restartApp(requireContext(), prefArrays)
                }
            } catch (e: Exception) {

                withContext(Dispatchers.Main) {

                    MainActivity.isOnBackPressed = true

                    Toast.makeText(
                        requireContext(), getString(
                            R.string.error_importing_settings, e.message ?: e.toString()
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun BackupSettingsFragment.onExportHistory(intent: Intent?) {

        val dbPath = "${requireContext().filesDir?.parent}/databases/History.db"
        val dbName = "History.db"

        CoroutineScope(Dispatchers.IO).launch {

            try {

                if (HistoryHelper.isHistoryEmpty(requireContext()))
                    throw IOException(getString(R.string.history_is_empty))

                MainActivity.isOnBackPressed = false

                val pickerDir = intent?.data?.let {
                    requireContext().let { it1 -> DocumentFile.fromTreeUri(it1, it) }
                }

                delay(1000L)
                pickerDir?.findFile(dbName)?.delete()
                val outputStream = pickerDir?.createFile(
                    "application/vnd.sqlite3",
                    dbName
                )?.uri?.let {
                    requireContext().contentResolver?.openOutputStream(it)
                }

                val fileInputStream = FileInputStream(dbPath)
                val buffer = byteArrayOf((1024 * 8).toByte())
                var read: Int

                while (true) {

                    read = fileInputStream.read(buffer)

                    if (read != -1)
                        outputStream?.write(buffer, 0, read)
                    else break
                }

                fileInputStream.close()
                outputStream?.flush()
                outputStream?.close()

                withContext(Dispatchers.Main) {

                    Toast.makeText(
                        requireContext(), getString(
                            R.string.history_exported_successfully
                        ), Toast.LENGTH_LONG
                    ).show()
                }

                MainActivity.isOnBackPressed = true
            } catch (e: java.lang.Exception) {

                withContext(Dispatchers.Main) {

                    MainActivity.isOnBackPressed = true

                    Toast.makeText(
                        requireContext(), "${
                            getString(
                                R.string
                                    .error_exporting_history
                            )
                        }\n${e.message ?: e.toString()}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun BackupSettingsFragment.onImportHistory(uri: Uri?, exportHistoryPref: Preference?) {

        val dbPath = "${requireContext().filesDir?.parent}/databases/History.db"

        CoroutineScope(Dispatchers.IO).launch {

            try {

                MainActivity.isOnBackPressed = false

                delay(1.seconds)
                File(dbPath).deleteOnExit()
                File("$dbPath-journal").deleteOnExit()

                File(dbPath).createNewFile()

                val fileOutputStream = FileOutputStream(dbPath)
                val inputStream = uri?.let {
                    requireContext().contentResolver?.openInputStream(it)
                }

                val buffer = byteArrayOf((1024 * 8).toByte())
                var read: Int

                while (true) {

                    read = inputStream?.read(buffer) ?: -1

                    if (read != -1)
                        fileOutputStream.write(buffer, 0, read)
                    else break
                }

                inputStream?.close()
                fileOutputStream.flush()
                fileOutputStream.close()

                MainActivity.isOnBackPressed = true

                val isHistoryNotEmpty = HistoryHelper.isHistoryNotEmpty(requireContext())

                withContext(Dispatchers.Main) {

                    exportHistoryPref?.isEnabled = isHistoryNotEmpty && !HistoryHelper
                        .isHistoryMax(requireContext())
                }

                if (!isHistoryNotEmpty)
                    throw IOException(getString(R.string.history_is_empty))
                else withContext(Dispatchers.Main) {

                    Toast.makeText(
                        requireContext(), getString(
                            R.string
                                .history_imported_successfully
                        ), Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: java.lang.Exception) {

                withContext(Dispatchers.Main) {

                    MainActivity.isOnBackPressed = true

                    Toast.makeText(
                        requireContext(), "${
                            getString(
                                R.string
                                    .error_importing_history
                            )
                        }\n${e.message ?: e.toString()}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}