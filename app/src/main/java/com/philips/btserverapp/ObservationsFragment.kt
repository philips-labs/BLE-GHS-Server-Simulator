/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserverapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.fragment.app.Fragment
import com.philips.btserver.observations.ObservationType
import com.philips.btserver.R
import com.philips.btserver.databinding.FragmentObservationsBinding
import com.philips.btserver.gatt.BatteryService
import com.philips.btserver.generichealthservice.ReconnectionConfigurationService
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
        ObservationEmitter.reset()
        ObservationEmitter.addObservationType(ObservationType.MDC_ECG_HEART_RATE)

        binding.checkboxObsTempStore.isChecked = ObservationStore.isTemporaryStore

        setupCheckBox(binding.checkboxTempObs, ObservationType.MDC_TEMP_BODY)
        setupCheckBox(binding.checkboxHRObs, ObservationType.MDC_ECG_HEART_RATE)
        setupCheckBox(binding.checkboxPPGObs, ObservationType.MDC_PPG_TIME_PD_PP)
        setupCheckBox(binding.checkboxSPO2Obs, ObservationType.MDC_PULS_OXIM_SAT_O2)
        setupCheckBox(binding.checkboxBPObs, ObservationType.MDC_PRESS_BLD_NONINV)
        setupCheckBox(binding.checkboxDiscreteObs, ObservationType.MDC_ATTR_ALERT_TYPE)
        setupCheckBox(binding.checkboxStringObs, ObservationType.MDC_DRUG_NAME_LABEL)
        setupCheckBox(binding.checkboxTLVObs, ObservationType.MDC_DOSE_DRUG_DELIV)
        setupCheckBox(binding.checkboxCompoundDiscreteObs, ObservationType.MDC_DEV_PUMP_PROGRAM_STATUS)
        setupCheckBox(binding.checkboxCompoundStateEventObs, ObservationType.MDC_ATTR_ALARM_STATE)

        binding.checkboxBundleObs.setOnClickListener { clickBundleObs() }
        binding.checkboxObsTempStore.setOnClickListener { clickTemporaryObsStore() }
        binding.btnStartStopEmitter.setOnClickListener { toggleEmitter() }
        binding.btnSingleShotEmit.setOnClickListener { ObservationEmitter.singleShotEmit() }
        binding.btnClearObsStore.setOnClickListener { ObservationStore.clear() }
        binding.btnAddStored.setOnClickListener { ObservationEmitter.addStoredObservation() }
        binding.btnReadyForDisconnect.setOnClickListener { ReconnectionConfigurationService.getInstance().readyForDisconnect() }
        binding.btnTriggerObservationSchedule.setOnClickListener {
            ObservationEmitter.setObservationSchedule(
                ObservationType.MDC_ECG_HEART_RATE,
                4f,
                4f) }
        binding.btnUpdateBattery.setOnClickListener { BatteryService.getInstance().updateAndNotifyBatteryLevel() }
//        updateObservationCount()
//        updateEmitterButton()
        checkIfCanBundle()
    }

    private fun setupCheckBox(checkBox: CheckBox, observationType: ObservationType) {
        checkBox.isChecked = ObservationEmitter.observationTypes.contains(observationType)
        checkBox.setOnClickListener {
            if (checkBox.isChecked) {
                ObservationEmitter.addObservationType(observationType)
            } else {
                ObservationEmitter.removeObservationType(observationType)
            }
            checkIfCanBundle()
        }
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

    fun clickTemporaryObsStore() {
        ObservationStore.isTemporaryStore = binding.checkboxObsTempStore?.isChecked ?: false
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