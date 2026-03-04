package com.coinlab.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.coinlab.app.R
import com.coinlab.app.data.preferences.AuthPreferences
import com.coinlab.app.ui.airdrop.AirdropScreen
import com.coinlab.app.ui.alerts.PriceAlertsScreen
import com.coinlab.app.ui.analysis.TechnicalAnalysisScreen
import com.coinlab.app.ui.auth.AuthViewModel
import com.coinlab.app.ui.auth.LoginScreen
import com.coinlab.app.ui.auth.RegisterScreen
import com.coinlab.app.ui.community.CommunityScreen
import com.coinlab.app.ui.comparison.CoinComparisonScreen
import com.coinlab.app.ui.detail.CoinDetailScreen
import com.coinlab.app.ui.home.HomeScreen
import com.coinlab.app.ui.market.MarketScreen
import com.coinlab.app.ui.news.NewsScreen
import com.coinlab.app.ui.portfolio.AddTransactionScreen
import com.coinlab.app.ui.portfolio.PortfolioScreen
import com.coinlab.app.ui.profile.ProfileScreen
import com.coinlab.app.ui.search.SearchScreen
import com.coinlab.app.ui.settings.SettingsScreen
import com.coinlab.app.ui.staking.StakingScreen
import com.coinlab.app.ui.wallet.WalletScreen
import com.coinlab.app.ui.web3.Web3Screen
import com.coinlab.app.ui.ai.AiAssistantScreen
import com.coinlab.app.ui.chat.ChatListScreen
import com.coinlab.app.ui.chat.ChatScreen
import com.coinlab.app.ui.prediction.PredictionGameScreen
import com.coinlab.app.ui.trading.SocialTradingScreen
import com.coinlab.app.ui.theme.CoinLabGreen

data class BottomNavItem(
    val screen: Screen,
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.Market, R.string.nav_market, Icons.Filled.ShowChart, Icons.Outlined.ShowChart),
    BottomNavItem(Screen.Community, R.string.community, Icons.Filled.Forum, Icons.Outlined.Forum),
    BottomNavItem(Screen.News, R.string.nav_news, Icons.Filled.Article, Icons.Outlined.Article),
    BottomNavItem(Screen.Profile, R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person)
)

@Composable
fun CoinLabNavHost(
    navController: NavHostController = rememberNavController(),
    deepLinkCoinId: String? = null,
    autoLogin: Boolean = false
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }

    // Handle deep link navigation
    LaunchedEffect(deepLinkCoinId) {
        deepLinkCoinId?.let { coinId ->
            navController.navigate(Screen.CoinDetail.createRoute(coinId)) {
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Color(0xFF0A0A14),
                    contentColor = Color.White
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.screen.route
                        } == true

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = stringResource(item.labelResId)
                                )
                            },
                            label = {
                                Text(
                                    stringResource(item.labelResId),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CoinLabGreen,
                                selectedTextColor = CoinLabGreen,
                                indicatorColor = CoinLabGreen.copy(alpha = 0.1f),
                                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                                unselectedTextColor = Color.White.copy(alpha = 0.5f)
                            ),
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (autoLogin) Screen.Home.route else Screen.Login.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(250)) + scaleIn(
                    initialScale = 0.96f,
                    animationSpec = tween(250)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200)) + scaleOut(
                    targetScale = 0.96f,
                    animationSpec = tween(200)
                )
            }
        ) {
            // Auth Screens
            composable(
                Screen.Login.route,
                enterTransition = { fadeIn(tween(400)) },
                exitTransition = { fadeOut(tween(300)) }
            ) {
                LoginScreen(
                    onNavigateToRegister = {
                        navController.navigate(Screen.Register.route)
                    },
                    onLoginSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                Screen.Register.route,
                enterTransition = {
                    slideInHorizontally(tween(350)) { it } + fadeIn(tween(350))
                },
                exitTransition = {
                    slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300))
                }
            ) {
                RegisterScreen(
                    onNavigateToLogin = { navController.popBackStack() },
                    onRegisterSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            // Home Screen
            composable(Screen.Home.route) {
                HomeScreen(
                    onCoinClick = { coinId ->
                        navController.navigate(Screen.CoinDetail.createRoute(coinId))
                    },
                    onSearchClick = {
                        navController.navigate(Screen.Search.route)
                    },
                    onWeb3Click = {
                        navController.navigate(Screen.Web3.route)
                    },
                    onCommunityClick = {
                        navController.navigate(Screen.Community.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onAirdropClick = {
                        navController.navigate(Screen.Airdrop.route)
                    },
                    onComparisonClick = {
                        navController.navigate(Screen.CoinComparison.route)
                    },
                    onStakingClick = {
                        navController.navigate(Screen.Staking.route)
                    },
                    onAllMarketClick = {
                        navController.navigate(Screen.Market.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onPredictionClick = {
                        navController.navigate(Screen.PredictionGame.route)
                    },
                    onTradingClick = {
                        navController.navigate(Screen.SocialTrading.route)
                    }
                )
            }

            composable(Screen.Market.route) {
                MarketScreen(
                    onCoinClick = { coinId ->
                        navController.navigate(Screen.CoinDetail.createRoute(coinId))
                    },
                    onSearchClick = {
                        navController.navigate(Screen.Search.route)
                    },
                    onAlertsClick = {
                        navController.navigate(Screen.PriceAlerts.route)
                    }
                )
            }

            // Community (now a bottom nav tab)
            composable(Screen.Community.route) {
                CommunityScreen()
            }

            // Portfolio (accessible but not in bottom nav)
            composable(Screen.Portfolio.route) {
                PortfolioScreen(
                    onCoinClick = { coinId ->
                        navController.navigate(Screen.CoinDetail.createRoute(coinId))
                    },
                    onAddTransaction = { coinId ->
                        navController.navigate(Screen.AddTransaction.createRoute(coinId))
                    }
                )
            }

            composable(Screen.News.route) {
                NewsScreen()
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // Profile Screen
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onAlertsClick = {
                        navController.navigate(Screen.PriceAlerts.route)
                    },
                    onSearchClick = {
                        navController.navigate(Screen.Search.route)
                    },
                    onWalletClick = {
                        navController.navigate(Screen.Wallet.route)
                    },
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Screen.CoinDetail.route,
                arguments = listOf(navArgument("coinId") { type = NavType.StringType }),
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(300)
                    )
                },
                exitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300)
                    )
                }
            ) { backStackEntry ->
                val coinId = backStackEntry.arguments?.getString("coinId") ?: return@composable
                CoinDetailScreen(
                    coinId = coinId,
                    onBack = { navController.popBackStack() },
                    onAddTransaction = {
                        navController.navigate(Screen.AddTransaction.createRoute(coinId))
                    },
                    onTechnicalAnalysis = {
                        navController.navigate(Screen.TechnicalAnalysis.createRoute(coinId))
                    }
                )
            }

            composable(
                route = Screen.AddTransaction.route,
                arguments = listOf(navArgument("coinId") { type = NavType.StringType }),
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(300)
                    )
                },
                exitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(300)
                    )
                }
            ) { backStackEntry ->
                val coinId = backStackEntry.arguments?.getString("coinId") ?: return@composable
                AddTransactionScreen(
                    coinId = coinId,
                    onBack = { navController.popBackStack() }
                )
            }

            // Price Alerts
            composable(Screen.PriceAlerts.route) {
                PriceAlertsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // Search
            composable(Screen.Search.route) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onCoinClick = { coinId ->
                        navController.navigate(Screen.CoinDetail.createRoute(coinId))
                    }
                )
            }

            // Technical Analysis
            composable(
                route = Screen.TechnicalAnalysis.route,
                arguments = listOf(navArgument("coinId") { type = NavType.StringType }),
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(300)
                    )
                },
                exitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300)
                    )
                }
            ) { backStackEntry ->
                val coinId = backStackEntry.arguments?.getString("coinId") ?: return@composable
                TechnicalAnalysisScreen(
                    coinId = coinId,
                    onBack = { navController.popBackStack() }
                )
            }

            // Coin Comparison
            composable(Screen.CoinComparison.route) {
                CoinComparisonScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // Web3 & DeFi
            composable(Screen.Web3.route) {
                Web3Screen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            // Airdrop Calendar
            composable(Screen.Airdrop.route) {
                AirdropScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            // Staking
            composable(Screen.Staking.route) {
                StakingScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            // Wallet
            composable(
                Screen.Wallet.route,
                enterTransition = {
                    slideInVertically(tween(350)) { it } + fadeIn(tween(350))
                },
                exitTransition = {
                    slideOutVertically(tween(300)) { it } + fadeOut(tween(300))
                }
            ) {
                WalletScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // v9.5 — AI Assistant
            composable(Screen.AiAssistant.route) {
                AiAssistantScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // v9.5 — Chat List
            composable(Screen.ChatList.route) {
                ChatListScreen(
                    onBack = { navController.popBackStack() },
                    onChatClick = { chatId ->
                        navController.navigate(Screen.Chat.createRoute(chatId))
                    }
                )
            }

            // v9.5 — Chat
            composable(
                route = Screen.Chat.route,
                arguments = listOf(navArgument("chatId") { type = NavType.StringType })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
                ChatScreen(
                    chatId = chatId,
                    onBack = { navController.popBackStack() }
                )
            }

            // v9.5 — Prediction Game
            composable(Screen.PredictionGame.route) {
                PredictionGameScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // v9.5 — Social Trading
            composable(Screen.SocialTrading.route) {
                SocialTradingScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
