package bep.hax.emoji;

import bep.hax.Bep;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class EmojiData {
    private static final Pattern SHORTCODE = Pattern.compile(":([A-Za-z0-9_+\\-]+):");
    private static final Map<String, String> MAP = load();
    private static final IntSet CODEPOINTS = loadCodepoints();

    public static boolean enabled() {
        return true;
    }

    private EmojiData() {
    }

    public static boolean isEmoji(int codePoint) {
        return CODEPOINTS.contains(codePoint);
    }

    private static IntSet loadCodepoints() {
        try (InputStream in = EmojiData.class.getResourceAsStream("/assets/bephax/emoji/codepoints.json")) {
            if (in == null) {
                Bep.LOG.warn("[Emoji] codepoints.json not found");
                return new IntOpenHashSet();
            }

            Type type = (new TypeToken<List<Integer>>() {}).getType();
            List<Integer> list = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
            IntOpenHashSet set = new IntOpenHashSet();
            if (list != null) {
                for (Integer cp : list) {
                    set.add(cp);
                }
            }

            return set;
        } catch (Exception e) {
            Bep.LOG.error("[Emoji] failed to load codepoints.json", e);
            return new IntOpenHashSet();
        }
    }

    private static Map<String, String> load() {
        try (InputStream in = EmojiData.class.getResourceAsStream("/assets/bephax/emoji/shortcodes.json")) {
            if (in == null) {
                Bep.LOG.warn("[Emoji] shortcodes.json not found");
                return Collections.emptyMap();
            } else {
                Type type = (new TypeToken<Map<String, String>>() {}).getType();
                Map<String, String> map = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
                return map != null ? map : Collections.emptyMap();
            }
        } catch (Exception e) {
            Bep.LOG.error("[Emoji] failed to load shortcodes.json", e);
            return Collections.emptyMap();
        }
    }

    public static String expand(String text) {
        if (text != null && !text.isEmpty()) {
            if (text.indexOf(58) >= 0 && !MAP.isEmpty()) {
                Matcher m = SHORTCODE.matcher(text);
                StringBuilder out = new StringBuilder();

                while (m.find()) {
                    String emoji = MAP.get(m.group(1).toLowerCase(Locale.ROOT));
                    m.appendReplacement(out, Matcher.quoteReplacement(emoji != null ? emoji : m.group()));
                }

                m.appendTail(out);
                text = out.toString();
            }

            return stripUnrenderable(text);
        } else {
            return text;
        }
    }

    public static String stripUnrenderable(String text) {
        if (text != null && !text.isEmpty()) {
            StringBuilder out = null;
            int i = 0;
            int n = text.length();

            while (i < n) {
                int cp = text.codePointAt(i);
                int len = Character.charCount(cp);
                boolean drop = cp == 65038 || cp == 65039 || cp == 8205 || cp >= 127995 && cp <= 127999;
                boolean regional = cp >= 127462 && cp <= 127487;
                if ((drop || regional) && out == null) {
                    out = new StringBuilder(n);
                    out.append(text, 0, i);
                }

                if (out != null) {
                    if (regional) {
                        out.append((char)(65 + cp - 127462));
                    } else if (!drop) {
                        out.appendCodePoint(cp);
                    }
                }

                i += len;
            }

            return out == null ? text : out.toString();
        } else {
            return text;
        }
    }

    public static Component expandText(Component text) {
        if (text == null) {
            return text;
        }

        String flat = text.getString();
        boolean hasShortcodes = !MAP.isEmpty() && flat.indexOf(58) >= 0;
        return !hasShortcodes && stripUnrenderable(flat) == flat ? text : rebuild(text);
    }

    private static MutableComponent rebuild(Component text) {
        ComponentContents content = text.getContents();
        MutableComponent out;
        if (content instanceof PlainTextContents plain) {
            out = MutableComponent.create(PlainTextContents.create(expand(plain.text())));
        } else if (content instanceof TranslatableContents tr) {
            Object[] args = tr.getArgs();
            Object[] newArgs = new Object[args.length];

            for (int i = 0; i < args.length; i++) {
                Object a = args[i];
                if (a instanceof Component t) {
                    newArgs[i] = rebuild(t);
                } else if (a instanceof String s) {
                    newArgs[i] = expand(s);
                } else {
                    newArgs[i] = a;
                }
            }

            out = MutableComponent.create(new TranslatableContents(tr.getKey(), tr.getFallback(), newArgs));
        } else {
            out = MutableComponent.create(content);
        }

        out.setStyle(text.getStyle());

        for (Component sibling : text.getSiblings()) {
            out.append(rebuild(sibling));
        }

        return out;
    }
}
