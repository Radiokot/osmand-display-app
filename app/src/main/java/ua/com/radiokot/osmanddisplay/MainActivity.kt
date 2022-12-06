package ua.com.radiokot.osmanddisplay

import android.app.Activity
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MainActivity : AppCompatActivity() {
    private val deviceSelectionLauncher =
        registerForActivityResult(StartIntentSenderForResult(), this::onDeviceSelectionResult)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val companionDeviceManager =
            getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager

        companionDeviceManager.associate(
            AssociationRequest.Builder()
                .addDeviceFilter(
                    BluetoothLeDeviceFilter.Builder()
                        .setScanFilter(
                            ScanFilter.Builder()
                                .setServiceUuid(ParcelUuid(UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")))
                                .build()
                        )
                        .build()
                )
                .setSingleDevice(true)
                .build(),
            object : CompanionDeviceManager.Callback() {
                override fun onDeviceFound(chooserLauncher: IntentSender) {
                    deviceSelectionLauncher.launch(
                        IntentSenderRequest.Builder(chooserLauncher).build()
                    )
                }

                override fun onFailure(error: CharSequence) {
                    Toast.makeText(
                        this@MainActivity,
                        error,
                        Toast.LENGTH_SHORT
                    ).show()
                }

            },
            Handler(Looper.getMainLooper())
        )
    }

    private fun onDeviceSelectionResult(result: ActivityResult) {
        result
            .data
            ?.takeIf { result.resultCode == Activity.RESULT_OK }
            ?.also { data ->
                val scanResult =
                    data.getParcelableExtra<ScanResult>(CompanionDeviceManager.EXTRA_DEVICE)
                if (scanResult != null) {
                    Toast.makeText(this, scanResult.device.address, Toast.LENGTH_SHORT).show()
                }
            }
    }
}