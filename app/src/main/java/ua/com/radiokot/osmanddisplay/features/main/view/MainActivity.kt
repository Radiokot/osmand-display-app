package ua.com.radiokot.osmanddisplay.features.main.view

import android.app.Activity
import android.bluetooth.le.ScanResult
import android.companion.CompanionDeviceManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.activity.result.registerForActivityResult
import androidx.lifecycle.MutableLiveData
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krishna.debug_tools.activity.ActivityDebugTools
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.capture_map_frame_button
import kotlinx.android.synthetic.main.activity_main.clear_imported_tracks_button
import kotlinx.android.synthetic.main.activity_main.clear_screen_button
import kotlinx.android.synthetic.main.activity_main.map_frame_image_view
import kotlinx.android.synthetic.main.activity_main.map_track_text_view
import kotlinx.android.synthetic.main.activity_main.open_dev_tools_button
import kotlinx.android.synthetic.main.activity_main.randomize_bearing_check_box
import kotlinx.android.synthetic.main.activity_main.selected_device_text_view
import kotlinx.android.synthetic.main.activity_main.send_random_direction_button
import kotlinx.android.synthetic.main.activity_main.start_map_broadcasting_button
import kotlinx.android.synthetic.main.activity_main.stop_map_broadcasting_button
import org.koin.android.ext.android.get
import org.koin.android.ext.android.getKoin
import org.koin.core.parameter.parametersOf
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.extension.getNumericProperty
import ua.com.radiokot.osmanddisplay.base.extension.kLogger
import ua.com.radiokot.osmanddisplay.base.extension.setThrottleOnClickListener
import ua.com.radiokot.osmanddisplay.base.view.BaseActivity
import ua.com.radiokot.osmanddisplay.features.broadcasting.logic.DisplayCommandSender
import ua.com.radiokot.osmanddisplay.features.broadcasting.model.DisplayCommand
import ua.com.radiokot.osmanddisplay.features.main.data.model.SelectedBleDevice
import ua.com.radiokot.osmanddisplay.features.main.logic.ScanAndSelectBleDeviceUseCase
import ua.com.radiokot.osmanddisplay.features.map.logic.MapBroadcastingService
import ua.com.radiokot.osmanddisplay.features.map.logic.MapFrameFactory
import ua.com.radiokot.osmanddisplay.features.map.model.LocationData
import ua.com.radiokot.osmanddisplay.features.track.data.model.ImportedTrackRecord
import ua.com.radiokot.osmanddisplay.features.track.logic.ClearImportedTracksUseCase
import ua.com.radiokot.osmanddisplay.features.track.view.ImportedTrackSelectionBottomSheet

class MainActivity : BaseActivity() {
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

    sealed class SelectedTrack {
        object Nothing : SelectedTrack()
        class Selected(
            val name: String,
            val source: ImportedTrackRecord?
        ) : SelectedTrack() {
            constructor(source: ImportedTrackRecord) : this(
                name = source.name,
                source = source,
            )
        }
    }

    private val selectedDevice: MutableLiveData<SelectedDevice> =
        MutableLiveData(SelectedDevice.Nothing)
    private val selectedTrack: MutableLiveData<SelectedTrack> =
        MutableLiveData(SelectedTrack.Nothing)

    private val selectedDeviceAddress: String?
        get() = (selectedDevice.value as? SelectedDevice.Selected)?.address
    private val selectedTrackRecord: ImportedTrackRecord?
        get() = (selectedTrack.value as? SelectedTrack.Selected)?.source

    private val deviceSelectionLauncher =
        registerForActivityResult(StartIntentSenderForResult(), this::onDeviceSelectionResult)

    private val mapBroadcastingPermissionsLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
            arrayOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.POST_NOTIFICATIONS"
            ),
            this::onMapBroadcastingPermissionsGranted
        )

    private val commandSender: DisplayCommandSender
        get() = get { parametersOf(selectedDeviceAddress!!) }

    private val logger = kLogger("MMainActivity")

    private val mapCameraZoom: Double =
        requireNotNull(getKoin().getNumericProperty("mapCameraZoom"))
    private val mapFramePostScale: Double =
        getKoin().getNumericProperty("mapFramePostScale", 1.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initDeviceSelection()
        initTrackSelection()
        initButtons()

        bindMapBroadcastingService()
    }

    private fun initButtons() {
        start_map_broadcasting_button.setOnClickListener {
            mapBroadcastingPermissionsLauncher.launch(Unit)
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

        open_dev_tools_button.setOnClickListener {
            openDevTools()
        }

        capture_map_frame_button.setOnClickListener {
            captureMapFrame()
        }

        clear_imported_tracks_button.setOnClickListener {
            clearImportedTracksWithConfirmation()
        }

        selectedDevice
            .observe(this) { selectedDevice ->
                when (selectedDevice) {
                    SelectedDevice.Nothing -> {
                        start_map_broadcasting_button.isEnabled = false
                        send_random_direction_button.isEnabled = false
                        clear_screen_button.isEnabled = false
                    }

                    is SelectedDevice.Selected -> {
                        start_map_broadcasting_button.isEnabled = true
                        send_random_direction_button.isEnabled = true
                        clear_screen_button.isEnabled = true
                    }
                }
            }
    }

    private fun clearImportedTracksWithConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_imported_tracks)
            .setMessage(R.string.clear_imported_tracks_confirmation)
            .setPositiveButton(R.string.yes) { _, _ ->
                get<ClearImportedTracksUseCase>()
                    .perform()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        selectedTrack.value = SelectedTrack.Nothing
                        toastManager.short(R.string.imported_tracks_cleared)
                    }
                    .addTo(compositeDisposable)
            }
            .setNegativeButton(R.string.no, null)
            .show()
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

    private fun openDevTools() {
        startActivity(Intent(this, ActivityDebugTools::class.java))
    }

    private var captureDisposable: Disposable? = null
    private fun captureMapFrame() {
        captureDisposable?.dispose()

        val mapFrameFactory: MapFrameFactory = get { parametersOf(selectedTrackRecord, false) }

        captureDisposable = mapFrameFactory
            .composeFrame(
                location = LocationData(
                    lng = 35.07203,
                    lat = 48.45664,
                    bearing =
                    if (randomize_bearing_check_box.isChecked)
                        (1..360).random().toDouble()
                    else
                        null,
                ),
                cameraZoom = mapCameraZoom,
                postScale = mapFramePostScale,
            )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnEvent { _, _ ->
                mapFrameFactory.destroy()
            }
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

    private fun initDeviceSelection() {
        selected_device_text_view.setThrottleOnClickListener {
            scanAndSelectDevice()
        }

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
                val scanResult: ScanResult? =
                    data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
                if (scanResult != null) {
                    val selectedBleDevice = SelectedBleDevice(scanResult)
                    selectedDevice.value = SelectedDevice.Selected(selectedBleDevice)
                }
            }
    }

    private fun initTrackSelection() {
        map_track_text_view.setThrottleOnClickListener {
            selectTrack()
        }

        supportFragmentManager.setFragmentResultListener(
            ImportedTrackSelectionBottomSheet.REQUEST_KEY,
            this,
            ImportedTrackSelectionBottomSheet.ResultListener { selectedTrackRecord ->
                selectedTrack.value = SelectedTrack.Selected(selectedTrackRecord)
            }
        )

        selectedTrack
            .observe(this) { selectedTrack ->
                when (selectedTrack) {
                    is SelectedTrack.Nothing -> {
                        map_track_text_view.setText(R.string.no_track)
                    }

                    is SelectedTrack.Selected -> {
                        map_track_text_view.text = selectedTrack.name
                    }
                }
            }
    }

    private fun selectTrack() {
        ImportedTrackSelectionBottomSheet()
            .show(supportFragmentManager, ImportedTrackSelectionBottomSheet.TAG)
    }

    private fun onMapBroadcastingPermissionsGranted(result: Map<String, Boolean>) {
        if (result.values.all { it }) {
            startMapBroadcastingService()
        } else {
            toastManager.short(R.string.error_all_permissions_are_required)
        }
    }

    private val mapBroadcastingServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder?) {
            service as MapBroadcastingService.Binder
            val serviceDeviceAddress = service.deviceAddress

            logger.debug {
                "subscribeToMapBroadcastingService(): map_bc_service_connected:" +
                        "\nservice=$service," +
                        "\nserviceDeviceAddress=$serviceDeviceAddress"
            }

            if (serviceDeviceAddress != null) {
                selectedDevice.postValue(
                    SelectedDevice.Selected(
                        name = getString(R.string.current_device),
                        address = serviceDeviceAddress,
                    )
                )
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            logger.debug {
                "subscribeToMapBroadcastingService(): map_bc_service_disconnected"
            }
        }
    }

    private fun startMapBroadcastingService() {
        val intent = Intent(this, MapBroadcastingService::class.java)
            .putExtras(
                MapBroadcastingService.getBundle(
                    deviceAddress = selectedDeviceAddress!!,
                    track = selectedTrackRecord
                )
            )
        startForegroundService(intent)
    }

    private fun stopMapBroadcastingService() {
        val intent = Intent(this, MapBroadcastingService::class.java)
        try {
            unbindService(mapBroadcastingServiceConnection)
        } catch (_: Exception) {
            // I don't care whether it was bound or not.
        }
        stopService(intent)
    }

    private fun bindMapBroadcastingService() {
        bindService(
            Intent(this, MapBroadcastingService::class.java),
            mapBroadcastingServiceConnection,
            BIND_AUTO_CREATE
        )
    }
}
