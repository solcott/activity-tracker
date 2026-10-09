package io.github.solcott.activitytracker.activityfile

/**
 * Subsurface's own logbook (version 3), shaped like a real `.ssrf`: a site, a trip with a nitrox dive (samples carry a temperature only
 * when it changes) and a rebreather dive outside any trip with two computers.
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
}
