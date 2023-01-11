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

/**
 * A simple [Fragment] subclass.
 * Use the [AppLogFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AppLogFragment : Fragment() {

    val logCallback = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            if (propertyId == BR.log) updateLogView()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make the observation log scrollable
        logTextView?.setMovementMethod(ScrollingMovementMethod())
        updateLogView()

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_app_log, container, false)
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

    fun clearAppLog(view: View) {
        AppLog.clear()
    }
}