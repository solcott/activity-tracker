package io.github.solcott.activitytracker.activityfile

import com.garmin.fit.AntplusDeviceType
import com.garmin.fit.BatteryStatus
import com.garmin.fit.BleDeviceType
import com.garmin.fit.DateTime
import com.garmin.fit.DeviceInfoMesg
import com.garmin.fit.GarminProduct
import com.garmin.fit.LocalDeviceType
import com.garmin.fit.Manufacturer
import com.garmin.fit.Mesg
import com.garmin.fit.SourceType

/**
 * The `device_info` messages of a real Edge 840 ride, for [FitFixtures.mtbRide]: the Edge and its barometer, a Garmin HRM 200 over
 * Bluetooth, and an SRAM power meter and AXS derailleur over ANT+, with that ride's serial numbers, firmware, operating times and battery
 * voltages and, like it, no ANT device numbers. Each device writes a message at the start and the end. As on that ride, the power meter's
 * first message has no serial: it comes in another 18 s in.
 */
object Edge840Devices {
    const val EDGE_SERIAL = 3_510_682_071L
    const val HRM_SERIAL = 3_629_252_158L
    const val POWER_METER_SERIAL = 3_236_233_426L
    const val DERAILLEUR_SERIAL = 1_518_497_200L

    /** The ride's messages: every device at [start], the power meter's serial 18 s in, and every device again [endSeconds] in. */
    fun messages(start: DateTime, endSeconds: Long, hrmBattery: Pair<Int, Int>): List<Mesg> {
        fun at(seconds: Long) = DateTime(start).apply { add(seconds) }
        return devices(at(0), hrmBattery.first, end = false) +
            power(at(18), serial = true, operating = 2_215_232, volts = 1.58984375f) +
            devices(at(endSeconds), hrmBattery.second, end = true)
    }

    private fun devices(at: DateTime, hrmBattery: Int, end: Boolean) =
        listOf(
            device(at, 0, SourceType.LOCAL, Manufacturer.GARMIN, GarminProduct.EDGE_840) {
                serialNumber = EDGE_SERIAL
                softwareVersion = 31.33f
            },
            device(at, 1, SourceType.LOCAL, Manufacturer.GARMIN, GarminProduct.EDGE_840) { localDeviceType = LocalDeviceType.BAROMETER },
            device(at, 2, SourceType.BLUETOOTH_LOW_ENERGY, Manufacturer.GARMIN, GarminProduct.HRM_200) {
                bleDeviceType = BleDeviceType.HEART_RATE
                batteryStatus = BatteryStatus.GOOD
                batteryLevel = hrmBattery.toShort()
                serialNumber = HRM_SERIAL
                softwareVersion = 5.4f
            },
            if (end) power(at, serial = true, operating = 2_221_408, volts = 1.57421875f)
            else power(at, serial = false, operating = 2_215_232, volts = 1.58984375f),
            device(at, 4, SourceType.ANTPLUS, Manufacturer.SRAM, 1060) {
                antplusDeviceType = AntplusDeviceType.SHIFTING
                batteryStatus = BatteryStatus.OK
                serialNumber = DERAILLEUR_SERIAL
                softwareVersion = 2.05f
                cumOperatingTime = if (end) 25_872L else 19_664L
                batteryVoltage = 2.55859375f
            },
        )

    private fun power(at: DateTime, serial: Boolean, operating: Long, volts: Float) =
        device(at, 3, SourceType.ANTPLUS, Manufacturer.SRAM, 1052) {
            antplusDeviceType = AntplusDeviceType.BIKE_POWER
            batteryStatus = BatteryStatus.LOW
            softwareVersion = 1.4f
            cumOperatingTime = operating
            batteryVoltage = volts
            if (serial) serialNumber = POWER_METER_SERIAL
        }

    @Suppress("LongParameterList")
    private fun device(
        timestamp: DateTime,
        index: Int,
        source: SourceType,
        manufacturer: Int,
        product: Int,
        configure: DeviceInfoMesg.() -> Unit = {},
    ) =
        DeviceInfoMesg().apply {
            this.timestamp = timestamp
            deviceIndex = index.toShort()
            sourceType = source
            this.manufacturer = manufacturer
            this.product = product
            configure()
        }
}
