import io.ktor.auth.UserIdPrincipal

object FormFields {
    const val USERNAME = "username"
    const val PASSWORD = "password"
}

object AuthName {
    const val SESSION = "session"
    const val FORM = "FormAuth"
}

object CommonRoutes {
    const val LOGIN = "/login"
    const val REGISTER = "/register"
    const val LOGOUT = "/logout"
    const val CHAT = "/chat"
}

typealias ChatPrincipal = UserIdPrincipal

