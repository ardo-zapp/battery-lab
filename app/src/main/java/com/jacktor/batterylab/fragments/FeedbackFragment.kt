package com.jacktor.batterylab.fragments

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.MainApp.Companion.isGooglePlay
import com.jacktor.batterylab.R
import com.jacktor.batterylab.utilities.Constants.GOOGLE_PLAY_APP_LINK
//import com.jacktor.batterylab.utilities.Constants.TELEGRAM_DEVELOPER_LINK
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.FORCIBLY_SHOW_RATE_THE_APP

class FeedbackFragment : PreferenceFragmentCompat() {


    // Telegram
    //private var telegramDeveloper: Preference? = null

    // Other
    private var email: Preference? = null
    private var rateTheApp: Preference? = null
    private var shareTheApp: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        val pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

        addPreferencesFromResource(R.xml.feedback_settings)


        // Telegram
        /*telegramDeveloper = findPreference("telegram_developer")
        telegramDeveloper?.setOnPreferenceClickListener {

            try { startActivity(Intent(Intent.ACTION_VIEW, TELEGRAM_DEVELOPER_LINK.toUri())) }

            catch(_: ActivityNotFoundException) {

                val clipboardManager = requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newPlainText("telegram_developer",
                    TELEGRAM_DEVELOPER_LINK)
                clipboardManager.setPrimaryClip(clipData)
                Toast.makeText(requireContext(), R.string.telegram_link_copied,
                    Toast.LENGTH_LONG).show()
            }

            true
        } */

        // Other
        email = findPreference("email")

        rateTheApp = findPreference("rate_the_app")

        shareTheApp = findPreference("share_the_app")

        rateTheApp?.isVisible = isGooglePlay(requireContext()) || pref.getBoolean(
            FORCIBLY_SHOW_RATE_THE_APP, resources.getBoolean(
                R.bool.forcibly_show_rate_the_app
            )
        )

        email?.setOnPreferenceClickListener {

            try {

                val version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    requireContext().packageManager?.getPackageInfo(
                        requireContext().packageName,
                        PackageManager.PackageInfoFlags.of(0)
                    )?.versionName
                else {
                    requireContext().packageManager?.getPackageInfo(
                        requireContext().packageName,
                        0
                    )?.versionName
                }
                val build = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    requireContext().packageManager?.getPackageInfo(
                        requireContext().packageName,
                        PackageManager.PackageInfoFlags.of(0)
                    )?.let {
                        PackageInfoCompat.getLongVersionCode(it).toString()
                    }
                else {
                    requireContext().packageManager?.getPackageInfo(
                        requireContext().packageName,
                        0
                    )?.let { PackageInfoCompat.getLongVersionCode(it).toString() }
                }
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW, "mailto:${
                            email
                                ?.summary
                        }?subject=Battery Lab $version (Build $build). ${
                            requireContext().getString(
                                R.string.feedback
                            )
                        }".toUri()
                    )
                )
            } catch (_: ActivityNotFoundException) {

                val clipboardManager = requireContext().getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager
                val clipData = ClipData.newPlainText("email", email?.summary)
                clipboardManager.setPrimaryClip(clipData)
                Toast.makeText(
                    requireContext(), R.string.email_copied,
                    Toast.LENGTH_LONG
                ).show()
            }

            true
        }

        if (rateTheApp?.isVisible == true)
            rateTheApp?.setOnPreferenceClickListener {

                try {
                    requireContext().startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            GOOGLE_PLAY_APP_LINK.toUri()
                        )
                    )
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(
                        requireContext(), requireContext().getString(
                            R.string.unknown_error
                        ), Toast.LENGTH_LONG
                    ).show()
                }

                true
            }

        shareTheApp?.setOnPreferenceClickListener {

            val linkToGooglePlay = GOOGLE_PLAY_APP_LINK

            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {

                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, linkToGooglePlay)

            }, getString(R.string.share_the_app)))

            true
        }
    }
}