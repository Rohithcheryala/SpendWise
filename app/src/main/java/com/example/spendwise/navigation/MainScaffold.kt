package com.example.spendwise.navigation

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DismissibleDrawerSheet
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

@Composable
fun MainScaffold(
    rootNavController: NavHostController
) {
    val tabNavController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val backStackEntry by tabNavController.currentBackStackEntryAsState()

    val currentDestination = Destination.fromRoute(
        backStackEntry?.destination?.route
    ) ?: Destination.BUDGET

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet {
                        DismissibleDrawerSheet(
                            modifier = Modifier.width(300.dp) // Set your preferred sidebar width here
                        ) {
                            Text(text = "Additional Navigation", modifier = Modifier.padding(16.dp))

                            NavigationDrawerItem(
                                label = { Text("Transactions") },
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close() } // Close side nav smoothly
                                    rootNavController.navigate(Screen.Transaction.route)
                                }
                            )
                        }
                        // Add more custom sidebar options here (Settings, Profile, etc.)
                    }
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    bottomBar = {
                        MainNavigationBar(
                            current = currentDestination,
                            onDestinationClick = { destination ->
                                when (destination) {
                                    Destination.MORE -> {
                                        // Trigger the drawer to open
                                        scope.launch { drawerState.open() }
                                    }

                                    Destination.UPI -> {
                                        // Handle scanner navigation if separate from onScanClick
                                    }

                                    else -> {
                                        // Standard tab navigation
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
    }
}

@Composable
fun MainNavigationBar(
    current: Destination,
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
                                if (destination == Destination.INBOX) {
                                    Badge {
                                        Text("5")
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

@Composable
fun MainNavigationBarFAB(
    current: Destination,
    onDestinationClick: (Destination) -> Unit,
    onScanClick: (Destination) -> Unit,
    onMoreClick: (Destination) -> Unit,
    modifier: Modifier = Modifier
) {
    Box {
        NavigationBar(
            modifier = modifier,
            tonalElevation = 3.dp
        ) {

            Destination.entries.forEach { destination ->
                if (destination == Destination.UPI) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.Red)
                    )
                } else {
                    NavigationBarItem(
                        selected = current == destination,
                        onClick = {
                            onDestinationClick(destination)
                        },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (destination == Destination.INBOX) {
                                        Badge {
                                            Text("5")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.contentDescription
                                )
                            }

                        },
                        label = {
                            Text(destination.label)
                        },
                        alwaysShowLabel = true
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = { },
            modifier = Modifier
                .align(alignment = Alignment.Center)
                .offset(y = (-28).dp) // overlap the bar
                .size(72.dp),
            shape = CircleShape
        ) {
            Icon(
                Icons.Rounded.QrCodeScanner,
                contentDescription = "Scan"
            )
        }
    }
}

