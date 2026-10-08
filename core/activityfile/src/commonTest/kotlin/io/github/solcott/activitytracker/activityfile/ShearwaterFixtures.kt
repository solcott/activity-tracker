package io.github.solcott.activitytracker.activityfile

/**
 * Shearwater Cloud's XML export, trimmed from a real Peregrine TX dive: the whole header, but only a few of its 10-second records. The
 * prolog really does say utf-16 on an ASCII file.
 */
internal object ShearwaterFixtures {
    @Suppress("LongParameterList")
    fun dive(
        records: List<String> =
            listOf(record(0, 3.5, 83.0), record(10_000, 30.0, 82.0), record(20_000, 71.2, 82.0), record(30_000, 0.0, 83.0)),
        imperial: Boolean = true,
        startDate: String = "10/16/2024 5:30:57 PM",
        maxDepth: String = "71.6",
        maxTime: String = "2754",
        product: String = "13",
    ) =
        """<?xml version="1.0" encoding="utf-16"?>
        <dive xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="3">
          <diveLog>
            <number>21</number>
            <gfMin>40</gfMin>
            <gfMax>85</gfMax>
            <surfaceMins>355</surfaceMins>
            <imperialUnits>$imperial</imperialUnits>
            <startBatteryVoltage>4</startBatteryVoltage>
            <startCns>1</startCns>
            <startDate>$startDate</startDate>
            <startO2SensorStatus>false</startO2SensorStatus>
            <computerFirmware>98</computerFirmware>
            <maxDepth>$maxDepth</maxDepth>
            <maxTime>$maxTime</maxTime>
            <errorHistory />
            <endCns>10</endCns>
            <endDate>10/16/2024 6:16:46 PM</endDate>
            <computerSerial>9DA7AB33</computerSerial>
            <computerModel>2</computerModel>
            <logVersion>14</logVersion>
            <decoModel>0</decoModel>
            <startSurfacePressure>1010</startSurfacePressure>
            <product>$product</product>
            <diveLogRecords>
              ${records.joinToString("\n")}
            </diveLogRecords>
          </diveLog>
        </dive>"""

    @Suppress("LongParameterList")
    fun record(timeMillis: Long, depth: Double, waterTemp: Double, o2: Double = 0.32, he: Double = 0.0, circuit: String = "OC/BO") =
        """<diveLogRecord>
            <currentTime>$timeMillis</currentTime>
            <currentDepth>$depth</currentDepth>
            <firstStopDepth>0</firstStopDepth>
            <ttsMins>1</ttsMins>
            <averagePPO2>0.399999976</averagePPO2>
            <fractionO2>$o2</fractionO2>
            <fractionHe>$he</fractionHe>
            <currentNdl>99</currentNdl>
            <currentCircuitSetting>$circuit</currentCircuitSetting>
            <waterTemp>$waterTemp</waterTemp>
            <tank0pressurePSI>AI is off</tank0pressurePSI>
            <tank2pressurePSI>N/A</tank2pressurePSI>
            <gasTime>Not paired</gasTime>
            <sac>AI is off</sac>
          </diveLogRecord>"""
}
