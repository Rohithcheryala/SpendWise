package com.example.spendwise.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
    val inboxCount = inboxViewModel.uiState.messages.size

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
                Spacer(Modifier.height(8.dp))

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
                                scope.launch { drawerState.close() }
                                rootNavController.navigate(screen.route)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
            }
        }
    ) {
        Scaffold(
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
                modifier = Modifier.padding(padding)
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
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(72.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            Destination.entries.forEach { destination ->

                val selected = destination == current

                if (destination == Destination.UPI) {

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    onDestinationClick(destination)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.contentDescription,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                } else {

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                onDestinationClick(destination)
                            },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        BadgedBox(
                            badge = {
                                if (destination == Destination.INBOX && inboxCount > 0) {
                                    Badge {
                                        Text(inboxCount.toString())
                                    }
                                }
                            }
                        ) {

                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.contentDescription,
                                modifier = Modifier.size(24.dp),
                                tint =
                                    if (selected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(selected) {

                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


