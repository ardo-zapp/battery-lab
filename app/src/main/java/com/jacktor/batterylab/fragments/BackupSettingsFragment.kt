package com.jacktor.batterylab.fragments

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.jacktor.batterylab.R
import com.jacktor.batterylab.helpers.HistoryHelper
import com.jacktor.batterylab.interfaces.BackupSettingsInterface
import com.jacktor.batterylab.utilities.Constants


class BackupSettingsFragment : PreferenceFragmentCompat(), BackupSettingsInterface {

    private lateinit var getResult: ActivityResultLauncher<Intent>

    private var exportSettings: Preference? = null
    private var importSettings: Preference? = null
    private var exportHistory: Preference? = null
    private var importHistory: Preference? = null
    private var pref: SharedPreferences? = null

    private var requestCode = 0


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        addPreferencesFromResource(R.xml.backup_settings)


        getResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            when (requestCode) {

                Constants.EXPORT_SETTINGS_REQUEST_CODE ->
                    if (it.resultCode == Activity.RESULT_OK) onExportSettings(it.data)

                Constants.IMPORT_SETTINGS_REQUEST_CODE ->
                    if (it.resultCode == Activity.RESULT_OK) onImportSettings(it.data?.data)

                Constants.EXPORT_HISTORY_REQUEST_CODE ->
                    if (it.resultCode == Activity.RESULT_OK) onExportHistory(it.data)

                Constants.IMPORT_HISTORY_REQUEST_CODE ->
                    if (it.resultCode == Activity.RESULT_OK) onImportHistory(
                        it.data?.data,
                        exportHistory
                    )

                Constants.OPEN_DOCUMENT_TREE_REQUEST_CODE ->
                    if (it.resultCode == Activity.RESULT_OK) {
                        requireContext().contentResolver.takePersistableUriPermission(
                            it.data?.data!!,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        pref?.edit { putString("filestorageuri", it.data?.data!!.toString()) }
                    }
            }
        }

        exportSettings = findPreference("export_settings")

        importSettings = findPreference("import_settings")

        exportHistory = findPreference("export_history")

        importHistory = findPreference("import_history")

        exportSettings?.setOnPreferenceClickListener {

            try {
                requestCode = Constants.EXPORT_SETTINGS_REQUEST_CODE
                getResult.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    requireContext(), getString(
                        R.string.error_exporting_settings,
                        e.message ?: e.toString()
                    ), Toast.LENGTH_LONG
                ).show()
            }

            true
        }

        importSettings?.setOnPreferenceClickListener {

            try {
                requestCode = Constants.IMPORT_SETTINGS_REQUEST_CODE
                getResult.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/xml"
                })
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    requireContext(), getString(
                        R.string.error_importing_settings,
                        e.message ?: e.toString()
                    ), Toast.LENGTH_LONG
                ).show()
            }

            true
        }

        exportHistory?.setOnPreferenceClickListener {

            try {
                requestCode = Constants.EXPORT_HISTORY_REQUEST_CODE
                getResult.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(requireContext(), e.message ?: e.toString(), Toast.LENGTH_LONG)
                    .show()
            }

            true
        }

        importHistory?.setOnPreferenceClickListener {

            try {
                requestCode = Constants.IMPORT_HISTORY_REQUEST_CODE
                getResult.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                })
            } catch (e: ActivityNotFoundException) {

                Toast.makeText(requireContext(), e.message ?: e.toString(), Toast.LENGTH_LONG)
                    .show()
            }

            true
        }

    }

    override fun onResume() {

        super.onResume()

        exportHistory?.isEnabled = HistoryHelper.isHistoryNotEmpty(requireContext())
    }


}