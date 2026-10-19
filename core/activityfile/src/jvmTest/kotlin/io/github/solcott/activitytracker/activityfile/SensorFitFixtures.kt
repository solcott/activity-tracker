package io.github.solcott.activitytracker.activityfile

import com.garmin.fit.ActivityMesg
import com.garmin.fit.AntplusDeviceType
import com.garmin.fit.BleDeviceType
import com.garmin.fit.DateTime
import com.garmin.fit.DeveloperDataIdMesg
import com.garmin.fit.DeveloperField
import com.garmin.fit.DeviceInfoMesg
import com.garmin.fit.FieldDescriptionMesg
import com.garmin.fit.FitBaseType
import com.garmin.fit.HrMesg
import com.garmin.fit.HrvMesg
import com.garmin.fit.Manufacturer
import com.garmin.fit.MesgNum
import com.garmin.fit.SourceType
import com.garmin.fit.Sport as FitSport
import com.garmin.fit.SubSport
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * FIT files with sensor data, written with Garmin's encoder: heart rate in `hr` messages, HRV, developer fields and new record channels.
 */
object SensorFitFixtures {
    const val STRAP_SWIM_SECONDS = 300L

    /**
     * Two minutes of riding on a Fenix 7 with what [FitFixtures.mtbRide] lacks: an ANT+ speed sensor with its ANT device number 51234,
     * transmission type 5 and serial 3912345678, and a Bluetooth heart rate strap that gives no serial. No session profile name.
     */
    fun antSensorRide(start: Instant): ByteArray {
        val startedAt = DateTime(start.toJavaInstant())
        val sensors =
            listOf(
                DeviceInfoMesg().apply {
                    timestamp = startedAt
                    deviceIndex = 1.toShort()
                    sourceType = SourceType.ANTPLUS
                    manufacturer = Manufacturer.WAHOO_FITNESS
                    antplusDeviceType = AntplusDeviceType.BIKE_SPEED
                    antDeviceNumber = 51_234
                    antTransmissionType = 5.toShort()
                    serialNumber = 3_912_345_678L
                },
                DeviceInfoMesg().apply {
                    timestamp = startedAt
                    deviceIndex = 2.toShort()
                    sourceType = SourceType.BLUETOOTH_LOW_ENERGY
                    manufacturer = Manufacturer.POLAR_ELECTRO
                    bleDeviceType = BleDeviceType.HEART_RATE
                },
            )
        return FitFixtures.encode(
            start,
            FitSport.CYCLING,
            SubSport.ROAD,
            distance = 1000f,
            avgHr = 130,
            points = 3,
            interval = 60,
            extra = sensors,
        ) { _, _ ->
        }
    }

    /**
     * A 5-minute pool swim whose records (every 10 s) have no heart rate: a chest strap stored it in `hr` messages, one reading a second at
     * 120 + seconds / 10 bpm. The first message anchors the strap's clock (event time 1000 s) at the start; each later one carries eight
     * readings as offsets from it.
     */
    fun strapSwim(start: Instant): ByteArray {
        val startedAt = DateTime(start.toJavaInstant())
        val anchor = 1000f
        val readings = (0..STRAP_SWIM_SECONDS).map { it to (120 + it / 10).toShort() }
        val messages =
            listOf(
                HrMesg().apply {
                    timestamp = DateTime(startedAt)
                    setEventTimestamp(0, anchor)
                    setFilteredBpm(0, readings.first().second)
                }
            ) +
                readings.drop(1).chunked(8).map { chunk ->
                    HrMesg().apply {
                        chunk.forEachIndexed { i, (seconds, bpm) ->
                            setEventTimestamp(i, anchor + seconds)
                            setFilteredBpm(i, bpm)
                        }
                    }
                }
        return FitFixtures.encode(
            start,
            FitSport.SWIMMING,
            SubSport.LAP_SWIMMING,
            distance = 250f,
            avgHr = 0,
            points = (STRAP_SWIM_SECONDS / 10 + 1).toInt(),
            interval = 10,
            extra = messages,
        ) { i, record ->
            record.distance = i * 25f / 3
        }
    }

    /** Each RR interval [sensorRun]'s `hrv` messages hold, in seconds: a steady 0.5 s beat alternating ±10 ms, with one 4 s dropout. */
    val SENSOR_RUN_RR: List<Float> = List(60) { if (it % 2 == 0) 0.49f else 0.51f }.toMutableList().apply { add(30, 4f) }

    /** [sensorRun]'s UTC offset: its activity message's local timestamp is two hours ahead. */
    const val SENSOR_RUN_OFFSET_SECONDS = 2 * 3600L

    /** [sensorRun]'s session: 4,200 strides. */
    const val SENSOR_RUN_STRIDES = 4200L

    /**
     * A run with a chest strap recording HRV and a Stryd writing Connect IQ developer fields (`Power` 250 W where the record has no native
     * power except on the last record, which has 300 W; `Form Power` 70 W; `Leg Spring Stiffness` 9.5 kN/m), recorded where the device's
     * clock was UTC+2. Its session counts [SENSOR_RUN_STRIDES] strides.
     */
    fun sensorRun(start: Instant): ByteArray {
        val startedAt = DateTime(start.toJavaInstant())
        val points = FitFixtures.RUN_POINTS
        val developer =
            DeveloperDataIdMesg().apply {
                developerDataIndex = 0.toShort()
                repeat(16) { setApplicationId(it, (it + 1).toByte()) }
                applicationVersion = 1L
            }
        fun description(number: Int, name: String, units: String, scale: Int) =
            FieldDescriptionMesg().apply {
                developerDataIndex = 0.toShort()
                fieldDefinitionNumber = number.toShort()
                fitBaseTypeId = FitBaseType.UINT16
                setFieldName(0, name)
                setUnits(0, units)
                if (scale != 1) this.scale = scale.toShort()
                nativeMesgNum = MesgNum.RECORD
            }
        val power = description(0, "Power", "Watts", 1)
        val formPower = description(1, "Form Power", "Watts", 1)
        val stiffness = description(2, "Leg Spring Stiffness", "KN/m", 100)
        val end = DateTime(startedAt).apply { add((points - 1) * 30L) }
        val hrv = SENSOR_RUN_RR.chunked(5).map { chunk -> HrvMesg().apply { chunk.forEachIndexed { i, rr -> setTime(i, rr) } } }
        val activity =
            ActivityMesg().apply {
                timestamp = end
                localTimestamp = end.timestamp + SENSOR_RUN_OFFSET_SECONDS
                numSessions = 1
            }
        return FitFixtures.encode(
            start,
            FitSport.RUNNING,
            SubSport.GENERIC,
            distance = 1000f,
            avgHr = 150,
            points = points,
            before = listOf(developer, power, formPower, stiffness),
            extra = hrv + activity,
            session = { totalCycles = SENSOR_RUN_STRIDES },
        ) { i, record ->
            record.heartRate = 150.toShort()
            record.distance = i * 100f
            if (i == points - 1) record.power = 300
            record.addDeveloperField(DeveloperField(power, developer).apply { value = 250 })
            record.addDeveloperField(DeveloperField(formPower, developer).apply { value = 70 })
            record.addDeveloperField(DeveloperField(stiffness, developer).apply { value = 9.5f })
        }
    }

    /**
     * An e-bike ride with cycling dynamics pedals, a CORE sensor and a Moxy: every record carries left/right torque effectiveness 80/76.5%,
     * pedal smoothness 22/20.5%, PCO -2/+3 mm, core temperature 38.25 °C, SmO₂ 65.5%, total hemoglobin 12.34 g/dL, battery 90 − i%, assist
     * mode 2 and assist level 50%. Its session counts 1,500 crank revolutions.
     */
    fun dynamicsRide(start: Instant): ByteArray =
        FitFixtures.encode(
            start,
            FitSport.E_BIKING,
            SubSport.GENERIC,
            distance = 5000f,
            avgHr = 140,
            points = 5,
            session = { totalCycles = 1500L },
        ) { i, record ->
            record.power = 200
            record.leftTorqueEffectiveness = 80f
            record.rightTorqueEffectiveness = 76.5f
            record.leftPedalSmoothness = 22f
            record.rightPedalSmoothness = 20.5f
            record.leftPco = (-2).toByte()
            record.rightPco = 3.toByte()
            record.coreTemperature = 38.25f
            record.saturatedHemoglobinPercent = 65.5f
            record.totalHemoglobinConc = 12.34f
            record.ebikeBatteryLevel = (90 - i).toShort()
            record.ebikeAssistMode = 2.toShort()
            record.ebikeAssistLevelPercent = 50.toShort()
        }
}
