package bep.hax.modules;

import bep.hax.Bep;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;

public class WebChat extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgServer = this.settings.createGroup("Server");
    private final SettingGroup sgFilters = this.settings.createGroup("Filters");
    private final Setting<Integer> port = this.sgServer
        .add(new Builder().name("port").description("Port for the web server.").defaultValue(8765).range(1024, 65535).sliderRange(8000, 9000).build());
    private final Setting<Boolean> openBrowserButton = this.sgServer
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("open-in-browser")
                .description("Toggle this to open the web chat in your browser.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> showTimestamps = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("timestamps")
                .description("Show timestamps for messages.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> showCoordinates = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-coordinates")
                .description("Show current coordinates with dimension conversion.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Integer> maxMessages = this.sgGeneral
        .add(
            new Builder()
                .name("max-messages")
                .description("Maximum number of messages to keep in history.")
                .defaultValue(1000)
                .range(50, 5000)
                .sliderRange(100, 2000)
                .build()
        );
    private final Setting<Boolean> showPlayerMessages = this.sgFilters
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-player-messages")
                .description("Show player chat messages.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> showSystemMessages = this.sgFilters
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-system-messages")
                .description("Show system messages.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> showDeathMessages = this.sgFilters
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-death-messages")
                .description("Show death messages.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> hideChatInGame = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("hide-in-game-chat")
                .description("Hide the in-game chat HUD when web chat is active.")
                .defaultValue(true)
                .build()
        );
    private final Setting<String> pageTitle = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("page-title")
                .description("Title shown in browser tab (uses server address if empty).")
                .defaultValue("")
                .build()
        );
    private final Setting<Boolean> persistEnabled = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("persist-enabled")
                .description("Keep module enabled between game sessions (WARNING: May cause server hosting issues).")
                .defaultValue(false)
                .build()
        );
    private HttpServer server;
    private ExecutorService executor;
    private final List<WebChat.ChatMessage> messageHistory = new ArrayList<>();
    private final ConcurrentLinkedQueue<String> commandQueue = new ConcurrentLinkedQueue<>();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final Gson gson = new Gson();
    private volatile boolean serverRunning = false;
    private int currentX = 0;
    private int currentY = 0;
    private int currentZ = 0;
    private String currentDimension = "Overworld";

    public WebChat() {
        super(Bep.CATEGORY, "web-chat", "Displays Minecraft chat in a web browser with coordinate tracking.");
    }

    @Override
    public boolean isActive() {
        return !this.persistEnabled.get() && !this.serverRunning ? false : super.isActive();
    }

    @Override
    public void onActivate() {
        try {
            Class.forName("com.sun.net.httpserver.HttpServer");
            this.info("Attempting to start web server on port " + this.port.get() + "...");
            this.startWebServer();
            this.serverRunning = true;
            int actualPort = this.server != null ? this.server.getAddress().getPort() : this.port.get();
            this.info("Web chat server started successfully on port " + actualPort);
            this.info("Open http://localhost:" + actualPort + " in your browser");
            this.info("Or use the 'Open in Browser' button in the module settings");
            this.addSystemMessage("Web Chat server started successfully!");
        } catch (ClassNotFoundException e) {
            this.error("HttpServer classes not available in this environment.");
            this.error("The Web Chat module requires Java's built-in HTTP server which may not be available.");
            this.serverRunning = false;
            this.toggle();
        } catch (Exception e) {
            this.error("Failed to start web server: " + e.getMessage());
            e.printStackTrace();
            this.serverRunning = false;
            this.toggle();
        }
    }

    @Override
    public void onDeactivate() {
        this.serverRunning = false;
        this.stopWebServer();
        this.messageHistory.clear();
        this.commandQueue.clear();
        this.info("Web chat server stopped");
    }

    private void startWebServer() throws IOException {
        int actualPort = this.port.get();

        for (int attempts = 0; attempts < 10; attempts++) {
            try {
                this.info("Creating HTTP server on port " + actualPort + "...");
                this.server = HttpServer.create(new InetSocketAddress("localhost", actualPort), 0);
                break;
            } catch (IOException e) {
                if (attempts >= 9) {
                    throw new IOException("Could not find an available port after 10 attempts");
                }

                this.warning("Port " + actualPort + " is in use, trying " + (actualPort + 1));
                actualPort++;
            }
        }

        this.executor = Executors.newFixedThreadPool(4);
        this.server.setExecutor(this.executor);
        this.info("Setting up HTTP handlers...");
        this.server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                try {
                    String response = WebChat.this.getHtmlPage();
                    byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(200, responseBytes.length);

                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBytes);
                        os.flush();
                    }

                    WebChat.this.info("Served main page to " + exchange.getRemoteAddress());
                } catch (Exception e) {
                    WebChat.this.error("Error serving main page: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        this.server.createContext("/api/messages", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                JsonObject response = new JsonObject();
                response.add("messages", WebChat.this.gson.toJsonTree(WebChat.this.messageHistory));
                response.addProperty("showCoordinates", WebChat.this.showCoordinates.get());
                if (WebChat.this.showCoordinates.get()) {
                    response.addProperty("x", WebChat.this.currentX);
                    response.addProperty("y", WebChat.this.currentY);
                    response.addProperty("z", WebChat.this.currentZ);
                    response.addProperty("dimension", WebChat.this.currentDimension);
                    if (WebChat.this.currentDimension.equals("Overworld")) {
                        response.addProperty("netherX", WebChat.this.currentX / 8);
                        response.addProperty("netherZ", WebChat.this.currentZ / 8);
                    } else if (WebChat.this.currentDimension.equals("Nether")) {
                        response.addProperty("overworldX", WebChat.this.currentX * 8);
                        response.addProperty("overworldZ", WebChat.this.currentZ * 8);
                    }
                }

                String jsonResponse = WebChat.this.gson.toJson(response);
                exchange.sendResponseHeaders(200, jsonResponse.getBytes(StandardCharsets.UTF_8).length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(jsonResponse.getBytes(StandardCharsets.UTF_8));
                }
            }
        });
        this.server.createContext("/api/send", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if ("POST".equals(exchange.getRequestMethod())) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject request = WebChat.this.gson.fromJson(body, JsonObject.class);
                    String message = request.get("message").getAsString();
                    if (message != null && !message.isEmpty()) {
                        WebChat.this.commandQueue.offer(message);
                    }

                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    exchange.sendResponseHeaders(200, 0L);
                } else if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                    exchange.sendResponseHeaders(204, -1L);
                }
            }
        });
        this.server.createContext("/health", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String response = "OK";
                exchange.sendResponseHeaders(200, response.length());

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        });
        this.server.start();
        int listeningPort = this.server.getAddress().getPort();
        if (listeningPort != this.port.get()) {
            this.warning("Server started on port " + listeningPort + " instead of configured port " + this.port.get());
        }

        this.info("HTTP server started successfully on port " + listeningPort);
    }

    private void stopWebServer() {
        try {
            if (this.server != null) {
                this.info("Stopping HTTP server...");
                this.server.stop(0);
                this.server = null;
            }

            if (this.executor != null) {
                this.executor.shutdownNow();
                this.executor = null;
            }
        } catch (Exception e) {
            this.error("Error stopping server: " + e.getMessage());
        }
    }

    private void openBrowser() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Action.BROWSE)) {
                int actualPort = this.server != null ? this.server.getAddress().getPort() : this.port.get();
                Desktop.getDesktop().browse(new URI("http://localhost:" + actualPort));
                this.info("Opened browser at http://localhost:" + actualPort);
            }
        } catch (Exception e) {
            this.warning("Could not open browser: " + e.getMessage());
        }
    }

    @EventHandler
    private void onTick(Post event) {
        if (this.openBrowserButton.get()) {
            this.openBrowserButton.set(false);
            if (this.serverRunning) {
                this.openBrowser();
                this.info("Opening web chat in browser...");
            } else {
                this.warning("Web server is not running! Activate the module first.");
            }
        }

        while (!this.commandQueue.isEmpty()) {
            String message = this.commandQueue.poll();
            if (message != null && this.mc.player != null && this.mc.player.connection != null) {
                if (message.startsWith("/")) {
                    this.mc.player.connection.sendCommand(message.substring(1));
                } else {
                    this.mc.player.connection.sendChat(message);
                }
            }
        }

        if (this.mc.player != null && this.showCoordinates.get()) {
            BlockPos pos = this.mc.player.blockPosition();
            this.currentX = pos.getX();
            this.currentY = pos.getY();
            this.currentZ = pos.getZ();
            String dimPath = this.mc.level.dimension().identifier().getPath();
            if (dimPath.contains("the_nether")) {
                this.currentDimension = "Nether";
            } else if (dimPath.contains("the_end")) {
                this.currentDimension = "End";
            } else {
                this.currentDimension = "Overworld";
            }
        }
    }

    @EventHandler(priority = 200)
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (this.serverRunning && event.getMessage() != null) {
            Component msg = event.getMessage();
            String plainText = this.stripFormatting(msg.getString());
            if (this.shouldShowMessage(plainText, msg)) {
                String timestamp = this.showTimestamps.get() ? "[" + LocalTime.now().format(TIME_FMT) + "] " : "";
                String color = this.getColorForMessage(plainText, msg);
                this.addMessage(timestamp + plainText, color, "received");
            }
        }
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (this.serverRunning && event.message != null) {
            String timestamp = this.showTimestamps.get() ? "[" + LocalTime.now().format(TIME_FMT) + "] " : "";
            String displayMessage = timestamp + "<" + this.mc.getUser().getName() + "> " + event.message;
            this.addMessage(displayMessage, "#ffffff", "sent");
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (this.serverRunning) {
            this.addSystemMessage("Disconnected from server");
        }
    }

    private void addMessage(String text, String color, String type) {
        synchronized (this.messageHistory) {
            this.messageHistory.add(new WebChat.ChatMessage(text, color, type));

            while (this.messageHistory.size() > this.maxMessages.get()) {
                this.messageHistory.remove(0);
            }
        }
    }

    private void addSystemMessage(String message) {
        String timestamp = this.showTimestamps.get() ? "[" + LocalTime.now().format(TIME_FMT) + "] " : "";
        this.addMessage(timestamp + "[SYSTEM] " + message, "#ffc864", "system");
    }

    private boolean shouldShowMessage(String plainText, Component msg) {
        if (plainText != null && !plainText.isEmpty()) {
            boolean isPlayerChat = plainText.matches("^<[^>]+>.*") || plainText.contains(" whispers") || plainText.contains("-> me");
            if (isPlayerChat && !this.showPlayerMessages.get()) {
                return false;
            } else {
                return !isPlayerChat && !this.showSystemMessages.get() ? false : this.showDeathMessages.get() || !this.isDeathMessage(msg);
            }
        } else {
            return false;
        }
    }

    private boolean isDeathMessage(Component msg) {
        if (!(msg.getContents() instanceof TranslatableContents tc)) {
            return false;
        } else {
            String key = tc.getKey();
            return key != null && key.startsWith("death.");
        }
    }

    private String getColorForMessage(String message, Component text) {
        Style style = text.getStyle();
        if (style != null && style.getColor() != null) {
            ChatFormatting formatting = ChatFormatting.getByName(style.getColor().serialize());
            if (formatting != null) {
                return this.getHexFromFormatting(formatting);
            }
        }

        if (message.contains("[Server]") || message.contains("[System]")) {
            return "#ffff55";
        } else if (message.contains("joined the game") || message.contains("left the game")) {
            return "#aaaaaa";
        } else if (message.matches("^<[^>]+>.*")) {
            return "#ffffff";
        } else {
            return !message.contains("whispers") && !message.contains("-> me") ? "#c8c8c8" : "#ff55ff";
        }
    }

    private String getHexFromFormatting(ChatFormatting formatting) {
        return switch (formatting) {
            case BLACK -> "#000000";
            case DARK_BLUE -> "#0000aa";
            case DARK_GREEN -> "#00aa00";
            case DARK_AQUA -> "#00aaaa";
            case DARK_RED -> "#aa0000";
            case DARK_PURPLE -> "#aa00aa";
            case GOLD -> "#ffaa00";
            case GRAY -> "#aaaaaa";
            case DARK_GRAY -> "#555555";
            case BLUE -> "#5555ff";
            case GREEN -> "#55ff55";
            case AQUA -> "#55ffff";
            case RED -> "#ff5555";
            case LIGHT_PURPLE -> "#ff55ff";
            case YELLOW -> "#ffff55";
            case WHITE -> "#ffffff";
            default -> "#c8c8c8";
        };
    }

    private String stripFormatting(String text) {
        return text.replaceAll("§[0-9a-fklmnor]", "");
    }

    public boolean shouldHideInGameChat() {
        return this.hideChatInGame.get();
    }

    private String getPageTitle() {
        if (!this.pageTitle.get().isEmpty()) {
            return this.pageTitle.get();
        } else if (this.mc.getCurrentServer() != null) {
            return this.mc.getCurrentServer().ip;
        } else {
            return this.mc.isLocalServer() ? "Singleplayer" : "Minecraft Web Chat";
        }
    }

    private String getHtmlPage() {
        String title = this.getPageTitle();
        return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n    <meta charset=\"UTF-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n    <title>"
            + title
            + "</title>    <style>\n        * {\n            margin: 0;\n            padding: 0;\n            box-sizing: border-box;\n        }\n        body {\n            font-family: 'Consolas', 'Monaco', monospace;\n            background: linear-gradient(135deg, #1e1e2e 0%, #2d2d44 100%);\n            color: #ffffff;\n            height: 100vh;\n            display: flex;\n            flex-direction: column;\n        }\n        #header {\n            background: rgba(0, 0, 0, 0.3);\n            padding: 15px 20px;\n            border-bottom: 2px solid #444;\n            backdrop-filter: blur(10px);\n        }\n        #header h1 {\n            font-size: 24px;\n            color: #55ff55;\n            margin-bottom: 0;\n        }\n        #header.no-coords h1 {\n            margin-bottom: 0;\n        }\n        #coordinates {\n            display: flex;\n            gap: 20px;\n            font-size: 14px;\n            color: #aaaaaa;\n            margin-top: 10px;\n        }\n        .coord-group {\n            display: flex;\n            align-items: center;\n            gap: 10px;\n        }\n        .coord-label {\n            color: #888;\n        }\n        .coord-value {\n            color: #55ffff;\n            font-weight: bold;\n        }\n        .dimension {\n            color: #ffaa00;\n            font-weight: bold;\n        }\n        #chat-container {\n            flex: 1;\n            overflow-y: auto;\n            padding: 20px;\n            background: rgba(0, 0, 0, 0.2);\n            margin: 10px;\n            border-radius: 10px;\n            backdrop-filter: blur(5px);\n        }\n        .message {\n            padding: 5px 10px;\n            margin: 2px 0;\n            border-radius: 4px;\n            background: rgba(0, 0, 0, 0.3);\n            word-wrap: break-word;\n            animation: slideIn 0.3s ease-out;\n        }\n        @keyframes slideIn {\n            from {\n                opacity: 0;\n                transform: translateX(-20px);\n            }\n            to {\n                opacity: 1;\n                transform: translateX(0);\n            }\n        }\n        .message.sent {\n            background: rgba(0, 100, 200, 0.2);\n            border-left: 3px solid #0064c8;\n        }\n        .message.system {\n            background: rgba(255, 200, 100, 0.2);\n            border-left: 3px solid #ffc864;\n        }\n        #input-container {\n            padding: 20px;\n            background: rgba(0, 0, 0, 0.4);\n            border-top: 2px solid #444;\n            display: flex;\n            gap: 10px;\n        }\n        #message-input {\n            flex: 1;\n            padding: 12px;\n            background: rgba(30, 30, 30, 0.8);\n            border: 1px solid #444;\n            color: white;\n            font-family: inherit;\n            font-size: 14px;\n            border-radius: 5px;\n            outline: none;\n            transition: border-color 0.3s;\n        }\n        #message-input:focus {\n            border-color: #55ff55;\n        }\n        #send-button {\n            padding: 12px 30px;\n            background: linear-gradient(135deg, #55ff55 0%, #00aa00 100%);\n            color: black;\n            border: none;\n            font-weight: bold;\n            cursor: pointer;\n            border-radius: 5px;\n            transition: transform 0.2s, box-shadow 0.2s;\n        }\n        #send-button:hover {\n            transform: translateY(-2px);\n            box-shadow: 0 5px 15px rgba(85, 255, 85, 0.3);\n        }\n        #send-button:active {\n            transform: translateY(0);\n        }\n        #status {\n            position: absolute;\n            top: 15px;\n            right: 20px;\n            padding: 5px 10px;\n            background: rgba(0, 255, 0, 0.2);\n            border: 1px solid #00ff00;\n            border-radius: 20px;\n            font-size: 12px;\n            color: #00ff00;\n        }\n        #status.disconnected {\n            background: rgba(255, 0, 0, 0.2);\n            border-color: #ff0000;\n            color: #ff0000;\n        }\n        .conversion-info {\n            font-size: 12px;\n            color: #888;\n            margin-left: 5px;\n        }\n        ::-webkit-scrollbar {\n            width: 10px;\n        }\n        ::-webkit-scrollbar-track {\n            background: rgba(0, 0, 0, 0.2);\n        }\n        ::-webkit-scrollbar-thumb {\n            background: rgba(85, 255, 85, 0.3);\n            border-radius: 5px;\n        }\n        ::-webkit-scrollbar-thumb:hover {\n            background: rgba(85, 255, 85, 0.5);\n        }\n    </style>\n</head>\n<body>\n    <div id=\"header\">\n        <h1>\ud83c\udf10"
            + title
            + "</h1>        <div id=\"coordinates\" style=\"display: none;\">\n            <div class=\"coord-group\">\n                <span class=\"coord-label\">Dimension:</span>\n                <span id=\"dimension\" class=\"dimension\">Overworld</span>\n            </div>\n            <div class=\"coord-group\">\n                <span class=\"coord-label\">Current:</span>\n                <span class=\"coord-value\">X: <span id=\"x\">0</span></span>\n                <span class=\"coord-value\">Y: <span id=\"y\">0</span></span>\n                <span class=\"coord-value\">Z: <span id=\"z\">0</span></span>\n            </div>\n            <div class=\"coord-group\" id=\"conversion-coords\" style=\"display: none;\">\n                <span class=\"coord-label\" id=\"conversion-label\">Nether:</span>\n                <span class=\"coord-value\">X: <span id=\"conv-x\">0</span></span>\n                <span class=\"coord-value\">Z: <span id=\"conv-z\">0</span></span>\n            </div>\n        </div>\n        <div id=\"status\">● Connected</div>\n    </div>\n    <div id=\"chat-container\"></div>\n    <div id=\"input-container\">\n        <input type=\"text\" id=\"message-input\" placeholder=\"Type a message or command...\" autofocus>\n        <button id=\"send-button\">Send</button>\n    </div>\n    <script>\n        const chatContainer = document.getElementById('chat-container');\n        const messageInput = document.getElementById('message-input');\n        const sendButton = document.getElementById('send-button');\n        const statusDiv = document.getElementById('status');\n        let lastMessageCount = 0;\n        let connected = true;\n        async function fetchMessages() {\n            try {\n                const response = await fetch('/api/messages');\n                const data = await response.json();\n                const coordsDiv = document.getElementById('coordinates');\n                const headerDiv = document.getElementById('header');\n                if (data.showCoordinates) {\n                    coordsDiv.style.display = 'flex';\n                    headerDiv.classList.remove('no-coords');\n                    document.getElementById('x').textContent = data.x;\n                    document.getElementById('y').textContent = data.y;\n                    document.getElementById('z').textContent = data.z;\n                    document.getElementById('dimension').textContent = data.dimension;\n                    const conversionGroup = document.getElementById('conversion-coords');\n                    const conversionLabel = document.getElementById('conversion-label');\n                    if (data.dimension === 'Overworld' && data.netherX !== undefined) {\n                        conversionGroup.style.display = 'flex';\n                        conversionLabel.textContent = 'Nether:';\n                        document.getElementById('conv-x').textContent = data.netherX;\n                        document.getElementById('conv-z').textContent = data.netherZ;\n                    } else if (data.dimension === 'Nether' && data.overworldX !== undefined) {\n                        conversionGroup.style.display = 'flex';\n                        conversionLabel.textContent = 'Overworld:';\n                        document.getElementById('conv-x').textContent = data.overworldX;\n                        document.getElementById('conv-z').textContent = data.overworldZ;\n                    } else if (data.dimension === 'End') {\n                        conversionGroup.style.display = 'none';\n                    }\n                } else {\n                    coordsDiv.style.display = 'none';\n                    headerDiv.classList.add('no-coords');\n                }\n                if (data.messages && data.messages.length > lastMessageCount) {\n                    const newMessages = data.messages.slice(lastMessageCount);\n                    newMessages.forEach(msg => {\n                        const messageDiv = document.createElement('div');\n                        messageDiv.className = 'message ' + msg.type;\n                        messageDiv.style.color = msg.color;\n                        messageDiv.textContent = msg.text;\n                        chatContainer.appendChild(messageDiv);\n                    });\n                    lastMessageCount = data.messages.length;\n                    chatContainer.scrollTop = chatContainer.scrollHeight;\n                } else if (data.messages && data.messages.length < lastMessageCount) {\n                    chatContainer.innerHTML = '';\n                    data.messages.forEach(msg => {\n                        const messageDiv = document.createElement('div');\n                        messageDiv.className = 'message ' + msg.type;\n                        messageDiv.style.color = msg.color;\n                        messageDiv.textContent = msg.text;\n                        chatContainer.appendChild(messageDiv);\n                    });\n                    lastMessageCount = data.messages.length;\n                }\n                if (!connected) {\n                    connected = true;\n                    statusDiv.textContent = '● Connected';\n                    statusDiv.classList.remove('disconnected');\n                }\n            } catch (error) {\n                if (connected) {\n                    connected = false;\n                    statusDiv.textContent = '● Disconnected';\n                    statusDiv.classList.add('disconnected');\n                }\n            }\n        }\n        async function sendMessage() {\n            const message = messageInput.value.trim();\n            if (!message) return;\n            try {\n                await fetch('/api/send', {\n                    method: 'POST',\n                    headers: {\n                        'Content-Type': 'application/json'\n                    },\n                    body: JSON.stringify({ message })\n                });\n                messageInput.value = '';\n            } catch (error) {\n                console.error('Failed to send message:', error);\n            }\n        }\n        sendButton.addEventListener('click', sendMessage);\n        messageInput.addEventListener('keypress', (e) => {\n            if (e.key === 'Enter') {\n                sendMessage();\n            }\n        });\n        setInterval(fetchMessages, 500);\n        fetchMessages();\n    </script>\n</body>\n</html>\n";
    }

    private static class ChatMessage {
        final String text;
        final String color;
        final String type;

        ChatMessage(String text, String color, String type) {
            this.text = text;
            this.color = color;
            this.type = type;
        }
    }
}
