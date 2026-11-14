package com.jacktor.batterylab.adapters

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.jacktor.batterylab.fragments.tab.AppUsageFragment
import com.jacktor.batterylab.fragments.tab.IdleDrainFragment
import com.jacktor.batterylab.fragments.tab.LastChargeFragment
import com.jacktor.batterylab.fragments.tab.StressImpedanceFragment

class ViewPagerAdapter(fragment: Fragment) :
    FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> IdleDrainFragment()
            1 -> StressImpedanceFragment()
            2 -> AppUsageFragment()
            3 -> LastChargeFragment()
            else -> throw IllegalStateException("Invalid tab position")
        }
    }
}
