package id.tunarasa.app.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import id.tunarasa.app.ui.about.AboutScreen
import id.tunarasa.app.ui.admin.AdminQueueScreen
import id.tunarasa.app.ui.admin.DashboardScreen
import id.tunarasa.app.ui.admin.QaLogsScreen
import id.tunarasa.app.ui.admin.SignInScreen
import id.tunarasa.app.ui.admin.UserManagementScreen
import id.tunarasa.app.ui.chat.ChatScreen
import id.tunarasa.app.ui.faq.FaqScreen
import id.tunarasa.app.ui.navigation.Routes
import id.tunarasa.app.ui.publicflow.institution.InstitutionScreen
import id.tunarasa.app.ui.publicflow.method.CommMethodScreen
import id.tunarasa.app.ui.publicflow.sibi.SibiTranslatorScreen
import id.tunarasa.app.ui.publicflow.stt.SpeechToTextScreen
import id.tunarasa.app.ui.publicflow.welcome.WelcomeScreen
import id.tunarasa.app.ui.summary.SummaryScreen
import id.tunarasa.app.ui.admin.InstitutionAdminScreen
import id.tunarasa.app.ui.summary.NoteScreen

@Composable
fun TunarasaNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.WELCOME
    ) {
        // --- ALUR PUBLIK ---

        composable(Routes.WELCOME) {
            WelcomeScreen(
                onStartClick = { navController.navigate(Routes.INSTITUTIONS) },
                onAdminLoginClick = { navController.navigate("admin/sign-in") },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.INSTITUTIONS) {
            InstitutionScreen(
                onBackClick = { navController.popBackStack() },
                onSelectInstitution = { institution ->
                    navController.navigate(
                        Routes.faq(slug = institution.id, name = institution.name)
                    )
                }
            )
        }

        composable(
            route = Routes.FAQ,
            arguments = listOf(
                navArgument("slug") { type = NavType.StringType },
                navArgument("name") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val slug = backStackEntry.arguments?.getString("slug").orEmpty()
            val name = backStackEntry.arguments?.getString("name").orEmpty()

            FaqScreen(
                institutionSlug = slug,
                institutionName = name,
                onBackClick = { navController.popBackStack() },
                onSelectQuestion = { question ->
                    navController.navigate(
                        Routes.chat(slug = slug, initialQuestion = question, method = "text")
                    )
                },
                onAskOtherClick = {
                    navController.navigate(Routes.commMethod(slug))
                }
            )
        }

        composable(
            route = Routes.COMM_METHOD,
            arguments = listOf(navArgument("institutionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val slug = backStackEntry.arguments?.getString("institutionId").orEmpty()
            CommMethodScreen(
                onBackClick = { navController.popBackStack() },
                onSelectSibi = { navController.navigate(Routes.sibiTranslator(slug)) },
                onSelectSpeech = { navController.navigate(Routes.speechToText(slug)) },
                onSelectTextChat = { navController.navigate(Routes.chat(slug = slug, method = "text")) }
            )
        }

        composable(
            route = Routes.SIBI_TRANSLATOR,
            arguments = listOf(navArgument("institutionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val slug = backStackEntry.arguments?.getString("institutionId").orEmpty()
            SibiTranslatorScreen(
                onBackClick = { navController.popBackStack() },
                onSwitchToTextChat = {
                    navController.navigate(Routes.chat(slug = slug, method = "text"))
                },
                onSendQuestion = { formedQuestion ->
                    navController.navigate(
                        Routes.chat(slug = slug, initialQuestion = formedQuestion, method = "gesture")
                    )
                }
            )
        }

        composable(
            route = Routes.SPEECH_TO_TEXT,
            arguments = listOf(navArgument("institutionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val slug = backStackEntry.arguments?.getString("institutionId").orEmpty()
            SpeechToTextScreen(
                onBackClick = { navController.popBackStack() },
                onUseTextClick = { spokenText ->
                    navController.navigate(
                        Routes.chat(slug = slug, initialQuestion = spokenText, method = "speech")
                    )
                }
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("slug") { type = NavType.StringType },
                navArgument("question") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("method") {
                    type = NavType.StringType
                    defaultValue = "text"
                }
            )
        ) { backStackEntry ->
            val slug = backStackEntry.arguments?.getString("slug").orEmpty()
            val question = backStackEntry.arguments?.getString("question").orEmpty()
            val method = backStackEntry.arguments?.getString("method") ?: "text"

            ChatScreen(
                institutionSlug = slug,
                initialQuestion = question,
                inputMethod = method,
                onBackClick = { navController.popBackStack() },
                onSwitchToSibi = { navController.navigate(Routes.sibiTranslator(slug)) },
                onSwitchToSpeech = { navController.navigate(Routes.speechToText(slug)) },
                onFinishConversation = { sessionToken, name ->
                    navController.navigate("summary/${Uri.encode(sessionToken)}?name=${Uri.encode(name)}")
                }
            )
        }

        composable(
            route = "summary/{token}?name={name}",
            arguments = listOf(
                navArgument("token") { type = NavType.StringType },
                navArgument("name") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            )
        ) { backStackEntry ->
            val token = backStackEntry.arguments?.getString("token").orEmpty()
            SummaryScreen(
                token = token,
                institutionName = backStackEntry.arguments?.getString("name").orEmpty(),
                onBackToHome = {
                    navController.popBackStack(Routes.WELCOME, inclusive = false)
                }
            )
        }

        composable(
            route = "note/{token}",
            arguments = listOf(navArgument("token") { type = NavType.StringType })
        ) { backStackEntry ->
            val token = backStackEntry.arguments?.getString("token").orEmpty()
            NoteScreen(
                token = token,
                onBackClick = { navController.popBackStack() },
                onBackToHome = {
                    navController.popBackStack(Routes.WELCOME, inclusive = false)
                }
            )
        }

        // --- ALUR ADMIN ---

        composable(Routes.ADMIN_SIGN_IN) {
            SignInScreen(
                onBackClick = { navController.popBackStack() },
                onSignInSuccess = {
                    navController.navigate(Routes.ADMIN_DASHBOARD) {
                        popUpTo(Routes.ADMIN_SIGN_IN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ADMIN_DASHBOARD) {
            DashboardScreen(
                onNavigateToQueue = { navController.navigate(Routes.ADMIN_QUEUE) },
                onNavigateToQaLogs = { navController.navigate(Routes.ADMIN_QA_LOGS) },
                onNavigateToInstitutions = { navController.navigate(Routes.ADMIN_INSTITUTIONS) },
                onNavigateToUsers = { navController.navigate(Routes.ADMIN_USERS) },
                onLogout = {
                    navController.navigate(Routes.ADMIN_SIGN_IN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ADMIN_QUEUE) {
            AdminQueueScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.ADMIN_QA_LOGS) {
            QaLogsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.ADMIN_INSTITUTIONS) {
            InstitutionAdminScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.ADMIN_USERS) {
            UserManagementScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
