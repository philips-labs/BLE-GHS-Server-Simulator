package com.welie.btserver.ui.main

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.welie.btserver.DeviceInformationService
import com.welie.btserver.ObservationType
import com.welie.btserver.R
import com.welie.btserver.generichealthservice.ObservationEmitter
import kotlinx.android.synthetic.main.fragment_generic_health_sensor.*


/**
 * A simple [Fragment] subclass.
 * Use the [GenericHealthSensorFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class GenericHealthSensorFragment : Fragment() {
    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private val deviceInfoService = DeviceInformationService.getInstance()

    private var dialogInputView: EditText? = null

    private var emitterRunning = false;

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_generic_health_sensor, container, false)
    }

    override fun onResume() {
        super.onResume()
        checkboxTempObs?.setOnClickListener { clickTempObs() }
        checkboxHRObs?.setOnClickListener { clickHRObs() }
        checkboxPPGObs?.setOnClickListener { clickPPGObs() }
        btnStartStopEmitter?.setOnClickListener { toggleEmitter() }
        update()
    }

    fun update() {
    }

    fun clickTempObs() {
        if (checkboxTempObs.isChecked) {
            ObservationEmitter.addBodyTempObservation(38.7f)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_TEMP_BODY)
        }
    }

    fun clickHRObs() {
        if (checkboxHRObs.isChecked) {
            ObservationEmitter.addHRObservation(65f)
        } else {
            ObservationEmitter.removeObservationType(ObservationType.MDC_ECG_HEART_RATE)
        }
    }

    fun clickPPGObs() {

        val numberOfCycles = 5
        val samplesPerSecond = 51
        val sampleSeconds = 5
        val buffer = ByteArray(samplesPerSecond * sampleSeconds)
        for (i in 0..buffer.size - 1) {
            // Straight sine function means one cycle every 2*pi samples:
            // buffer[i] = sin(i);
            // Multiply by 2*pi--now it's one cycle per sample:
            // buffer[i] = sin((2 * pi) * i);
            // Multiply by 1,000 samples per second--now it's 1,000 cycles per second:
            // buffer[i] = sin(1000 * (2 * pi) * i);
            // Divide by 44,100 samples per second--now it's 1,000 cycles per 44,100
            // samples, which is just what we needed:
            buffer[i] = (Math.sin(numberOfCycles * (2 * Math.PI) * i / samplesPerSecond) * 255).toInt().toByte()
        }
        // Now create a sample array observation
        val sampleArray = buffer
        ObservationEmitter.addPPGObservation(sampleArray)
    }


    private fun getAdvName(): String {
        return bluetoothAdapter.name
    }

    private fun getModelNumber(): String {
        return deviceInfoService?.getModelNumber() ?: "Not available"
    }

    private fun toggleEmitter() {
        if (emitterRunning) {
            ObservationEmitter.stopEmitter()
            btnStartStopEmitter.text = getString(R.string.startEmitter)
        } else {
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

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment GenericHealthSensorFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance() =
                GenericHealthSensorFragment().apply {}
    }
}