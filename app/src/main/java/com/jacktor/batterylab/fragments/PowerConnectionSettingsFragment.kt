package com.jacktor.batterylab.fragments

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.preference.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jacktor.batterylab.R
import com.jacktor.batterylab.utilities.FileUtils
import com.jacktor.batterylab.utilities.preferences.Prefs

class PowerConnectionSettingsFragment() : PreferenceFragmentCompat() {

    private lateinit var pref: Prefs

    // Preferences
    private val preferencesMap = mutableMapOf<String, Preference?>()

    private var selectedFileType: FileType? = null

    private enum class FileType(val key: String) {
        AC("ac_connected_sound"),
        USB("usb_connected_sound"),
        DISCONNECTED("disconnected_sound")
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showFileChooser()
        } else {
            val messageRes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                R.string.audio_permission_not_granted
            } else {
                R.string.storage_permission_not_granted
            }
            showPermissionBlockedDialog(messageRes)
        }
    }

    private val selectFileActivityResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri =
                result.data?.data?.let { FileUtils.getRealPath(requireContext(), it)?.toUri() }
            uri?.let {
                selectedFileType?.let { fileType ->
                    pref.setString(fileType.key, it.toString())
                }
                setSummaries()
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        pref = Prefs(requireContext())
        addPreferencesFromResource(R.xml.power_connection_settings)

        // Initialize preferences map
        preferencesMap["enablePowerConnection"] = findPreference("power_connection_service")
        preferencesMap[FileType.AC.key] = findPreference(FileType.AC.key)
        preferencesMap[FileType.USB.key] = findPreference(FileType.USB.key)
        preferencesMap[FileType.DISCONNECTED.key] = findPreference(FileType.DISCONNECTED.key)
        preferencesMap["resetSound"] = findPreference("reset_sound")
        preferencesMap["soundDelay"] = findPreference("sound_delay")
        preferencesMap["enableVibration"] = findPreference("enable_vibration")
        preferencesMap["vibrationDuration"] = findPreference("vibrate_duration")
        preferencesMap["customVibrationDuration"] = findPreference("custom_vibrate_duration")
        preferencesMap["vibrationMode"] = findPreference("vibrate_mode")
        preferencesMap["showToast"] = findPreference("enable_toast")

        setupListeners()
        togglePreferences(pref.getBoolean("power_connection_service", false))
        setSummaries()
    }

    private fun setupListeners() {
        preferencesMap["enablePowerConnection"]?.setOnPreferenceChangeListener { _, newValue ->
            val isEnabled = newValue as? Boolean == true
            togglePreferences(isEnabled)
            true
        }

        preferencesMap[FileType.AC.key]?.setOnPreferenceClickListener {
            handleFileSelection(FileType.AC)
            true
        }

        preferencesMap[FileType.USB.key]?.setOnPreferenceClickListener {
            handleFileSelection(FileType.USB)
            true
        }

        preferencesMap[FileType.DISCONNECTED.key]?.setOnPreferenceClickListener {
            handleFileSelection(FileType.DISCONNECTED)
            true
        }

        preferencesMap["resetSound"]?.setOnPreferenceClickListener {
            resetSoundPreferences()
            setSummaries()
            true
        }
    }

    private fun handleFileSelection(fileType: FileType) {
        selectedFileType = fileType
        requestPermissionAndBrowseFile()
    }

    private fun requestPermissionAndBrowseFile() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) == PackageManager.PERMISSION_GRANTED -> {
                showFileChooser()
            }

            shouldShowRequestPermissionRationale(permission) -> {
                showPermissionBlockedDialog(R.string.storage_permission_not_granted)
            }

            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        selectFileActivityResult.launch(intent)
    }

    private fun togglePreferences(isEnabled: Boolean) {
        preferencesMap.filterKeys { it != "enablePowerConnection" }.values.forEach {
            it?.isEnabled = isEnabled
        }
    }

    private fun setSummaries() {
        FileType.entries.forEach { fileType ->
            val filePath = pref.getString(fileType.key, "")
            val summary =
                filePath?.takeIf { it.isNotEmpty() } ?: getString(R.string.sound_not_selected)
            preferencesMap[fileType.key]?.summary = summary
        }
    }

    private fun resetSoundPreferences() {
        FileType.entries.forEach { fileType ->
            pref.setString(fileType.key, "")
        }
    }

    private fun showPermissionBlockedDialog(messageRes: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(messageRes)
            .setNegativeButton(R.string.open_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    data = Uri.fromParts("package", requireActivity().packageName, null)
                })
            }
            .setPositiveButton(R.string.dialog_button_close) { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        val isPowerConnectionEnabled = pref.getBoolean("power_connection_service", false)
        togglePreferences(isPowerConnectionEnabled)
        setSummaries() // Update summary
    }
}
