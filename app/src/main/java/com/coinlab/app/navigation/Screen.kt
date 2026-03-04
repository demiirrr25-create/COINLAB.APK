package com.coinlab.app.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object Home : Screen("home")
    data object Market : Screen("market")
    data object Portfolio : Screen("portfolio")
    data object News : Screen("news")
    data object Settings : Screen("settings")
    data object CoinDetail : Screen("coin_detail/{coinId}") {
        fun createRoute(coinId: String) = "coin_detail/$coinId"
    }
    data object AddTransaction : Screen("add_transaction/{coinId}") {
        fun createRoute(coinId: String) = "add_transaction/$coinId"
    }
    data object PriceAlerts : Screen("price_alerts")
    data object Search : Screen("search")
    data object TechnicalAnalysis : Screen("technical_analysis/{coinId}") {
        fun createRoute(coinId: String) = "technical_analysis/$coinId"
    }
    data object CoinComparison : Screen("coin_comparison")
    data object Web3 : Screen("web3")
    data object Community : Screen("community")
    data object Airdrop : Screen("airdrop")
    data object Profile : Screen("profile")
    data object Staking : Screen("staking")

    // v9.5 — New feature screens
    data object AiAssistant : Screen("ai_assistant")
    data object ChatList : Screen("chat_list")
    data object Chat : Screen("chat/{chatId}") {
        fun createRoute(chatId: String) = "chat/$chatId"
    }
    data object PredictionGame : Screen("prediction_game")
    data object SocialTrading : Screen("social_trading")
}
