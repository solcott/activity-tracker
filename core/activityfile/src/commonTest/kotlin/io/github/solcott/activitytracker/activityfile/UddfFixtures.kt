package io.github.solcott.activitytracker.activityfile

/**
 * A UDDF 3.2 logbook shaped like MacDive's and Subsurface's exports: a dive computer, a site and two mixes defined once, then two dives in
 * one repetition group that link to them. Everything SI: meters, seconds, Kelvin, Pascal and gas fractions.
 */
internal object UddfFixtures {
    fun logbook(dives: List<String> = listOf(nitroxDive(), decoDive)) =
        """<?xml version="1.0" encoding="utf-8"?>
        <uddf xmlns="http://www.streit.cc/uddf/3.2/" version="3.2.0">
          <generator><name>MacDive</name><version>2.16</version></generator>
          <diver>
            <owner id="owner">
              <personal><firstname>Alex</firstname></personal>
              <equipment>
                <divecomputer id="dc1">
                  <name>My Perdix</name>
                  <manufacturer id="sw"><name>Shearwater</name></manufacturer>
                  <model>Perdix AI</model>
                  <serialnumber>A1B2C3</serialnumber>
                </divecomputer>
              </equipment>
            </owner>
            <buddy id="buddy_sam"><personal><firstname>Sam</firstname><lastname>Rivera</lastname></personal></buddy>
          </diver>
          <divesite>
            <site id="site_bluehole">
              <name>Blue Hole</name>
              <geography><location>Lighthouse Reef</location><latitude>17.3157</latitude><longitude>-87.5346</longitude></geography>
            </site>
          </divesite>
          <gasdefinitions>
            <mix id="ean32"><name>EAN32</name><o2>0.32</o2><he>0.0</he></mix>
            <mix id="tx2135"><name>Tx21/35</name><o2>0.21</o2><he>0.35</he></mix>
            <mix id="ean50"><name>EAN50</name><o2>0.50</o2><he>0.0</he></mix>
          </gasdefinitions>
          <profiledata>
            <repetitiongroup id="rg1">
              ${dives.joinToString("\n")}
            </repetitiongroup>
          </profiledata>
        </uddf>"""

    fun nitroxDive(datetime: String = "2024-05-12T10:02:00") =
        """<dive id="d12">
          <informationbeforedive>
            <link ref="site_bluehole"/>
            <divenumber>12</divenumber>
            <datetime>$datetime</datetime>
            <surfaceintervalbeforedive><infinity/></surfaceintervalbeforedive>
          </informationbeforedive>
          <tankdata>
            <link ref="ean32"/>
            <tankvolume>0.012</tankvolume>
            <tankpressurebegin>20000000</tankpressurebegin>
            <tankpressureend>5000000</tankpressureend>
          </tankdata>
          <samples>
            <waypoint><depth>0</depth><divetime>0</divetime><temperature>299.15</temperature><switchmix ref="ean32"/></waypoint>
            <waypoint><depth>18.5</depth><divetime>600</divetime><temperature>297.15</temperature></waypoint>
            <waypoint><depth>30.1</depth><divetime>1200</divetime></waypoint>
            <waypoint><depth>0</depth><divetime>2700</divetime><temperature>298.15</temperature></waypoint>
          </samples>
          <informationafterdive>
            <greatestdepth>30.1</greatestdepth>
            <averagedepth>17.2</averagedepth>
            <diveduration>2730</diveduration>
            <lowesttemperature>297.15</lowesttemperature>
          </informationafterdive>
        </dive>"""

    /**
     * What logbooks keep beyond the profile: a tank by id with its volume and mix, its pressure, heart rate, ppO₂ (calculated, and three
     * rebreather cells), CNS, no-deco time and a deco stop on the waypoints, a switch to EAN50 at 1500 s, the air temperature, altitude,
     * visibility, no-fly and desaturation times, highest ppO₂ and notes, and a buddy that mustn't be published.
     */
    const val richDive =
        """<dive id="d14">
          <informationbeforedive>
            <link ref="site_bluehole"/>
            <link ref="buddy_sam"/>
            <divenumber>14</divenumber>
            <datetime>2024-05-13T09:00:00</datetime>
            <airtemperature>303.15</airtemperature>
            <altitude>450</altitude>
          </informationbeforedive>
          <tankdata id="tank_main">
            <link ref="ean32"/>
            <tankvolume>0.012</tankvolume>
            <tankpressurebegin>20000000</tankpressurebegin>
            <tankpressureend>6000000</tankpressureend>
          </tankdata>
          <samples>
            <waypoint>
              <depth>0</depth><divetime>0</divetime><switchmix ref="ean32"/>
              <tankpressure ref="tank_main">20000000</tankpressure><heartrate>80</heartrate><calculatedpo2>32000</calculatedpo2>
            </waypoint>
            <waypoint>
              <depth>25</depth><divetime>600</divetime><tankpressure ref="tank_main">15000000</tankpressure><heartrate>95</heartrate>
              <calculatedpo2>130000</calculatedpo2><measuredpo2 ref="c1">118000</measuredpo2><measuredpo2 ref="c2">120000</measuredpo2>
              <measuredpo2 ref="c3">122000</measuredpo2><cns>12</cns><nodecotime>840</nodecotime>
              <alarm>rbt</alarm>
            </waypoint>
            <waypoint>
              <switchmix ref="ean50"/><depth>6</depth><divetime>1500</divetime><decostop kind="mandatory" decodepth="3" duration="180"/>
              <heartrate>90</heartrate>
            </waypoint>
            <waypoint>
              <alarm>ascent</alarm><alarm>wetnotes</alarm><depth>0</depth><divetime>2400</divetime>
              <tankpressure ref="tank_main">6000000</tankpressure>
            </waypoint>
          </samples>
          <informationafterdive>
            <greatestdepth>25</greatestdepth>
            <visibility>15</visibility>
            <noflighttime>64800</noflighttime>
            <desaturationtime>72000</desaturationtime>
            <highestpo2>140000</highestpo2>
            <notes><para>Turtle at the wall</para></notes>
          </informationafterdive>
        </dive>"""

    const val decoDive =
        """<dive id="d13">
          <informationbeforedive>
            <divenumber>13</divenumber>
            <datetime>2024-05-12T14:30:00</datetime>
            <surfaceintervalbeforedive><passedtime>12780</passedtime></surfaceintervalbeforedive>
          </informationbeforedive>
          <samples>
            <waypoint><depth>0</depth><divetime>0</divetime><switchmix ref="tx2135"/></waypoint>
            <waypoint><depth>60</depth><divetime>900</divetime></waypoint>
            <waypoint><depth>21</depth><divetime>2400</divetime><switchmix ref="ean50"/></waypoint>
            <waypoint><depth>0</depth><divetime>3600</divetime></waypoint>
          </samples>
          <informationafterdive><greatestdepth>60</greatestdepth></informationafterdive>
        </dive>"""
}
