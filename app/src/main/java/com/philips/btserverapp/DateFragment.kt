package com.philips.btserverapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.philips.btserver.R
import com.philips.btserver.databinding.FragmentDateBinding
import com.philips.btserver.databinding.FragmentObservationsBinding
import com.philips.btserver.extensions.*
import com.philips.btserver.generichealthservice.Observation
import com.philips.btserver.generichealthservice.ObservationEmitter
import java.sql.Time

class DateFragment : Fragment(), AdapterView.OnItemSelectedListener {

    private var _binding: FragmentDateBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentDateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.dateSyncMethod
        ArrayAdapter.createFromResource(
            this.context!!,
            R.array.date_sync_methods_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            binding.dateSyncMethod.adapter = adapter
        }
        binding.dateSyncMethod.onItemSelectedListener = this
        binding.choiceClockTickCounter.setOnClickListener {
            val timeChoicesEnabled = !binding.choiceClockTickCounter.isChecked
            binding.textClockGroup.visibility = if (timeChoicesEnabled) View.VISIBLE else View.GONE
            binding.tickCounterGroup.visibility = if (timeChoicesEnabled) View.GONE else View.VISIBLE
            binding.dateSyncGroup.visibility = if (timeChoicesEnabled) View.VISIBLE else View.INVISIBLE

            if (!timeChoicesEnabled) startTickCounterDisplay()

            binding.choiceClockUTCTime.setEnabled(timeChoicesEnabled)
            binding.choiceClockUTCTime.isChecked = timeChoicesEnabled && binding.choiceClockUTCTime.isChecked
//            binding.choiceClockMilliseconds.setEnabled(timeChoicesEnabled)
            binding.choiceClockIncludesTZ.setEnabled(timeChoicesEnabled)
            binding.choiceClockIncludesTZ.isChecked = timeChoicesEnabled && binding.choiceClockIncludesTZ.isChecked
            binding.choiceClockIncludesDST.setEnabled(timeChoicesEnabled)
            binding.choiceClockIncludesDST.isChecked = timeChoicesEnabled && binding.choiceClockIncludesDST.isChecked
            binding.choiceClockManagesTZ.setEnabled(timeChoicesEnabled)
            binding.choiceClockManagesTZ.isChecked = timeChoicesEnabled && binding.choiceClockManagesTZ.isChecked
            binding.choiceClockManagesDST.setEnabled(timeChoicesEnabled)
            binding.choiceClockManagesDST.isChecked = timeChoicesEnabled && binding.choiceClockManagesDST.isChecked

            TimestampFlags.currentFlags = this.timestampFlags
        }
    }

    // Made public for ObservationTest
    val timestampFlags: BitMask
        get() {
            var flags = BitMask(TimestampFlags.isCurrentTimeline.bit)
            if (binding.choiceClockMilliseconds.isChecked) flags = flags.plus(TimestampFlags.isMilliseconds)
            if (binding.choiceClockTickCounter.isChecked) {
                flags = flags.plus(TimestampFlags.isTickCounter)
            } else {
                if (binding.choiceClockUTCTime.isChecked) flags = flags.plus(TimestampFlags.isUTC)
                if (binding.choiceClockIncludesTZ.isChecked) flags = flags.plus(TimestampFlags.isTZPresent)
                if (binding.choiceClockIncludesDST.isChecked) flags = flags.plus(TimestampFlags.isDSTPresent)
            }

            return flags
        }

    private fun startTickCounterDisplay() {
        Handler(Looper.getMainLooper()).post(object : Runnable {
            override fun run() {
                TimestampFlags.currentFlags = timestampFlags
                binding.tickCounter.text = SystemClock.elapsedRealtime().toString()
                if (binding.choiceClockTickCounter.isChecked) {
                    Handler(Looper.getMainLooper()).postDelayed(this, 1000)
                }
            }
        })
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
        // An item was selected. You can retrieve the selected item using
        System.out.println("onItemSelected: ${parent.getItemAtPosition(pos)} pos: $pos timesource: ${Timesource.value(pos)}")
        Timesource.currentSource = Timesource.value(pos)
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        // Another interface callback
    }

}