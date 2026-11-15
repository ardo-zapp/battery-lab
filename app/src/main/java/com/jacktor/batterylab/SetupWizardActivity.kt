package com.jacktor.batterylab

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import com.jacktor.batterylab.adapters.SetupWizardAdapter
import com.jacktor.batterylab.adapters.WizardStepType
import com.jacktor.batterylab.databinding.ActivitySetupWizardBinding
import com.jacktor.batterylab.helpers.MotionHelper
import com.jacktor.batterylab.helpers.SetupPermissionHelper
import com.jacktor.batterylab.utilities.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SetupWizardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MISSING_ONLY = "extra_missing_only"
        const val EXTRA_FROM_SETUP_WIZARD = "extra_from_setup_wizard"
    }

    private lateinit var binding: ActivitySetupWizardBinding
    private lateinit var steps: List<WizardStepType>
    private lateinit var adapter: SetupWizardAdapter

    private var isMissingOnlyMode = false
    private var hasAskedNotificationPermission = false
    private var isXiaomiAutostartOpened = false
    private var resumePollJob: Job? = null

    private val requestNotificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
            binding.root.performHapticFeedback(feedback)
        }
        refreshStepStates()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        isMissingOnlyMode = intent?.getBooleanExtra(EXTRA_MISSING_ONLY, false) == true
        steps = buildWizardSteps()
        if (steps.isEmpty()) {
            completeWizard()
            return
        }

        binding = ActivitySetupWizardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets()
        setupViewPager()
        setupIndicators()
        setupControls()
        handleBackNavigation()
        updateChromeForPage(0)
    }

    override fun onResume() {
        super.onResume()
        refreshStepStates()

        // Android / MIUI may commit battery optimization changes slightly after returning from system UI.
        resumePollJob?.cancel()
        resumePollJob = lifecycleScope.launch {
            repeat(5) {
                delay(300L)
                refreshStepStates()
            }
        }
    }

    override fun onPause() {
        resumePollJob?.cancel()
        super.onPause()
    }

    private fun applyWindowInsets() {
        val baseTopPadding = binding.topBarContainer.paddingTop
        val baseBottomPadding = binding.bottomBarContent.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.wizardRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBarContainer.setPadding(
                binding.topBarContainer.paddingLeft,
                baseTopPadding + systemBars.top,
                binding.topBarContainer.paddingRight,
                binding.topBarContainer.paddingBottom
            )
            binding.bottomBarContent.setPadding(
                binding.bottomBarContent.paddingLeft,
                binding.bottomBarContent.paddingTop,
                binding.bottomBarContent.paddingRight,
                baseBottomPadding + systemBars.bottom
            )
            insets
        }
    }

    private fun buildWizardSteps(): List<WizardStepType> {
        if (isMissingOnlyMode) {
            return SetupPermissionHelper.getMissingEssentialSteps(this)
        }
        return buildList {
            if (SetupPermissionHelper.needsNotificationPermission()) {
                add(WizardStepType.NOTIFICATIONS)
            }
            add(WizardStepType.BATTERY_OPTIMIZATION)
            if (SetupPermissionHelper.isXiaomi(this@SetupWizardActivity)) {
                add(WizardStepType.XIAOMI_AUTOSTART)
            } else if (SetupPermissionHelper.needsHuaweiAdvisory()) {
                add(WizardStepType.HUAWEI_INFO)
            }
            add(WizardStepType.SUMMARY)
        }
    }

    private fun setupViewPager() {
        adapter = SetupWizardAdapter(
            steps = steps,
            isXiaomiAutostartOpened = { isXiaomiAutostartOpened },
            onStepActionClick = ::handleStepAction,
            onUsageAccessClick = {
                if (!SetupPermissionHelper.openUsageAccessSettings(this)) {
                    showSettingsOpenError()
                }
            },
            onJumpToStep = { targetType ->
                val targetIndex = steps.indexOf(targetType)
                if (targetIndex >= 0) {
                    binding.viewPager.setCurrentItem(targetIndex, true)
                }
            }
        )

        binding.viewPager.adapter = adapter
        binding.viewPager.offscreenPageLimit = 1
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateChromeForPage(position)
            }
        })
    }

    private fun setupIndicators() {
        binding.progressIndicator.max = (steps.size * 100).coerceAtLeast(100)
        binding.dotsContainer.removeAllViews()
        repeat(steps.size) {
            val dot = View(this)
            binding.dotsContainer.addView(dot)
        }
        updateDots(0)
    }

    private fun setupControls() {
        MotionHelper.bindPressScale(binding.btnNext, binding.btnPrevious, binding.btnSkip)

        binding.btnBack.setOnClickListener { navigateBack() }
        binding.btnPrevious.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current > 0) {
                binding.viewPager.setCurrentItem(current - 1, true)
            }
        }
        binding.btnSkip.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < steps.lastIndex) {
                binding.viewPager.setCurrentItem(current + 1, true)
            } else {
                completeWizard()
            }
        }
        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (!canAdvanceStep(steps[current])) return@setOnClickListener
            if (current < steps.lastIndex) {
                binding.viewPager.setCurrentItem(current + 1, true)
            } else {
                completeWizard()
            }
        }
    }

    private fun handleBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateBack()
            }
        })
    }

    private fun navigateBack() {
        val current = binding.viewPager.currentItem
        if (current > 0) {
            binding.viewPager.setCurrentItem(current - 1, true)
        } else {
            finish()
        }
    }

    private fun canAdvanceStep(step: WizardStepType): Boolean {
        return when (step) {
            WizardStepType.NOTIFICATIONS ->
                SetupPermissionHelper.isNotificationPermissionGranted(this)

            WizardStepType.BATTERY_OPTIMIZATION ->
                SetupPermissionHelper.isIgnoringBatteryOptimizations(this)

            WizardStepType.XIAOMI_AUTOSTART -> when (SetupPermissionHelper.getXiaomiAutostartState(this)) {
                true -> true
                false -> false
                null -> isXiaomiAutostartOpened
            }

            WizardStepType.HUAWEI_INFO,
            WizardStepType.SUMMARY -> true
        }
    }

    private fun updateChromeForPage(position: Int) {
        val stepCount = steps.size
        val isLast = position >= steps.lastIndex
        val canAdvance = canAdvanceStep(steps[position])

        binding.tvStepCounter.text = getString(R.string.wizard_step_counter, position + 1, stepCount)
        binding.progressIndicator.setProgressCompat((position + 1) * 100, true)

        binding.btnPrevious.isVisible = position > 0
        binding.btnSkip.isVisible = steps[position] != WizardStepType.SUMMARY
        binding.viewPager.isUserInputEnabled = canAdvance

        binding.btnNext.isEnabled = canAdvance
        binding.btnNext.setText(
            if (isLast) R.string.wizard_finish else R.string.wizard_next
        )
        if (canAdvance) {
            binding.btnNext.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.md_theme_primary))
            binding.btnNext.setTextColor(ContextCompat.getColor(this, R.color.white))
            binding.btnNext.alpha = 1f
        } else {
            binding.btnNext.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.md_theme_surfaceVariant))
            binding.btnNext.setTextColor(ContextCompat.getColor(this, R.color.card_subtitle_text))
            binding.btnNext.alpha = 0.7f
        }

        updateDots(position)
    }

    private fun updateDots(activePosition: Int) {
        val density = resources.displayMetrics.density
        val heightPx = (8 * density).toInt()
        val activeWidthPx = (24 * density).toInt()
        val inactiveWidthPx = (8 * density).toInt()
        val marginPx = (4 * density).toInt()

        val activeColor = ContextCompat.getColor(this, R.color.md_theme_primary)
        val inactiveColor = ContextCompat.getColor(this, R.color.md_theme_surfaceVariant)

        for (i in 0 until binding.dotsContainer.childCount) {
            val dot = binding.dotsContainer.getChildAt(i)
            val isActive = i == activePosition
            val params = LinearLayout.LayoutParams(
                if (isActive) activeWidthPx else inactiveWidthPx,
                heightPx
            ).apply {
                setMargins(marginPx, 0, marginPx, 0)
            }
            dot.layoutParams = params
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = heightPx / 2f
                setColor(if (isActive) activeColor else inactiveColor)
            }
        }
    }

    private fun refreshStepStates() {
        if (!::adapter.isInitialized) return
        adapter.notifyItemRangeChanged(0, steps.size, SetupWizardAdapter.PAYLOAD_STATUS_REFRESH)
        updateChromeForPage(binding.viewPager.currentItem)
    }

    private fun handleStepAction(step: WizardStepType) {
        when (step) {
            WizardStepType.NOTIFICATIONS -> handleNotificationPermissionClick()
            WizardStepType.BATTERY_OPTIMIZATION -> {
                if (!SetupPermissionHelper.requestIgnoreBatteryOptimizations(this)) {
                    showSettingsOpenError()
                }
            }

            WizardStepType.XIAOMI_AUTOSTART -> {
                isXiaomiAutostartOpened = true
                if (!SetupPermissionHelper.openXiaomiAutostartSettings(this)) {
                    showSettingsOpenError()
                } else {
                    refreshStepStates()
                }
            }

            WizardStepType.HUAWEI_INFO -> {
                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, "${Constants.DONT_KILL_MY_APP_LINK}/huawei".toUri())
                    )
                } catch (_: ActivityNotFoundException) {
                    showSettingsOpenError()
                }
            }

            WizardStepType.SUMMARY -> completeWizard()
        }
    }

    private fun handleNotificationPermissionClick() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (SetupPermissionHelper.isNotificationPermissionGranted(this)) {
            refreshStepStates()
            return
        }

        val canAskSystemPrompt = !hasAskedNotificationPermission ||
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )

        if (canAskSystemPrompt) {
            hasAskedNotificationPermission = true
            requestNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            if (!SetupPermissionHelper.openAppNotificationSettings(this)) {
                showSettingsOpenError()
            }
        }
    }

    private fun showSettingsOpenError() {
        Toast.makeText(this, R.string.wizard_action_error, Toast.LENGTH_SHORT).show()
    }

    private fun completeWizard() {
        PreferenceManager.getDefaultSharedPreferences(this).edit {
            putBoolean("is_first_launch", false)
            putBoolean("setup_wizard_completed", true)
        }
        setResult(RESULT_OK)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_FROM_SETUP_WIZARD, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }
}
