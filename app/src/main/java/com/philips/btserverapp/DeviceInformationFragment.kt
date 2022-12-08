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
import android.widget.Switch
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.philips.btserver.BluetoothServer
import com.philips.btserver.BluetoothServerAdvertisingListener
import com.philips.btserver.gatt.DeviceInformationService
import com.philips.btserver.R
import com.philips.btserver.databinding.FragmentDeviceInformationBinding
import timber.log.Timber

class DeviceInformationFragment : Fragment(), BluetoothServerAdvertisingListener {

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
        return binding.root
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
        binding.toggleMultiClientConnect.setOnClickListener {
            Timber.i("${if ((it as Switch).isChecked) "Allowing" else "Disallowing"} multiple connections")
            (activity as MainActivity).allowMultipleClientConnections = (it as Switch).isChecked
        }
        binding.lblAdvName.text = getAdvName()
        binding.lblModelNumber.text = getModelNumber()
        binding.btnToggleAdvertising.text = getString(R.string.startAdvertising)
        binding.toggleMultiClientConnect.isChecked = (activity as MainActivity).allowMultipleClientConnections
    }

    private fun getAdvName(): String {
        return BluetoothServer.getInstance()?.getAdvertisingName() ?: "---"
    }

    private fun getModelNumber(): String {
        return deviceInfoService?.modelNumber ?: getString(R.string.model_number)
    }

    private fun changeAdvName() {
        doAlertDialog("${getString(R.string.change)} ${getString(R.string.advertisment_name)}", getAdvName()) { _, _ ->
            val newName = dialogInputView?.text.toString()
            try {
                BluetoothServer.getInstance()?.let {
                    it.setAdvertisingName(newName)
                    dialogUpdate("${getString(R.string.advertisment_name)} is $newName")
                }
            } catch (e: SecurityException) {
                Timber.i("Security Exception in setting BT adapter name. Check permission logic")
            }
        }
    }

//    fun toggleMultiClientConnect(view: View) {
//        (activity as MainActivity).allowMultipleClientConnections = (view as Switch).isChecked
//    }

    private fun changeModelNumber() {
        doAlertDialog("${getString(R.string.change)} ${getString(R.string.model_number)}", getModelNumber()) { _, _ ->
            val newName = dialogInputView?.text.toString()
            deviceInfoService?.let { it.modelNumber = newName }
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

}