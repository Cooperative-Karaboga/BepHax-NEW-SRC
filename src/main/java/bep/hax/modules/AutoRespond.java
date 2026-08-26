package bep.hax.modules;

import bep.hax.Bep;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class AutoRespond extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgResponses = this.settings.createGroup("Responses");
    private final SettingGroup sgTiming = this.settings.createGroup("Timing");
    private final SettingGroup sgFilters = this.settings.createGroup("Filters");
    private final Setting<String> trigger = this.sgGeneral
        .add(new Builder().name("trigger").description("Word or phrase to detect in messages.").defaultValue("doh").build());
    private final Setting<AutoRespond.MatchMode> matchMode = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("match-mode"))
                        .description("How to match the trigger in messages."))
                    .defaultValue(AutoRespond.MatchMode.Contains))
                .build()
        );
    private final Setting<Boolean> caseSensitive = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("case-sensitive")
                .description("Whether trigger detection is case sensitive.")
                .defaultValue(false)
                .build()
        );
    private final Setting<List<String>> responses = this.sgResponses
        .add(
            new meteordevelopment.meteorclient.settings.StringListSetting.Builder()
                .name("responses")
                .description("List of responses. Use the + button to add more.")
                .defaultValue(List.of("doh"))
                .build()
        );
    private final Setting<AutoRespond.ResponseMode> responseMode = this.sgResponses
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("response-mode"))
                        .description("How to select responses from the list."))
                    .defaultValue(AutoRespond.ResponseMode.Random))
                .build()
        );
    private final Setting<Boolean> usePrivateReply = this.sgResponses
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("private-reply")
                .description("Reply to whispers using /msg command.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> cooldown = this.sgTiming
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("cooldown")
                .description("Cooldown between responses in seconds.")
                .defaultValue(60)
                .min(0)
                .max(300)
                .sliderMin(0)
                .sliderMax(300)
                .build()
        );
    private final Setting<Integer> minDelay = this.sgTiming
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("min-delay")
                .description("Minimum delay before responding in milliseconds.")
                .defaultValue(500)
                .min(0)
                .max(10000)
                .sliderMin(0)
                .sliderMax(5000)
                .build()
        );
    private final Setting<Integer> maxDelay = this.sgTiming
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-delay")
                .description("Maximum delay before responding in milliseconds.")
                .defaultValue(2000)
                .min(0)
                .max(10000)
                .sliderMin(0)
                .sliderMax(5000)
                .build()
        );
    private final Setting<Boolean> respondToSelf = this.sgFilters
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("respond-to-self")
                .description("Respond to your own messages.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> respondToWhispers = this.sgFilters
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("respond-to-whispers")
                .description("Respond to private/whisper messages.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> ignoreServerMessages = this.sgFilters
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("ignore-server-messages")
                .description("Ignore messages from the server (no player name).")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> logTriggers = this.sgFilters
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("log-triggers")
                .description("Log when trigger is detected (for debugging).")
                .defaultValue(false)
                .build()
        );
    private static final Pattern CHAT_PATTERN_2B2T = Pattern.compile("^<\\d{1,2}:\\d{2}> <([a-zA-Z0-9_]{1,16})> (.+)$");
    private static final Pattern WHISPER_PATTERN_2B2T = Pattern.compile("^<\\d{1,2}:\\d{2}> ([a-zA-Z0-9_]{1,16}) whispers: (.+)$");
    private static final Pattern TO_PATTERN_2B2T = Pattern.compile("^<\\d{1,2}:\\d{2}> to ([a-zA-Z0-9_]{1,16}): (.+)$");
    private final Random random = new Random();
    private long lastResponseTime = 0L;
    private String pendingResponse = null;
    private String pendingTargetPlayer = null;
    private long responseScheduledTime = 0L;
    private int currentResponseIndex = 0;

    public AutoRespond() {
        super(Bep.CATEGORY, "auto-respond", "Automatically respond when someone says a specific word or phrase.");
    }

    @Override
    public void onActivate() {
        this.lastResponseTime = 0L;
        this.pendingResponse = null;
        this.pendingTargetPlayer = null;
        this.responseScheduledTime = 0L;
        this.currentResponseIndex = 0;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (this.mc.player != null && this.mc.level != null) {
            String messageText = event.getMessage().getString();
            AutoRespond.ChatMessage parsedMessage = this.parseMessage(messageText);
            if (parsedMessage == null) {
                if (this.logTriggers.get()) {
                    this.info("Failed to parse message: %s", messageText);
                }
            } else if (!this.ignoreServerMessages.get() || parsedMessage.playerName != null) {
                if (this.respondToSelf.get() || !parsedMessage.isFromSelf) {
                    if (this.respondToWhispers.get() || !parsedMessage.isWhisper) {
                        if (this.messageContainsTrigger(parsedMessage.content)) {
                            long currentTime = System.currentTimeMillis();
                            long cooldownMs = this.cooldown.get().intValue() * 1000L;
                            if (currentTime - this.lastResponseTime < cooldownMs) {
                                if (this.logTriggers.get()) {
                                    this.info(
                                        "Trigger detected but on cooldown (%d seconds remaining)", (cooldownMs - (currentTime - this.lastResponseTime)) / 1000L
                                    );
                                }
                            } else {
                                String selectedResponse = this.getNextResponse();
                                if (selectedResponse != null && !selectedResponse.isEmpty()) {
                                    int delayMs = this.minDelay.get() + this.random.nextInt(Math.max(1, this.maxDelay.get() - this.minDelay.get() + 1));
                                    this.responseScheduledTime = currentTime + delayMs;
                                    this.pendingResponse = selectedResponse;
                                    this.pendingTargetPlayer = parsedMessage.isWhisper && this.usePrivateReply.get() ? parsedMessage.playerName : null;
                                    this.lastResponseTime = currentTime;
                                    if (this.logTriggers.get()) {
                                        String targetInfo = this.pendingTargetPlayer != null ? " to " + this.pendingTargetPlayer : "";
                                        this.info("Trigger detected! Responding%s in %dms", targetInfo, delayMs);
                                    }
                                } else {
                                    if (this.logTriggers.get()) {
                                        this.info("No valid response available");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.pendingResponse != null && System.currentTimeMillis() >= this.responseScheduledTime) {
                this.sendResponse(this.pendingResponse, this.pendingTargetPlayer);
                this.pendingResponse = null;
                this.pendingTargetPlayer = null;
                this.responseScheduledTime = 0L;
            }
        }
    }

    private String getNextResponse() {
        List<String> responseList = this.responses.get();
        if (responseList != null && !responseList.isEmpty()) {
            return switch ((AutoRespond.ResponseMode)this.responseMode.get()) {
                case Random -> (String)responseList.get(this.random.nextInt(responseList.size()));
                case Sequential -> {
                    String response = responseList.get(this.currentResponseIndex % responseList.size());
                    this.currentResponseIndex++;
                    yield response;
                }
            };
        } else {
            return null;
        }
    }

    private AutoRespond.ChatMessage parseMessage(String rawMessage) {
        if (this.mc.player == null) {
            return null;
        }

        String playerName = this.mc.player.getName().getString();
        Matcher chatMatcher = CHAT_PATTERN_2B2T.matcher(rawMessage);
        if (chatMatcher.matches()) {
            String sender = chatMatcher.group(1);
            String content = chatMatcher.group(2);
            return new AutoRespond.ChatMessage(sender, content, false, sender.equals(playerName));
        }

        Matcher whisperMatcher = WHISPER_PATTERN_2B2T.matcher(rawMessage);
        if (whisperMatcher.matches()) {
            String sender = whisperMatcher.group(1);
            String content = whisperMatcher.group(2);
            return new AutoRespond.ChatMessage(sender, content, true, sender.equals(playerName));
        }

        Matcher toMatcher = TO_PATTERN_2B2T.matcher(rawMessage);
        if (toMatcher.matches()) {
            String recipient = toMatcher.group(1);
            String content = toMatcher.group(2);
            return new AutoRespond.ChatMessage(recipient, content, true, true);
        }

        if (rawMessage.contains(">")) {
            int startIdx = rawMessage.indexOf(60);
            int endIdx = rawMessage.indexOf(62);
            if (startIdx >= 0 && endIdx > startIdx) {
                String sender = rawMessage.substring(startIdx + 1, endIdx);
                String content = rawMessage.substring(endIdx + 1).trim();
                if (sender.matches("[a-zA-Z0-9_]{1,16}")) {
                    return new AutoRespond.ChatMessage(sender, content, false, sender.equals(playerName));
                }
            }
        }

        return new AutoRespond.ChatMessage(null, rawMessage, false, false);
    }

    private boolean messageContainsTrigger(String message) {
        String triggerText = this.trigger.get();
        if (triggerText.isEmpty()) {
            return false;
        }

        String messageToCheck = this.caseSensitive.get() ? message : message.toLowerCase();
        String triggerToCheck = this.caseSensitive.get() ? triggerText : triggerText.toLowerCase();

        return switch ((AutoRespond.MatchMode)this.matchMode.get()) {
            case Contains -> messageToCheck.contains(triggerToCheck);
            case StartsWith -> messageToCheck.startsWith(triggerToCheck);
            case EndsWith -> messageToCheck.endsWith(triggerToCheck);
            case Exact -> messageToCheck.equals(triggerToCheck);
            case Word -> this.containsWord(messageToCheck, triggerToCheck);
        };
    }

    private boolean containsWord(String message, String word) {
        String regex = "(?i)\\b" + Pattern.quote(word) + "\\b";
        return Pattern.compile(regex).matcher(message).find();
    }

    private void sendResponse(String message, String targetPlayer) {
        if (this.mc.player != null) {
            String finalMessage;
            if (targetPlayer != null && this.usePrivateReply.get()) {
                finalMessage = "/msg " + targetPlayer + " " + message;
            } else {
                finalMessage = message;
            }

            this.mc.player.connection.sendChat(finalMessage);
            if (this.logTriggers.get()) {
                this.info("Sent response: %s", finalMessage);
            }
        }
    }

    @Override
    public String getInfoString() {
        if (this.pendingResponse != null) {
            long remainingMs = this.responseScheduledTime - System.currentTimeMillis();
            if (remainingMs > 0L) {
                return String.format("Responding in %.1fs", remainingMs / 1000.0);
            }
        }

        long cooldownRemaining = this.cooldown.get().intValue() * 1000L - (System.currentTimeMillis() - this.lastResponseTime);
        return cooldownRemaining > 0L && this.lastResponseTime > 0L ? String.format("Cooldown: %ds", cooldownRemaining / 1000L) : null;
    }

    private static class ChatMessage {
        public final String playerName;
        public final String content;
        public final boolean isWhisper;
        public final boolean isFromSelf;

        public ChatMessage(String playerName, String content, boolean isWhisper, boolean isFromSelf) {
            this.playerName = playerName;
            this.content = content;
            this.isWhisper = isWhisper;
            this.isFromSelf = isFromSelf;
        }
    }

    public enum MatchMode {
        Contains("Contains"),
        StartsWith("Starts With"),
        EndsWith("Ends With"),
        Exact("Exact"),
        Word("Whole Word");

        private final String title;

        MatchMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return this.title;
        }
    }

    public enum ResponseMode {
        Random("Random"),
        Sequential("Sequential");

        private final String title;

        ResponseMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return this.title;
        }
    }
}
