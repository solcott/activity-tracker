package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ChannelSource
import io.github.solcott.activitytracker.lexicon.app.CombinedChannel
import io.github.solcott.activitytracker.lexicon.app.MemberView
import io.github.solcott.activitytracker.lexicon.app.StreamChannel

/**
 * A kind of data the athlete chooses a combined activity's source for, or leaves out (`feed/combined-sources`): its [channels] (each a
 * [ChannelSource.channel]) always come from the same member, or are all left out. A channel in no kind keeps its default ranking.
 */
enum class DataKind(val channels: List<String>) {
    Map(listOf(CombinedChannel.POSITION)),
    DistanceSpeed(listOf(StreamChannel.DISTANCE, StreamChannel.SPEED)),
    Elevation(listOf(StreamChannel.ALTITUDE)),
    HeartRate(listOf(StreamChannel.HEART_RATE, StreamChannel.RESPIRATION_RATE)),
    Power(
        listOf(
            StreamChannel.POWER,
            StreamChannel.LEFT_RIGHT_BALANCE,
            StreamChannel.TORQUE_EFFECTIVENESS_LEFT,
            StreamChannel.TORQUE_EFFECTIVENESS_RIGHT,
            StreamChannel.PEDAL_SMOOTHNESS_LEFT,
            StreamChannel.PEDAL_SMOOTHNESS_RIGHT,
            StreamChannel.PCO_LEFT,
            StreamChannel.PCO_RIGHT,
        )
    ),
    Cadence(listOf(StreamChannel.CADENCE)),
    Temperature(listOf(StreamChannel.TEMPERATURE, StreamChannel.CORE_TEMPERATURE)),
    RunningDynamics(
        listOf(
            StreamChannel.VERTICAL_OSCILLATION,
            StreamChannel.STANCE_TIME,
            StreamChannel.STEP_LENGTH,
            StreamChannel.VERTICAL_RATIO,
            StreamChannel.STANCE_TIME_BALANCE,
            StreamChannel.FORM_POWER,
            StreamChannel.LEG_SPRING_STIFFNESS,
        )
    ),
    Laps(listOf(CombinedChannel.LAPS, CombinedChannel.SETS, CombinedChannel.LENGTHS)),
    Dive(
        listOf(
            StreamChannel.DEPTH,
            CombinedChannel.DIVE,
            StreamChannel.PP_O2,
            StreamChannel.CNS,
            StreamChannel.N2_LOAD,
            StreamChannel.NDL,
            StreamChannel.DECO_STOP_DEPTH,
            StreamChannel.DECO_STOP_TIME,
            StreamChannel.TTS,
        )
    );

    companion object {
        /** The kind [channel] belongs to; null for one in no kind. */
        fun of(channel: String): DataKind? = entries.firstOrNull { channel in it.channels }
    }
}

/**
 * A kind of data some member of a combined activity recorded: the [members] that have it, best first by the default ranking, and the one it
 * comes from, [chosen], null when the athlete left it out ([excluded]).
 */
data class KindSource(val kind: DataKind, val members: List<AtUri>, val chosen: AtUri?) {
    val excluded: Boolean
        get() = chosen == null
}

/** A combined activity's [kinds] of data and its [members] as recorded, to name each (`feed/combined-sources`). */
data class CombinedSources(val kinds: List<KindSource>, val members: List<MemberView>)

object DataKinds {
    /**
     * Every kind any of [members] recorded, in [DataKind] order, as the ranking [stored] in the combined activity has it. A kind is left
     * out when any of its channels is stored with no members; otherwise it comes from the member ranked first for its first channel any
     * member has.
     */
    fun of(members: List<CombineMember>, stored: List<ChannelSource>): List<KindSource> {
        val defaults = ActivityCombiner.rankings(members)
        val ranked = ActivityCombiner.rankings(members, stored)
        return DataKind.entries.mapNotNull { kind ->
            val present = kind.channels.filter { it in defaults }
            if (present.isEmpty()) return@mapNotNull null
            val having = present.flatMap { defaults.getValue(it) }.distinct()
            val excluded = present.any { ranked[it]?.isEmpty() == true }
            KindSource(kind, having, if (excluded) null else ranked[present.first()]?.firstOrNull())
        }
    }

    /** [members] as the page names them: by device, or by when each started. */
    fun views(members: List<CombineMember>): List<MemberView> = members.map {
        MemberView(it.uri, it.start, it.record.elapsedTime, it.record.sport, it.record.device)
    }

    /**
     * [stored] with [kind]'s channels set to [choice], for the [members] that have them: a member moves first, keeping the rest in order;
     * [SourceChoice.Exclude] stores each with no members; [SourceChoice.Default] drops them, so they're ranked by default again.
     */
    fun choose(members: List<CombineMember>, stored: List<ChannelSource>, kind: DataKind, choice: SourceChoice): List<ChannelSource> {
        val others = stored.filter { it.channel !in kind.channels }
        val present = kind.channels.filter { it in ActivityCombiner.rankings(members) }
        val ranked = ActivityCombiner.rankings(members, stored.filter { it.channel !in kind.channels || it.members.isNotEmpty() })
        val chosen =
            when (choice) {
                SourceChoice.Default -> emptyList()
                SourceChoice.Exclude -> present.map { ChannelSource(it, emptyList()) }
                is SourceChoice.Member ->
                    present.map { channel ->
                        val ranking = ranked[channel].orEmpty()
                        ChannelSource(channel, if (choice.uri in ranking) listOf(choice.uri) + (ranking - choice.uri) else ranking)
                    }
            }
        return others + chosen
    }
}

/** What the athlete chose for a kind of data ([DataKinds.choose]). */
sealed interface SourceChoice {
    /** It comes from [uri] first, the other members after it where [uri] has no data. */
    data class Member(val uri: AtUri) : SourceChoice

    /** It's left out of the combined activity. */
    data object Exclude : SourceChoice

    /** It's included again, ranked by default. */
    data object Default : SourceChoice
}
