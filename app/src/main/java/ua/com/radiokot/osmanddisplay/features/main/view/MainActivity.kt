package ua.com.radiokot.osmanddisplay.features.main.view

import android.Manifest
import android.app.Activity
import android.bluetooth.le.ScanResult
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.activity.result.registerForActivityResult
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import com.google.android.material.color.MaterialColors
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
import ua.com.radiokot.osmanddisplay.base.util.localfile.OpenLocalFileContract
import ua.com.radiokot.osmanddisplay.base.view.ToastManager
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.DirectionsBroadcastingService
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.DisplayCommandSender
import ua.com.radiokot.osmanddisplay.features.broadcasting.model.DisplayCommand
import ua.com.radiokot.osmanddisplay.features.main.data.model.SelectedBleDevice
import ua.com.radiokot.osmanddisplay.features.main.logic.ScanAndSelectBleDeviceUseCase
import ua.com.radiokot.osmanddisplay.features.map.logic.MapBroadcastingService
import ua.com.radiokot.osmanddisplay.features.map.logic.MapFrameFactory
import ua.com.radiokot.osmanddisplay.features.map.model.LocationData
import ua.com.radiokot.osmanddisplay.features.map.track.view.ImportedTrackSelectionBottomSheet

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

    private val trackFileOpeningLauncher =
        registerForActivityResult(
            OpenLocalFileContract(lazy { contentResolver }),
            setOf(
                "application/geo+json", // Hopeless
                "application/octet-stream"
            ),
            this::onTrackFileOpened
        )

    private val compositeDisposable = CompositeDisposable()

    private val toastManager: ToastManager by inject()

    private val bluetoothConnectPermission: PermissionManager by lazy {
        PermissionManager("android.permission.BLUETOOTH_CONNECT", 332)
    }

    private val locationAndBluetoothConnectPermission: PermissionManager by lazy {
        PermissionManager(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                "android.permission.BLUETOOTH_CONNECT"
            ), 333
        )
    }

    private val commandSender: DisplayCommandSender
        get() = get { parametersOf(selectedDeviceAddress!!) }

    private val mapFrameFactory: MapFrameFactory by inject { parametersOf("track.geojson") }

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
        selected_device_text_view.setOnClickListener {
            scanAndSelectDevice()
        }

        start_directions_broadcasting_button.setOnClickListener {
            checkPermissionAndStartDirectionsBroadcastingService()
        }

        stop_directions_broadcasting_button.setOnClickListener {
            stopDirectionsBroadcastingService()
        }

        map_track_text_view.setOnClickListener {
//            trackFileOpeningLauncher.launch(Unit)
            ImportedTrackSelectionBottomSheet(this)
                .show()
        }

        start_map_broadcasting_button.setOnClickListener {
            checkPermissionAndStartMapBroadcastingService()
        }

        stop_map_broadcasting_button.setOnClickListener {
            stopMapBroadcastingService()
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

        capture_map_frame_button.setOnClickListener {
            captureMapFrame()
        }

        selectedDevice
            .observe(this) { selectedDevice ->
                when (selectedDevice) {
                    SelectedDevice.Nothing -> {
                        start_directions_broadcasting_button.isEnabled = false
                        start_map_broadcasting_button.isEnabled = false
                        send_random_direction_button.isEnabled = false
                        clear_screen_button.isEnabled = false
                    }
                    is SelectedDevice.Selected -> {
                        start_directions_broadcasting_button.isEnabled = true
                        start_map_broadcasting_button.isEnabled = true
                        send_random_direction_button.isEnabled = true
                        clear_screen_button.isEnabled = true
                    }
                }
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

    private var captureDisposable: Disposable? = null
    private fun captureMapFrame() {
        captureDisposable?.dispose()
        captureDisposable = mapFrameFactory
            .composeFrame(
                location = LocationData(
                    lng = 35.07203,
                    lat = 48.45664,
                    bearing = (1..360).random().toDouble(),
                ),
                zoom = 15.3
            )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = this::showMapFrame,
                onError = { toastManager.short("Error") }
            )
            .addTo(compositeDisposable)
    }

    private fun showMapFrame(frame: Bitmap) {
        map_frame_image_view.apply {
            drawable.also {
                if (it is BitmapDrawable) {
                    it.bitmap.recycle()
                }
            }
            visibility = View.VISIBLE
            setImageBitmap(frame)
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

    private fun onTrackFileOpened(result: OpenLocalFileContract.Result) {
        if (result is OpenLocalFileContract.Result.Opened) {
            map_track_text_view.text = result.file.toString()
        }
    }

    private fun initSelectedDeviceDisplay() {
        selectedDevice
            .observe(this) { selectedDevice ->
                when (selectedDevice) {
                    SelectedDevice.Nothing -> {
                        selected_device_text_view.apply {
                            text = getString(R.string.select_display)
                            setTextColor(
                                MaterialColors.getColor(
                                    this,
                                    android.R.attr.textColorSecondary
                                )
                            )
                        }
                    }
                    is SelectedDevice.Selected -> {
                        selected_device_text_view.apply {
                            text =
                                getString(
                                    R.string.template_selected_device_name_address,
                                    selectedDevice.name ?: getString(R.string.device_no_name),
                                    selectedDevice.address
                                )
                            setTextColor(
                                MaterialColors.getColor(
                                    this,
                                    android.R.attr.textColorPrimary
                                )
                            )
                        }
                    }
                }
            }
    }

    private fun checkPermissionAndStartDirectionsBroadcastingService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothConnectPermission.check(this, this::startDirectionsBroadcastingService)
        } else {
            startDirectionsBroadcastingService()
        }
    }

    private fun startDirectionsBroadcastingService() {
        val intent = Intent(this, DirectionsBroadcastingService::class.java)
            .apply {
                putExtras(DirectionsBroadcastingService.getBundle(selectedDeviceAddress!!))
            }
        startForegroundService(intent)
    }

    private fun stopDirectionsBroadcastingService() {
        val intent = Intent(this, DirectionsBroadcastingService::class.java)
        stopService(intent)
    }

    private fun checkPermissionAndStartMapBroadcastingService() {
        locationAndBluetoothConnectPermission.check(this, this::startMapBroadcastingService)
    }

    private fun startMapBroadcastingService() {
        val intent = Intent(this, MapBroadcastingService::class.java)
            .apply {
                putExtras(MapBroadcastingService.getBundle(selectedDeviceAddress!!))
            }
        startForegroundService(intent)
    }

    private fun stopMapBroadcastingService() {
        val intent = Intent(this, MapBroadcastingService::class.java)
        stopService(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        bluetoothConnectPermission.handlePermissionResult(requestCode, permissions, grantResults)
        locationAndBluetoothConnectPermission.handlePermissionResult(
            requestCode,
            permissions,
            grantResults
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.dispose()
        mapFrameFactory.destroy()
    }
}