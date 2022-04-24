/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserverapp

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.philips.btserver.BluetoothServerAdvertisingListener
import com.philips.btserver.gatt.DeviceInformationService
import com.philips.btserver.R
import com.philips.btserver.databinding.FragmentDeviceInformationBinding
import com.philips.btserver.observations.ObservationEmitter
import com.philips.btserver.observations.ObservationStore
import com.philips.btserver.observations.ObservationStoreListener
import com.welie.blessed.BluetoothCentral
import timber.log.Timber

class DeviceInformationFragment : Fragment(), BluetoothServerAdvertisingListener, ObservationStoreListener {

    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private val hander = Handler(Looper.getMainLooper())
    private val deviceInfoService = DeviceInformationService.getInstance()

    private var dialogInputView: EditText? = null

    private var _binding: FragmentDeviceInformationBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?): View {
        _binding = FragmentDeviceInformationBinding.inflate(inflater, container, false)
        ObservationStore.addListener(this)
        return binding.root
    }

    override fun onDestroyView() {
        ObservationStore.removeListener(this)
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        update()
    }

    fun update() {
        // Update can be called via bluetooth starting up, being turned on, etc when view isn't around
        if (_binding == null) return
        binding.btnModelNumName.setOnClickListener { changeModelNumber() }
        binding.btnAdvName.setOnClickListener { changeAdvName() }
        binding.btnClearObsStore.setOnClickListener { ObservationStore.clear() }
        binding.lblAdvName.text = getAdvName()
        binding.lblModelNumber.text = getModelNumber()
        binding.btnToggleAdvertising.text = getString(R.string.startAdvertising)
        binding.txtObservationStoreCount.text = "${ObservationStore.numberOfStoredObservations}"
    }

    private fun getAdvName(): String {
        return try {
            bluetoothAdapter.name
        } catch (e: SecurityException) {
            Timber.i("Security Exception in getting BT adapter name. Check permission logic")
            "Can't get name"
        }
    }

    private fun getModelNumber(): String {
        return deviceInfoService?.getModelNumber() ?: getString(R.string.model_number)
    }

    private fun changeAdvName() {
        doAlertDialog("${getString(R.string.change)} ${getString(R.string.advertisment_name)}", getAdvName()) { _, _ ->
            val newName = dialogInputView?.text.toString()
            try {
                bluetoothAdapter.name = newName
                dialogUpdate("${getString(R.string.advertisment_name)} is $newName")
            } catch (e: SecurityException) {
                Timber.i("Security Exception in setting BT adapter name. Check permission logic")
            }
        }
    }

    private fun changeModelNumber() {
        doAlertDialog("${getString(R.string.change)} ${getString(R.string.model_number)}", getModelNumber()) { _, _ ->
            val newName = dialogInputView?.text.toString()
            deviceInfoService?.setModelNumber(newName)
            dialogUpdate("${getString(R.string.model_number)} is $newName")
        }
    }

    private fun dialogUpdate(toastString: String) {
        hander.postDelayed({ update() }, 5000)
        Toast.makeText(context, toastString, Toast.LENGTH_LONG).show()
    }

    private fun doAlertDialog(title: String, initialText: String, onClick: DialogInterface.OnClickListener) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
        builder.setTitle(title)
        dialogInputView = EditText(context)
        dialogInputView?.inputType = InputType.TYPE_CLASS_TEXT
        dialogInputView?.setText(initialText)
        builder.setView(dialogInputView)

        builder.setPositiveButton("Ok", onClick)
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    override fun onStartAdvertising() {
        TODO("Not yet implemented")
    }

    override fun onStopAdvertising() {
        TODO("Not yet implemented")
    }

    override fun observationStoreChanged() {
        binding.txtObservationStoreCount.text = "${ObservationStore.numberOfStoredObservations}"
    }
}