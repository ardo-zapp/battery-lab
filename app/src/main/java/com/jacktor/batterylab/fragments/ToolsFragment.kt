package com.jacktor.batterylab.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.jacktor.batterylab.MainActivity
import com.jacktor.batterylab.R
import com.jacktor.batterylab.adapters.ViewPagerAdapter
import com.jacktor.batterylab.databinding.ToolsFragmentBinding
import com.jacktor.batterylab.helpers.MotionHelper
import com.jacktor.batterylab.interfaces.views.MenuInterface
import kotlin.math.abs

class ToolsFragment : Fragment(R.layout.tools_fragment), MenuInterface {

    private lateinit var binding: ToolsFragmentBinding
    private var pref: SharedPreferences? = null

    companion object {
        //var instance: ToolsFragment? = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = ToolsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Setup ViewPager2 dan TabLayout
        val viewPagerAdapter = ViewPagerAdapter(requireActivity())
        binding.viewPager.adapter = viewPagerAdapter
        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.setPageTransformer { page, position ->
            if (!MotionHelper.isAnimationsEnabled()) {
                page.alpha = 1f
                page.scaleX = 1f
                page.scaleY = 1f
                page.translationX = 0f
                return@setPageTransformer
            }
            val absPos = abs(position)
            page.alpha = (1f - absPos * 0.45f).coerceIn(0f, 1f)
            val scale = (0.965f + (1f - absPos) * 0.035f).coerceIn(0.965f, 1f)
            page.scaleX = scale
            page.scaleY = scale
            page.translationX = -position * page.width * 0.08f
        }

        // Hubungkan TabLayout dengan ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = getString(R.string.idle_drain)
                1 -> tab.text = getString(R.string.stress_impedance_tab)
            }
        }.attach()

        // Callback untuk mendeteksi perubahan halaman
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                (activity as? MainActivity)?.inflateMenu(position) // Panggil dari MenuInterface
            }
        })
    }

    /*override fun onResume() {
        super.onResume()
        if (instance == null) instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }*/
}
