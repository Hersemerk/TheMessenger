import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.form
import io.ktor.features.*
import io.ktor.html.respondHtml
import io.ktor.http.ContentType
import io.ktor.http.cio.websocket.*
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.content.*
import io.ktor.http.push
import io.ktor.request.isMultipart
import io.ktor.request.receiveMultipart
import io.ktor.request.receiveParameters
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.response.respondTextWriter
import io.ktor.routing.*
import io.ktor.routing.head
import io.ktor.sessions.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.*
import kotlinx.html.*
import model.Employee
import model.Message
import java.time.*
import java.util.*
import model.employees
import model.messages
import org.litote.kmongo.*
import kotlin.collections.HashMap

fun Application.main() {
    ChatApplication().apply { main() }
}

class ChatApplication {
    private val server = ChatServer()

    fun Application.main() {
        employees.insertOne(Employee("vlazarev", "123"))

        install(DefaultHeaders)
        install(CallLogging)
        install(WebSockets) {
            pingPeriod = Duration.ofMinutes(1)
        }

        install(Sessions) {
            cookie<ChatPrincipal>("SESSION")
        }

        install(Authentication) {
            form(AuthName.FORM) {
                userParamName = FormFields.USERNAME
                passwordParamName = FormFields.PASSWORD
                challenge {
                    val errors: Map<Any, AuthenticationFailedCause> = call.authentication.errors
                    when (errors.values.singleOrNull()) {
                        AuthenticationFailedCause.InvalidCredentials ->
                            call.respondRedirect("${CommonRoutes.LOGIN}?invalid")

                        AuthenticationFailedCause.NoCredentials ->
                            call.respondRedirect("${CommonRoutes.LOGIN}?no")

                        else ->
                            call.respondRedirect(CommonRoutes.LOGIN)
                    }
                }
                validate {
                    cred: UserPasswordCredential ->
                    val empl = employees.findOne(Employee::login eq cred.name, Employee::password eq cred.password)
                    if (empl != null) ChatPrincipal(
                            cred.name
                    ) else null
                }
                //skipWhen { call -> call.sessions.get<ChatPrincipal>() != null }
            }
        }

        routing {
            loginRoute()
            registerRoute()
            homeRoute()
            chatRoute()

            webSocket("/ws") {
                val session = call.sessions.get<ChatPrincipal>()

                if (session == null) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No session"))
                    return@webSocket
                }

                server.memberJoin(session.name, this)

                try {
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            receivedMessage(session.name, frame.readText())
                        }
                    }
                } finally {
                    server.memberLeft(session.name, this)
                }
            }

            static {
                defaultResource("chat.html", "web")
                resources("web")
            }
        }
    }

    private suspend fun receivedMessage(name: String, command: String) {
        val regex = """([\w\s]+)@([\w\s]+)""".toRegex()
        val matchResult = regex.find(command)
        val (recepient, message) = matchResult!!.destructured
        messages.insertOne(Message(LocalDateTime.now(), recepient, name, message))
        server.sendTo(recepient, name, message)
    }
}

internal fun Route.chatRoute() {
    get("/chat") {
        val principal = call.sessions.get<ChatPrincipal>()!!
        call.respondHtml {
            head {
                meta { charset="UTF-8" }
                link {
                    rel="stylesheet"
                    href="chat.css"
                }
                title { +"Чат" }
            }
            body {
                div {
                    id="chat-container"
                    div {
                        textInput {
                            placeholder="Поиск"
                        }
                    }
                    div {
                        id="conversation-list"
                    }

                    div {
                        id="new-message-container"
                        a(classes = "white-back") {
                            +"+"
                        }
                    }
                    div {
                        id="chat-title"
                        span {
                            id="interlocutor-span"
                            +"login"
                        }
                        val interlocutor = "login"
                        label {
                            id="interlocutor"
                            hidden=true
                            +"$interlocutor"
                        }
                    }
                    div(classes = "messages") {
                        id="messages"
                    }
                    div {
                        id="chat-message-list"
                        val msgs = messages.find()
                        var style: String
                        msgs.forEach {
                            if (it.rec == principal.name) {
                                style = "you-message"
                            }
                            else {
                                style = "other-message"
                            }
                            div(classes = "message-row " + style) {
                                div(classes = "message-text") { +it.text }
                                div(classes = "message-time") { +it.date.toString() }
                            }
                        }
                    }
                    div {
                        id="chat-form"
                        input {
                            type=InputType.button
                            id="sendButton"
                            value="send"
                        }
                        textInput {
                            id="commandInput"
                        }
                    }
                }
                script {
                    type="text/javascript"
                    src="main.js"
                }
            }
        }
    }
}

internal fun Route.homeRoute() {
    authenticate(AuthName.FORM, optional = true) {
        get("/") {
            if (call.principal<ChatPrincipal>() == null) {
                call.respondRedirect(CommonRoutes.LOGIN)
            } else {
                call.respondRedirect(CommonRoutes.CHAT)
            }
        }
    }
}

internal fun Route.loginRoute() {
    route(CommonRoutes.LOGIN) {
        get {
            call.respondHtml {
                head {
                    meta {
                        charset="UTF-8"
                    }
                    title { +"ТЭЦ-1" }
                    link {
                        rel="stylesheet"
                        type="text/css"
                        href="login.css"
                    }
                }
                body {
                    div(classes = "container") {
                        div(classes = "panel") {
                            h1(classes = "panel_title") {
                                +"Вход"
                            }
                            val queryParams = call.request.queryParameters
                            val errorMsg = when {
                                "invalid" in queryParams -> "Sorry, incorrect username or password."
                                "no" in queryParams -> "Sorry, you need to be logged in to do that."
                                else -> null
                            }
                            if (errorMsg != null) {
                                div {
                                    style = "color:red;"
                                    +errorMsg
                                }
                            }
                            form(/*encType = FormEncType.multipartFormData,*/ method = FormMethod.post) {
                                acceptCharset = "utf-8"
                                div(classes = "form_row") {
                                    div(classes = "field type-text name-Login[username]") {
                                        div(classes = "field-control") {
                                            textInput {
                                                name = FormFields.USERNAME
                                                placeholder = "Логин"
                                                required = true
                                            }
                                        }
                                        div(classes = "clear") {

                                        }
                                    }
                                }
                                div(classes = "form_row") {
                                    div(classes = "field type-text name-Login[username]") {
                                        div(classes = "field-control") {
                                            passwordInput {
                                                name = FormFields.PASSWORD
                                                placeholder = "Пароль"
                                                required=true
                                            }
                                        }
                                        div(classes = "clear") {

                                        }
                                    }
                                }
                                div(classes = "form_buttons"){
                                    submitInput(classes = "form_button"){
                                        value = "Войти"
                                    }
                                }
                                div {
                                    a(href = CommonRoutes.REGISTER) {
                                        +"Зарегистрироваться"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        authenticate(AuthName.FORM) {
            post {
                val principal = call.principal<ChatPrincipal>()!!
                call.sessions.set(principal)
                call.respondRedirect("/chat")
            }
        }
    }
}

internal fun Route.registerRoute() {
    route(CommonRoutes.REGISTER) {
        get {
            call.respondHtml {
                head {
                    meta {
                        charset="UTF-8"
                    }
                    title { +"ТЭЦ-1" }
                    link {
                        rel="stylesheet"
                        type="text/css"
                        href="login.css"
                    }
                }
                body {
                    div(classes = "container") {
                        div(classes = "panel") {
                            h1(classes = "panel_title") {
                                +"Регистрация"
                            }
                            val queryParams = call.request.queryParameters
                            val errorMsg = when {
                                "invalid" in queryParams -> "Sorry, incorrect username or password."
                                "no" in queryParams -> "Sorry, you need to be logged in to do that."
                                else -> null
                            }
                            if (errorMsg != null) {
                                div {
                                    style = "color:red;"
                                    +errorMsg
                                }
                            }
                            form(encType = FormEncType.multipartFormData, method = FormMethod.post) {
                                acceptCharset = "utf-8"
                                div(classes = "form_row") {
                                    div(classes = "field type-text name-Login[username]") {
                                        div(classes = "field-control") {
                                            textInput {
                                                name = FormFields.USERNAME
                                                placeholder = "Логин"
                                                required = true
                                            }
                                        }
                                        div(classes = "clear") {

                                        }
                                    }
                                }
                                div(classes = "form_row") {
                                    div(classes = "field type-text name-Login[username]") {
                                        div(classes = "field-control") {
                                            passwordInput {
                                                name = FormFields.PASSWORD
                                                placeholder = "Пароль"
                                                required=true
                                            }
                                        }
                                        div(classes = "clear") {

                                        }
                                    }
                                }
                                div(classes = "form_buttons"){
                                    submitInput(classes = "form_button"){
                                        value = "Зарегистрироваться"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        post {
            val multipart = call.receiveMultipart()
            val data: HashMap<String, String> = hashMapOf("login" to "", "password" to "")
            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        if (part.name == FormFields.USERNAME) {
                            data["login"] = part.value
                        }
                        if (part.name == FormFields.PASSWORD) {
                            data["password"] = part.value
                        }
                    }
                }
                part.dispose()
            }
            val login = data["login"]?: "kek"
            val password = data["password"]?: "lul"
            employees.insertOne(Employee(login, password))
            call.authentication.principal(ChatPrincipal(login))
            call.respondRedirect("/chat")
        }
    }
}