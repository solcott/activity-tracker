package io.github.solcott.activitytracker.activityfile

/**
 * Subsurface's own logbook (version 3), shaped like a real `.ssrf`: a site, a trip with a nitrox dive (samples carry a temperature only
 * when it changes) and a rebreather dive outside any trip with two computers. [decoDive] is a trimix dive whose samples carry deco state
 * only when it changes and pressures for two cylinders.
 */
internal object SubsurfaceFixtures {
    fun logbook(dives: String = trip() + ccrDive) =
        """<divelog program='subsurface' version='3'>
        <settings><fingerprint model='4a3b1c2d' serial='7f' deviceid='1a2b' diveid='3c4d' data='00'/></settings>
        <divesites>
        <site uuid='4a3b1c2d' name='Palancar Gardens' gps='20.354000 -87.026000'>
        <geo cat='2' origin='0' value='Mexico'/>
        </site>
        </divesites>
        <dives>
        $dives
        </dives>
        </divelog>"""

    fun trip(date: String = "2024-03-02", time: String = "09:15:00") =
        """<trip date='2024-03-01' time='08:00:00' location='Cozumel'>
        <dive number='41' rating='4' divesiteid='4a3b1c2d' date='$date' time='$time' duration='52:30 min'>
          <buddy>Sam</buddy>
          <cylinder size='11.1 l' workpressure='207.0 bar' description='AL80' o2='32.0%' start='206.8 bar' end='68.9 bar' />
          <divecomputer model='Suunto D5' deviceid='1a2b' diveid='3c4d'>
          <depth max='24.3 m' mean='14.1 m' />
          <temperature water='27.0 C' />
          <sample time='0:10 min' depth='1.5 m' temp='28.0 C' />
          <sample time='10:00 min' depth='24.3 m' temp='27.0 C' />
          <sample time='30:00 min' depth='12.0 m' />
          <sample time='52:20 min' depth='0.0 m' />
          </divecomputer>
        </dive>
        </trip>"""

    const val ccrDive =
        """<dive number='42' date='2024-03-04' time='13:40:05' duration='1:05:00 min'>
          <cylinder size='3.0 l' workpressure='200.0 bar' description='O2' o2='100.0%' />
          <cylinder size='3.0 l' workpressure='200.0 bar' description='diluent' o2='21.0%' he='35.0%' />
          <divecomputer model='Shearwater Petrel' dctype='CCR'>
          <depth max='45.0 m' />
          <temperature water='22.0 C' />
          <event time='0:00 min' type='25' flags='1' name='gaschange' cylinder='1' o2='21.0%' he='35.0%' />
          <sample time='0:10 min' depth='2.0 m' />
          <sample time='20:00 min' depth='45.0 m' />
          <sample time='1:04:50 min' depth='0.0 m' />
          </divecomputer>
          <divecomputer model='Suunto D5'>
          <depth max='44.6 m' />
          <sample time='0:10 min' depth='99.0 m' />
          </divecomputer>
        </dive>"""

    /**
     * A deco dive on two cylinders: NDL 12 min from 10:00, deco from 20:00 (a 3 m stop for 2 min, TTS 5 min, CNS 14%, ppO₂ 1.4) until
     * `in_deco='0'` at 30:00. The first cylinder is read by `pressure0`, the second by `pressure1` with `sensor1` naming it once.
     */
    const val decoDive =
        """<dive number='43' date='2024-03-05' time='10:00:00' duration='40:00 min'>
          <cylinder size='11.1 l' o2='21.0%' he='35.0%' />
          <cylinder size='5.5 l' o2='50.0%' />
          <divecomputer model='Shearwater Perdix'>
          <depth max='50.0 m' />
          <sample time='0:10 min' depth='2.0 m' pressure0='210.0 bar' sensor1='1' pressure1='200.0 bar' />
          <sample time='10:00 min' depth='50.0 m' ndl='12:00 min' pressure0='150.0 bar' />
          <sample time='11:00 min' depth='45.0 m' />
          <sample time='12:00 min' depth='40.0 m' ndl='0:00 min' />
          <sample time='20:00 min' depth='3.0 m' in_deco='1' stopdepth='3.0 m' stoptime='2:00 min' tts='5:00 min' cns='14%' po2='1.40 bar' pressure1='150.0 bar' />
          <sample time='25:00 min' depth='3.0 m' />
          <sample time='30:00 min' depth='3.0 m' in_deco='0' pressure0='90.0 bar' />
          <sample time='39:50 min' depth='0.0 m' />
          </divecomputer>
        </dive>"""
}
