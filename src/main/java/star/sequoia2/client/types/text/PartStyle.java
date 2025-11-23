package star.sequoia2.client.types.text;

import com.wynntils.utils.colors.CustomColor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public final class PartStyle {
    private static final String STYLE_PREFIX = "§";
    private static final Int2ObjectMap<Formatting> INTEGER_TO_CHATFORMATTING_MAP = (Int2ObjectMap)Arrays.stream(Formatting.values()).filter(Formatting::isColor).collect(() -> new Int2ObjectOpenHashMap(Formatting.values().length), (map, cf) -> map.put(cf.getColorValue() | -16777216, cf), Map::putAll);
    private final StyledTextPart owner;
    private final CustomColor color;
    private final CustomColor shadowColor;
    private final boolean obfuscated;
    private final boolean bold;
    private final boolean strikethrough;
    private final boolean underlined;
    private final boolean italic;
    private final ClickEvent clickEvent;
    private final HoverEvent hoverEvent;
    private final Identifier font;

    private PartStyle(StyledTextPart owner, CustomColor color, CustomColor shadowColor, boolean obfuscated, boolean bold, boolean strikethrough, boolean underlined, boolean italic, ClickEvent clickEvent, HoverEvent hoverEvent, Identifier font) {
        this.owner = owner;
        this.color = color;
        this.shadowColor = shadowColor;
        this.obfuscated = obfuscated;
        this.bold = bold;
        this.strikethrough = strikethrough;
        this.underlined = underlined;
        this.italic = italic;
        this.clickEvent = clickEvent;
        this.hoverEvent = hoverEvent;
        this.font = font;
    }

    PartStyle(PartStyle partStyle, StyledTextPart owner) {
        this.owner = owner;
        this.color = partStyle.color;
        this.shadowColor = partStyle.shadowColor;
        this.obfuscated = partStyle.obfuscated;
        this.bold = partStyle.bold;
        this.strikethrough = partStyle.strikethrough;
        this.underlined = partStyle.underlined;
        this.italic = partStyle.italic;
        this.clickEvent = partStyle.clickEvent;
        this.hoverEvent = partStyle.hoverEvent;
        this.font = partStyle.font;
    }

    static PartStyle fromStyle(Style style, StyledTextPart owner, Style parentStyle) {
        Style inheritedStyle;
        if (parentStyle == null) {
            inheritedStyle = style;
        } else {
            inheritedStyle = style.withParent(parentStyle);
        }

        return new PartStyle(owner, inheritedStyle.getColor() == null ? CustomColor.NONE : CustomColor.fromInt(inheritedStyle.getColor().getRgb() | -16777216), inheritedStyle.getShadowColor() == null ? CustomColor.NONE : CustomColor.fromInt(inheritedStyle.getShadowColor() | -16777216), inheritedStyle.isObfuscated(), inheritedStyle.isBold(), inheritedStyle.isStrikethrough(), inheritedStyle.isUnderlined(), inheritedStyle.isItalic(), inheritedStyle.getClickEvent(), inheritedStyle.getHoverEvent(), inheritedStyle.getFont());
    }

    public String asString(PartStyle previousStyle, PartStyle.StyleType type) {
        if (type == PartStyle.StyleType.NONE) {
            return "";
        } else {
            StringBuilder styleString = new StringBuilder();
            boolean skipFormatting = false;
            if (previousStyle != null && (this.color == CustomColor.NONE || previousStyle.color.equals(this.color))) {
                String differenceString = this.tryConstructDifference(previousStyle, type == PartStyle.StyleType.INCLUDE_EVENTS);
                if (differenceString != null) {
                    styleString.append(differenceString);
                    skipFormatting = true;
                } else {
                    styleString.append("§").append(Formatting.RESET.getCode());
                }
            }

            if (!skipFormatting) {
                if (this.color != CustomColor.NONE) {
                    Formatting chatFormatting = (Formatting)INTEGER_TO_CHATFORMATTING_MAP.get(this.color.asInt());
                    if (chatFormatting != null) {
                        styleString.append("§").append(chatFormatting.getCode());
                    } else {
                        styleString.append("§").append(this.color.toHexString());
                    }
                }

                if (this.obfuscated) {
                    styleString.append("§").append(Formatting.OBFUSCATED.getCode());
                }

                if (this.bold) {
                    styleString.append("§").append(Formatting.BOLD.getCode());
                }

                if (this.strikethrough) {
                    styleString.append("§").append(Formatting.STRIKETHROUGH.getCode());
                }

                if (this.underlined) {
                    styleString.append("§").append(Formatting.UNDERLINE.getCode());
                }

                if (this.italic) {
                    styleString.append("§").append(Formatting.ITALIC.getCode());
                }

                if (type == PartStyle.StyleType.INCLUDE_EVENTS) {
                    if (this.clickEvent != null) {
                        styleString.append("§").append("[").append(this.owner.getParent().getClickEventIndex(this.clickEvent)).append("]");
                    }

                    if (this.hoverEvent != null) {
                        styleString.append("§").append("<").append(this.owner.getParent().getHoverEventIndex(this.hoverEvent)).append(">");
                    }
                }
            }

            return styleString.toString();
        }
    }

    public Style getStyle() {
        TextColor textColor = this.color == CustomColor.NONE ? null : TextColor.fromRgb(this.color.asInt() & 16777215);
        Integer shadowColorInt = this.shadowColor == CustomColor.NONE ? null : this.shadowColor.asInt() & 16777215;
        return new Style(textColor, shadowColorInt, this.bold, this.italic, this.underlined, this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, (String)null, this.font);
    }

    public PartStyle withColor(Formatting color) {
        if (!color.isColor()) {
            throw new IllegalArgumentException("ChatFormatting " + String.valueOf(color) + " is not a color!");
        } else {
            CustomColor newColor = CustomColor.fromInt(color.getColorValue() | -16777216);
            return new PartStyle(this.owner, newColor, this.shadowColor, this.obfuscated, this.bold, this.strikethrough, this.underlined, this.italic, this.clickEvent, this.hoverEvent, this.font);
        }
    }

    public PartStyle withColor(CustomColor color) {
        return new PartStyle(this.owner, color, this.shadowColor, this.obfuscated, this.bold, this.strikethrough, this.underlined, this.italic, this.clickEvent, this.hoverEvent, this.font);
    }

    public boolean isBold() {
        return this.bold;
    }

    public boolean isObfuscated() {
        return this.obfuscated;
    }

    public boolean isStrikethrough() {
        return this.strikethrough;
    }

    public boolean isUnderlined() {
        return this.underlined;
    }

    public boolean isItalic() {
        return this.italic;
    }

    public ClickEvent getClickEvent() {
        return this.clickEvent;
    }

    public HoverEvent getHoverEvent() {
        return this.hoverEvent;
    }

    public CustomColor getColor() {
        return this.color;
    }

    public CustomColor getShadowColor() {
        return this.shadowColor;
    }

    public Identifier getFont() {
        return this.font;
    }

    public PartStyle withShadowColor(CustomColor shadowColor) {
        return new PartStyle(this.owner, this.color, shadowColor, this.obfuscated, this.bold, this.strikethrough, this.underlined, this.italic, this.clickEvent, this.hoverEvent, this.font);
    }

    public PartStyle withBold(boolean bold) {
        return new PartStyle(this.owner, this.color, this.shadowColor, this.obfuscated, bold, this.strikethrough, this.underlined, this.italic, this.clickEvent, this.hoverEvent, this.font);
    }

    public PartStyle withObfuscated(boolean obfuscated) {
        return new PartStyle(this.owner, this.color, this.shadowColor, obfuscated, this.bold, this.strikethrough, this.underlined, this.italic, this.clickEvent, this.hoverEvent, this.font);
    }

    public PartStyle withStrikethrough(boolean strikethrough) {
        return new PartStyle(this.owner, this.color, this.shadowColor, this.obfuscated, this.bold, strikethrough, this.underlined, this.italic, this.clickEvent, this.hoverEvent, this.font);
    }

    public PartStyle withUnderlined(boolean underlined) {
        return new PartStyle(this.owner, this.color, this.shadowColor, this.obfuscated, this.bold, this.strikethrough, underlined, this.italic, this.clickEvent, this.hoverEvent, this.font);
    }

    public PartStyle withItalic(boolean italic) {
        return new PartStyle(this.owner, this.color, this.shadowColor, this.obfuscated, this.bold, this.strikethrough, this.underlined, italic, this.clickEvent, this.hoverEvent, this.font);
    }

    public PartStyle withClickEvent(ClickEvent clickEvent) {
        return new PartStyle(this.owner, this.color, this.shadowColor, this.obfuscated, this.bold, this.strikethrough, this.underlined, this.italic, clickEvent, this.hoverEvent, this.font);
    }

    public PartStyle withHoverEvent(HoverEvent hoverEvent) {
        return new PartStyle(this.owner, this.color, this.shadowColor, this.obfuscated, this.bold, this.strikethrough, this.underlined, this.italic, this.clickEvent, hoverEvent, this.font);
    }

    public PartStyle withFont(Identifier font) {
        return new PartStyle(this.owner, this.color, this.shadowColor, this.obfuscated, this.bold, this.strikethrough, this.underlined, this.italic, this.clickEvent, this.hoverEvent, font);
    }

    private String tryConstructDifference(PartStyle oldStyle, boolean includeEvents) {
        StringBuilder add = new StringBuilder();
        int oldColorInt = oldStyle.color.asInt();
        int newColorInt = this.color.asInt();
        if (oldColorInt == -1) {
            if (newColorInt != -1) {
                Optional var10000 = Arrays.stream(Formatting.values()).filter((c) -> c.isColor() && newColorInt == (c.getColorValue() | -16777216)).findFirst();
                Objects.requireNonNull(add);
                var10000.ifPresent(add::append);
            }
        } else if (oldColorInt != newColorInt) {
            return null;
        }

        if (oldStyle.obfuscated && !this.obfuscated) {
            return null;
        } else {
            if (!oldStyle.obfuscated && this.obfuscated) {
                add.append(Formatting.OBFUSCATED);
            }

            if (oldStyle.bold && !this.bold) {
                return null;
            } else {
                if (!oldStyle.bold && this.bold) {
                    add.append(Formatting.BOLD);
                }

                if (oldStyle.strikethrough && !this.strikethrough) {
                    return null;
                } else {
                    if (!oldStyle.strikethrough && this.strikethrough) {
                        add.append(Formatting.STRIKETHROUGH);
                    }

                    if (oldStyle.underlined && !this.underlined) {
                        return null;
                    } else {
                        if (!oldStyle.underlined && this.underlined) {
                            add.append(Formatting.UNDERLINE);
                        }

                        if (oldStyle.italic && !this.italic) {
                            return null;
                        } else {
                            if (!oldStyle.italic && this.italic) {
                                add.append(Formatting.ITALIC);
                            }

                            if (includeEvents) {
                                if (oldStyle.clickEvent != null && this.clickEvent == null) {
                                    return null;
                                }

                                if (oldStyle.clickEvent != this.clickEvent) {
                                    add.append("§").append("[").append(this.owner.getParent().getClickEventIndex(this.clickEvent)).append("]");
                                }

                                if (oldStyle.hoverEvent != null && this.hoverEvent == null) {
                                    return null;
                                }

                                if (oldStyle.hoverEvent != this.hoverEvent) {
                                    add.append("§").append("<").append(this.owner.getParent().getHoverEventIndex(this.hoverEvent)).append(">");
                                }
                            }

                            return add.toString();
                        }
                    }
                }
            }
        }
    }

    public String toString() {
        String var10000 = String.valueOf(this.color);
        return "PartStyle{color=" + var10000 + ", obfuscated=" + this.obfuscated + ", bold=" + this.bold + ", strikethrough=" + this.strikethrough + ", underlined=" + this.underlined + ", italic=" + this.italic + ", clickEvent=" + String.valueOf(this.clickEvent) + ", hoverEvent=" + String.valueOf(this.hoverEvent) + ", font=" + String.valueOf(this.font) + "}";
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            PartStyle partStyle = (PartStyle)o;
            return this.obfuscated == partStyle.obfuscated && this.bold == partStyle.bold && this.strikethrough == partStyle.strikethrough && this.underlined == partStyle.underlined && this.italic == partStyle.italic && Objects.equals(this.color, partStyle.color) && Objects.equals(this.clickEvent, partStyle.clickEvent) && Objects.equals(this.hoverEvent, partStyle.hoverEvent) && Objects.equals(this.font, partStyle.font);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return Objects.hash(new Object[]{this.color, this.obfuscated, this.bold, this.strikethrough, this.underlined, this.italic, this.clickEvent, this.hoverEvent, this.font});
    }

    public static enum StyleType {
        INCLUDE_EVENTS,
        DEFAULT,
        NONE;
    }
}
