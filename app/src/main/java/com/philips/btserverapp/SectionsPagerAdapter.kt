package com.philips.btserverapp

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.philips.btserverapp.DeviceInformationFragment
import com.philips.btserverapp.ExperimentalFragment
import com.philips.btserverapp.ObservationsFragment
import com.philips.btserverapp.PlaceholderFragment
import com.philips.btserver.R

private val TAB_TITLES = arrayOf(
        R.string.tab_text_ble_info,
        R.string.tab_text_observations,
        R.string.tab_text_experimental
        )

class SectionsPagerAdapter(private val context: Context, fm: FragmentManager)
    : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    override fun getItem(position: Int): Fragment {
       return when (position) {
            0 -> DeviceInformationFragment()
            1 -> ObservationsFragment()
            2 -> ExperimentalFragment()
            else -> PlaceholderFragment.newInstance(position)
        }
    }

    override fun getPageTitle(position: Int): CharSequence {
        return context.resources.getString(TAB_TITLES[position])
    }

    override fun getCount(): Int {
        // Show 2 total pages.
        return TAB_TITLES.size;
    }

    fun updatePages() {
        getItem(0).let {
            (it as DeviceInformationFragment).update()
        }
    }
}