package ua.com.radiokot.osmanddisplay.features.broadcasting.logic

import android.bluetooth.BluetoothGattCharacteristic
import com.welie.blessed.*
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import ua.com.radiokot.osmanddisplay.features.broadcasting.model.DisplayCommand
import java.util.*

class BleDisplayCommandSender(
    private val deviceAddress: String,
    private val serviceUuid: UUID,
    private val characteristicUuid: UUID,
    private val bluetoothCentralManager: BluetoothCentralManager,
) : DisplayCommandSender {
    private val isBusySubject: Subject<Boolean> = PublishSubject.create()
    override val isBusy: Observable<Boolean> = isBusySubject

    override fun send(command: DisplayCommand): Completable = Completable.create { emitter ->
        isBusySubject.onNext(true)

        bluetoothCentralManager.autoConnectPeripheral(
            bluetoothCentralManager.getPeripheral(deviceAddress),
            object : BluetoothPeripheralCallback() {
                override fun onServicesDiscovered(peripheral: BluetoothPeripheral) {
                    peripheral.writeCharacteristic(
                        serviceUuid,
                        characteristicUuid,
                        command.toByteArray(),
                        WriteType.WITHOUT_RESPONSE
                    )
                }

                override fun onCharacteristicWrite(
                    peripheral: BluetoothPeripheral,
                    value: ByteArray?,
                    characteristic: BluetoothGattCharacteristic,
                    status: GattStatus
                ) {
                    isBusySubject.onNext(false)

                    when (status) {
                        GattStatus.SUCCESS -> {
                            bluetoothCentralManager.cancelConnection(peripheral)
                            emitter.onComplete()
                        }
                        else -> {
                            emitter.tryOnError(Exception("Failed to send the command. Status: $status"))
                        }
                    }
                }
            }
        )
    }
}