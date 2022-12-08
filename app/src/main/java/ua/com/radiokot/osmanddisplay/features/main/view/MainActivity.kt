package ua.com.radiokot.osmanddisplay.features.main.view

import android.Manifest
import android.app.Activity
import android.bluetooth.le.ScanResult
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import com.google.android.material.elevation.SurfaceColors
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.data.storage.ObjectPersistence
import ua.com.radiokot.osmanddisplay.base.util.PermissionManager
import ua.com.radiokot.osmanddisplay.base.view.ToastManager
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.BroadcastingService
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.DisplayCommandSender
import ua.com.radiokot.osmanddisplay.features.broadcasting.model.DisplayCommand
import ua.com.radiokot.osmanddisplay.features.main.data.model.SelectedBleDevice
import ua.com.radiokot.osmanddisplay.features.main.logic.ScanAndSelectBleDeviceUseCase

class MainActivity : AppCompatActivity() {
    sealed class SelectedDevice {
        object Nothing : SelectedDevice()
        class Selected(
            val name: String?,
            val address: String
        ) : SelectedDevice() {
            constructor(selectedBleDevice: SelectedBleDevice) : this(
                name = selectedBleDevice.name,
                address = selectedBleDevice.address,
            )
        }
    }

    private val selectedDevicePersistence: ObjectPersistence<SelectedBleDevice> by inject()

    private val selectedDevice: MutableLiveData<SelectedDevice> =
        MutableLiveData(SelectedDevice.Nothing)

    private val deviceSelectionLauncher =
        registerForActivityResult(StartIntentSenderForResult(), this::onDeviceSelectionResult)

    private val compositeDisposable = CompositeDisposable()

    private val toastManager: ToastManager by inject()

    private val bluetoothConnectPermission: PermissionManager by lazy {
        PermissionManager(Manifest.permission.BLUETOOTH_CONNECT, 332)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initColor()
        initSelectedDeviceDisplay()
        initButtons()
    }

    private fun initColor() {
        window.statusBarColor = SurfaceColors.SURFACE_2.getColor(this)
    }

    private fun initButtons() {
        scan_and_select_device_button.setOnClickListener {
            scanAndSelectDevice()
        }

        start_broadcasting_button.setOnClickListener {
            checkPermissionAndStartBroadcastingService()
        }

        stop_broadcasting_button.setOnClickListener {
            stopBroadcastingService()
        }

        val commandSender: DisplayCommandSender =
            get { parametersOf(selectedDevicePersistence.loadItem()!!.address) }
        val turnTypes = setOf(1, 2, 3, 4, 5, 6, 7, 10, 11)
        val distances = (1..3000)
        send_random_direction_button.setOnClickListener {
            commandSender
                .send(
                    DisplayCommand.ShowDirection(
                        turnType = turnTypes.random(),
                        distanceM = distances.random()
                    )
                )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onComplete = { toastManager.short("Complete") },
                    onError = { toastManager.short("Otsos") }
                )
                .addTo(compositeDisposable)
        }
    }

    private var scanAndSelectDisposable: Disposable? = null
    private fun scanAndSelectDevice() {
        scanAndSelectDisposable?.dispose()
        scanAndSelectDisposable = get<ScanAndSelectBleDeviceUseCase> {
            parametersOf(this)
        }
            .perform()
            .subscribeOn(AndroidSchedulers.mainThread())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                toastManager.short(R.string.scanning_for_devices)
            }
            .subscribeBy(
                onSuccess = {
                    deviceSelectionLauncher.launch(
                        IntentSenderRequest.Builder(it).build()
                    )
                },
                onError = {
                    toastManager.short(R.string.scan_failed)
                    Log.e("MainActivity", "Scan failed", it)
                }
            )
            .addTo(compositeDisposable)
    }

    private fun onDeviceSelectionResult(result: ActivityResult) {
        result
            .data
            ?.takeIf { result.resultCode == Activity.RESULT_OK }
            ?.also { data ->
                val scanResult =
                    data.getParcelableExtra<ScanResult>(CompanionDeviceManager.EXTRA_DEVICE)
                if (scanResult != null) {
                    val selectedBleDevice = SelectedBleDevice(scanResult)
                    selectedDevicePersistence.saveItem(selectedBleDevice)
                    selectedDevice.value = SelectedDevice.Selected(selectedBleDevice)
                }
            }
    }

    private fun initSelectedDeviceDisplay() {
        selectedDevice
            .observe(this) { selectedDevice ->
                when (selectedDevice) {
                    SelectedDevice.Nothing ->
                        selected_device_text_view.text = getString(R.string.no_device_selected)
                    is SelectedDevice.Selected ->
                        selected_device_text_view.text =
                            getString(
                                R.string.template_selected_device_name_address,
                                selectedDevice.name ?: getString(R.string.device_no_name),
                                selectedDevice.address
                            )
                }
            }

        selectedDevicePersistence
            .loadItem()
            ?.let(SelectedDevice::Selected)
            ?.also(selectedDevice::setValue)
    }

    private fun checkPermissionAndStartBroadcastingService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothConnectPermission.check(this, this::startBroadcastingService)
        } else {
            startBroadcastingService()
        }
    }

    private fun startBroadcastingService() {
        val intent = Intent(this, BroadcastingService::class.java)
        startForegroundService(intent)
    }

    private fun stopBroadcastingService() {
        val intent = Intent(this, BroadcastingService::class.java)
        stopService(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        bluetoothConnectPermission.handlePermissionResult(requestCode, permissions, grantResults)
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.dispose()
    }
}