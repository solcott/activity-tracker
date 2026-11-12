package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.string

/** A batch save under way that this screen didn't start (feed/batch-import): how far it got, and the upload queue. */
@Composable
internal fun SavingImportCard(saving: SavingImport, onViewUploads: () -> Unit) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ImportActivityTexts.savingTitle.string(), style = MaterialTheme.typography.titleMedium)
            Text(ImportActivityTexts.savingProgress(saving).string(), style = MaterialTheme.typography.bodyMedium)
            AppTextButton(onClick = onViewUploads) { Text(ImportActivityTexts.viewUploads.string()) }
        }
    }
}

/** An import whose save stopped before all of it was saved (feed/batch-import), until the athlete dismisses it. */
@Composable
internal fun StoppedImportNotice(stopped: StoppedImport, onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            ImportActivityTexts.stopped(stopped).string(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        AppTextButton(onClick = onDismiss) { Text(ImportActivityTexts.stoppedDismiss.string()) }
    }
}
