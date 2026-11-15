package com.jacktor.batterylab

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import com.jacktor.batterylab.databinding.ActivityOnboardingBinding
import com.jacktor.batterylab.databinding.ItemFeatureBinding
import com.jacktor.batterylab.helpers.MotionHelper

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val setupWizardLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFeature(
            binding.featureOne,
            R.drawable.ic_data_usage_24,
            R.string.welcome_feature_one_title,
            R.string.welcome_feature_one_summary
        )

        setupFeature(
            binding.featureTwo,
            R.drawable.ic_audio_24,
            R.string.welcome_feature_two_title,
            R.string.welcome_feature_two_summary
        )

        setupFeature(
            binding.featureThree,
            R.drawable.ic_data_exploration_24,
            R.string.welcome_feature_three_title,
            R.string.welcome_feature_three_summary
        )

        setupFeature(
            binding.featureFour,
            R.drawable.ic_general_24,
            R.string.welcome_feature_third_title,
            R.string.welcome_feature_third_summary
        )

        MotionHelper.bindPressScale(binding.btnContinue)
        MotionHelper.animateEntrance(binding.featureOne.root, delayMs = 40L)
        MotionHelper.animateEntrance(binding.featureTwo.root, delayMs = 95L)
        MotionHelper.animateEntrance(binding.featureThree.root, delayMs = 150L)
        MotionHelper.animateEntrance(binding.featureFour.root, delayMs = 205L)
        MotionHelper.animateEntrance(binding.btnContinue, delayMs = 260L)

        binding.btnContinue.setOnClickListener {
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this, android.R.anim.fade_in, android.R.anim.fade_out
            )
            setupWizardLauncher.launch(
                Intent(this, SetupWizardActivity::class.java),
                options
            )
        }
    }

    private fun setupFeature(
        itemBinding: ItemFeatureBinding,
        iconRes: Int,
        titleRes: Int,
        summaryRes: Int
    ) {
        itemBinding.ivIcon.setImageResource(iconRes)
        itemBinding.tvTitle.setText(titleRes)
        itemBinding.tvSummary.setText(summaryRes)
    }
}
