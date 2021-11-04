package com.philips.btserverapp

import android.os.Bundle
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
import com.philips.btserver.generichealthservice.ObservationEmitter

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
            binding.choiceClockUTCTime.setEnabled(timeChoicesEnabled)
            binding.choiceClockMilliseconds.setEnabled(timeChoicesEnabled)
            binding.choiceClockMilliseconds.setEnabled(timeChoicesEnabled)
            binding.choiceClockIncludesTZ.setEnabled(timeChoicesEnabled)
            binding.choiceClockIncludesDST.setEnabled(timeChoicesEnabled)
            binding.choiceClockManagesTZ.setEnabled(timeChoicesEnabled)
            binding.choiceClockManagesDST.setEnabled(timeChoicesEnabled)
        }
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        // Another interface callback
    }

}