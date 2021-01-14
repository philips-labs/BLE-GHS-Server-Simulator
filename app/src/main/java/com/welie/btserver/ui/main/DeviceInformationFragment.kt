package com.welie.btserver.ui.main

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
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
import com.welie.btserver.DeviceInformationService
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_device_information, container, false)


    }

    override fun onResume() {
        super.onResume()
        btnAdvName?.setOnClickListener { changeAdvName() }
        update()
    }

    fun update() {
        hello?.text = bluetoothAdapter.name
        val dis = DeviceInformationService.getInstance()
        hello2?.text = dis?.getModelNumber() ?: "Not available"
    }

    private fun changeAdvName() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
        builder.setTitle("Change Adv Name")
        val input = EditText(context)
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.inputType = InputType.TYPE_CLASS_TEXT // or InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.setText(bluetoothAdapter.name)
        builder.setView(input)

        builder.setPositiveButton("OK") { dialog, which ->
            val newName = input.text.toString()
            bluetoothAdapter.name = newName
            // TODO BluetoothServer should facade and also provide listeners for events like name change... also need to persist in UserPrefs
            hander.postDelayed({ update() }, 5000)
            Toast.makeText(context, "Changing Adv Name to $newName", Toast.LENGTH_LONG).show()
        }
        builder.setNegativeButton("Cancel") { dialog, which -> dialog.cancel() }
        builder.show()
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment DeviceInformationFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance() =
                DeviceInformationFragment().apply {}
    }
}