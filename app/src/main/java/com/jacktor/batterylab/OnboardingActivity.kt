package com.jacktor.batterylab

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.databinding.ActivityOnboardingBinding
import com.jacktor.batterylab.databinding.ItemFeatureBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

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

        binding.btnContinue.setOnClickListener {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)

            prefs.edit { putBoolean("is_first_launch", false) }
            startActivity(Intent(this, MainActivity::class.java))
            finish()
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
