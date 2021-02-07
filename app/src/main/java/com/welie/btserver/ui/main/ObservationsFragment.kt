package com.welie.btserver.ui.main

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.welie.btserver.generichealthservice.ObservationType
import com.welie.btserver.R
import com.welie.btserver.generichealthservice.ObservationEmitter
import kotlinx.android.synthetic.main.fragment_observations.*


/**
 * A simple [Fragment] subclass.
 * Use the [ObservationsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ObservationsFragment : Fragment() {

    private var dialogInputView: EditText? = null

    private var emitterRunning = false;

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_observations, container, false)
    }

    override fun onResume() {
        super.onResume()
        checkboxTempObs?.setOnClickListener { clickTempObs() }
        checkboxHRObs?.setOnClickListener { clickHRObs() }
        checkboxPPGObs?.setOnClickListener { clickPPGObs() }
        checkboxSPO2Obs?.setOnClickListener { clickSPO2Obs() }
        checkboxMergeObs?.setOnClickListener { ObservationEmitter.mergeObservations = checkboxMergeObs.isChecked }
        btnStartStopEmitter?.setOnClickListener { toggleEmitter() }
        btnSingleShotEmit?.setOnClickListener { ObservationEmitter.singleShotEmit() }
        update()
    }

    fun update() {
        checkboxMergeObs.isChecked = ObservationEmitter.mergeObservations
    }

    fun clickTempObs() {
        if (checkboxTempObs.isChecked) {
            ObservationEmitter.addObservationType(ObservationType.MDC_TEMP_BODY)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_TEMP_BODY)
        }
    }

    fun clickHRObs() {
        if (checkboxHRObs.isChecked) {
            ObservationEmitter.addObservationType(ObservationType.MDC_ECG_HEART_RATE)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_ECG_HEART_RATE)
        }
    }

    fun clickPPGObs() {
        if (checkboxPPGObs.isChecked) {
            ObservationEmitter.addObservationType(ObservationType.MDC_PPG_TIME_PD_PP)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_PPG_TIME_PD_PP)
        }
    }

    // TODO: Confirm MDC_SPO2_OXYGENATION_RATIO... just using it for now here and receiver demo app
    fun clickSPO2Obs() {
        if (checkboxSPO2Obs.isChecked) {
            ObservationEmitter.addObservationType(ObservationType.MDC_SPO2_OXYGENATION_RATIO)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_SPO2_OXYGENATION_RATIO)
        }
    }

    private fun toggleEmitter() {
        if (emitterRunning) {
            ObservationEmitter.stopEmitter()
            btnSingleShotEmit.isEnabled = true
            btnStartStopEmitter.text = getString(R.string.startEmitter)
        } else {
            btnSingleShotEmit.isEnabled = false
            ObservationEmitter.emitterPeriod = 1
            ObservationEmitter.startEmitter()
            btnStartStopEmitter.text = getString(R.string.stopEmitter)
        }
        emitterRunning = !emitterRunning
    }

    private fun doAlertDialog(title: String, initialText: String, onClick: DialogInterface.OnClickListener) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
        builder.setTitle(title)
        dialogInputView = EditText(context)
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        dialogInputView?.inputType = InputType.TYPE_CLASS_TEXT // or InputType.TYPE_TEXT_VARIATION_PASSWORD
        dialogInputView?.setText(initialText)
        builder.setView(dialogInputView)

        builder.setPositiveButton("OK", onClick)
        builder.setNegativeButton("Cancel") { dialog, which -> dialog.cancel() }
        builder.show()
    }
}