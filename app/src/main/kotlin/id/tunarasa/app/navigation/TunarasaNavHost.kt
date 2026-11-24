package id.tunarasa.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import id.tunarasa.app.ui.about.AboutScreen
import id.tunarasa.app.ui.admin.AdminQueueScreen
import id.tunarasa.app.ui.admin.DashboardScreen
import id.tunarasa.app.ui.admin.InstitutionAdminScreen
import id.tunarasa.app.ui.admin.QaLogsScreen
import id.tunarasa.app.ui.admin.SignInScreen
import id.tunarasa.app.ui.admin.UserManagementScreen
import id.tunarasa.app.ui.chat.ChatScreen
import id.tunarasa.app.ui.faq.FaqScreen
import id.tunarasa.app.ui.home.HomeScreen
import id.tunarasa.app.ui.institution.InstitutionSelectScreen
import id.tunarasa.app.ui.summary.NoteScreen
import id.tunarasa.app.ui.summary.SummaryScreen

/** PLACEHOLDER. Owner: FE. */
@Composable
fun TunarasaNavHost() {
    NavHost(navController = rememberNavController(), startDestination = "home") {
        composable("home") { HomeScreen() }
        composable("about") { AboutScreen() }
        composable("institutions") { InstitutionSelectScreen() }
        composable("faq/{slug}") { FaqScreen() }
        composable("chat/{slug}") { ChatScreen() }
        composable("note/{token}") { NoteScreen() }
        composable("summary/{token}") { SummaryScreen() }
        composable("admin/sign-in") { SignInScreen() }
        composable("admin/queue") { AdminQueueScreen() }
        composable("admin/dashboard") { DashboardScreen() }
        composable("admin/qa-logs") { QaLogsScreen() }
        composable("admin/institutions") { InstitutionAdminScreen() }
        composable("admin/users") { UserManagementScreen() }
    }
}
