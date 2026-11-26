package id.tunarasa.app.ui.navigation

import android.net.Uri

object Routes {
    // Public Flow
    const val WELCOME = "welcome"
    const val INSTITUTIONS = "institutions"
    const val POPULAR_QUESTIONS = "popular_questions/{institutionId}/{institutionName}"
    const val COMM_METHOD = "comm_method/{institutionId}"
    const val FAQ = "faq/{slug}?name={name}"
    const val ABOUT = "about"
    const val SIBI_TRANSLATOR = "sibi_translator/{institutionId}"
    const val SPEECH_TO_TEXT = "speech_to_text/{institutionId}"
    const val CHAT = "chat/{slug}?question={question}&method={method}"
    const val SUMMARY = "summary/{sessionId}"
    const val ADMIN_SIGN_IN = "admin/sign-in"
    const val ADMIN_DASHBOARD = "admin/dashboard"
    const val ADMIN_QUEUE = "admin/queue"
    // Admin Routes
    const val ADMIN_QA_LOGS = "admin/qa-logs"
    const val ADMIN_INSTITUTIONS = "admin/institutions"
    const val ADMIN_USERS = "admin/users"

    fun popularQuestions(institutionId: String, institutionName: String) =
        "popular_questions/$institutionId/$institutionName"

    fun faq(slug: String, name: String) =
        "faq/${Uri.encode(slug)}?name=${Uri.encode(name)}"
    fun commMethod(institutionId: String) =
        "comm_method/${Uri.encode(institutionId)}"
    fun sibiTranslator(institutionId: String) =
        "sibi_translator/${Uri.encode(institutionId)}"
    fun speechToText(institutionId: String) =
        "speech_to_text/${Uri.encode(institutionId)}"
    fun chat(slug: String, initialQuestion: String = "", method: String = "text") =
        "chat/${Uri.encode(slug)}?question=${Uri.encode(initialQuestion)}&method=${Uri.encode(method)}"
    fun summary(sessionId: String) = "summary/$sessionId"

}
