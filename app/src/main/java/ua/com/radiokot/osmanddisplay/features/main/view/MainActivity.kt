package ua.com.radiokot.osmanddisplay.features.main.view

import android.app.Activity
import android.bluetooth.le.ScanResult
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import mu.KotlinLogging
import org.koin.android.ext.android.get
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.util.PermissionManager
import ua.com.radiokot.osmanddisplay.base.view.ToastManager
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.BroadcastingService
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.DisplayCommandSender
import ua.com.radiokot.osmanddisplay.features.broadcasting.model.DisplayCommand
import ua.com.radiokot.osmanddisplay.features.main.data.model.SelectedBleDevice
import ua.com.radiokot.osmanddisplay.features.main.logic.ScanAndSelectBleDeviceUseCase
import ua.com.radiokot.osmanddisplay.features.map.view.MapActivity

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

    private val selectedDevice: MutableLiveData<SelectedDevice> =
        MutableLiveData(SelectedDevice.Nothing)

    private val selectedDeviceAddress: String?
        get() = (selectedDevice.value as? SelectedDevice.Selected)?.address

    private val deviceSelectionLauncher =
        registerForActivityResult(StartIntentSenderForResult(), this::onDeviceSelectionResult)

    private val compositeDisposable = CompositeDisposable()

    private val toastManager: ToastManager by inject()

    private val bluetoothConnectPermission: PermissionManager by lazy {
        PermissionManager("android.permission.BLUETOOTH_CONNECT", 332)
    }

    private val commandSender: DisplayCommandSender
        get() = get { parametersOf(selectedDeviceAddress!!) }

    private val logger = KotlinLogging.logger("MainActivity@${hashCode()}")

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
        scan_and_select_display_button.setOnClickListener {
            scanAndSelectDevice()
        }

        start_broadcasting_button.setOnClickListener {
            checkPermissionAndStartBroadcastingService()
        }

        stop_broadcasting_button.setOnClickListener {
            stopBroadcastingService()
        }

        send_random_direction_button.setOnClickListener {
            sendRandomDirection()
        }

        clear_screen_button.setOnClickListener {
            clearTheScreen()
        }

        open_osm_and_button.setOnClickListener {
            openOsmAnd()
        }

        open_map_button.setOnClickListener {
            openMap()
        }
    }

    private val turnTypes = setOf(1, 2, 3, 4, 5, 6, 7, 10, 11)
    private val distances = (1..3000)
    private var randomDirectionDisposable: Disposable? = null
    private fun sendRandomDirection() {
        randomDirectionDisposable?.dispose()
        randomDirectionDisposable = commandSender
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
                onError = { toastManager.short("Error") }
            )
            .addTo(compositeDisposable)
    }

    private var clearTheScreenDisposable: Disposable? = null
    private fun clearTheScreen() {
        clearTheScreenDisposable?.dispose()
        clearTheScreenDisposable = commandSender
            .send(DisplayCommand.ClearScreen)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onComplete = { toastManager.short("Complete") },
                onError = { toastManager.short("Error") }
            )
            .addTo(compositeDisposable)
    }

    private fun openOsmAnd() {
        packageManager.getLaunchIntentForPackage(getKoin().getProperty("osmAndPackage")!!)
            ?.also(this::startActivity)
    }

    private fun openMap() {
        startActivity(
            Intent(this, MapActivity::class.java)
                .putExtras(MapActivity.getBundle(selectedDeviceAddress))
        )
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
                    logger.error(it) {
                        "scan_failed"
                    }
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
                    selectedDevice.value = SelectedDevice.Selected(selectedBleDevice)
                }
            }
    }

    private fun initSelectedDeviceDisplay() {
        selectedDevice
            .observe(this) { selectedDevice ->
                when (selectedDevice) {
                    SelectedDevice.Nothing -> {
                        selected_device_text_view.text = getString(R.string.no_display_selected)
                        start_broadcasting_button.isEnabled = false
                        send_random_direction_button.isEnabled = false
                        clear_screen_button.isEnabled = false
                    }
                    is SelectedDevice.Selected -> {
                        selected_device_text_view.text =
                            getString(
                                R.string.template_selected_device_name_address,
                                selectedDevice.name ?: getString(R.string.device_no_name),
                                selectedDevice.address
                            )

                        start_broadcasting_button.isEnabled = true
                        send_random_direction_button.isEnabled = true
                        clear_screen_button.isEnabled = true
                    }
                }
            }
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
            .apply {
                putExtras(BroadcastingService.getBundle(selectedDeviceAddress!!))
            }
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