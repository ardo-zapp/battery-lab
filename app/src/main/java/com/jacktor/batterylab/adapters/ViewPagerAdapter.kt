package com.jacktor.batterylab.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.jacktor.batterylab.fragments.tab.AppUsageFragment
import com.jacktor.batterylab.fragments.tab.IdleDrainFragment
import com.jacktor.batterylab.fragments.tab.StressImpedanceFragment

class ViewPagerAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> IdleDrainFragment()
            1 -> StressImpedanceFragment()
            2 -> AppUsageFragment()
            else -> throw IllegalStateException("Invalid tab position")
        }
    }
}
