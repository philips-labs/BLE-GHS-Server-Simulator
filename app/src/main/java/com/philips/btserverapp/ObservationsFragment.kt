/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserverapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.philips.btserver.generichealthservice.ObservationType
import com.philips.btserver.R
import com.philips.btserver.databinding.FragmentObservationsBinding
import com.philips.btserver.generichealthservice.ObservationEmitter

class ObservationsFragment : Fragment() {

    private var emitterRunning = false;

    private var _binding: FragmentObservationsBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentObservationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.checkboxTempObs.setOnClickListener { clickTempObs() }
        binding.checkboxHRObs.setOnClickListener { clickHRObs() }
        binding.checkboxPPGObs.setOnClickListener { clickPPGObs() }
        binding.checkboxSPO2Obs.setOnClickListener { clickSPO2Obs() }
        binding.btnStartStopEmitter.setOnClickListener { toggleEmitter() }
        binding.btnSingleShotEmit.setOnClickListener { ObservationEmitter.singleShotEmit() }
    }

    fun clickTempObs() {
        if (binding.checkboxTempObs.isChecked) {
            ObservationEmitter.addObservationType(ObservationType.MDC_TEMP_BODY)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_TEMP_BODY)
        }
    }

    fun clickHRObs() {
        if (binding.checkboxHRObs.isChecked) {
            ObservationEmitter.addObservationType(ObservationType.MDC_ECG_HEART_RATE)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_ECG_HEART_RATE)
        }
    }

    fun clickPPGObs() {
        if (binding.checkboxPPGObs.isChecked) {
            ObservationEmitter.addObservationType(ObservationType.MDC_PPG_TIME_PD_PP)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_PPG_TIME_PD_PP)
        }
    }

    fun clickSPO2Obs() {
        if (binding.checkboxSPO2Obs.isChecked) {
            ObservationEmitter.addObservationType(ObservationType.MDC_SPO2_OXYGENATION_RATIO)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_SPO2_OXYGENATION_RATIO)
        }
    }

    private fun toggleEmitter() {
        if (emitterRunning) {
            ObservationEmitter.stopEmitter()
            binding.btnSingleShotEmit.isEnabled = true
            binding.btnStartStopEmitter.text = getString(R.string.startEmitter)
        } else {
            binding.btnSingleShotEmit.isEnabled = false
            ObservationEmitter.emitterPeriod = 1
            ObservationEmitter.startEmitter()
            binding.btnStartStopEmitter.text = getString(R.string.stopEmitter)
        }
        emitterRunning = !emitterRunning
    }
}