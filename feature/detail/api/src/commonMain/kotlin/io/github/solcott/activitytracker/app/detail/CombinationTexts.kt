package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.activity.DataKind
import io.github.solcott.activitytracker.lexicon.app.MemberView

/**
 * What the owner's combination editor says and offers (feed/combined-sources, dev/shared-display-text): Edit beside the members, the
 * sheet's title and Done, each member's Remove and its confirmation, and each kind of data's row. The Compose sheet and the native one ask
 * these.
 */
@Suppress("TooManyFunctions") // One per line of the sheet, so both UIs ask the same.
object CombinationTexts {
    val edit: AppText = AppText.named("action_edit")
    val editDescription: AppText = AppText.named("edit_combination")
    val title: AppText = AppText.named("edit_combination_title")
    val done: AppText = AppText.named("action_done")
    val cancel: AppText = AppText.named("action_cancel")
    val retry: AppText = AppText.named("action_retry")
    val members: AppText = RecordingTexts.members
    val removeMessage: AppText = AppText.named("remove_member_message")
    val dataSources: AppText = AppText.named("data_sources")
    val leftOut: AppText = AppText.named("data_source_left_out")

    /** Whether the owner's page offers Edit beside the members: it's their combined activity. */
    fun offersEdit(page: ActivityDetailPage): Boolean = page.manage != null && !page.activity.members.isNullOrEmpty()

    /** "Remove Edge 840": a member's button and its confirmation's title and action. */
    fun remove(member: MemberView): AppText = AppText.named("detail_remove_member", RecordingTexts.memberName(member))

    /** What confirming a member's removal sends. */
    fun removeEvent(member: MemberView): ActivityDetailEvent = ActivityDetailEvent.RemoveMember(member.uri)

    /** A kind of data's name: "Heart rate", "Map and GPS". */
    fun kind(kind: DataKind): AppText =
        AppText.named(
            when (kind) {
                DataKind.Map -> "data_kind_map"
                DataKind.DistanceSpeed -> "data_kind_distance_speed"
                DataKind.Elevation -> "data_kind_elevation"
                DataKind.HeartRate -> "data_kind_heart_rate"
                DataKind.Power -> "data_kind_power"
                DataKind.Cadence -> "data_kind_cadence"
                DataKind.Temperature -> "data_kind_temperature"
                DataKind.RunningDynamics -> "data_kind_running_dynamics"
                DataKind.Laps -> "data_kind_laps"
                DataKind.Dive -> "data_kind_dive"
            },
        )

    /**
     * On the owner's page, a member's device, or else when it started, in the device's time zone: the owner always sees their own start.
     */
    fun sourceName(member: MemberView): AppText =
        member.device?.let(AppText::written) ?: AppText.named("data_source_started", AppText.Time(member.startedAt.epochSeconds))

    /** "From Edge 840", where a kind comes from when only one member recorded it. */
    fun from(member: MemberView): AppText = AppText.named("chart_source_from", sourceName(member))

    /** "Power comes from Edge 840", what a screen reader says for a kind's source menu. */
    fun comesFrom(row: DataSourceRow, member: MemberView): AppText =
        AppText.named("data_source_comes_from", kind(row.kind), sourceName(member))

    /** "Include Power", a kind's switch's spoken label. */
    fun include(row: DataSourceRow): AppText = AppText.named("data_source_include", kind(row.kind))

    /** A kind's row offers a menu of its sources: it's included and more than one member recorded it. */
    fun offersMenu(row: DataSourceRow): Boolean = row.chosen != null && row.offered.isNotEmpty()

    /** What choosing [member] in a kind's menu sends; null when it's the one already chosen. */
    fun chooseEvent(row: DataSourceRow, member: MemberView): ActivityDetailEvent? =
        if (member.uri == row.chosen?.uri) null else ActivityDetailEvent.ChooseSource(row.kind, member.uri)

    /** What switching a kind sends. */
    fun includeEvent(row: DataSourceRow, included: Boolean): ActivityDetailEvent = ActivityDetailEvent.SetIncluded(row.kind, included)

    /** The rows can be changed: no rebuild is running. */
    fun idle(sources: DataSources): Boolean = sources.saving == null

    /** The row whose rebuild is running shows a spinner. */
    fun isSaving(sources: DataSources, row: DataSourceRow): Boolean = sources.saving == row.kind
}
