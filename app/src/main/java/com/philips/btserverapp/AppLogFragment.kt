package com.philips.btserverapp

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.databinding.Observable
import com.philips.btserver.R
import com.philips.btserver.BR
import com.philips.btserver.databinding.FragmentAppLogBinding
import com.philips.btserver.databinding.FragmentUsersBinding

/**
 * A simple [Fragment] subclass.
 * Use the [AppLogFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AppLogFragment : Fragment() {

    private var _binding: FragmentAppLogBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    val logCallback = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            if (propertyId == BR.log) updateLogView()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        inflater.inflate(R.layout.fragment_app_log, container, false)
        _binding = FragmentAppLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.clearLogButton.setOnClickListener {
            AppLog.clear()
            updateLogView()
        }
        // Make the observation log scrollable
        logTextView?.setMovementMethod(ScrollingMovementMethod())
        updateLogView()
    }

    override fun onResume() {
        super.onResume()
        AppLog.addOnPropertyChangedCallback(logCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLog.removeOnPropertyChangedCallback(logCallback)
    }

    private fun updateLogView() {
        logTextView?.setText(AppLog.log)
    }

    private val logTextView : TextView? get()  {
        return activity?.findViewById(R.id.appLog)
    }
}