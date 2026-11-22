package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.db.PathTerrain
import io.github.solcott.activitytracker.appview.db.Paths
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.terrain.TerrainElevation
import io.github.solcott.activitytracker.terrain.TerrainTiles
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.compoundOr
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update

/**
 * A path's terrain profile (segments/path-profile): each of its line pieces sampled against [tiles] as a segment's line is, one height
 * about every [SPACING_M], and stored in [PathTerrain] for the pieces the tiles cover.
 */
internal class PathTerrainSampler(private val db: Database, private val tiles: TerrainTiles) {
    /**
     * Samples the path at [pathUri] and replaces its profiles, unless its lines changed meanwhile (a re-line queues another job). A path no
     * longer indexed is skipped.
     */
    suspend fun sample(pathUri: String) {
        val lines = db.tx { Paths.select(Paths.lines).where { Paths.uri eq pathUri }.firstOrNull()?.get(Paths.lines) } ?: return
        val pieces = lines.split('\n')
        val profiles = pieces.mapIndexedNotNull { index, encoded ->
            val piece = SegmentGeometry.decodePolyline(encoded)?.takeIf { it.size >= 2 } ?: return@mapIndexedNotNull null
            val length = SegmentGeometry.lengthMeters(piece)
            tiles.terrainOf(piece, pointsFor(length))?.let { Sampled(index, length.roundToLong(), it) }
        }
        db.tx {
            val updated =
                Paths.update({ (Paths.uri eq pathUri) and (Paths.lines eq lines) }) { it[terrainComplete] = profiles.size == pieces.size }
            if (updated == 0) return@tx
            PathTerrain.deleteWhere { PathTerrain.pathUri eq pathUri }
            PathTerrain.batchInsert(profiles, shouldReturnGeneratedValues = false) { sampled ->
                this[PathTerrain.pathUri] = pathUri
                this[PathTerrain.piece] = sampled.piece
                this[PathTerrain.profileDm] = sampled.terrain.profileDm.joinToString(",")
                this[PathTerrain.lengthM] = sampled.lengthM
                this[PathTerrain.gainM] = sampled.terrain.gain
                this[PathTerrain.lossM] = sampled.terrain.loss
                this[PathTerrain.maxGrade] = sampled.terrain.maxGradeTenths
            }
        }
    }

    /**
     * The paths not wholly sampled whose box touches the tiles' coverage: what the start-up backfill queues. Null when tiles cover none.
     */
    fun unsampled(): Op<Boolean>? {
        val areas = tiles.coverage
        if (areas.isEmpty()) return null
        val touches =
            areas
                .map { area ->
                    (Paths.maxLatE7 greaterEq area.minLatE7) and
                        (Paths.minLatE7 lessEq area.maxLatE7) and
                        (Paths.maxLngE7 greaterEq area.minLngE7) and
                        (Paths.minLngE7 lessEq area.maxLngE7)
                }
                .compoundOr()
        return (Paths.terrainComplete eq false) and touches
    }

    private class Sampled(val piece: Int, val lengthM: Long, val terrain: TerrainElevation)

    companion object {
        /** About how far apart a piece's heights are. */
        const val SPACING_M = 50.0

        /** The most heights one piece's profile holds: `feed.getPath#pieceProfile.heights`'s `maxLength`. */
        const val MAX_POINTS = 512

        /** How many heights a piece [lengthM] long gets: one about every [SPACING_M], both ends included, from 2 to [MAX_POINTS]. */
        fun pointsFor(lengthM: Double): Int = ((lengthM / SPACING_M).roundToInt() + 1).coerceIn(2, MAX_POINTS)
    }
}
