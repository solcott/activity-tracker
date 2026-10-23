package io.github.solcott.activitytracker.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/** The `activitytracker` rule set, configured in `config/detekt/detekt.yml`. */
class ActivityTrackerRuleSetProvider : RuleSetProvider {
    override val ruleSetId = RuleSetId("activitytracker")

    override fun instance() =
        RuleSet(ruleSetId, listOf(::UseTextFieldState, ::UseSingleFlight, ::UseAdaptiveComponents, ::NoSystemClockInUi))
}
