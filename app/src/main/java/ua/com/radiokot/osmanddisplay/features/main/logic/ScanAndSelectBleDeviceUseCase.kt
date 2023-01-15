package ua.com.radiokot.osmanddisplay.features.main.logic

import android.bluetooth.le.ScanFilter
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import java.util.*

/**
 * Scans for the required BLE device and, if found,
 * returns a companion device chooser intent sender.
 */
class ScanAndSelectBleDeviceUseCase(
    private val companionDeviceManager: CompanionDeviceManager,
    private val filterServiceUuid: UUID,
) {
    fun perform(): Single<IntentSender> = Single.create { emitter ->
        var isDisposed = false
        emitter.setDisposable(object : Disposable {
            override fun dispose() {
                isDisposed = true
            }

            override fun isDisposed(): Boolean = isDisposed
        })

        companionDeviceManager.associate(
            AssociationRequest.Builder()
                .addDeviceFilter(
                    BluetoothLeDeviceFilter.Builder()
                        .setScanFilter(
                            ScanFilter.Builder()
                                .setServiceUuid(ParcelUuid(filterServiceUuid))
                                .build()
                        )
                        .build()
                )
                .setSingleDevice(true)
                .build(),
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    if (!isDisposed) {
                        emitter.onSuccess(intentSender)
                    }
                }

                override fun onFailure(error: CharSequence?) {
                    if (!isDisposed) {
                        emitter.tryOnError(Exception(error?.toString()))
                    }
                }
            },
            Handler(Looper.getMainLooper())
        )
    }
}