package io.github.solcott.activitytracker.activityfile

import com.garmin.fit.BandedExercisesExerciseName
import com.garmin.fit.BattleRopeExerciseName
import com.garmin.fit.BenchPressExerciseName
import com.garmin.fit.BikeExerciseName
import com.garmin.fit.BikeOutdoorExerciseName
import com.garmin.fit.CalfRaiseExerciseName
import com.garmin.fit.CardioExerciseName
import com.garmin.fit.CarryExerciseName
import com.garmin.fit.ChopExerciseName
import com.garmin.fit.CoreExerciseName
import com.garmin.fit.CrunchExerciseName
import com.garmin.fit.CurlExerciseName
import com.garmin.fit.DeadliftExerciseName
import com.garmin.fit.EllipticalExerciseName
import com.garmin.fit.ExerciseCategory
import com.garmin.fit.FloorClimbExerciseName
import com.garmin.fit.FlyeExerciseName
import com.garmin.fit.HipRaiseExerciseName
import com.garmin.fit.HipStabilityExerciseName
import com.garmin.fit.HipSwingExerciseName
import com.garmin.fit.HyperextensionExerciseName
import com.garmin.fit.IndoorBikeExerciseName
import com.garmin.fit.IndoorRowExerciseName
import com.garmin.fit.LadderExerciseName
import com.garmin.fit.LateralRaiseExerciseName
import com.garmin.fit.LegCurlExerciseName
import com.garmin.fit.LegRaiseExerciseName
import com.garmin.fit.LungeExerciseName
import com.garmin.fit.MoveExerciseName
import com.garmin.fit.OlympicLiftExerciseName
import com.garmin.fit.PlankExerciseName
import com.garmin.fit.PlyoExerciseName
import com.garmin.fit.PoseExerciseName
import com.garmin.fit.PullUpExerciseName
import com.garmin.fit.PushUpExerciseName
import com.garmin.fit.RowExerciseName
import com.garmin.fit.RunExerciseName
import com.garmin.fit.RunIndoorExerciseName
import com.garmin.fit.SandbagExerciseName
import com.garmin.fit.ShoulderPressExerciseName
import com.garmin.fit.ShoulderStabilityExerciseName
import com.garmin.fit.ShrugExerciseName
import com.garmin.fit.SitUpExerciseName
import com.garmin.fit.SledExerciseName
import com.garmin.fit.SledgeHammerExerciseName
import com.garmin.fit.SquatExerciseName
import com.garmin.fit.StairStepperExerciseName
import com.garmin.fit.SuspensionExerciseName
import com.garmin.fit.TireExerciseName
import com.garmin.fit.TotalBodyExerciseName
import com.garmin.fit.TricepsExtensionExerciseName
import com.garmin.fit.WarmUpExerciseName

/**
 * The Java SDK's name for a set's exercise: [subtype] read as the exercise-name type of [category] (a `bench_press` set's subtype is a
 * `BenchPressExerciseName`). Null for a category without a name type, or a name newer than the SDK.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod") // One branch per FIT exercise category.
internal fun exerciseName(category: Int, subtype: Int): String? =
    when (category) {
        ExerciseCategory.BANDED_EXERCISES -> BandedExercisesExerciseName.getStringFromValue(subtype)
        ExerciseCategory.BATTLE_ROPE -> BattleRopeExerciseName.getStringFromValue(subtype)
        ExerciseCategory.BENCH_PRESS -> BenchPressExerciseName.getStringFromValue(subtype)
        ExerciseCategory.BIKE -> BikeExerciseName.getStringFromValue(subtype)
        ExerciseCategory.BIKE_OUTDOOR -> BikeOutdoorExerciseName.getStringFromValue(subtype)
        ExerciseCategory.CALF_RAISE -> CalfRaiseExerciseName.getStringFromValue(subtype)
        ExerciseCategory.CARDIO -> CardioExerciseName.getStringFromValue(subtype)
        ExerciseCategory.CARRY -> CarryExerciseName.getStringFromValue(subtype)
        ExerciseCategory.CHOP -> ChopExerciseName.getStringFromValue(subtype)
        ExerciseCategory.CORE -> CoreExerciseName.getStringFromValue(subtype)
        ExerciseCategory.CRUNCH -> CrunchExerciseName.getStringFromValue(subtype)
        ExerciseCategory.CURL -> CurlExerciseName.getStringFromValue(subtype)
        ExerciseCategory.DEADLIFT -> DeadliftExerciseName.getStringFromValue(subtype)
        ExerciseCategory.ELLIPTICAL -> EllipticalExerciseName.getStringFromValue(subtype)
        ExerciseCategory.FLOOR_CLIMB -> FloorClimbExerciseName.getStringFromValue(subtype)
        ExerciseCategory.FLYE -> FlyeExerciseName.getStringFromValue(subtype)
        ExerciseCategory.HIP_RAISE -> HipRaiseExerciseName.getStringFromValue(subtype)
        ExerciseCategory.HIP_STABILITY -> HipStabilityExerciseName.getStringFromValue(subtype)
        ExerciseCategory.HIP_SWING -> HipSwingExerciseName.getStringFromValue(subtype)
        ExerciseCategory.HYPEREXTENSION -> HyperextensionExerciseName.getStringFromValue(subtype)
        ExerciseCategory.INDOOR_BIKE -> IndoorBikeExerciseName.getStringFromValue(subtype)
        ExerciseCategory.INDOOR_ROW -> IndoorRowExerciseName.getStringFromValue(subtype)
        ExerciseCategory.LADDER -> LadderExerciseName.getStringFromValue(subtype)
        ExerciseCategory.LATERAL_RAISE -> LateralRaiseExerciseName.getStringFromValue(subtype)
        ExerciseCategory.LEG_CURL -> LegCurlExerciseName.getStringFromValue(subtype)
        ExerciseCategory.LEG_RAISE -> LegRaiseExerciseName.getStringFromValue(subtype)
        ExerciseCategory.LUNGE -> LungeExerciseName.getStringFromValue(subtype)
        ExerciseCategory.MOVE -> MoveExerciseName.getStringFromValue(subtype)
        ExerciseCategory.OLYMPIC_LIFT -> OlympicLiftExerciseName.getStringFromValue(subtype)
        ExerciseCategory.PLANK -> PlankExerciseName.getStringFromValue(subtype)
        ExerciseCategory.PLYO -> PlyoExerciseName.getStringFromValue(subtype)
        ExerciseCategory.POSE -> PoseExerciseName.getStringFromValue(subtype)
        ExerciseCategory.PULL_UP -> PullUpExerciseName.getStringFromValue(subtype)
        ExerciseCategory.PUSH_UP -> PushUpExerciseName.getStringFromValue(subtype)
        ExerciseCategory.ROW -> RowExerciseName.getStringFromValue(subtype)
        ExerciseCategory.RUN -> RunExerciseName.getStringFromValue(subtype)
        ExerciseCategory.RUN_INDOOR -> RunIndoorExerciseName.getStringFromValue(subtype)
        ExerciseCategory.SANDBAG -> SandbagExerciseName.getStringFromValue(subtype)
        ExerciseCategory.SHOULDER_PRESS -> ShoulderPressExerciseName.getStringFromValue(subtype)
        ExerciseCategory.SHOULDER_STABILITY -> ShoulderStabilityExerciseName.getStringFromValue(subtype)
        ExerciseCategory.SHRUG -> ShrugExerciseName.getStringFromValue(subtype)
        ExerciseCategory.SIT_UP -> SitUpExerciseName.getStringFromValue(subtype)
        ExerciseCategory.SLED -> SledExerciseName.getStringFromValue(subtype)
        ExerciseCategory.SLEDGE_HAMMER -> SledgeHammerExerciseName.getStringFromValue(subtype)
        ExerciseCategory.SQUAT -> SquatExerciseName.getStringFromValue(subtype)
        ExerciseCategory.STAIR_STEPPER -> StairStepperExerciseName.getStringFromValue(subtype)
        ExerciseCategory.SUSPENSION -> SuspensionExerciseName.getStringFromValue(subtype)
        ExerciseCategory.TIRE -> TireExerciseName.getStringFromValue(subtype)
        ExerciseCategory.TOTAL_BODY -> TotalBodyExerciseName.getStringFromValue(subtype)
        ExerciseCategory.TRICEPS_EXTENSION -> TricepsExtensionExerciseName.getStringFromValue(subtype)
        ExerciseCategory.WARM_UP -> WarmUpExerciseName.getStringFromValue(subtype)
        else -> null
    }?.ifEmpty { null }
