package ru.shlyahten.cvt.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shlyahten.cvt.R
import ru.shlyahten.cvt.ui.theme.AutoAmber
import ru.shlyahten.cvt.ui.theme.AutoBackground
import ru.shlyahten.cvt.ui.theme.AutoBorder
import ru.shlyahten.cvt.ui.theme.AutoCyan
import ru.shlyahten.cvt.ui.theme.AutoEmerald
import ru.shlyahten.cvt.ui.theme.AutoSurface
import ru.shlyahten.cvt.ui.theme.AutoSurfaceCard
import ru.shlyahten.cvt.ui.theme.AutoTextPrimary
import ru.shlyahten.cvt.ui.theme.AutoTextSecondary

data class PermissionsState(
    val bluetoothGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val overlayGranted: Boolean = false,
) {
    val canProceed: Boolean get() = bluetoothGranted
    val allEssentialGranted: Boolean get() = bluetoothGranted && notificationsGranted
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    state: PermissionsState,
    onRequestBluetooth: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAll: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.background(AutoBackground),
        containerColor = AutoBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.permissions_title),
                        fontWeight = FontWeight.Bold,
                        color = AutoTextPrimary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AutoSurface,
                    titleContentColor = AutoTextPrimary,
                )
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            val isLandscape = maxWidth >= 600.dp

            if (isLandscape) {
                // Two-column layout for Teyes landscape screens
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column: Intro info + Action buttons
                    Column(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = AutoCyan,
                            )
                            Text(
                                text = stringResource(R.string.permissions_subtitle),
                                fontSize = 14.sp,
                                color = AutoTextSecondary,
                                lineHeight = 20.sp,
                            )
                        }

                        BottomActionButtons(
                            state = state,
                            onRequestAll = onRequestAll,
                            onContinue = onContinue,
                        )
                    }

                    // Right Column: Permission Cards list
                    Column(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PermissionCardsList(
                            state = state,
                            onRequestBluetooth = onRequestBluetooth,
                            onRequestNotifications = onRequestNotifications,
                            onRequestOverlay = onRequestOverlay,
                        )
                    }
                }
            } else {
                // Single column layout for portrait mode
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.permissions_subtitle),
                        fontSize = 14.sp,
                        color = AutoTextSecondary,
                        lineHeight = 20.sp,
                    )

                    PermissionCardsList(
                        state = state,
                        onRequestBluetooth = onRequestBluetooth,
                        onRequestNotifications = onRequestNotifications,
                        onRequestOverlay = onRequestOverlay,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    BottomActionButtons(
                        state = state,
                        onRequestAll = onRequestAll,
                        onContinue = onContinue,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCardsList(
    state: PermissionsState,
    onRequestBluetooth: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Bluetooth Permission Card
        PermissionItemCard(
            title = stringResource(R.string.permissions_bt_title),
            description = stringResource(R.string.permissions_bt_desc),
            isGranted = state.bluetoothGranted,
            isMandatory = true,
            buttonText = stringResource(R.string.permissions_bt_btn),
            onActionClick = onRequestBluetooth,
        )

        // 2. Notifications Permission Card (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            PermissionItemCard(
                title = stringResource(R.string.permissions_notif_title),
                description = stringResource(R.string.permissions_notif_desc),
                isGranted = state.notificationsGranted,
                isMandatory = false,
                buttonText = stringResource(R.string.permissions_notif_btn),
                onActionClick = onRequestNotifications,
            )
        }

        // 3. Floating Overlay Permission Card (SYSTEM_ALERT_WINDOW)
        PermissionItemCard(
            title = stringResource(R.string.permissions_overlay_title),
            description = stringResource(R.string.permissions_overlay_desc),
            isGranted = state.overlayGranted,
            isMandatory = false,
            buttonText = stringResource(R.string.permissions_overlay_btn),
            onActionClick = onRequestOverlay,
        )
    }
}

@Composable
private fun PermissionItemCard(
    title: String,
    description: String,
    isGranted: Boolean,
    isMandatory: Boolean,
    buttonText: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isGranted) AutoEmerald.copy(alpha = 0.5f) else if (isMandatory) AutoAmber.copy(alpha = 0.6f) else AutoBorder
    val statusColor = if (isGranted) AutoEmerald else if (isMandatory) AutoAmber else AutoTextSecondary
    val statusText = if (isGranted) {
        stringResource(R.string.permissions_status_granted)
    } else if (isMandatory) {
        stringResource(R.string.permissions_status_required)
    } else {
        stringResource(R.string.permissions_status_optional)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AutoSurface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AutoTextPrimary,
                    modifier = Modifier.weight(1f)
                )

                // Status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = description,
                fontSize = 12.sp,
                color = AutoTextSecondary,
                lineHeight = 17.sp,
            )

            if (!isGranted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isMandatory) {
                        Button(
                            onClick = onActionClick,
                            colors = ButtonDefaults.buttonColors(containerColor = AutoCyan),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = buttonText,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onActionClick,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AutoCyan),
                            border = BorderStroke(1.dp, AutoCyan.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = buttonText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomActionButtons(
    state: PermissionsState,
    onRequestAll: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!state.allEssentialGranted) {
            Button(
                onClick = onRequestAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AutoAmber),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.permissions_grant_all_btn),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.Black
                )
            }
        }

        Button(
            onClick = onContinue,
            enabled = state.canProceed,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AutoCyan,
                disabledContainerColor = AutoSurfaceCard
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(R.string.permissions_continue_btn),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (state.canProceed) Color.Black else AutoTextSecondary
            )
        }

        if (!state.canProceed) {
            Text(
                text = stringResource(R.string.permissions_bt_required_warning),
                color = AutoAmber,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
