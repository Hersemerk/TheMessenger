import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.channels.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/**
 * Class in charge of the logic of the chat server.
 * It contains handlers to events and commands to send messages to specific users in the server.
 */
class ChatServer {
    /**
     * Atomic counter used to get unique user-names based on the maxiumum users the server had.
     */
    val usersCounter = AtomicInteger()

    /**
     * A concurrent map associating session IDs to user names.
     */
    val memberNames = ConcurrentHashMap<String, String>()

    /**
     * Associates a session-id to a set of websockets.
     * Since a browser is able to open several tabs and windows with the same cookies and thus the same session.
     * There might be several opened sockets for the same client.
     */
    val members = ConcurrentHashMap<String, MutableList<WebSocketSession>>()

    /**
     * A list of the lastest messages sent to the server, so new members can have a bit context of what
     * other people was talking about before joining.
     */
    val lastMessages = LinkedList<String>()

    /**
     * Handles that a member identified with a session id and a socket joined.
     */
    suspend fun memberJoin(member: String, socket: WebSocketSession) {
        val name = memberNames.computeIfAbsent(member) { member }

        usersCounter.incrementAndGet()

        val list = members.computeIfAbsent(member) { CopyOnWriteArrayList<WebSocketSession>() }
        list.add(socket)

        notifyUsers("clear@")
        if (list.size == 1) {
            memberNames.values.forEach {
                notifyUsers("adduser@$it")
            }
        }
    }

    /**
     * Handles a [member] idenitified by its session id renaming [to] a specific name.
     */
//    suspend fun memberRenamed(member: String, to: String) {
//        // Re-sets the member name.
//        val oldName = memberNames.put(member, to) ?: member
//        // Notifies everyone in the server about this change.
//        notifyUsers("server", "Member renamed from $oldName to $to")
//    }

    /**
     * Handles that a [member] with a specific [socket] left the server.
     */
    suspend fun memberLeft(member: String, socket: WebSocketSession) {
        // Removes the socket connection for this member
        val connections = members[member]
        connections?.remove(socket)

        // If no more sockets are connected for this member, let's remove it from the server
        // and notify the rest of the users about this event.
        if (connections != null && connections.isEmpty()) {
            val name = memberNames.remove(member) ?: member
            notifyUsers("deleteuser@$name")
        }
    }

    /**
     * Handles the 'who' command by sending the member a list of all all members names in the server.
     */
    suspend fun who(sender: String) {
        members[sender]?.send(Frame.Text(memberNames.values.joinToString(prefix = "[server::who] ")))
    }

    /**
     * Handles the 'help' command by sending the member a list of available commands.
     */
    suspend fun help(sender: String) {
        members[sender]?.send(Frame.Text("[server::help] Possible commands are: /user, /help and /who"))
    }

    /**
     * Handles sending to a [recipient] from a [sender] a [message].
     *
     * Both [recipient] and [sender] are identified by its session-id.
     */
    suspend fun sendTo(recipient: String, sender: String, message: String) {
        members[recipient]?.send(Frame.Text("$message"))
    }

    /**
     * Handles a [message] sent from a [sender] by notifying the rest of the users.
     */
    suspend fun message(sender: String, message: String) {
        // Pre-format the message to be send, to prevent doing it for all the users or connected sockets.
        val name = memberNames[sender] ?: sender
        val formatted = "[$name] $message"

        // Sends this pre-formatted message to all the members in the server.
        notifyUsers(formatted)

        // Appends the message to the list of [lastMessages] and caps that collection to 100 items to prevent
        // growing too much.
        synchronized(lastMessages) {
            lastMessages.add(formatted)
            if (lastMessages.size > 100) {
                lastMessages.removeFirst()
            }
        }
    }

    /**
     * Sends a [message] to all the members in the server, including all the connections per member.
     */
    private suspend fun notifyUsers(user: String) {
        members.values.forEach { socket ->
            socket.send(Frame.Text(user))
        }
    }

    /**
     * Sends a [message] coming from a [sender] to all the members in the server, including all the connections per member.
     */
//    private suspend fun notifyUsers(sender: String, message: String) {
//        val name = memberNames[sender] ?: sender
//        notifyUsers(" $message")
//    }

    /**
     * Sends a [message] to a list of [this] [WebSocketSession].
     */
    suspend fun List<WebSocketSession>.send(frame: Frame) {
        forEach {
            try {
                it.send(frame.copy())
            } catch (t: Throwable) {
                try {
                    it.close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, ""))
                } catch (ignore: ClosedSendChannelException) {
                    // at some point it will get closed
                }
            }
        }
    }
}