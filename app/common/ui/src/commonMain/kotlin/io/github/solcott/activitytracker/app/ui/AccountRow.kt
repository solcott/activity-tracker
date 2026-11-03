package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic

/** One athlete in a list: avatar, display name (or handle when there's none) and handle. */
@Composable
fun AccountRow(account: ProfileViewBasic, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = account.shownName
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).handCursor().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(account.avatar, name, 40.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(handleLabel(account.handle.value), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** An [AccountRow] with [actions] (buttons, a status) at its end and, under it, [supporting] lines. */
@Composable
fun AccountActionRow(
    account: ProfileViewBasic,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            AccountRow(account, onClick)
            supporting?.invoke()
        }
        actions()
    }
}
