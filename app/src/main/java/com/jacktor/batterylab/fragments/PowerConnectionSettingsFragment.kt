package com.jacktor.batterylab.fragments

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.R

class PowerConnectionSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var pref: SharedPreferences

    private val preferencesMap = mutableMapOf<String, Preference?>()
    private var selectedFileType: FileType? = null

    private enum class FileType(val key: String) {
        AC("ac_connected_sound"),
        USB("usb_connected_sound"),
        DISCONNECTED("disconnected_sound")
    }

    // SAF picker
    private val openAudio = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val type = selectedFileType ?: return@registerForActivityResult

        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        pref.edit { putString(type.key, uri.toString()) }
        setSummaries()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        pref = PreferenceManager.getDefaultSharedPreferences(requireContext())
        addPreferencesFromResource(R.xml.power_connection_settings)

        // Map preferences
        preferencesMap["power_connection_service"] = findPreference("power_connection_service")
        preferencesMap[FileType.AC.key] = findPreference(FileType.AC.key)
        preferencesMap[FileType.USB.key] = findPreference(FileType.USB.key)
        preferencesMap[FileType.DISCONNECTED.key] = findPreference(FileType.DISCONNECTED.key)
        preferencesMap["reset_sound"] = findPreference("reset_sound")
        preferencesMap["sound_delay"] = findPreference("sound_delay")
        preferencesMap["enable_vibration"] = findPreference("enable_vibration")
        preferencesMap["vibrate_duration"] = findPreference("vibrate_duration")
        preferencesMap["custom_vibrate_duration"] = findPreference("custom_vibrate_duration")
        preferencesMap["vibrate_mode"] = findPreference("vibrate_mode")
        preferencesMap["enable_toast"] = findPreference("enable_toast")

        setupListeners()
        togglePreferences(pref.getBoolean("power_connection_service", false))
        setSummaries()
    }

    private fun setupListeners() {
        preferencesMap["power_connection_service"]?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as? Boolean ?: false
            togglePreferences(enabled)
            true
        }

        preferencesMap[FileType.AC.key]?.setOnPreferenceClickListener {
            handleFileSelection(FileType.AC); true
        }
        preferencesMap[FileType.USB.key]?.setOnPreferenceClickListener {
            handleFileSelection(FileType.USB); true
        }
        preferencesMap[FileType.DISCONNECTED.key]?.setOnPreferenceClickListener {
            handleFileSelection(FileType.DISCONNECTED); true
        }

        preferencesMap["reset_sound"]?.setOnPreferenceClickListener {
            resetSoundPreferences()
            setSummaries()
            true
        }
    }

    private fun handleFileSelection(fileType: FileType) {
        selectedFileType = fileType
        showFileChooser()
    }

    private fun showFileChooser() {
        openAudio.launch(arrayOf("audio/*"))
    }

    private fun togglePreferences(isEnabled: Boolean) {
        preferencesMap
            .filterKeys { it != "power_connection_service" }
            .values
            .forEach { it?.isEnabled = isEnabled }
    }

    private fun setSummaries() {
        FileType.entries.forEach { fileType ->
            val uriStr = pref.getString(fileType.key, "").orEmpty()
            val summary = if (uriStr.isNotEmpty()) {
                getDisplayNameFromUri(uriStr.toUri()) ?: uriStr
            } else {
                getString(R.string.sound_not_selected)
            }
            preferencesMap[fileType.key]?.summary = summary
        }
    }

    private fun resetSoundPreferences() {
        val cr = requireContext().contentResolver
        FileType.entries.forEach { fileType ->
            val uriStr = pref.getString(fileType.key, null) ?: return@forEach
            val uri = uriStr.toUri()

            cr.persistedUriPermissions.firstOrNull { it.uri == uri }?.let { perm ->
                var modeFlags = 0
                if (perm.isReadPermission) modeFlags =
                    modeFlags or Intent.FLAG_GRANT_READ_URI_PERMISSION
                if (perm.isWritePermission) modeFlags =
                    modeFlags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                if (modeFlags != 0) {
                    runCatching { cr.releasePersistableUriPermission(uri, modeFlags) }
                }
            }
        }
        pref.edit {
            FileType.entries.forEach { putString(it.key, "") }
        }
    }

    private fun getDisplayNameFromUri(uri: Uri): String? {
        return runCatching {
            requireContext().contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment
    }

    override fun onResume() {
        super.onResume()
        val enabled = pref.getBoolean("power_connection_service", false)
        togglePreferences(enabled)
        setSummaries()
    }
}
