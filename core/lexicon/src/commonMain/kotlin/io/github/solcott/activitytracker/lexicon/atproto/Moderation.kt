package io.github.solcott.activitytracker.lexicon.atproto

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.XrpcProcedure
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `com.atproto.moderation.defs#reasonType`, an open enum: the values this app sends. */
object ReasonType {
    const val SPAM = "com.atproto.moderation.defs#reasonSpam"
    const val VIOLATION = "com.atproto.moderation.defs#reasonViolation"
    const val MISLEADING = "com.atproto.moderation.defs#reasonMisleading"
    const val SEXUAL = "com.atproto.moderation.defs#reasonSexual"
    const val RUDE = "com.atproto.moderation.defs#reasonRude"
    const val OTHER = "com.atproto.moderation.defs#reasonOther"
}

/** The subject of `com.atproto.moderation.createReport`: a union of an account and a record. */
@Serializable(with = ReportSubjectSerializer::class)
sealed interface ReportSubject {
    /** `com.atproto.admin.defs#repoRef`: an account. */
    @Serializable
    data class Repo(val did: Did, @SerialName("\$type") val type: String = TYPE) : ReportSubject {
        companion object {
            const val TYPE = "com.atproto.admin.defs#repoRef"
        }
    }

    /** `com.atproto.repo.strongRef`: one version of a record. */
    @Serializable
    data class Record(val uri: AtUri, val cid: Cid, @SerialName("\$type") val type: String = TYPE) : ReportSubject {
        companion object {
            const val TYPE = "com.atproto.repo.strongRef"
        }
    }
}

/** Picks the member by `$type`; each member writes its own. */
internal object ReportSubjectSerializer : JsonContentPolymorphicSerializer<ReportSubject>(ReportSubject::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ReportSubject> =
        when (val type = element.jsonObject["\$type"]?.jsonPrimitive?.content) {
            ReportSubject.Repo.TYPE -> ReportSubject.Repo.serializer()
            ReportSubject.Record.TYPE -> ReportSubject.Record.serializer()
            else -> throw SerializationException("Unknown report subject \$type: $type")
        }
}

/**
 * `com.atproto.moderation.createReport`. Clients send it to their own PDS with `atproto-proxy: <labelerDid>#atproto_labeler`, and the PDS
 * forwards it to the labeler with service auth of its own.
 */
object CreateReport {
    /** The lexicon's `maxGraphemes` for `reason`. */
    const val MAX_REASON_LENGTH = 2000

    @Serializable
    data class Input(
        /** A [ReasonType] value. */
        val reasonType: String,
        val subject: ReportSubject,
        /** Free text from the reporter. */
        val reason: String? = null,
    )

    @Serializable
    data class Output(
        val id: Long,
        val reasonType: String,
        val subject: ReportSubject,
        val reportedBy: Did,
        val createdAt: String,
        val reason: String? = null,
    )

    val method = XrpcProcedure("com.atproto.moderation.createReport", Input.serializer(), Output.serializer())
}
