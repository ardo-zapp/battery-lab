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
        private const val ARG_INITIAL_TAB = "initial_tab"

        fun newInstance(initialTab: Int = 0): ToolsFragment = ToolsFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_INITIAL_TAB, initialTab)
            }
        }
    }

    private var pendingTabIndex: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = ToolsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val viewPagerAdapter = ViewPagerAdapter(this)
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
            // Keep translationX zeroed so switching across ViewPager2 pages never overlaps neighbors.
            page.translationX = 0f
            page.scaleX = 1f
            page.scaleY = 1f
            page.alpha = (1f - absPos).coerceIn(0f, 1f)
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = getString(R.string.idle_drain)
                1 -> tab.text = getString(R.string.stress_impedance_tab)
                2 -> tab.text = getString(R.string.app_usage_tab)
                3 -> tab.text = getString(R.string.last_charge_tab)
            }
        }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                (activity as? MainActivity)?.inflateMenu(position)
            }
        })

        val initialTab = if (pendingTabIndex >= 0) {
            pendingTabIndex
        } else {
            arguments?.getInt(ARG_INITIAL_TAB, -1) ?: -1
        }
        pendingTabIndex = -1
        if (initialTab in 0 until viewPagerAdapter.itemCount) {
            binding.viewPager.post {
                if (isAdded) binding.viewPager.setCurrentItem(initialTab, false)
            }
        }
    }

    fun selectTab(index: Int) {
        if (!::binding.isInitialized) {
            pendingTabIndex = index
            return
        }
        val count = binding.viewPager.adapter?.itemCount ?: return
        if (index in 0 until count) {
            binding.viewPager.post {
                if (isAdded) binding.viewPager.setCurrentItem(index, false)
            }
        }
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
