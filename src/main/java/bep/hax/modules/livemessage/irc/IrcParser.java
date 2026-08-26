package bep.hax.modules.livemessage.irc;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IrcParser {
    public static IrcParser.ParsedMessage parse(String raw) {
        if (raw != null && !raw.isEmpty()) {
            IrcParser.ParsedMessage msg = new IrcParser.ParsedMessage();
            int idx = 0;
            if (raw.startsWith("@")) {
                int spaceIdx = raw.indexOf(32);
                if (spaceIdx == -1) {
                    return null;
                }

                String tagString = raw.substring(1, spaceIdx);
                msg.tags = parseTags(tagString);
                idx = spaceIdx + 1;

                while (idx < raw.length() && raw.charAt(idx) == ' ') {
                    idx++;
                }
            }

            if (idx < raw.length() && raw.charAt(idx) == ':') {
                int spaceIdx = raw.indexOf(32, idx);
                if (spaceIdx == -1) {
                    return null;
                }

                msg.prefix = raw.substring(idx + 1, spaceIdx);
                idx = spaceIdx + 1;
            }

            while (idx < raw.length() && raw.charAt(idx) == ' ') {
                idx++;
            }

            int cmdEnd = raw.indexOf(32, idx);
            if (cmdEnd == -1) {
                msg.command = raw.substring(idx).toUpperCase();
                msg.params = new String[0];
                return msg;
            }

            msg.command = raw.substring(idx, cmdEnd).toUpperCase();
            idx = cmdEnd + 1;

            while (idx < raw.length() && raw.charAt(idx) == ' ') {
                idx++;
            }

            List<String> paramList = new ArrayList<>();

            while (idx < raw.length()) {
                if (raw.charAt(idx) == ':') {
                    msg.trailing = raw.substring(idx + 1);
                    break;
                }

                int nextSpace = raw.indexOf(32, idx);
                if (nextSpace == -1) {
                    paramList.add(raw.substring(idx));
                    break;
                }

                paramList.add(raw.substring(idx, nextSpace));
                idx = nextSpace + 1;

                while (idx < raw.length() && raw.charAt(idx) == ' ') {
                    idx++;
                }
            }

            msg.params = paramList.toArray(new String[0]);
            return msg;
        } else {
            return null;
        }
    }

    private static Map<String, String> parseTags(String tagString) {
        Map<String, String> tags = new HashMap<>();
        if (tagString != null && !tagString.isEmpty()) {
            String[] parts = tagString.split(";");

            for (String part : parts) {
                int eqIdx = part.indexOf(61);
                if (eqIdx > 0) {
                    String key = part.substring(0, eqIdx);
                    String value = unescapeTagValue(part.substring(eqIdx + 1));
                    tags.put(key, value);
                } else if (!part.isEmpty()) {
                    tags.put(part, "");
                }
            }

            return tags;
        } else {
            return tags;
        }
    }

    private static String unescapeTagValue(String value) {
        if (value != null && value.contains("\\")) {
            StringBuilder sb = new StringBuilder(value.length());

            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '\\' && i + 1 < value.length()) {
                    char next = value.charAt(++i);
                    switch (next) {
                        case ':':
                            sb.append(';');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 's':
                            sb.append(' ');
                            break;
                        default:
                            sb.append(next);
                    }
                } else {
                    sb.append(c);
                }
            }

            return sb.toString();
        } else {
            return value;
        }
    }

    public static class ParsedMessage {
        public Map<String, String> tags;
        public String prefix;
        public String command;
        public String[] params;
        public String trailing;

        public long getServerTimestamp() {
            if (this.tags == null) {
                return -1L;
            }

            String timeTag = this.tags.get("time");
            if (timeTag == null) {
                return -1L;
            }

            try {
                Instant instant = Instant.parse(timeTag);
                return instant.toEpochMilli();
            } catch (DateTimeParseException e) {
                return -1L;
            }
        }

        public String getNick() {
            if (this.prefix == null) {
                return null;
            } else {
                int bangIndex = this.prefix.indexOf(33);
                if (bangIndex > 0) {
                    return this.prefix.substring(0, bangIndex);
                } else {
                    return this.prefix.contains(".") ? null : this.prefix;
                }
            }
        }
    }
}
