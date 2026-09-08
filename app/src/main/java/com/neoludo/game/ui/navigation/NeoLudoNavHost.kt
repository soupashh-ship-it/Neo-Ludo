package com.neoludo.game.ui.navigation

import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.neoludo.game.multiplayer.LocalMultiplayerClient
import com.neoludo.game.multiplayer.OnlineClientFactory
import com.neoludo.game.multiplayer.OnlineRoomClient
import com.neoludo.game.ui.friends.FriendsScreen
import com.neoludo.game.ui.game.GameScreen
import com.neoludo.game.ui.home.HomeScreen
import com.neoludo.game.ui.locker.LockerScreen
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
    data object Game : Screen("game/{mode}/{roomId}/{playerCount}/{difficulty}/{color}") {
        fun createRoute(
            mode: String,
            roomId: String,
            playerCount: Int = 4,
            difficulty: String = "NORMAL",
            color: String = "RED"
        ) = "game/$mode/$roomId/$playerCount/$difficulty/$color"
    }
    data object Result : Screen("result/{winnerColor}/{captures}/{sixes}") {
        fun createRoute(winnerColor: String, captures: Int, sixes: Int) =
            "result/$winnerColor/$captures/$sixes"
    }
    data object Profile : Screen("profile")
    data object Friends : Screen("friends")
    data object Settings : Screen("settings")
    data object Rules : Screen("rules")
    data object Locker : Screen("locker")
}

@Composable
fun NeoLudoNavHost(
    app: NeoLudoApplication,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val scope = coroutineScope
    val context = LocalContext.current

    val profile by app.profileRepository.profile.collectAsState(initial = UserProfile())
    val stats by app.statsRepository.stats.collectAsState(initial = UserStats())
    val settings by app.settingsRepository.settings.collectAsState(initial = GameSettings())
    var activeOnlineClient by remember { mutableStateOf<OnlineRoomClient?>(null) }
    var lastGameRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Pin the online identity before any room is created/joined.
        runCatching { app.profileRepository.ensureStableProfile() }
    }
    LaunchedEffect(settings.soundVolume, settings.soundEnabled, settings.hapticsEnabled) {
        app.soundController.soundVolume = settings.soundVolume
        app.soundController.soundEnabled = settings.soundEnabled
        app.hapticController.hapticsEnabled = settings.hapticsEnabled
    }
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
                onStartOnline = { count ->
                    val code = "ON-" + (1000..9999).random()
                    navController.navigate(Screen.Game.createRoute("ONLINE", code, count, "NORMAL", "RED"))
                },
                onNavigateFriends = {
                    navController.navigate(Screen.CreateRoom.route)
                },
                onNavigateJoinRoom = {
                    navController.navigate(Screen.JoinRoom.route)
                },
                onStartLocal = { count ->
                    navController.navigate(Screen.Game.createRoute("LOCAL", "local_match", count, "NORMAL", "RED"))
                },
                onStartAi = { diff, count, col ->
                    navController.navigate(Screen.Game.createRoute("AI", "ai_match", count, diff, col))
                },
                onNavigateProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateRules = { navController.navigate(Screen.Rules.route) },
                onNavigateFriendsList = { navController.navigate(Screen.Friends.route) },
                onNavigateLocker = { navController.navigate(Screen.Locker.route) },
                onClaimDailyBonus = {
                    coroutineScope.launch { app.profileRepository.claimDailyReward(200, 10) }
                }
            )
        }

        composable(Screen.CreateRoom.route) {
            CreateRoomScreen(
                onCreateRoom = { count, fillBots, rules, color ->
                    // Firebase when this build ships google-services.json,
                    // otherwise the free public relay — no setup either way.
                    val client = OnlineClientFactory.create(
                        context = context,
                        localPlayerId = profile.id,
                        localPlayerName = profile.displayName,
                        localAvatarId = profile.avatarId,
                        preferredColor = color,
                        maxPlayers = count,
                        ruleSet = rules
                    )
                    val result = client.createRoom(count, fillBots, rules)
                    if (result.isSuccess) {
                        activeOnlineClient = client
                        navController.navigate(Screen.Lobby.createRoute(client.currentRoomId))
                    }
                    result
                },
                onNavigateJoin = {
                    navController.navigate(Screen.JoinRoom.route)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.JoinRoom.route) {
            JoinRoomScreen(
                onJoinRoom = { roomId ->
                    val client = OnlineClientFactory.create(
                        context = context,
                        localPlayerId = profile.id,
                        localPlayerName = profile.displayName,
                        localAvatarId = profile.avatarId,
                        initialRoomId = roomId,
                        maxPlayers = 4
                    )
                    val result = client.joinRoom(roomId)
                    if (result.isSuccess) {
                        activeOnlineClient = client
                        navController.navigate(Screen.Lobby.createRoute(client.currentRoomId))
                    }
                    result
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Lobby.route,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: "NL-1234"
            val client = activeOnlineClient ?: remember(roomId) {
                OnlineClientFactory.create(
                    context = context,
                    localPlayerId = profile.id,
                    localPlayerName = profile.displayName,
                    localAvatarId = profile.avatarId,
                    initialRoomId = roomId,
                    maxPlayers = 4,
                    ruleSet = LudoRuleSet(
                        autoMoveSinglePiece = settings.autoMoveSinglePiece,
                        penalty3xSix = settings.penalty3xSix,
                        turnTimerSeconds = settings.turnTimerSeconds
                    )
                )
            }
            LobbyWaitingRoomScreen(
                roomId = roomId,
                client = client,
                localPlayerId = profile.id,
                onStartGame = {
                    navController.navigate(Screen.Game.createRoute("ONLINE", roomId, client.maxPlayers, "NORMAL", client.preferredColor.name)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onBack = {
                    activeOnlineClient?.release()
                    activeOnlineClient = null
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Game.route,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("roomId") { type = NavType.StringType },
                navArgument("playerCount") { type = NavType.IntType; defaultValue = 4 },
                navArgument("difficulty") { type = NavType.StringType; defaultValue = "NORMAL" },
                navArgument("color") { type = NavType.StringType; defaultValue = "RED" }
            )
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "LOCAL"
            val roomId = backStackEntry.arguments?.getString("roomId") ?: "game_room"
            val playerCount = backStackEntry.arguments?.getInt("playerCount") ?: 4
            val difficultyStr = backStackEntry.arguments?.getString("difficulty") ?: "NORMAL"
            val difficulty = runCatching { Difficulty.valueOf(difficultyStr) }.getOrDefault(Difficulty.NORMAL)
            val colorStr = backStackEntry.arguments?.getString("color") ?: "RED"
            val chosenColor = runCatching { PlayerColor.valueOf(colorStr) }.getOrDefault(PlayerColor.RED)

            LaunchedEffect(mode, roomId, playerCount, difficultyStr, colorStr) {
                lastGameRoute = Screen.Game.createRoute(mode, roomId, playerCount, difficultyStr, colorStr)
            }

            val client = remember(mode, roomId, playerCount, difficulty, chosenColor) {
                when (mode) {
                    "AI" -> BotMultiplayerClient(
                        humanName = profile.displayName,
                        humanAvatarId = profile.avatarId,
                        humanColor = chosenColor,
                        botCount = playerCount - 1,
                        difficulty = difficulty,
                        ruleSet = LudoRuleSet(
                            autoMoveSinglePiece = settings.autoMoveSinglePiece,
                            penalty3xSix = settings.penalty3xSix,
                            turnTimerSeconds = settings.turnTimerSeconds
                        )
                    )
                    "ONLINE" -> activeOnlineClient ?: OnlineClientFactory.create(
                        context = context,
                        localPlayerId = profile.id,
                        localPlayerName = profile.displayName,
                        localAvatarId = profile.avatarId,
                        preferredColor = chosenColor,
                        initialRoomId = roomId,
                        maxPlayers = playerCount,
                        ruleSet = LudoRuleSet(
                            autoMoveSinglePiece = settings.autoMoveSinglePiece,
                            penalty3xSix = settings.penalty3xSix,
                            turnTimerSeconds = settings.turnTimerSeconds
                        )
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
                boardTheme = settings.boardTheme,
                diceSkin = settings.diceSkin,
                pawnSkin = settings.pawnSkin,
                reducedMotion = settings.reducedMotion,
                onUpdateTheme = { newTheme ->
                    coroutineScope.launch { app.settingsRepository.updateSettings(settings.copy(boardTheme = newTheme)) }
                },
                onGameFinished = { winnerColor, captures, sixes ->
                    val isWin = winnerColor == chosenColor
                    coroutineScope.launch {
                        app.statsRepository.recordMatchResult(
                            isWin = isWin,
                            mode = mode,
                            capturesMade = captures,
                            sixesRolled = sixes,
                            piecesHome = if (isWin) 4 else 0,
                            winnerColor = winnerColor.name
                        )
                        if (isWin) {
                            val rewardCoins = when (mode) {
                                "ONLINE" -> 1000
                                "AI" -> 500
                                else -> 250
                            }
                            val rewardGems = if (mode == "ONLINE") 5 else 2
                            app.profileRepository.addCurrency(rewardCoins, rewardGems)
                        }
                    }
                    activeOnlineClient = null
                    navController.navigate(Screen.Result.createRoute(winnerColor.name, captures, sixes)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onExitGame = {
                    activeOnlineClient = null
                    navController.popBackStack()
                }
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
                    val replay = lastGameRoute
                    // ONLINE rooms can't be replayed (room finished) — go Home.
                    // AI/LOCAL replay the stored game route with a fresh client.
                    if (replay != null && !replay.startsWith("game/ONLINE")) {
                        navController.navigate(replay) {
                            popUpTo(Screen.Home.route)
                        }
                    } else {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
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

        composable(Screen.Locker.route) {
            LockerScreen(
                profile = profile,
                currentBoardTheme = settings.boardTheme,
                currentDiceSkin = settings.diceSkin,
                currentPawnSkin = settings.pawnSkin,
                onSelectBoardTheme = { theme ->
                    coroutineScope.launch {
                        app.settingsRepository.updateSettings(settings.copy(boardTheme = theme))
                        app.profileRepository.updateProfile(profile.copy(selectedBoardTheme = theme))
                    }
                },
                onSelectDiceSkin = { skin ->
                    coroutineScope.launch {
                        app.settingsRepository.updateSettings(settings.copy(diceSkin = skin))
                        app.profileRepository.updateProfile(profile.copy(selectedDiceSkin = skin))
                    }
                },
                onSelectPawnSkin = { skin ->
                    coroutineScope.launch {
                        app.settingsRepository.updateSettings(settings.copy(pawnSkin = skin))
                        app.profileRepository.updateProfile(profile.copy(selectedPawnSkin = skin))
                    }
                },
                onUnlockBoardTheme = { theme, c, g ->
                    coroutineScope.launch {
                        if (app.profileRepository.unlockBoardTheme(theme, c, g)) {
                            app.settingsRepository.updateSettings(settings.copy(boardTheme = theme))
                        }
                    }
                },
                onUnlockDiceSkin = { skin, c, g ->
                    coroutineScope.launch {
                        if (app.profileRepository.unlockDiceSkin(skin, c, g)) {
                            app.settingsRepository.updateSettings(settings.copy(diceSkin = skin))
                        }
                    }
                },
                onUnlockPawnSkin = { skin, c, g ->
                    coroutineScope.launch {
                        if (app.profileRepository.unlockPawnSkin(skin, c, g)) {
                            app.settingsRepository.updateSettings(settings.copy(pawnSkin = skin))
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
