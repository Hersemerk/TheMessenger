// Global variable to hold the websocket.
var socket = null;

/**
 * This function is in charge of connecting the client.
 */
function connect() {
    // First we create the socket.
    // The socket will be connected automatically asap. Not now but after returning to the event loop,
    // so we can register handlers safely before the connection is performed.
    console.log("Begin connect");
    socket = new WebSocket("ws://" + window.location.host + "/ws");

    // We set a handler that will be executed if the socket has any kind of unexpected error.
    // Since this is a just sample, we only report it at the console instead of making more complex things.
    socket.onerror = function() {
        console.log("socket error");
    };

    // We set a handler upon connection.
    // What this does is to put a text in the messages container notifying about this event.
    socket.onopen = function() {
        console.log("Connected");
    };

    // If the connection was closed gracefully (either normally or with a reason from the server),
    // we have this handler to notify to the user via the messages container.
    // Also we will retry a connection after 5 seconds.
    socket.onclose = function(evt) {
        // Try to gather an explanation about why this was closed.
        var explanation = "";
        if (evt.reason && evt.reason.length > 0) {
            explanation = "reason: " + evt.reason;
        } else {
            explanation = "without a reason specified";
        }

        // Notify the user using the messages container.
        console.log("Disconnected with close code " + evt.code + " and " + explanation);
        // Try to reconnect after 5 seconds.
        setTimeout(connect, 5000);
    };

    // If we receive a message from the server, we want to handle it.
    socket.onmessage = function(event) {
        received(event.data.toString());
    };
}

/**
 * Handle messages received from the sever.
 *
 * @param message The textual message
 */
function received(message) {
    if (message.startsWith("clear@")) {
        clear();
    }
    if (message.startsWith("adduser@")) {
        addUser(message.substring(8));
    }
    else if (message.startsWith("deleteuser@")) {
        addUser(message.substring(11));
    }
    else {
         write(message);
    }
}

/**
 * Writes a message in the HTML 'messages' container that the user can see.
 *
 * @param message The message to write in the container
 */
function clear() {
    var users = document.getElementById("conversation-list");
    users.innerHTML = '';
}

function write(message) {
    var msg = document.createElement("div");
    msg.className = "message-row other-message";
    var msg_text = document.createElement("div");
    msg_text.className = "message-text";
    var msg_time = document.createElement("div");
    msg_time.className = "message-time";
    msg_text.textContent = message;
    var time = new Date();
    msg_time.textContent = time.getHours() + ":" + time.getMinutes();
    msg.appendChild(msg_text);
    msg.appendChild(msg_time);

    // Then we get the 'messages' container that should be available in the HTML itself already.
    var messagesDiv = document.getElementById("chat-message-list");
    // We adds the text
    messagesDiv.appendChild(msg);
    // We scroll the container to where this text is so the use can see it on long conversations if he/she has scrolled up.
    messagesDiv.scrollTop = msg.offsetTop;
}

function addUser(username) {
    var users = document.getElementById("conversation-list");
    var user = document.createElement("div");
    user.addEventListener("click", selectInterlocutor);
    user.className = "conversation";
    var img = document.createElement("img");
    img.src = "ava.jpg";
    var title = document.createElement("div");
    title.className = "title-text";
    title.textContent = username;
    var msg = document.createElement("div");
    msg.className = "conversation-message";
    user.appendChild(img);
    user.appendChild(title);
    user.appendChild(msg);
    users.appendChild(user);
}

function selectInterlocutor(){
    var list = document.querySelectorAll("div.conversation");
    for (var i = 0; i < list.length; i++) {
        list[i].classList.remove("active");
    }
    this.classList.add("active");
    var inter = document.getElementById("interlocutor");
    inter.textContent = this.childNodes[1].textContent;
}

function deleteUser(username) {
    var users = document.getElementById("conversation-list");
    var user = document.createElement("div");
    user.className = "conversation";
    var img = document.createElement("img");
    img
    var title = document.createElement("div");
    title.className = "title-text";
    var msg = document.createElement("div");
    msg.className = "conversation-message";
    user.appendChild(title);
    user.appendChild(msg);
    users.appendChild(user);
}
/**
 * Function in charge of sending the 'commandInput' text to the server via the socket.
 */
function onSend() {
    var input = document.getElementById("commandInput");
    // Validates that the input exists
    if (input) {
        var text = input.value;
        var rec = document.getElementById("interlocutor");
        // Validates that there is a text and that the socket exists
        if (text && socket) {
            // Sends the text
            msg = rec.textContent + "@" + text;
            socket.send(msg);
            var msg = document.createElement("div");
            msg.className = "message-row you-message";
            var msg_text = document.createElement("div");
            msg_text.className = "message-text";
            var msg_time = document.createElement("div");
            msg_time.className = "message-time";
            msg_text.textContent = text;
            var time = new Date();
            msg_time.textContent = time.getHours() + ":" + time.getMinutes();
            msg.appendChild(msg_text);
            msg.appendChild(msg_time);
            var messagesDiv = document.getElementById("chat-message-list");
                // We adds the text
                messagesDiv.appendChild(msg);
                // We scroll the container to where this text is so the use can see it on long conversations if he/she has scrolled up.
                messagesDiv.scrollTop = msg.offsetTop;
            // Clears the input so the user can type a new command or text to say
            input.value = "";
        }
    }
}

/**
 * The initial code to be executed once the page has been loaded and is ready.
 */
function start() {
    // First, we should connect to the server.
    connect();

    // If we click the sendButton, let's send the message.
    document.getElementById("sendButton").onclick = onSend;
    // If we pressed the 'enter' key being inside the 'commandInput', send the message to improve accessibility and making it nicer.
    document.getElementById("commandInput").onkeydown = function(e) {
        if (e.keyCode == 13) {
            onSend();
        }
    };
}

/**
 * The entry point of the client.
 */
function initLoop() {
    // Is the sendButton available already? If so, start. If not, let's wait a bit and rerun this.
    if (document.getElementById("sendButton")) {
        start();
    } else {
        setTimeout(initLoop, 300);
    }
}

// This is the entry point of the client.
initLoop();