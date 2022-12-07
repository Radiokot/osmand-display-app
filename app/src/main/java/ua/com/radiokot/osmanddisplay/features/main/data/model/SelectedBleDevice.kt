package ua.com.radiokot.osmanddisplay.features.main.data.model

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class SelectedBleDevice
@JsonCreator
constructor(
    @JsonProperty("name")
    val name: String?,

    @JsonProperty("address")
    val address: String,
) {
    constructor(scanResult: ScanResult) : this(
        name = scanResult.scanRecord?.deviceName,
        address = scanResult.device.address,
    )
}