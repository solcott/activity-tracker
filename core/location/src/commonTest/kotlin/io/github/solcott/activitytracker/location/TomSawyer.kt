package io.github.solcott.activitytracker.location

/**
 * The OSM way for "Rush Trail - Tom Sawyer Section" (way 1420268013, Draper, Utah): 229 nodes about 2 m apart through tight switchbacks,
 * the line a 5 m simplification cut across. © OpenStreetMap contributors, ODbL-1.0.
 */
internal object TomSawyer {
    private const val LINE =
        """gmeflAjh_htEq@zBI^El@?fA?vA[`Pa@~Bu@bCwCdGm@bBY~AIxBInAQ~@O|@Gh@Bj@Jd@\`AXjALfBGx@UhACz@Fn@X^XR^B\OVc@HWFo@@cAB_AP{@r@gA""" +
            """xA}Bp@mALg@Hk@TeATs@\_@^Ob@Bb@ZL|@?t@Wt@m@rAQZg@n@yA`BUn@En@@b@JXXVd@DTI`@c@j@wB^oBTo@RY`@G`@HTTJ^@f@Ez@UbAS~@It@Cl@?t@?""" +
            """z@QrBWhAWj@U`@c@b@SNUNWTMXI\BZD\LVTJZGTOb@a@xCaDh@a@f@Qh@GZBXPLTF\Bn@JvARfAX|@Rn@Tb@\Zb@^f@Fb@LNRF\A`@K^YTY?UGe@Os@k@c@s""" +
            """@a@}@OaAMk@UWu@Um@Ce@NkBf@g@^OVEX@ZD\JXPTVFh@Ct@]v@Wh@A\FVb@Hl@Bp@N~@Rj@b@v@xAlAj@p@NTNd@TjAZ~A`@bAr@hAl@~AhBhEZZ`@f@b@l""" +
            """@h@^^^`@^XFj@Fl@Dl@BtBEd@JXXJf@Ad@Kh@m@`@}Bb@k@JcBPk@@m@Eo@Wo@g@k@q@e@{@[iAi@_CYy@Yc@aBkCoBwBcCaB]Y]_@Uc@Sm@U[[K[C[@e@Hm""" +
            """@PW\E`@?`@JVPNRN\D\PZJZ\v@|@vAdBv@j@R^B^LjBJrAFv@Nt@r@lAp@`Ap@|Ah@fALp@A~@OdA"""

    val points: List<GeoPoint> = checkNotNull(SegmentGeometry.decodePolyline(LINE))

    val samples: List<LocationSample>
        get() = points.map { LocationSample(it.lat, it.lng, null, null, null, kotlin.time.Instant.DISTANT_PAST) }
}
