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
                </divecomputer>
              </equipment>
            </owner>
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
