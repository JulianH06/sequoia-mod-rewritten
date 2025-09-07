package star.sequoia2.utils.text.parser;

import com.wynntils.core.text.PartStyle;
import com.wynntils.core.text.StyledText;
import com.wynntils.core.text.StyledTextPart;
import net.minecraft.text.*;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.accessors.TeXParserAccessor;
import star.sequoia2.features.impl.Settings;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TeXParser implements FeaturesAccessor, TeXParserAccessor {
    // modified from mit 6.1010 lisp2 lab; "real world applications" can't believe this is the first place i use this

    private final Pattern TOKEN_PATTERN = Pattern.compile(
            "\\\\([^\\s\\\\{}]+)"       // \cmd
                    + "|\\\\([\\\\{}])" // escape sequences
                    + "|\\{"            // brace
                    + "|}"              // other brace
                    + "|[^\\\\{}]+"     // text
    );

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();

        // types of tokens: \c, {, }, text
        Matcher m = TOKEN_PATTERN.matcher(text);

        while (m.find()) {
            tokens.add(m.group());
        }

        return tokens;
    }

    private record R(MutableText text, int next) {}

    private R parseMutableText(List<String> tokens, Integer index) {
        MutableText parsed = Text.empty();

        while (index < tokens.size() && !tokens.get(index).equals("}")) {
            String token = tokens.get(index);
            R literal;
            if (token.equals("\\n")) {
                literal = new R(Text.literal("\n"), index + 1);
            } else if (token.charAt(0) == '\\' && !token.equals("\\\\") && !token.equals("\\{") && !token.equals("\\}")) {
                index++;
                literal = texFunctions.get(token).apply(tokens, index);
            } else {
                literal = getNextLiteral(tokens, index);
            }
            parsed.append(literal.text);
            index = literal.next;
        }
        return new R(parsed, index + 1);
    }

    private R getNextLiteral(List<String> tokens, Integer index) {
        String token = tokens.get(index);
        if (Objects.equals(token, "{")) {
            return parseMutableText(tokens, index + 1);
        } else {
            return new R(Text.literal(token), index + 1);
        }
    }

    /*
     * MutableTeX "documentation"
     * - public  MutableText parseMutableText(String text, Object... parameters)
     * - parameters work the same way as formatted strings
     * - \\\\, \\{, \\} are escape characters
     * -
     * - prefix commands with \\ (technically \, but java escape character)
     * - enclose "groups" with {}
     * - commands take some number of arguments
     *   and will process them whether
     *   they're enclosed in a group or not
     * - e.g. \\color{ff00ff}purple
     *   will result in purple colored "purple"
     * -
     * - gradient{n}{color1}{color2}...{colorN}{text}
     *   colors the given text in a gradient smoothly interpolated through the n colors
     *   where 'n' is the number of color arguments
     *   \\gradient{2}{ff0000}{0000ff}{red to blue}
     *   \\gradient{3}{ffffff}{000000}{ff0000}{white to black to red}
     * -
     * - color{color}{text}
     *   colors the text a constant color
     *   \\color{00ff00}{green}
     * - dcolor does the same thing but with a decimal color
     * -
     * - hover{hoverText}{text}
     *   when hovering over text, hoverText appears
     *   \\hover{this text shows up}{when the cursor hovers over this}
     * -
     * - click{clickAction}{clickEvent}{text}
     *   when clicking the text, the event defined by clickAction and clickEvent triggers
     *   valid clickActions: OPEN_URL, OPEN_FILE, RUN_COMMAND, SUGGEST_COMMAND, CHANGE_PAGE, COPY_TO_CLIPBOARD
     *   clickEvent could be for example /msg starfaiien hi
     *   \\click{RUN_COMMAND}{/msg starfaiien hi}{click to say hi!}
     * -
     * - ranking{rank}
     * - macro for ranking to display " \\-{(}\\2{#%s}\\-{)}" more concisely
     * - \\ranking{50}
     * -
     * - nickname{nick}
     * - macro for player character nicknames to display " \\-{(}\\i{\\3{%s}}\\-{)}" more concisely
     * - \\nickname{lamental illness}
     * -
     * - pill{bgColor}{textColor}{text}
     *   makes a pill text
     *   \\pill{267326}{80dfff}{Sequoia}
     * -
     * - built in formatters
     *   these take a single argument and applies a format to it
     *   \\-{text} light
     *   \\={text} normal
     *   \\+{text} dark
     *   \\1{text} accent1
     *   \\2{text} accent2
     *   \\3{text} accent3
     *   \\i{text} italicize
     *   \\b{text} bold
     *   \\u{text} underline
     *   \\s{text} bold
     *   \\k{text} underline
     * -
     * - many things can be combined, so the following is also a valid command:
     *   \\hover{try clicking this}{\\click{RUN_COMMAND}{/kill}{\\b{\\+{oopsies}} (\\-{this kills you})}} \\={why, hello}
     */

    private final Map<String, BiFunction<List<String>, Integer, R>> texFunctions;

    public TeXParser() {
        Map<String, BiFunction<List<String>, Integer, R>> m = new HashMap<>();
        m.put("\\gradient", this::gradient);       // \gradient{color1}{color2}{text}
        m.put("\\color", this::color);             // \color{n}{color}n{text}
        m.put("\\dcolor", this::dcolor);           // \dcolor{n}{color}n{text}
        m.put("\\hover", this::hover);             // \hover{hoverText}{text}
        m.put("\\click", this::click);             // \click{clickAction}{clickEvent}{text}
        // valid clickActions: OPEN_URL, OPEN_FILE, RUN_COMMAND, SUGGEST_COMMAND, CHANGE_PAGE, COPY_TO_CLIPBOARD
        // idk if all of these actually work but command ones definitely do
        m.put("\\ranking", this::ranking);         // \ranking{rank int} (macro)
        m.put("\\nickname", this::nickname);       // \nickname{nick String} (macro)
        m.put("\\pill", this::pill);               // \pill{bgcolor}{textcolor}{text} (macro)
        m.put("\\-", this::light);                 // \-{text}
        m.put("\\=", this::normal);                // \={text}
        m.put("\\+", this::dark);                  // \+{text}
        m.put("\\1", this::accent1);               // \1{text}
        m.put("\\2", this::accent2);               // \2{text}
        m.put("\\3", this::accent3);               // \3{text}
        m.put("\\i", this::italicize);             // \i{text}
        m.put("\\b", this::boldfont);              // \b{text}
        m.put("\\u", this::underline);             // \ u{text}
        m.put("\\s", this::strikethrough);         // \s{text}
        m.put("\\k", this::obfuscated);            // \ k{text}

        texFunctions = m;
    }
//    private  R gradient(List<String> tokens, int index) {
//        R startLiteral = getNextLiteral(tokens, index);
//        int c0 = Integer.parseInt(startLiteral.text.getString(), 16);
//        index = startLiteral.next;
//        R endLiteral = getNextLiteral(tokens, index);
//        int c1 = Integer.parseInt(endLiteral.text.getString(), 16);
//        index = endLiteral.next;
//        R textLiteral = getNextLiteral(tokens, index);
//        String raw = textLiteral.text.getString();
//        index = textLiteral.next;
//        MutableText out = Text.empty();
//        int r0 = (c0 >> 16) & 0xFF, g0 = (c0 >> 8) & 0xFF,  b0 = c0 & 0xFF;
//        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF,  b1 = c1 & 0xFF;
//        int n = raw.length();
//        for (int i = 0; i < n; i++) {
//            double t = n == 1 ? 0 : (double) i / (n - 1);
//            int r = (int) Math.round(r0 + (r1 - r0) * t);
//            int g = (int) Math.round(g0 + (g1 - g0) * t);
//            int b = (int) Math.round(b0 + (b1 - b0) * t);
//            int rgb = (r << 16) | (g << 8) | b;
//            out.append(Text.literal(String.valueOf(raw.charAt(i)))
//                    .styled(s -> s.withColor(rgb)));
//        }
//        return new R(out, index);
//    }
    private R gradient(List<String> tokens, int index) {
        R countLiteral = getNextLiteral(tokens, index);
        int colorCount = Integer.parseInt(countLiteral.text.getString());
        index = countLiteral.next;

        List<Integer> colors = new ArrayList<>();
        for (int i = 0; i < colorCount; i++) {
            R colorLiteral = getNextLiteral(tokens, index);
            int color = Integer.parseInt(colorLiteral.text.getString(), 16);
            colors.add(color);
            index = colorLiteral.next;
        }

        R textLiteral = getNextLiteral(tokens, index);
        String raw = textLiteral.text.getString();
        index = textLiteral.next;

        MutableText out = Text.empty();
        int n = raw.length();
        if (n == 0 || colorCount == 0) return new R(out, index);

        for (int i = 0; i < n; i++) {
//            float t = (float) i / (n - 1);  // in [0, 1]
            int rgb = getRgb(i, n, colorCount, colors);

            out.append(Text.literal(String.valueOf(raw.charAt(i)))
                    .styled(s -> s.withColor(rgb)));
        }

        return new R(out, index);
    }
    private R color(List<String> tokens, Integer index) {
        R colorLiteral = getNextLiteral(tokens, index);
        int color = Integer.parseInt(colorLiteral.text.getString(), 16);
        index = colorLiteral.next;
        R textLiteral = getNextLiteral(tokens, index);
        MutableText text = textLiteral.text;
        index = textLiteral.next;

        return new R(text.styled(style -> style.withColor(color)), index);
    }
    private R dcolor(List<String> tokens, Integer index) {
        R colorLiteral = getNextLiteral(tokens, index);
        int color = Integer.parseInt(colorLiteral.text.getString(), 10);
        index = colorLiteral.next;
        R textLiteral = getNextLiteral(tokens, index);
        MutableText text = textLiteral.text;
        index = textLiteral.next;

        return new R(text.styled(style -> style.withColor(color)), index);
    }
    private R hover(List<String> tokens, Integer index) {
        R hoverLiteral = getNextLiteral(tokens, index);
        MutableText hover = hoverLiteral.text;
        index = hoverLiteral.next;
        R textLiteral = getNextLiteral(tokens, index);
        MutableText text = textLiteral.text;
        index = textLiteral.next;

        return new R(text.styled(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))), index);

    }
    private R click(List<String> tokens, Integer index) {
        R actionLiteral = getNextLiteral(tokens, index);
        ClickEvent.Action action = ClickEvent.Action.valueOf(actionLiteral.text.getString());
        // open_url, open_file, run_command, suggest_command, change_page, copy_to_clipboard
        index = actionLiteral.next;
        R clickLiteral = getNextLiteral(tokens, index);
        String click = clickLiteral.text.getString();
        index = clickLiteral.next;
        R textLiteral = getNextLiteral(tokens, index);
        MutableText text = textLiteral.text;
        index = textLiteral.next;

        return new R(text.styled(style -> style.withClickEvent(new ClickEvent(action, click))), index);

    }
    private R ranking(List<String> tokens, Integer index) {
        R textLiteral = getNextLiteral(tokens, index);
        MutableText text = textLiteral.text;
        index = textLiteral.next;

        return new R(parseMutableText(!Objects.equals(text.getString(), "null") ? " \\-{(}\\2{#%s}\\-{)}" : "", text.getString()), index);
    }
    private R nickname(List<String> tokens, Integer index) {
        R textLiteral = getNextLiteral(tokens, index);
        MutableText text = textLiteral.text;
        index = textLiteral.next;

        return new R(parseMutableText(!Objects.equals(text.getString(), "null") ? " \\-{(}\\i{\\3{%s}}\\-{)}" : "", text.getString()), index);
    }
    private R pill(List<String> tokens, Integer index) {
        R bgColorLiteral = getNextLiteral(tokens, index);
        String bgColor = bgColorLiteral.text.getString();
        index = bgColorLiteral.next;
        R textColorLiteral = getNextLiteral(tokens, index);
        String textColor = textColorLiteral.text.getString();
        index = textColorLiteral.next;
        R textLiteral = getNextLiteral(tokens, index);
        MutableText text = textLiteral.text;
        index = textLiteral.next;
        return new R(parseMutableText(!Objects.equals(text.getString(), "null") ? getPill(text.getString(),bgColor,textColor) : "", text.getString()), index);
    }

    private R styled(List<String> tokens, Integer index, UnaryOperator<Style> style) {
        R textLiteral = getNextLiteral(tokens, index);
        return new R(textLiteral.text.styled(style), textLiteral.next);
    }
    private R light(List<String> tokens, Integer index) {
        return styled(tokens, index, features().get(Settings.class).map(settings -> settings.getTheme().get().getTheme().light()).orElse(style -> style));
    }
    private R normal(List<String> tokens, Integer index) {
        return styled(tokens, index, features().get(Settings.class).map(settings -> settings.getTheme().get().getTheme().normal()).orElse(style -> style));
    }
    private R dark(List<String> tokens, Integer index) {
        return styled(tokens, index, features().get(Settings.class).map(settings -> settings.getTheme().get().getTheme().dark()).orElse(style -> style));
    }
    private R accent1(List<String> tokens, Integer index) {
        return styled(tokens, index, features().get(Settings.class).map(settings -> settings.getTheme().get().getTheme().accent1()).orElse(style -> style));
    }
    private R accent2(List<String> tokens, Integer index) {
        return styled(tokens, index, features().get(Settings.class).map(settings -> settings.getTheme().get().getTheme().accent2()).orElse(style -> style));
    }
    private R accent3(List<String> tokens, Integer index) {
        return styled(tokens, index, features().get(Settings.class).map(settings -> settings.getTheme().get().getTheme().accent3()).orElse(style -> style));
    }
    private R italicize(List<String> tokens, Integer index) {
        return styled(tokens, index, style -> style.withItalic(true));
    }
    private R boldfont(List<String> tokens, Integer index) {
        return styled(tokens, index, style -> style.withBold(true));
    }
    private R underline(List<String> tokens, Integer index) {
        return styled(tokens, index, style -> style.withUnderline(true));
    }
    private R strikethrough(List<String> tokens, Integer index) {
        return styled(tokens, index, style -> style.withStrikethrough(true));
    }
    private R obfuscated(List<String> tokens, Integer index) {
        return styled(tokens, index, style -> style.withObfuscated(true));
    }


    private int getRgb(int i, int n, int colorCount, List<Integer> colors) {
        if (colorCount == 1) {
            return colors.getFirst();
        }
        float t = (float) i / (n-1);
        float scaledT = t * (colorCount - 1);
        int segment = Math.min((int) scaledT, colorCount - 2); // avoid overflow
        float localT = scaledT - segment;

        int c0 = colors.get(segment);
        int c1 = colors.get(segment + 1);

        int r0 = (c0 >> 16) & 0xFF, g0 = (c0 >> 8) & 0xFF, b0 = c0 & 0xFF;
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;

        int r = Math.round(r0 + (r1 - r0) * localT);
        int g = Math.round(g0 + (g1 - g0) * localT);
        int b = Math.round(b0 + (b1 - b0) * localT);
        return (r << 16) | (g << 8) | b;
    }

    private char getUnicodeChar(char letter) {
        if (letter >= 'A' && letter <= 'Z') {
            int offset = letter - 65;
            return (char)('\ue040' + offset);
        } else if (letter >= 'a' && letter <= 'z') {
            int offset = letter - 97;
            return (char)('\ue040' + offset);
        } else {
            return ' ';
        }
    }
    public String getPill(String text, String bgColor, String textColor) {
        StringBuilder prefix = new StringBuilder();
        bgColor = String.format("\\color{%s}", bgColor);
        textColor = String.format("\\color{%s}", textColor);
        prefix.append(bgColor).append("{\ue010\u2064}");

        for(char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                char unicodeChar = getUnicodeChar(c);
                prefix.append(bgColor).append("{\ue00f\ue012}").append(textColor).append("{").append(unicodeChar).append("}");
            }
        }
        prefix.append(bgColor).append("{\ue011}");

        return prefix.toString();
    }

    public MutableText parseMutableText(String text) {
//        MutableText testing = Text.empty();
//        List<String> tokens = tokenize(text);
//
//        for (int i = 0; i < tokens.toArray().length; i++) {
//            testing.append(tokens.get(i));
//            testing.append("\n");
//        }
        return parseMutableText(tokenize(text), 0).text; // i keep needing this for debugging
    }

    public MutableText parseMutableText(String text, Object... parameters) {
        return parseMutableText(String.format(text, parameters));
    }

    public String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) return raw;

        StringBuilder sb = new StringBuilder(raw.length() * 2);  // worst‑case doubling
        for (char ch : raw.toCharArray()) {
            if (ch == '\\' || ch == '{' || ch == '}') {
                sb.append('\\');   // add the escape
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    // inverse function, courtesy of GPT and lightly modified

    public String toTeX(StyledText styled) {
        StringBuilder out = new StringBuilder();

        ClickEvent currentClick = null;
        HoverEvent currentHover = null;
        StringBuilder run = new StringBuilder();

        for (StyledTextPart part : styled) {
            ClickEvent pe = part.getPartStyle().getClickEvent();
            HoverEvent he = part.getPartStyle().getHoverEvent();

            // If event context changes, flush the previous run
            if (!same(pe, currentClick) || !same(he, currentHover)) {
                flushRun(out, run, currentClick, currentHover);
                run.setLength(0);
                currentClick = pe;
                currentHover = he;
            }

            // Keep your existing inline formatting codes; only escape TeX control chars.
            run.append(escapeTeX(part.getString(null, PartStyle.StyleType.DEFAULT)));
        }

        // Flush the final run
        flushRun(out, run, currentClick, currentHover);
        return out.toString();
    }

    private boolean same(ClickEvent a, ClickEvent b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        // action + value is the semantic identity
        return a.getAction() == b.getAction()
                && Objects.equals(a.getValue(), b.getValue());
    }

    private boolean same(HoverEvent a, HoverEvent b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.getAction() != b.getAction()) return false;

        // Compare the *payload* by a stable signature
        var act = a.getAction();
        if (act == HoverEvent.Action.SHOW_TEXT) {
            Text ta = a.getValue(HoverEvent.Action.SHOW_TEXT);
            Text tb = b.getValue(HoverEvent.Action.SHOW_TEXT);
            return Objects.equals(hoverTextSignature(ta), hoverTextSignature(tb));
        } else {
            // For non-text hovers (item/entity), fall back to value equality
            Object va = a.getValue(act);
            Object vb = b.getValue(act);
            return Objects.equals(va, vb);
        }
    }

    /** Build a stable signature for hover SHOW_TEXT content. */
    private String hoverTextSignature(Text t) {
        if (t == null) return "";
        // Keep it cheap & deterministic: compare the plain string without MC formatting.
        // (If you need exact formatting fidelity, you can swap this for toTeX(StyledText.fromComponent(t)).)
        try {
            return com.wynntils.core.text.StyledText.fromComponent(t).getStringWithoutFormatting();
        } catch (Throwable e) {
            return String.valueOf(t.getString()); // safe fallback
        }
    }


    /** Wrap a run with \hover and/or \click if present, then append to out. */
    private void flushRun(StringBuilder out, StringBuilder run,
                                 ClickEvent click, HoverEvent hover) {
        if (run.length() == 0) return; // <-- instead of run.isEmpty()

        String inner = run.toString();

        if (hover != null) {
            String hoverText = serializeHoverText(hover);
            inner = "\\hover{" + hoverText + "}{" + inner + "}";
        }
        if (click != null) {
            String action = click.getAction().name();
            String value  = escapeTeX(click.getValue());
            inner = "\\click{" + action + "}{" + value + "}{" + inner + "}";
        }
        out.append(inner);
    }


    /** Convert hover payload to TeX; only SHOW_TEXT is encoded, others become a label. */
    private String serializeHoverText(HoverEvent hover) {
        try {
            if (hover.getAction() == HoverEvent.Action.SHOW_TEXT) {
                // Value is a Text component; keep its formatting codes, but also encode its own hover/click.
                Text value = hover.getValue(HoverEvent.Action.SHOW_TEXT);
                if (value != null) {
                    StyledText st = StyledText.fromComponent(value);
                    return toTeX(st);
                }
            }
            // Fallback for non-text hovers (items, entities, etc.)
            return "<hover>";
        } catch (ClassCastException ignored) {
            return escapeTeX(String.valueOf(hover.getValue(HoverEvent.Action.SHOW_TEXT)));
        }
    }

    /** Escape TeX control chars for your mini-language: backslash and braces. */
    private String escapeTeX(String s) {
        if (s == null || s.isEmpty()) return "";
        // order matters: backslash first
        return s
                .replace("\\", "\\\\")
                .replace("{", "\\{")
                .replace("}", "\\}");
    }

}
