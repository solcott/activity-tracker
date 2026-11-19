package io.github.solcott.activitytracker.appview.api

import io.ktor.http.ContentType

/** The type a private photo is served as: the PDS's when it is an image type photos may have, else opaque bytes. */
internal fun servedImageType(fromPds: String?): ContentType =
    runCatching { fromPds?.let(ContentType::parse)?.withoutParameters() }.getOrNull()?.takeIf { it in SERVED_IMAGE_TYPES }
        ?: ContentType.Application.OctetStream

/** `feed.activity#image` accepts WebP and JPEG; PNG for photos stored before that. */
private val SERVED_IMAGE_TYPES = setOf(ContentType.Image.JPEG, ContentType("image", "webp"), ContentType.Image.PNG)
