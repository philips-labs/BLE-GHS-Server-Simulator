package com.welie.btserver

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import androidx.viewpager.widget.ViewPager
import androidx.appcompat.app.AppCompatActivity
import com.welie.btserver.generichealthservice.ObservationEmitter
import com.welie.btserver.ui.main.DeviceInformationFragment
import com.welie.btserver.ui.main.SectionsPagerAdapter
import java.util.ArrayList

class MainActivity : AppCompatActivity() {

    var sectionsPagerAdapter: SectionsPagerAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager)
        val viewPager: ViewPager = findViewById(R.id.view_pager)
        viewPager.adapter = sectionsPagerAdapter
        val tabs: TabLayout = findViewById(R.id.tabs)
        tabs.setupWithViewPager(viewPager)
        val fab: FloatingActionButton = findViewById(R.id.fab)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Do some global BLE stuff?", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
        if (!isBluetoothEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        } else {
            checkPermissions()
        }
    }

    private val isBluetoothEnabled: Boolean
        private get() {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return false
            return bluetoothAdapter.isEnabled
        }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val missingPermissions = getMissingPermissions(requiredPermissions)
            if (missingPermissions.size > 0) {
                requestPermissions(missingPermissions, ACCESS_LOCATION_REQUEST)
            } else {
                permissionsGranted()
            }
        }
    }

    private fun getMissingPermissions(requiredPermissions: Array<String>): Array<String> {
        val missingPermissions: MutableList<String> = ArrayList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (requiredPermission in requiredPermissions) {
                if (applicationContext.checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(requiredPermission)
                }
            }
        }
        return missingPermissions.toTypedArray()
    }

    private val requiredPermissions: Array<String>
        private get() {
            val targetSdkVersion = applicationInfo.targetSdkVersion
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q) arrayOf(Manifest.permission.ACCESS_FINE_LOCATION) else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

    private fun permissionsGranted() {
        // Check if Location services are on because they are required to make scanning work
        if (checkLocationServices()) {
            initBluetoothHandler()
        }
    }

    private fun areLocationServicesEnabled(): Boolean {
        val locationManager = applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        return isGpsEnabled || isNetworkEnabled
    }

    private fun checkLocationServices(): Boolean {
        return if (!areLocationServicesEnabled()) {
            AlertDialog.Builder(this@MainActivity)
                    .setTitle("Location services are not enabled")
                    .setMessage("Scanning for Bluetooth peripherals requires locations services to be enabled.") // Want to enable?
                    .setPositiveButton("Enable") { dialogInterface, i ->
                        dialogInterface.cancel()
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                    .setNegativeButton("Cancel") { dialog, which -> // if this button is clicked, just close
                        // the dialog box and do nothing
                        dialog.cancel()
                    }
                    .create()
                    .show()
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Check if all permission were granted
        var allGranted = true
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false
                break
            }
        }
        if (allGranted) {
            permissionsGranted()
        } else {
            AlertDialog.Builder(this@MainActivity)
                    .setTitle("Location permission is required for scanning Bluetooth peripherals")
                    .setMessage("Please grant permissions")
                    .setPositiveButton("Retry") { dialogInterface, i ->
                        dialogInterface.cancel()
                        checkPermissions()
                    }
                    .create()
                    .show()
        }
    }

    private var observationEmitter: ObservationEmitter? = null

    private fun initBluetoothHandler() {
        BluetoothServer.getInstance(applicationContext)
        observationEmitter = ObservationEmitter()
        sectionsPagerAdapter?.getItem(0)?.let {
            (it as DeviceInformationFragment).update()
        }

    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val ACCESS_LOCATION_REQUEST = 2
    }
}