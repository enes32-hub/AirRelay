package io.github.jqssun.airplay.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.MainActivity
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.ui.theme.AirRelayColors

internal data class PermissionAccess(val activity: MainActivity?, val notifications: Boolean, val display: Boolean)

@Composable
internal fun permissionAccess(): PermissionAccess {
    val activity = LocalContext.current.mainActivity()
    // Snapshot-state read subscribes both setup and Settings to actual OS permission changes.
    val revision = activity?.permissionRevision?.value
    return remember(activity, revision) {
        PermissionAccess(activity, activity?.notificationsAllowed() == true, activity?.automaticDisplayAllowed() == true)
    }
}

@Composable
internal fun PermissionStatus(allowed: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(if (allowed) Icons.Rounded.CheckCircle else Icons.Rounded.Info, null,
            Modifier.size(20.dp), tint = if (allowed) AirRelayColors.Success else AirRelayColors.Muted)
        Text(stringResource(if (allowed) R.string.permission_allowed else R.string.permission_not_allowed),
            style = MaterialTheme.typography.labelLarge, color = if (allowed) AirRelayColors.Success else AirRelayColors.Muted)
    }
}

@Composable
internal fun PermissionManagementCard(title: String, body: String, allowed: Boolean, focusId: String, onManage: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(AirRelayColors.Panel, RoundedCornerShape(18.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = AirRelayColors.Muted)
        PermissionStatus(allowed)
        TvAction(stringResource(R.string.permission_manage), onManage, compact = true, focusId = focusId)
    }
}

@Composable
internal fun PermissionNetworkNote() {
    Column(Modifier.fillMaxWidth().background(AirRelayColors.Panel, RoundedCornerShape(18.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.permission_network_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.permission_network_body), style = MaterialTheme.typography.bodyMedium, color = AirRelayColors.Muted)
        Text(stringResource(R.string.permission_network_automatic), style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Muted)
    }
}

internal val needsDisplayPermission: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
