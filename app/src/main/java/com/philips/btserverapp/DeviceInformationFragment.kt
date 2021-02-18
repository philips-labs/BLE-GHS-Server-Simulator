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
import com.philips.btserver.gatt.DeviceInformationService
import com.welie.btserver.R
import kotlinx.android.synthetic.main.fragment_device_information.*


/**
 * A simple [Fragment] subclass.
 * Use the [DeviceInformationFragment.newInstance] factory method to
 * create an instance of this fragment.
 */

class DeviceInformationFragment : Fragment() {

    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private val hander = Handler(Looper.getMainLooper())
    private val deviceInfoService = DeviceInformationService.getInstance()

    private var dialogInputView: EditText? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_device_information, container, false)


    }

    override fun onResume() {
        super.onResume()
        btnAdvName?.setOnClickListener { changeAdvName() }
        btnModelNumName?.setOnClickListener { changeModelNumber() }
        update()
    }

    fun update() {
        hello?.text = getAdvName()
        hello2?.text = getModelNumber()
    }

    private fun getAdvName(): String {
        return bluetoothAdapter.name
    }

    private fun getModelNumber(): String {
        return deviceInfoService?.getModelNumber() ?: "Model Number"
    }

    private fun changeAdvName() {
        doAlertDialog("Change Adv Name", getAdvName()) { dialog, which ->
            val newName = dialogInputView?.text.toString()
            bluetoothAdapter.name = newName
            // TODO BluetoothServer should facade and also provide listeners for events like name change... also need to persist in UserPrefs
            hander.postDelayed({ update() }, 5000)
            Toast.makeText(context, "Changing Adv Name to $newName", Toast.LENGTH_LONG).show()
        }
    }

    private fun changeModelNumber() {
        doAlertDialog("Change Model #", getModelNumber()) { dialog, which ->
            val newName = dialogInputView?.text.toString()
            deviceInfoService?.setModelNumber(newName)
            // TODO BluetoothServer should facade and also provide listeners for events like name change... also need to persist in UserPrefs
            hander.postDelayed({ update() }, 5000)
            Toast.makeText(context, "Changing Model # to $newName", Toast.LENGTH_LONG).show()
        }
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