package com.jacktor.batterylab.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.jacktor.batterylab.R
import com.jacktor.batterylab.databinding.ItemWizardChecklistRowBinding
import com.jacktor.batterylab.databinding.ItemWizardStepBinding
import com.jacktor.batterylab.databinding.ItemWizardSummaryBinding
import com.jacktor.batterylab.helpers.MotionHelper
import com.jacktor.batterylab.helpers.SetupPermissionHelper

enum class WizardStepType {
    NOTIFICATIONS,
    BATTERY_OPTIMIZATION,
    XIAOMI_AUTOSTART,
    HUAWEI_INFO,
    SUMMARY
}

class SetupWizardAdapter(
    private val steps: List<WizardStepType>,
    private val isXiaomiAutostartOpened: () -> Boolean,
    private val onStepActionClick: (WizardStepType) -> Unit,
    private val onUsageAccessClick: () -> Unit,
    private val onJumpToStep: (WizardStepType) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_STEP = 0
        private const val VIEW_TYPE_SUMMARY = 1
        const val PAYLOAD_STATUS_REFRESH = "status_refresh"
    }

    override fun getItemViewType(position: Int): Int =
        if (steps[position] == WizardStepType.SUMMARY) VIEW_TYPE_SUMMARY else VIEW_TYPE_STEP

    override fun getItemCount(): Int = steps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SUMMARY) {
            SummaryViewHolder(ItemWizardSummaryBinding.inflate(inflater, parent, false))
        } else {
            StepViewHolder(ItemWizardStepBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        onBindViewHolder(holder, position, emptyList())
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        val step = steps[position]
        val statusOnly = payloads.contains(PAYLOAD_STATUS_REFRESH)
        when (holder) {
            is StepViewHolder -> holder.bind(step, statusOnly)
            is SummaryViewHolder -> holder.bind(statusOnly)
        }
    }

    private data class StepContent(
        val iconRes: Int,
        val badgeRes: Int,
        val titleRes: Int,
        val subtitleRes: Int,
        val point1Res: Int,
        val point2Res: Int,
        val point3Res: Int,
        val actionLabelRes: Int,
        val oemTipRes: Int? = null
    )

    private fun contentFor(step: WizardStepType, context: Context): StepContent = when (step) {
        WizardStepType.NOTIFICATIONS -> StepContent(
            iconRes = R.drawable.ic_notifications_pref_24dp,
            badgeRes = R.string.wizard_badge_essential,
            titleRes = R.string.wizard_notif_title,
            subtitleRes = R.string.wizard_notif_subtitle,
            point1Res = R.string.wizard_notif_benefit_1,
            point2Res = R.string.wizard_notif_benefit_2,
            point3Res = R.string.wizard_notif_benefit_3,
            actionLabelRes = R.string.wizard_notif_action
        )

        WizardStepType.BATTERY_OPTIMIZATION -> StepContent(
            iconRes = R.drawable.ic_battery_stat_24,
            badgeRes = R.string.wizard_badge_background,
            titleRes = R.string.wizard_battery_opt_title,
            subtitleRes = R.string.wizard_battery_opt_subtitle,
            point1Res = R.string.wizard_battery_opt_benefit_1,
            point2Res = R.string.wizard_battery_opt_benefit_2,
            point3Res = R.string.wizard_battery_opt_benefit_3,
            actionLabelRes = R.string.wizard_battery_opt_action,
            oemTipRes = if (SetupPermissionHelper.isXiaomi(context)) R.string.wizard_battery_opt_xiaomi_tip else null
        )

        WizardStepType.XIAOMI_AUTOSTART -> StepContent(
            iconRes = R.drawable.ic_autostart_24,
            badgeRes = R.string.wizard_badge_device,
            titleRes = R.string.wizard_autostart_title,
            subtitleRes = R.string.wizard_autostart_subtitle,
            point1Res = R.string.wizard_autostart_step_1,
            point2Res = R.string.wizard_autostart_step_2,
            point3Res = R.string.wizard_autostart_step_3,
            actionLabelRes = R.string.wizard_autostart_action
        )

        WizardStepType.HUAWEI_INFO -> StepContent(
            iconRes = R.drawable.ic_instruction_not_supported_24dp,
            badgeRes = R.string.wizard_badge_device,
            titleRes = R.string.wizard_huawei_title,
            subtitleRes = R.string.wizard_huawei_subtitle,
            point1Res = R.string.wizard_huawei_tip,
            point2Res = R.string.wizard_battery_opt_benefit_2,
            point3Res = R.string.wizard_notif_benefit_3,
            actionLabelRes = R.string.wizard_huawei_action
        )

        WizardStepType.SUMMARY -> error("SUMMARY uses SummaryViewHolder")
    }

    private fun resolveStatus(context: Context, step: WizardStepType): StepStatus {
        return when (step) {
            WizardStepType.NOTIFICATIONS -> {
                if (SetupPermissionHelper.isNotificationPermissionGranted(context)) {
                    StepStatus.GRANTED
                } else {
                    StepStatus.PENDING
                }
            }

            WizardStepType.BATTERY_OPTIMIZATION -> {
                if (SetupPermissionHelper.isIgnoringBatteryOptimizations(context)) {
                    StepStatus.GRANTED
                } else {
                    StepStatus.PENDING
                }
            }

            WizardStepType.XIAOMI_AUTOSTART -> {
                when (SetupPermissionHelper.getXiaomiAutostartState(context)) {
                    true -> StepStatus.GRANTED
                    false -> StepStatus.PENDING
                    null -> if (isXiaomiAutostartOpened()) StepStatus.MANUAL_CHECK else StepStatus.PENDING
                }
            }

            WizardStepType.HUAWEI_INFO -> StepStatus.ADVISORY
            WizardStepType.SUMMARY -> StepStatus.GRANTED
        }
    }

    private enum class StepStatus {
        GRANTED,
        PENDING,
        MANUAL_CHECK,
        ADVISORY
    }

    inner class StepViewHolder(
        private val binding: ItemWizardStepBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            MotionHelper.bindPressScale(binding.btnStepAction)
        }

        fun bind(step: WizardStepType, statusOnly: Boolean) {
            val context = binding.root.context
            val content = contentFor(step, context)

            if (!statusOnly) {
                binding.ivHeroIcon.setImageResource(content.iconRes)
                binding.tvCategoryBadge.setText(content.badgeRes)
                binding.tvStepTitle.setText(content.titleRes)
                binding.tvStepSubtitle.setText(content.subtitleRes)
                binding.tvPoint1.setText(content.point1Res)
                binding.tvPoint2.setText(content.point2Res)
                binding.tvPoint3.setText(content.point3Res)

                if (content.oemTipRes != null) {
                    binding.cardOemTip.isVisible = true
                    binding.tvOemTip.setText(content.oemTipRes)
                } else {
                    binding.cardOemTip.isVisible = false
                }

                binding.btnStepAction.setOnClickListener {
                    onStepActionClick(step)
                }

                MotionHelper.animateEntrance(binding.heroIconContainer, delayMs = 20L, translateDp = 12f)
                MotionHelper.animateEntrance(binding.cardStatusBanner, delayMs = 70L, translateDp = 12f)
                MotionHelper.animateEntrance(binding.cardDetails, delayMs = 120L, translateDp = 14f)
                MotionHelper.animateEntrance(binding.btnStepAction, delayMs = 170L, translateDp = 14f)
            }

            applyStatusVisuals(context, step, content)
        }

        private fun applyStatusVisuals(
            context: Context,
            step: WizardStepType,
            content: StepContent
        ) {
            val status = resolveStatus(context, step)
            val greenColor = ContextCompat.getColor(context, R.color.battery_info_level_normal)
            val warningColor = ContextCompat.getColor(context, R.color.battery_info_voltage_warning)
            val primaryColor = ContextCompat.getColor(context, R.color.md_theme_primary)
            val primaryContainer = ContextCompat.getColor(context, R.color.md_theme_primaryContainer)
            val cardBg = ContextCompat.getColor(context, R.color.card_bg)
            val surfaceVariant = ContextCompat.getColor(context, R.color.md_theme_surfaceVariant)

            when (status) {
                StepStatus.GRANTED -> {
                    binding.heroIconContainer.backgroundTintList =
                        ColorStateList.valueOf(ColorUtils.setAlphaComponent(greenColor, 36))
                    binding.ivHeroIcon.imageTintList = ColorStateList.valueOf(greenColor)
                    binding.ivStatusIcon.setImageResource(R.drawable.ic_check_circle_24)
                    binding.ivStatusIcon.imageTintList = ColorStateList.valueOf(greenColor)
                    binding.tvStatusLabel.setText(R.string.wizard_status_granted)
                    binding.tvStatusLabel.setTextColor(greenColor)
                    binding.cardStatusBanner.setCardBackgroundColor(
                        ColorUtils.compositeColors(
                            ColorUtils.setAlphaComponent(greenColor, 26),
                            cardBg
                        )
                    )
                    binding.cardStatusBanner.strokeColor = ColorUtils.setAlphaComponent(greenColor, 90)
                    binding.btnStepAction.isEnabled = false
                    binding.btnStepAction.setText(R.string.wizard_btn_granted)
                    binding.btnStepAction.setIconResource(R.drawable.ic_check_circle_24)
                    binding.btnStepAction.backgroundTintList =
                        ColorStateList.valueOf(ColorUtils.setAlphaComponent(greenColor, 32))
                    binding.btnStepAction.setTextColor(greenColor)
                    binding.btnStepAction.iconTint = ColorStateList.valueOf(greenColor)
                }

                StepStatus.PENDING -> {
                    binding.heroIconContainer.backgroundTintList = ColorStateList.valueOf(primaryContainer)
                    binding.ivHeroIcon.imageTintList = ColorStateList.valueOf(primaryColor)
                    binding.ivStatusIcon.setImageResource(R.drawable.ic_wizard_schedule_24)
                    binding.ivStatusIcon.imageTintList = ColorStateList.valueOf(warningColor)
                    binding.tvStatusLabel.setText(R.string.wizard_status_pending)
                    binding.tvStatusLabel.setTextColor(
                        ContextCompat.getColor(context, R.color.card_title_text)
                    )
                    binding.cardStatusBanner.setCardBackgroundColor(cardBg)
                    binding.cardStatusBanner.strokeColor = surfaceVariant
                    binding.btnStepAction.isEnabled = true
                    binding.btnStepAction.setText(content.actionLabelRes)
                    binding.btnStepAction.icon = null
                    binding.btnStepAction.backgroundTintList = ColorStateList.valueOf(primaryColor)
                    binding.btnStepAction.setTextColor(ContextCompat.getColor(context, R.color.white))
                }

                StepStatus.MANUAL_CHECK -> {
                    binding.heroIconContainer.backgroundTintList = ColorStateList.valueOf(primaryContainer)
                    binding.ivHeroIcon.imageTintList = ColorStateList.valueOf(primaryColor)
                    binding.ivStatusIcon.setImageResource(R.drawable.ic_wizard_schedule_24)
                    binding.ivStatusIcon.imageTintList = ColorStateList.valueOf(primaryColor)
                    binding.tvStatusLabel.setText(R.string.wizard_status_manual_check)
                    binding.tvStatusLabel.setTextColor(primaryColor)
                    binding.cardStatusBanner.setCardBackgroundColor(cardBg)
                    binding.cardStatusBanner.strokeColor = ColorUtils.setAlphaComponent(primaryColor, 90)
                    binding.btnStepAction.isEnabled = true
                    binding.btnStepAction.setText(R.string.wizard_btn_open_settings)
                    binding.btnStepAction.icon = null
                    binding.btnStepAction.backgroundTintList = ColorStateList.valueOf(primaryContainer)
                    binding.btnStepAction.setTextColor(
                        ContextCompat.getColor(context, R.color.md_theme_onPrimaryContainer)
                    )
                }

                StepStatus.ADVISORY -> {
                    binding.heroIconContainer.backgroundTintList = ColorStateList.valueOf(primaryContainer)
                    binding.ivHeroIcon.imageTintList = ColorStateList.valueOf(primaryColor)
                    binding.ivStatusIcon.setImageResource(R.drawable.ic_instruction_not_supported_24dp)
                    binding.ivStatusIcon.imageTintList = ColorStateList.valueOf(primaryColor)
                    binding.tvStatusLabel.setText(R.string.wizard_status_advisory)
                    binding.tvStatusLabel.setTextColor(primaryColor)
                    binding.cardStatusBanner.setCardBackgroundColor(cardBg)
                    binding.cardStatusBanner.strokeColor = surfaceVariant
                    binding.btnStepAction.isEnabled = true
                    binding.btnStepAction.setText(content.actionLabelRes)
                    binding.btnStepAction.icon = null
                    binding.btnStepAction.backgroundTintList = ColorStateList.valueOf(primaryContainer)
                    binding.btnStepAction.setTextColor(
                        ContextCompat.getColor(context, R.color.md_theme_onPrimaryContainer)
                    )
                }
            }
        }
    }

    inner class SummaryViewHolder(
        private val binding: ItemWizardSummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            MotionHelper.bindPressScale(binding.btnUsageAccess)
        }

        fun bind(statusOnly: Boolean) {
            val context = binding.root.context
            if (!statusOnly) {
                binding.btnUsageAccess.setOnClickListener { onUsageAccessClick() }
                MotionHelper.animateEntrance(binding.heroIconContainer, delayMs = 20L, translateDp = 12f)
                MotionHelper.animateEntrance(binding.cardUsageAccess, delayMs = 80L, translateDp = 14f)
                MotionHelper.animateEntrance(binding.cardChecklist, delayMs = 140L, translateDp = 14f)
            }

            bindUsageAccessButton(context)
            populateChecklist(context)
        }

        private fun bindUsageAccessButton(context: Context) {
            val hasUsage = SetupPermissionHelper.hasUsageStatsPermission(context)
            val greenColor = ContextCompat.getColor(context, R.color.battery_info_level_normal)
            val primaryContainer = ContextCompat.getColor(context, R.color.md_theme_primaryContainer)
            val onPrimaryContainer = ContextCompat.getColor(context, R.color.md_theme_onPrimaryContainer)

            if (hasUsage) {
                binding.btnUsageAccess.isEnabled = false
                binding.btnUsageAccess.setText(R.string.wizard_usage_action_enabled)
                binding.btnUsageAccess.setIconResource(R.drawable.ic_check_circle_24)
                binding.btnUsageAccess.iconTint = ColorStateList.valueOf(greenColor)
                binding.btnUsageAccess.backgroundTintList =
                    ColorStateList.valueOf(ColorUtils.setAlphaComponent(greenColor, 32))
                binding.btnUsageAccess.setTextColor(greenColor)
            } else {
                binding.btnUsageAccess.isEnabled = true
                binding.btnUsageAccess.setText(R.string.wizard_usage_action_enable)
                binding.btnUsageAccess.icon = null
                binding.btnUsageAccess.backgroundTintList = ColorStateList.valueOf(primaryContainer)
                binding.btnUsageAccess.setTextColor(onPrimaryContainer)
            }
        }

        private fun populateChecklist(context: Context) {
            val container = binding.checklistContainer
            container.removeAllViews()
            val inflater = LayoutInflater.from(context)

            val entries = buildList {
                if (SetupPermissionHelper.needsNotificationPermission()) {
                    add(
                        ChecklistEntry(
                            titleRes = R.string.wizard_check_notif,
                            isGranted = SetupPermissionHelper.isNotificationPermissionGranted(context),
                            isOptional = false,
                            onClick = { onJumpToStep(WizardStepType.NOTIFICATIONS) }
                        )
                    )
                }
                add(
                    ChecklistEntry(
                        titleRes = R.string.wizard_check_battery_opt,
                        isGranted = SetupPermissionHelper.isIgnoringBatteryOptimizations(context),
                        isOptional = false,
                        onClick = { onJumpToStep(WizardStepType.BATTERY_OPTIMIZATION) }
                    )
                )
                if (SetupPermissionHelper.isXiaomi(context)) {
                    val autostart = SetupPermissionHelper.getXiaomiAutostartState(context) == true
                    add(
                        ChecklistEntry(
                            titleRes = R.string.wizard_check_autostart,
                            isGranted = autostart,
                            isOptional = false,
                            onClick = { onJumpToStep(WizardStepType.XIAOMI_AUTOSTART) }
                        )
                    )
                }
                add(
                    ChecklistEntry(
                        titleRes = R.string.wizard_check_usage,
                        isGranted = SetupPermissionHelper.hasUsageStatsPermission(context),
                        isOptional = true,
                        onClick = { onUsageAccessClick() }
                    )
                )
            }

            val greenColor = ContextCompat.getColor(context, R.color.battery_info_level_normal)
            val warningColor = ContextCompat.getColor(context, R.color.battery_info_voltage_warning)
            val mutedColor = ContextCompat.getColor(context, R.color.card_subtitle_text)

            for (entry in entries) {
                val row = ItemWizardChecklistRowBinding.inflate(inflater, container, false)
                row.tvCheckTitle.setText(entry.titleRes)
                when {
                    entry.isGranted -> {
                        row.ivCheckIcon.setImageResource(R.drawable.ic_check_circle_24)
                        row.ivCheckIcon.imageTintList = ColorStateList.valueOf(greenColor)
                        row.tvCheckStatus.setText(R.string.wizard_btn_granted)
                        row.tvCheckStatus.setTextColor(greenColor)
                        row.root.isClickable = false
                    }

                    entry.isOptional -> {
                        row.ivCheckIcon.setImageResource(R.drawable.ic_wizard_schedule_24)
                        row.ivCheckIcon.imageTintList = ColorStateList.valueOf(mutedColor)
                        row.tvCheckStatus.setText(R.string.wizard_status_optional_pending)
                        row.tvCheckStatus.setTextColor(mutedColor)
                        row.root.setOnClickListener { entry.onClick() }
                    }

                    else -> {
                        row.ivCheckIcon.setImageResource(R.drawable.ic_wizard_schedule_24)
                        row.ivCheckIcon.imageTintList = ColorStateList.valueOf(warningColor)
                        row.tvCheckStatus.setText(R.string.wizard_status_pending)
                        row.tvCheckStatus.setTextColor(warningColor)
                        row.root.setOnClickListener { entry.onClick() }
                    }
                }
                container.addView(row.root)
            }
        }
    }

    private data class ChecklistEntry(
        val titleRes: Int,
        val isGranted: Boolean,
        val isOptional: Boolean,
        val onClick: () -> Unit
    )
}
