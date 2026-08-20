package com.neoludo.game.ui.navigation

import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neoludo.game.NeoLudoApplication
import com.neoludo.game.core.model.GameSettings
import com.neoludo.game.core.model.UserProfile
import com.neoludo.game.core.model.UserStats
import com.neoludo.game.engine.ai.Difficulty
import com.neoludo.game.engine.model.LudoRuleSet
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.multiplayer.BotMultiplayerClient
import com.neoludo.game.multiplayer.FirebaseMultiplayerClient
import com.neoludo.game.multiplayer.LocalMultiplayerClient
import com.neoludo.game.ui.friends.FriendsScreen
import com.neoludo.game.ui.game.GameScreen
import com.neoludo.game.ui.home.HomeScreen
import com.neoludo.game.ui.profile.ProfileScreen
import com.neoludo.game.ui.result.GameResultScreen
import com.neoludo.game.ui.room.CreateRoomScreen
import com.neoludo.game.ui.room.JoinRoomScreen
import com.neoludo.game.ui.room.LobbyWaitingRoomScreen
import com.neoludo.game.ui.rules.RulesGuideScreen
import com.neoludo.game.ui.settings.SettingsScreen
import com.neoludo.game.ui.splash.SplashScreen

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Home : Screen("home")
    data object CreateRoom : Screen("create_room")
    data object JoinRoom : Screen("join_room")
    data object Lobby : Screen("lobby/{roomId}") {
        fun createRoute(roomId: String) = "lobby/$roomId"
    }
    data object Game : Screen("game/{mode}/{roomId}/{playerCount}/{difficulty}") {
        fun createRoute(mode: String, roomId: String, playerCount: Int = 4, difficulty: String = "NORMAL") =
            "game/$mode/$roomId/$playerCount/$difficulty"
    }
    data object Result : Screen("result/{winnerColor}/{captures}/{sixes}") {
        fun createRoute(winnerColor: String, captures: Int, sixes: Int) =
            "result/$winnerColor/$captures/$sixes"
    }
    data object Profile : Screen("profile")
    data object Friends : Screen("friends")
    data object Settings : Screen("settings")
    data object Rules : Screen("rules")
}

@Composable
fun NeoLudoNavHost(
    app: NeoLudoApplication,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val scope = coroutineScope

    val profile by app.profileRepository.profile.collectAsState(initial = UserProfile())
    val stats by app.statsRepository.stats.collectAsState(initial = UserStats())
    val settings by app.settingsRepository.settings.collectAsState(initial = GameSettings())

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                profile = profile,
                stats = stats,
                onNavigateOnline = {
                    val code = "ON-" + (1000..9999).random()
                    navController.navigate(Screen.Game.createRoute("ONLINE", code, 4, "NORMAL"))
                },
                onNavigateFriends = {
                    navController.navigate(Screen.CreateRoom.route)
                },
                onNavigateLocal = {
                    navController.navigate(Screen.Game.createRoute("LOCAL", "local_match", 4, "NORMAL"))
                },
                onNavigateAi = {
                    navController.navigate(Screen.Game.createRoute("AI", "ai_match", 4, "NORMAL"))
                },
                onNavigateProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateRules = { navController.navigate(Screen.Rules.route) },
                onNavigateFriendsList = { navController.navigate(Screen.Friends.route) }
            )
        }

        composable(Screen.CreateRoom.route) {
            CreateRoomScreen(
                onRoomCreated = { roomId ->
                    navController.navigate(Screen.Lobby.createRoute(roomId))
                },
                onNavigateJoin = {
                    navController.navigate(Screen.JoinRoom.route)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.JoinRoom.route) {
            JoinRoomScreen(
                onJoinSuccess = { roomId ->
                    navController.navigate(Screen.Lobby.createRoute(roomId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Lobby.route,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: "NL-1234"
            LobbyWaitingRoomScreen(
                roomId = roomId,
                onStartGame = {
                    navController.navigate(Screen.Game.createRoute("ONLINE", roomId, 4, "NORMAL")) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Game.route,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("roomId") { type = NavType.StringType },
                navArgument("playerCount") { type = NavType.IntType; defaultValue = 4 },
                navArgument("difficulty") { type = NavType.StringType; defaultValue = "NORMAL" }
            )
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "LOCAL"
            val roomId = backStackEntry.arguments?.getString("roomId") ?: "game_room"
            val playerCount = backStackEntry.arguments?.getInt("playerCount") ?: 4
            val difficultyStr = backStackEntry.arguments?.getString("difficulty") ?: "NORMAL"
            val difficulty = runCatching { Difficulty.valueOf(difficultyStr) }.getOrDefault(Difficulty.NORMAL)

            val client = remember(mode, roomId) {
                when (mode) {
                    "AI" -> BotMultiplayerClient(
                        humanName = profile.displayName,
                        humanAvatarId = profile.avatarId,
                        humanColor = PlayerColor.RED,
                        botCount = playerCount - 1,
                        difficulty = difficulty,
                        ruleSet = LudoRuleSet(
                            autoMoveSinglePiece = settings.autoMoveSinglePiece,
                            penalty3xSix = settings.penalty3xSix,
                            turnTimerSeconds = settings.turnTimerSeconds
                        )
                    )
                    "ONLINE" -> FirebaseMultiplayerClient(
                        localPlayerId = profile.id,
                        localPlayerName = profile.displayName,
                        localAvatarId = profile.avatarId
                    )
                    else -> LocalMultiplayerClient(
                        playerCount = playerCount,
                        ruleSet = LudoRuleSet(
                            autoMoveSinglePiece = settings.autoMoveSinglePiece,
                            penalty3xSix = settings.penalty3xSix,
                            turnTimerSeconds = settings.turnTimerSeconds
                        )
                    )
                }
            }

            GameScreen(
                client = client,
                soundController = app.soundController,
                hapticController = app.hapticController,
                onGameFinished = { winnerColor, captures, sixes ->
                    navController.navigate(Screen.Result.createRoute(winnerColor.name, captures, sixes)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onExitGame = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Result.route,
            arguments = listOf(
                navArgument("winnerColor") { type = NavType.StringType },
                navArgument("captures") { type = NavType.IntType; defaultValue = 0 },
                navArgument("sixes") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStackEntry ->
            val winnerColor = backStackEntry.arguments?.getString("winnerColor") ?: "RED"
            val captures = backStackEntry.arguments?.getInt("captures") ?: 0
            val sixes = backStackEntry.arguments?.getInt("sixes") ?: 0

            GameResultScreen(
                winnerColor = winnerColor,
                captures = captures,
                sixes = sixes,
                onPlayAgain = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onMainMenu = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                profile = profile,
                stats = stats,
                onSaveProfile = { updated ->
                    coroutineScope.launch { app.profileRepository.updateProfile(updated) }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Friends.route) {
            FriendsScreen(
                friendRepository = app.friendRepository,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                settings = settings,
                onUpdateSettings = { updated ->
                    coroutineScope.launch { app.settingsRepository.updateSettings(updated) }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Rules.route) {
            RulesGuideScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
