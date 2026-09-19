package com.example.spendwise.navigation

import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.spendwise.ui.theme.Dimens
import com.example.spendwise.viewmodel.InboxViewModel
import kotlinx.coroutines.launch

@Composable
fun MainScaffold(
    rootNavController: NavHostController
) {
    val tabNavController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val inboxViewModel: InboxViewModel = hiltViewModel()
    val inboxCount = inboxViewModel.uiState.items.size

    val backStackEntry by tabNavController.currentBackStackEntryAsState()

    val currentDestination = Destination.fromRoute(
        backStackEntry?.destination?.route
    ) ?: Destination.BUDGET


    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(310.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.QrCodeScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Spendwise",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        text = "Smart Finance & Expenses",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                androidx.compose.material3.HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                // Primary create action — the "＋" is no longer buried behind
                // Transactions tab → FAB. Navigates to the create form directly.
                Button(
                    onClick = {
                        scope.launch {
                            drawerState.snapTo(DrawerValue.Closed)
                            rootNavController.navigate("transaction")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Add Transaction")
                }

                Spacer(Modifier.height(12.dp))

                Screen.entries
                    .filter { it != Screen.Transaction }
                    .forEach { screen ->
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.contentDescription
                                )
                            },
                            label = {
                                Text(
                                    screen.label,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                            },
                            selected = false,
                            onClick = {
                                // Close the drawer *before* navigating, in the
                                // same coroutine. Navigating first lets this
                                // composable leave the composition, which
                                // cancels the scope and aborts the close
                                // animation — leaving the drawer open when the
                                // user navigates back.
                                scope.launch {
                                    drawerState.snapTo(DrawerValue.Closed)
                                    rootNavController.navigate(screen.route)
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // Tab screens each render their own TopAppBar, which already applies
            // the status-bar inset. Consuming it here too would double it.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                MainNavigationBar(
                    current = currentDestination,
                    inboxCount = inboxCount,
                    onDestinationClick = { destination ->
                        when (destination) {
                            Destination.MORE -> {
                                scope.launch { drawerState.open() }
                            }

                            Destination.UPI -> {
                                tabNavController.navigate(destination.route) {
                                    popUpTo(tabNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    restoreState = true
                                    launchSingleTop = true
                                }
                            }

                            else -> {
                                tabNavController.navigate(destination.route) {
                                    popUpTo(tabNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    restoreState = true
                                    launchSingleTop = true
                                }
                            }
                        }
                    },
                    onScanClick = { destination -> rootNavController.navigate(destination.route) },
                    onMoreClick = {
                        scope.launch { drawerState.open() }
                    }
                )
            }
        ) { padding ->
            MainNavHost(
                navController = tabNavController,
                onOpenTransaction = { transactionId ->
                    rootNavController.navigate("transaction?transactionId=$transactionId")
                },
                modifier = Modifier
                    .padding(padding)
                    // The bottom bar already reserved the nav-bar strip (and,
                    // behind the open keyboard, the bar sits under it anyway).
                    // Without consuming that, any child that honours
                    // WindowInsets.ime — the Scan & Pay payment overlay —
                    // subtracted the FULL keyboard height from an area that was
                    // already shortened by the bar, leaving a dead band exactly
                    // the height of the bottom bar above the keys.
                    .consumeWindowInsets(padding)
            )
        }
    }
}

@Composable
fun MainNavigationBar(
    current: Destination,
    inboxCount: Int = 0,
    onDestinationClick: (Destination) -> Unit,
    onScanClick: (Destination) -> Unit,
    onMoreClick: (Destination) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
            )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = 64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Destination.entries.forEach { destination ->

                val selected = destination == current

                if (destination == Destination.UPI) {

                    // Center scan action — brand-colored circle, kept prominent
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(54.dp)
                                .clickable { onDestinationClick(destination) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.contentDescription,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }

                } else {

                    // Pill-indicator item: a true capsule behind the icon, and the
                    // label is ALWAYS visible — selection no longer shifts layout.
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onDestinationClick(destination)
                            }
                        ) {
                            // Capsule, not flat pill: fixed 32dp height (vs 24dp icon)
                            // + 16dp horizontal, so every icon sits on the same
                            // optical centerline regardless of badge state. The
                            // badge is drawn OUT-OF-FLOW over the capsule's
                            // top-right corner — it must never stretch the pill or
                            // shift the icon, whatever the count.
                            Box {
                                Surface(
                                    shape = CircleShape,
                                    color = if (selected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        Color.Transparent,
                                    modifier = Modifier.heightIn(min = 32.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.xs)
                                    ) {
                                        Icon(
                                            imageVector = destination.icon,
                                            contentDescription = destination.contentDescription,
                                            modifier = Modifier.size(24.dp),
                                            tint =
                                                if (selected)
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (destination == Destination.INBOX && inboxCount > 0) {
                                    Badge(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = (-2).dp, y = (-6).dp)
                                    ) {
                                        Text(inboxCount.toString())
                                    }
                                }
                            }

                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    if (selected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        }
    }
}


