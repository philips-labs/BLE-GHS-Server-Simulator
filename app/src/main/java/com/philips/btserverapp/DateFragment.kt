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
import com.philips.btserver.R
import com.philips.btserver.databinding.FragmentDateBinding
import com.philips.btserver.extensions.*
import com.philips.btserver.generichealthservice.ElapsedTimeService
import com.philips.btserver.util.TimeSource
import com.philips.btserver.util.TimeSourceListener
import timber.log.Timber

class DateFragment : Fragment(), AdapterView.OnItemSelectedListener, TimeSourceListener {

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
        setupTimeSourceSpinner()
        setupClockChoices()
        setupButtons()
        TimeSource.addListener(this)
    }

    override fun onResume() {
        super.onResume()
        updateTimeOptionViews()
        updateTimestampFlags()
    }

    private fun setupClockChoices() {
        binding.choiceClockTickCounter.setOnClickListener { updateTimestampFlags() }
        binding.choiceClockUTCTime.setOnClickListener { updateTimestampFlags() }
        binding.choiceClockMilliseconds.setOnClickListener { updateTimestampFlags() }
        binding.choiceClockIncludesTZ.setOnClickListener { updateTimestampFlags() }
        binding.choiceClockNeedsToBeSet.setOnClickListener { updateClockNeedsSetting() }
    }

    private fun updateTimestampFlags() {
        TimestampFlags.currentFlags = this.timestampFlags
        updateChoiceBoxes()
        updateTimesourceSpinner()
    }

    private fun updateClockNeedsSetting() {
        Timber.i("Updating ETS clock needs to be set flag....")
        val ets = ElapsedTimeService.getInstance()
        if (ets == null) {
         Timber.i("Oops, ETS not found...")
        } else {
            ets.clockNeedsToBeSet = binding.choiceClockNeedsToBeSet.isChecked
        }
        Timber.i("Done updating ETS clock.")
    }

    private fun updateChoiceBoxes() {
        updateTimeViews()
        if (!TimestampFlags.currentFlags.isTimestamp()) startTickCounterDisplay() else startTimeCounterDisplay()
        binding.choiceClockMilliseconds.isChecked = TimestampFlags.currentFlags.isMilliseconds()
        //updateTimeOptionViews()
    }

    private fun updateTimeViews() {
        val timeChoicesEnabled = TimestampFlags.currentFlags.isTimestamp()
        binding.textClockGroup.visibility = if (timeChoicesEnabled) View.VISIBLE else View.GONE
        binding.tickCounterGroup.visibility = if (timeChoicesEnabled) View.GONE else View.VISIBLE
        binding.dateSyncGroup.visibility = if (timeChoicesEnabled) View.VISIBLE else View.INVISIBLE
    }

    private fun updateTimeOptionViews() {
        val timeChoicesEnabled = TimestampFlags.currentFlags.isTimestamp()
        binding.choiceClockUTCTime.setEnabled(timeChoicesEnabled)
        binding.choiceClockUTCTime.isChecked = timeChoicesEnabled && isFlagSet(TimestampFlags.isUTC)
        binding.choiceClockIncludesTZ.setEnabled(timeChoicesEnabled)
        binding.choiceClockIncludesTZ.isChecked = timeChoicesEnabled && isFlagSet(TimestampFlags.isTZPresent)
    }

    private fun setupButtons() {
        binding.btnUpdateClock.setOnClickListener {TimeSource.adjustTimeSourceMillis(3600000) }
        binding.btnResetClock.setOnClickListener { TimeSource.setToCurrentSystemTime() }
    }

    private fun setupTimeSourceSpinner() {
        ArrayAdapter.createFromResource(
            context!!,
            R.array.date_sync_methods_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            binding.dateSyncMethod.adapter = adapter
        }
        binding.dateSyncMethod.onItemSelectedListener = this
        updateTimesourceSpinner()
    }

    private fun updateTimesourceSpinner() {
        binding.dateSyncMethod.setSelection(Timesource.currentSource.value)
    }

    // Made public for ObservationTest
    val timestampFlags: BitMask
        get() {
            var flags = BitMask(TimestampFlags.isCurrentTimeline.bit)
            if (binding.choiceClockMilliseconds.isChecked) {
                flags = flags.plus(TimestampFlags.timeScaleBit1)
            }
            if (binding.choiceClockTickCounter.isChecked) {
                flags = flags.plus(TimestampFlags.isTickCounter)
            } else {
                if (binding.choiceClockUTCTime.isChecked) flags = flags.plus(TimestampFlags.isUTC)
                if (binding.choiceClockIncludesTZ.isChecked) flags = flags.plus(TimestampFlags.isTZPresent)
            }

            return flags
        }

    private fun isFlagSet(flag: TimestampFlags): Boolean {
        return TimestampFlags.currentFlags.hasFlag(flag)
    }

    private fun startTickCounterDisplay() {
        Handler(Looper.getMainLooper()).post(object : Runnable {
            override fun run() {
                TimestampFlags.currentFlags = timestampFlags
//                binding.tickCounter.text = TickCounter.currentTickCounter().toString()
                binding.tickCounter.text = TimeSource.currentTickCounter.toString()
                if (binding.choiceClockTickCounter.isChecked) {
                    Handler(Looper.getMainLooper()).postDelayed(this, 1000)
                }
            }
        })
    }

    private fun startTimeCounterDisplay() {
        Handler(Looper.getMainLooper()).post(object : Runnable {
            override fun run() {
                binding.timerClock?.text = TimeSource.currentDate.toString()
                if (!binding.choiceClockTickCounter.isChecked) {
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

    // AdapterView Interface callback
    override fun onNothingSelected(parent: AdapterView<*>) {}
    override fun onTimeSourceChanged() {
        Timber.i("Timesource changed to: ${Timesource.currentSource.value}")
        updateTimesourceSpinner()
    }

}