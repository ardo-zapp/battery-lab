package com.jacktor.batterylab.fragments

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jacktor.batterylab.BuildConfig
import com.jacktor.batterylab.MainApp
import com.jacktor.batterylab.MainApp.Companion.isInstalledGooglePlay
import com.jacktor.batterylab.R
import com.jacktor.batterylab.interfaces.CheckUpdateInterface
import com.jacktor.batterylab.interfaces.ContributorsInterface
import com.jacktor.batterylab.utilities.Constants.GITHUB_LINK
import com.jacktor.batterylab.utilities.Constants.GITHUB_LINK_BATTERY_CAPCITY
import com.jacktor.batterylab.models.ContributorsModel
import com.jacktor.batterylab.security.NativeCore
import com.jacktor.premium.Premium
import com.jacktor.premium.data.model.PremiumTrustStatus

class AboutFragment : PreferenceFragmentCompat(), ContributorsInterface,
    CheckUpdateInterface {

    var pref: SharedPreferences? = null
    private var checkUpdate: Preference? = null
    private var developer: Preference? = null
    private var version: Preference? = null
    private var build: Preference? = null
    private var buildDate: Preference? = null
    private var appIntegrity: Preference? = null
    private var github: Preference? = null
    private var githubBC: Preference? = null
    private var betaTester: Preference? = null
    private var contributors: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

        addPreferencesFromResource(R.xml.about_settings)

        developer = findPreference("developer")

        version = findPreference("version")

        build = findPreference("build")

        buildDate = findPreference("build_date")

        appIntegrity = findPreference("app_integrity")

        checkUpdate = findPreference("check_update")

        github = findPreference("github")
        githubBC = findPreference("github_battery_capacity")

        betaTester = findPreference("become_a_beta_tester")

        checkUpdate?.apply {
            isVisible = isInstalledGooglePlay && MainApp.isGooglePlay(requireContext())
            setOnPreferenceClickListener {
                checkUpdateFromGooglePlay()
                true
            }
        }

        contributors = findPreference("contributors")

        betaTester?.isVisible = isInstalledGooglePlay

        version?.summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
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

        build?.summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
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

        buildDate?.summary = BuildConfig.BUILD_DATE

        updateAppIntegrityInfo()
        appIntegrity?.setOnPreferenceClickListener {
            showAppIntegrityDialog()
            true
        }

        developer?.setOnPreferenceClickListener {

            try {

                if (isInstalledGooglePlay)
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "market://search?q=pub:${developer?.summary}".toUri()
                        )
                    )
                else startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://play.google.com/store/apps/developer?id=${
                            developer
                                ?.summary
                        }".toUri()
                    )
                )
            } catch (e: ActivityNotFoundException) {

                Toast.makeText(
                    requireContext(), e.message ?: e.toString(),
                    Toast.LENGTH_LONG
                ).show()
            }

            true
        }

        github?.setOnPreferenceClickListener {

            try {

                startActivity(Intent(Intent.ACTION_VIEW, GITHUB_LINK.toUri()))
            } catch (e: ActivityNotFoundException) {

                Toast.makeText(
                    requireContext(), e.message ?: e.toString(),
                    Toast.LENGTH_LONG
                ).show()
            }

            true
        }

        githubBC?.setOnPreferenceClickListener {

            try {

                startActivity(Intent(Intent.ACTION_VIEW, GITHUB_LINK_BATTERY_CAPCITY.toUri()))
            } catch (e: ActivityNotFoundException) {

                Toast.makeText(
                    requireContext(), e.message ?: e.toString(),
                    Toast.LENGTH_LONG
                ).show()
            }

            true
        }

        betaTester?.setOnPreferenceClickListener {

            try {

                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://play.google.com/apps/testing/${
                            requireContext()
                                .packageName
                        }".toUri()
                    )
                )
            } catch (e: ActivityNotFoundException) {

                Toast.makeText(
                    requireContext(), e.message ?: e.toString(),
                    Toast.LENGTH_LONG
                ).show()
            }

            true
        }

        // Contributors list
        contributors?.setOnPreferenceClickListener {
            showContributorsDialog(requireContext())
            true
        }
    }

    //Visit Profile
    override fun onItemClick(data: ContributorsModel) {
        val openURL = Intent(Intent.ACTION_VIEW)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.visit_this_profile))
            .setMessage(data.name + " (" + data.username + ")")
            .setNeutralButton(getString(R.string.cancel)) { dialog, _ ->
                // Respond to neutral button press
                dialog.cancel()
            }
            //.setNegativeButton(getString(R.string.decline)) { dialog, which ->
            // Respond to negative button press
            //}
            .setPositiveButton(getString(R.string.yes_continue)) { _, _ ->
                openURL.data = data.htmlUrl.toUri()
                startActivity(openURL)
            }
            .show()
    }

    override fun onResume() {

        super.onResume()

        betaTester?.isVisible = isInstalledGooglePlay
        updateAppIntegrityInfo()
    }

    private fun resolveAppIntegrityStatus(): Pair<String, Int> {
        val ctx = requireContext()
        val coreTrust = try {
            NativeCore.nativeVerifyIntegrity(ctx, true)
        } catch (_: Throwable) {
            NativeCore.TRUST_TAMPERED
        }
        val premTrust = try {
            Premium.getTrustStatus(ctx, true)
        } catch (_: Throwable) {
            PremiumTrustStatus.TAMPERED
        }
        val level = minOf(coreTrust, premTrust.code)
        val label = when (level) {
            NativeCore.TRUST_OFFICIAL_RELEASE -> getString(R.string.app_integrity_official)
            NativeCore.TRUST_OWNER_DEBUG -> getString(R.string.app_integrity_owner_debug)
            NativeCore.TRUST_OPENSOURCE_DEBUG -> getString(R.string.app_integrity_opensource)
            else -> getString(R.string.app_integrity_tampered)
        }
        return label to level
    }

    private fun updateAppIntegrityInfo() {
        val (label, _) = resolveAppIntegrityStatus()
        appIntegrity?.summary = label
    }

    private fun showAppIntegrityDialog() {
        val ctx = requireContext()
        val (statusLabel, level) = resolveAppIntegrityStatus()
        val report = try {
            Premium.getSecurityReport(ctx, true)
        } catch (_: Throwable) {
            null
        }
        val sigText = if (
            level != NativeCore.TRUST_TAMPERED &&
            (report == null || (report.pmDiskMatch && report.cmdlineMatch && report.packageAllowed))
        ) {
            getString(R.string.app_integrity_valid)
        } else {
            getString(R.string.app_integrity_invalid)
        }
        val runtimeText = if (
            level != NativeCore.TRUST_TAMPERED &&
            (report == null || (report.hooksClean && report.debuggerClean && report.billingProxyClean && report.vaultIntact))
        ) {
            getString(R.string.app_integrity_clean)
        } else {
            getString(R.string.app_integrity_flagged)
        }
        val installerLabel = when (val pkg = report?.installerPackage.orEmpty()) {
            "com.android.vending", "com.google.android.feedback" -> "Google Play"
            "" -> getString(R.string.app_integrity_installer_unknown)
            else -> pkg
        }
        MaterialAlertDialogBuilder(ctx)
            .setIcon(R.drawable.ic_security_24)
            .setTitle(R.string.app_integrity)
            .setMessage(
                getString(
                    R.string.app_integrity_dialog_message,
                    statusLabel,
                    sigText,
                    runtimeText,
                    installerLabel
                )
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

}