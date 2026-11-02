package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.activityfile.ImportSource
import io.github.solcott.activitytracker.data.importsave.PickedFileSource
import io.github.vinceglb.filekit.PlatformFile

/** A [PickedFileSource], which a batch import's save can also keep and reopen (`SourceKeeper`). */
internal actual fun PlatformFile.toImportSource(): ImportSource = PickedFileSource(this)
