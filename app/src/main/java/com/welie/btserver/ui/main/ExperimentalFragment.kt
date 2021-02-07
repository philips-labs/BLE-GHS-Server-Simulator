package com.welie.btserver.ui.main

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.welie.btserver.R
import kotlinx.android.synthetic.main.fragment_experimental.*

/**
 * A simple [Fragment] subclass.
 * Use the [ExperimentalFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ExperimentalFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_experimental, container, false)
    }

}