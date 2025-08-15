package com.jacktor.batterylab.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.jacktor.batterylab.fragments.tab.CalibrationFragment
import com.jacktor.batterylab.fragments.tab.KernelFragment

class ViewPagerAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> CalibrationFragment()
            1 -> KernelFragment()
            else -> throw IllegalStateException("Invalid tab position")
        }
    }
}
