package ua.com.radiokot.osmanddisplay.features.broadcasting.logic

import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.welie.blessed.*
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.SingleSubject
import io.reactivex.subjects.Subject
import ua.com.radiokot.osmanddisplay.base.extension.encodeAsHexString
import ua.com.radiokot.osmanddisplay.base.extension.kLogger
import ua.com.radiokot.osmanddisplay.features.broadcasting.model.DisplayCommand
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * [DisplayCommandSender] implemented on BLE stack.
 *
 * @param deviceAddress address of the BLE device in form of AA:BB:CC:DD:EE:FF
 * @param serviceUuid UUID of the serial service
 * @param characteristicUuid UUID of the serial service characteristic
 * @param keepAlive if set, the connection will be left opened for further command sends.
 * Otherwise, the connection is opened and closed for each command send
 */
class BleDisplayCommandSender(
    private val deviceAddress: String,
    private val serviceUuid: UUID,
    private val characteristicUuid: UUID,
    private val keepAlive: Boolean,
    private val context: Context,
) : DisplayCommandSender {
    private val logger = kLogger("BLECommandSender")

    private val isBusySubject: Subject<Boolean> = PublishSubject.create()
    override val isBusy: Observable<Boolean> = isBusySubject

    private var connectedPeripheral: BluetoothPeripheral? = null
    private val connectionSubject: BehaviorSubject<Boolean> =
        BehaviorSubject.create()
    private var writtenValueSubject: SingleSubject<ByteArray> = SingleSubject.create()

    private val centralManager: BluetoothCentralManager by lazy {
        BluetoothCentralManager(context, centralManagerCallback, Handler(Looper.getMainLooper()))
    }

    private val centralManagerCallback = object : BluetoothCentralManagerCallback() {
        override fun onConnectedPeripheral(peripheral: BluetoothPeripheral) {
            // We need to wait for service discovery to treat the device as connected.
            // See peripheralCallback below.
        }

        override fun onDisconnectedPeripheral(peripheral: BluetoothPeripheral, status: HciStatus) {
            logger.debug {
                "peripheral_disconnected: " +
                        "address=${peripheral.address}, " +
                        "status=$status"
            }

            connectionSubject.onNext(false)
        }
    }

    private val peripheralCallback = object : BluetoothPeripheralCallback() {
        override fun onServicesDiscovered(peripheral: BluetoothPeripheral) {
            logger.debug {
                "peripheral_connected: " +
                        "address=${peripheral.address}"
            }

            connectedPeripheral = peripheral
            connectionSubject.onNext(true)
        }

        override fun onCharacteristicWrite(
            peripheral: BluetoothPeripheral,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic,
            status: GattStatus
        ) {
            logger.debug {
                "characteristic_written: " +
                        "value=${value.encodeAsHexString()}, " +
                        "\nstatus=$status"
            }

            if (status == GattStatus.SUCCESS) {
                writtenValueSubject.onSuccess(value)
            } else {
                writtenValueSubject.onError(Exception("Unsuccessful GATT status of write: $status"))
            }
        }
    }

    override fun send(command: DisplayCommand): Completable {
        val dataToWrite = command.toByteArray()

        return getConnectedPeripheral()
            .flatMap { peripheral ->
                writtenValueSubject = SingleSubject.create()

                peripheral.writeCharacteristic(
                    serviceUuid,
                    characteristicUuid,
                    dataToWrite,
                    if (command.requiresAcq)
                        WriteType.WITH_RESPONSE
                    else
                        WriteType.WITHOUT_RESPONSE
                )

                logger.debug {
                    "enqueued_characteristic_write: " +
                            "value=${dataToWrite.encodeAsHexString()}"
                }

                writtenValueSubject
            }
            .filter { writtenData -> writtenData.contentEquals(dataToWrite) }
            .timeout(6, TimeUnit.SECONDS)
            .doOnComplete {
                if (!keepAlive) {
                    connectedPeripheral?.also(centralManager::cancelConnection)
                    connectionSubject.onNext(false)

                    logger.debug {
                        "requested_peripheral_connection_cancellation: " +
                                "address=${deviceAddress}"
                    }
                }
            }
            .ignoreElement()
    }

    private fun getConnectedPeripheral(): Single<BluetoothPeripheral> {
        return connectionSubject
            .filter { isConnected -> isConnected && connectedPeripheral != null }
            .firstOrError()
            .map { connectedPeripheral!! }
            .doOnSubscribe {
                if (connectionSubject.value == false || connectedPeripheral == null) {
                    centralManager.connectPeripheral(
                        centralManager.getPeripheral(deviceAddress),
                        peripheralCallback
                    )

                    logger.debug {
                        "requested_peripheral_connection: " +
                                "address=${deviceAddress}"
                    }
                }
            }
    }
}