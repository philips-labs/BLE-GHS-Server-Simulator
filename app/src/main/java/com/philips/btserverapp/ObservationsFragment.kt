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
import com.philips.btserver.observations.ObservationType
import com.philips.btserver.R
import com.philips.btserver.databinding.FragmentObservationsBinding
import com.philips.btserver.observations.ObservationEmitter
import com.philips.btserver.observations.ObservationStore
import com.philips.btserver.observations.ObservationStoreListener

class ObservationsFragment : Fragment(), ObservationStoreListener {

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
        ObservationStore.addListener(this)

        ObservationEmitter.addObservationType(ObservationType.MDC_ECG_HEART_RATE)
        binding.checkboxHRObs.isChecked = true

        binding.checkboxTempObs.setOnClickListener { clickTempObs() }
        binding.checkboxHRObs.setOnClickListener { clickHRObs() }
        binding.checkboxPPGObs.setOnClickListener { clickPPGObs() }
        binding.checkboxSPO2Obs.setOnClickListener { clickSPO2Obs() }
        binding.checkboxBPObs.setOnClickListener { clickBPObs() }
        binding.checkboxDiscreteObs.setOnClickListener { clickDiscreteObs() }
        binding.checkboxStringObs.setOnClickListener { clickStringObs() }
        binding.checkboxTLVObs.setOnClickListener { clickTLVObs() }
        binding.checkboxCompoundDiscreteObs.setOnClickListener { clickCompoundDiscreteObs() }
        binding.checkboxCompoundStateEventObs.setOnClickListener { clickCompoundStateEventObs() }
        binding.checkboxBundleObs.setOnClickListener { clickBundleObs() }
        binding.btnStartStopEmitter.setOnClickListener { toggleEmitter() }
        binding.btnSingleShotEmit.setOnClickListener { ObservationEmitter.singleShotEmit() }
        binding.btnClearObsStore.setOnClickListener { ObservationStore.clear() }
        binding.btnAddStored.setOnClickListener { ObservationEmitter.addStoredObservation() }
        binding.btnTriggerObservationSchedule.setOnClickListener {
            ObservationEmitter.setObservationSchedule(
                ObservationType.MDC_ECG_HEART_RATE,
                4f,
                4f) }
//        updateObservationCount()
//        updateEmitterButton()
        checkIfCanBundle()
    }

    override fun onDestroyView() {
        ObservationStore.removeListener(this)
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        updateObservationCount()
        updateEmitterButton()
    }

    fun clickTempObs() {
        if (binding.checkboxTempObs.isChecked) {
            ObservationEmitter.addObservationType(ObservationType.MDC_TEMP_BODY)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_TEMP_BODY)
        }
        checkIfCanBundle()
    }

    fun clickHRObs() {
        if (binding.checkboxHRObs.isChecked) {
            ObservationEmitter.addObservationType(ObservationType.MDC_ECG_HEART_RATE)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_ECG_HEART_RATE)
        }
        checkIfCanBundle()
    }

    fun clickPPGObs() {
        if (binding.checkboxPPGObs.isChecked) {
            ObservationEmitter.addObservationType(ObservationType.MDC_PPG_TIME_PD_PP)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_PPG_TIME_PD_PP)
        }
        checkIfCanBundle()
    }

    fun clickSPO2Obs() {
        if (binding.checkboxSPO2Obs.isChecked) {
            ObservationEmitter.addObservationType(ObservationType.MDC_PULS_OXIM_SAT_O2)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_PULS_OXIM_SAT_O2)
        }
        checkIfCanBundle()
    }

    fun clickBPObs() {
        if (binding.checkboxBPObs.isChecked) {
            ObservationEmitter.addObservationType(ObservationType.MDC_PRESS_BLD_NONINV)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_PRESS_BLD_NONINV)
        }
        checkIfCanBundle()
    }

    fun clickDiscreteObs() {
        if (binding.checkboxDiscreteObs.isChecked) {
            ObservationEmitter.addObservationType(ObservationType.MDC_ATTR_ALERT_TYPE)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_ATTR_ALERT_TYPE)
        }
        checkIfCanBundle()
    }

    fun clickStringObs() {
        if (binding.checkboxStringObs.isChecked) {
            ObservationEmitter.addObservationType(ObservationType.MDC_DRUG_NAME_LABEL)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_DRUG_NAME_LABEL)
        }
        checkIfCanBundle()
    }

    fun clickCompoundDiscreteObs() {
        if (binding.checkboxCompoundDiscreteObs.isChecked) {
            ObservationEmitter.addObservationType(ObservationType.MDC_DEV_PUMP_PROGRAM_STATUS)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_DEV_PUMP_PROGRAM_STATUS)
        }
        checkIfCanBundle()
    }


    fun clickCompoundStateEventObs() {
        if (binding.checkboxCompoundStateEventObs.isChecked) {
            ObservationEmitter.addObservationType(ObservationType.MDC_ATTR_ALARM_STATE)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_ATTR_ALARM_STATE)
        }
        checkIfCanBundle()
    }

    fun clickTLVObs() {
        if (binding.checkboxTLVObs.isChecked) {
            ObservationEmitter.addObservationType(ObservationType.MDC_DOSE_DRUG_DELIV)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_DOSE_DRUG_DELIV)
        }
        checkIfCanBundle()
    }

    fun clickBundleObs() {
        ObservationEmitter.bundleObservations = binding.checkboxBundleObs.isChecked
    }

    private fun checkIfCanBundle() {
        if (ObservationEmitter.observationTypes.size > 1) {
            binding.checkboxBundleObs.isEnabled = true
        } else {
            binding.checkboxBundleObs.isChecked = false
            binding.checkboxBundleObs.isEnabled = false
        }
    }

    private fun toggleEmitter() {
        if (ObservationEmitter.isEmitting) {
            ObservationEmitter.stopEmitter()
            binding.btnSingleShotEmit.isEnabled = true
        } else {
            binding.btnSingleShotEmit.isEnabled = false
            ObservationEmitter.emitterPeriod = 1
            ObservationEmitter.startEmitter()
        }
        updateEmitterButton()
    }


    private fun triggerObservationScheduleChange() {
        ObservationEmitter.setObservationSchedule(ObservationType.MDC_ECG_HEART_RATE, 4f, 4f)
    }

    private fun updateEmitterButton() {
        binding.btnStartStopEmitter.text =
            if (ObservationEmitter.isEmitting)
                getString(R.string.stopEmitter)
            else
                getString(R.string.startEmitter)

    }

    private fun updateObservationCount() {
        binding.txtObservationStoreCount.text = "${ObservationStore.numberOfStoredObservations}"
    }

    override fun observationStoreChanged() { updateObservationCount() }

}