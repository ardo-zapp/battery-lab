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
import com.jacktor.batterylab.interfaces.views.MenuInterface

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

        // Hubungkan TabLayout dengan ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = getString(R.string.idle_drain)
                1 -> tab.text = getString(R.string.experiment)
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
