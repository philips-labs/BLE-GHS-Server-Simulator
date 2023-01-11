/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserverapp

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.philips.btserver.R

class SectionsPagerAdapter(private val context: Context, fm: FragmentManager)
    : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    private val TAB_TITLES = arrayOf(
        R.string.tab_text_observations,
        R.string.tab_text_date,
        R.string.tab_text_ble_info,
        R.string.tab_text_users,
        R.string.tab_text_log
    )

    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> ObservationsFragment()
            1 -> DateFragment()
            2 -> DeviceInformationFragment()
            3 -> UsersFragment()
            4 -> AppLogFragment()
            else -> error(R.string.invalid_section_number)
        }
    }

    override fun getPageTitle(position: Int): CharSequence {
        return context.resources.getString(TAB_TITLES[position])
    }

    override fun getCount(): Int {
        return TAB_TITLES.size;
    }

    fun updatePages() {
        getItem(2).let {
            (it as DeviceInformationFragment).update()
        }
    }
}