package star.sequoia2.client.types.text;

import com.google.common.collect.Iterables;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.wynntils.utils.MathUtils;
import com.wynntils.utils.type.IterationDecision;
import com.wynntils.utils.type.Pair;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

//robbed from goontils cuz they keep switching this, not about to maintain compatability with every version wynntils.com or something for credit

public final class StyledText implements Iterable<StyledTextPart> {
    private static final char POSITIVE_SPACE_HIGH_SURROGATE = '\udb00';
    private static final char NEGATIVE_SPACE_HIGH_SURROGATE = '\udaff';
    public static final StyledText EMPTY = new StyledText(List.of(), List.of(), List.of());
    private final List<StyledTextPart> parts;
    private final List<ClickEvent> clickEvents;
    private final List<HoverEvent> hoverEvents;

    private StyledText(List<StyledTextPart> parts, List<ClickEvent> clickEvents, List<HoverEvent> hoverEvents) {
        this.parts = (List)parts.stream().filter((styledTextPart) -> !styledTextPart.isEmpty()).map((styledTextPart) -> new StyledTextPart(styledTextPart, this)).collect(Collectors.toList());
        this.clickEvents = Collections.unmodifiableList(clickEvents);
        this.hoverEvents = Collections.unmodifiableList(hoverEvents);
    }

    public static StyledText fromComponent(Text component) {
        List<StyledTextPart> parts = new ArrayList();
        Deque<Pair<Text, Style>> deque = new LinkedList();
        deque.add(new Pair(component, Style.EMPTY));

        while(!deque.isEmpty()) {
            Pair<Text, Style> currentPair = (Pair)deque.pop();
            Text current = (Text)currentPair.key();
            Style parentStyle = (Style)currentPair.value();
            String componentString = MutableText.of(current.getContent()).getString();
            List<StyledTextPart> styledTextParts = StyledTextPart.fromCodedString(componentString, current.getStyle(), (StyledText)null, parentStyle);
            Style styleToFollowForChildren = current.getStyle().withParent(parentStyle);
            List<Pair<Text, Style>> siblingPairs = (List)current.getSiblings().stream().map((sibling) -> new Pair(sibling, styleToFollowForChildren)).collect(Collectors.toList());
            Collections.reverse(siblingPairs);
            Objects.requireNonNull(deque);
            siblingPairs.forEach(deque::addFirst);
            parts.addAll(styledTextParts.stream().filter((part) -> !part.isEmpty()).toList());
        }

        return fromParts(parts);
    }

    public static StyledText fromJson(JsonArray jsonArray) {
        return new StyledText(StyledTextPart.fromJson(jsonArray), List.of(), List.of());
    }

    public static StyledText fromString(String codedString) {
        return new StyledText(StyledTextPart.fromCodedString(codedString, Style.EMPTY, (StyledText)null, Style.EMPTY), List.of(), List.of());
    }

    public static StyledText fromModifiedString(String codedString, StyledText styledText) {
        List<HoverEvent> hoverEvents = List.copyOf(styledText.hoverEvents);
        List<ClickEvent> clickEvents = List.copyOf(styledText.clickEvents);
        return new StyledText(StyledTextPart.fromCodedString(codedString, Style.EMPTY, styledText, Style.EMPTY), clickEvents, hoverEvents);
    }

    public static StyledText fromUnformattedString(String unformattedString) {
        StyledTextPart part = new StyledTextPart(unformattedString, Style.EMPTY, (StyledText)null, Style.EMPTY);
        return new StyledText(List.of(part), List.of(), List.of());
    }

    public static StyledText fromPart(StyledTextPart part) {
        return fromParts(List.of(part));
    }

    public static StyledText fromParts(List<StyledTextPart> parts) {
        List<ClickEvent> clickEvents = new ArrayList();
        List<HoverEvent> hoverEvents = new ArrayList();

        for(StyledTextPart part : parts) {
            ClickEvent clickEvent = part.getPartStyle().getClickEvent();
            if (clickEvent != null && !clickEvents.contains(clickEvent)) {
                clickEvents.add(clickEvent);
            }

            HoverEvent hoverEvent = part.getPartStyle().getHoverEvent();
            if (hoverEvent != null && !hoverEvents.contains(hoverEvent)) {
                hoverEvents.add(hoverEvent);
            }
        }

        return new StyledText(parts, clickEvents, hoverEvents);
    }

    public String getString(PartStyle.StyleType type) {
        StringBuilder builder = new StringBuilder();
        PartStyle previousStyle = null;

        for(StyledTextPart part : this.parts) {
            builder.append(part.getString(previousStyle, type));
            previousStyle = part.getPartStyle();
        }

        return builder.toString();
    }

    public String getString() {
        return this.getString(PartStyle.StyleType.DEFAULT);
    }

    public String getStringWithoutFormatting() {
        return this.getString(PartStyle.StyleType.NONE);
    }

    public MutableText getComponent() {
        if (this.parts.isEmpty()) {
            return Text.empty();
        } else {
            MutableText component = Text.empty();

            for(StyledTextPart part : this.parts) {
                component.append(part.getComponent());
            }

            return component;
        }
    }

    public int length() {
        return this.parts.stream().mapToInt(StyledTextPart::length).sum();
    }

    public int length(PartStyle.StyleType styleType) {
        return this.getString(styleType).length();
    }

    public static StyledText join(StyledText styledTextSeparator, StyledText... texts) {
        List<StyledTextPart> parts = new ArrayList();
        int length = texts.length;

        for(int i = 0; i < length; ++i) {
            StyledText text = texts[i];
            parts.addAll(text.parts);
            if (i != length - 1) {
                parts.addAll(styledTextSeparator.parts);
            }
        }

        return fromParts(parts);
    }

    public static StyledText join(StyledText styledTextSeparator, Iterable<StyledText> texts) {
        return join(styledTextSeparator, (StyledText[])Iterables.toArray(texts, StyledText.class));
    }

    public static StyledText join(String codedStringSeparator, StyledText... texts) {
        return join(fromString(codedStringSeparator), texts);
    }

    public static StyledText join(String codedStringSeparator, Iterable<StyledText> texts) {
        return join(fromString(codedStringSeparator), (StyledText[])Iterables.toArray(texts, StyledText.class));
    }

    public static StyledText concat(StyledText... texts) {
        return fromParts(Arrays.stream(texts).map((text) -> text.parts).flatMap(Collection::stream).toList());
    }

    public static StyledText concat(Iterable<StyledText> texts) {
        return concat((StyledText[])Iterables.toArray(texts, StyledText.class));
    }

    public StyledText getNormalized() {
        return fromParts((List)this.parts.stream().map(StyledTextPart::asNormalized).collect(Collectors.toList()));
    }

    public StyledText stripAlignment() {
        return this.iterate((part, functionParts) -> {
            String text = part.getString((PartStyle)null, PartStyle.StyleType.NONE);
            if (text.contains(String.valueOf('\udb00')) || text.contains(String.valueOf('\udaff'))) {
                StringBuilder builder = new StringBuilder();

                for(int i = 0; i < text.length(); ++i) {
                    char ch = text.charAt(i);
                    if (Character.isHighSurrogate(ch) && (ch == '\udb00' || ch == '\udaff')) {
                        if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                            ++i;
                        }
                    } else {
                        builder.append(ch);
                    }
                }

                functionParts.set(0, new StyledTextPart(builder.toString(), part.getPartStyle().getStyle(), (StyledText)null, Style.EMPTY));
            }

            return IterationDecision.CONTINUE;
        });
    }

    public StyledText trim() {
        if (this.parts.isEmpty()) {
            return this;
        } else {
            List<StyledTextPart> newParts = new ArrayList(this.parts);
            newParts.set(0, ((StyledTextPart)newParts.getFirst()).stripLeading());
            int lastIndex = newParts.size() - 1;
            newParts.set(lastIndex, ((StyledTextPart)newParts.get(lastIndex)).stripTrailing());
            return fromParts(newParts);
        }
    }

    public boolean isEmpty() {
        return this.parts.isEmpty();
    }

    public boolean isBlank() {
        return this.parts.stream().allMatch(StyledTextPart::isBlank);
    }

    public boolean contains(String codedString) {
        return this.contains(codedString, PartStyle.StyleType.DEFAULT);
    }

    public boolean contains(StyledText styledText) {
        return this.contains(styledText.getString(PartStyle.StyleType.DEFAULT), PartStyle.StyleType.DEFAULT);
    }

    public boolean contains(String codedString, PartStyle.StyleType styleType) {
        return this.getString(styleType).contains(codedString);
    }

    public boolean contains(StyledText styledText, PartStyle.StyleType styleType) {
        return this.contains(styledText.getString(styleType), styleType);
    }

    public boolean startsWith(String codedString) {
        return this.startsWith(codedString, PartStyle.StyleType.DEFAULT);
    }

    public boolean startsWith(StyledText styledText) {
        return this.startsWith(styledText.getString(PartStyle.StyleType.DEFAULT), PartStyle.StyleType.DEFAULT);
    }

    public boolean startsWith(String codedString, PartStyle.StyleType styleType) {
        return this.getString(styleType).startsWith(codedString);
    }

    public boolean startsWith(StyledText styledText, PartStyle.StyleType styleType) {
        return this.startsWith(styledText.getString(styleType), styleType);
    }

    public boolean endsWith(String codedString) {
        return this.endsWith(codedString, PartStyle.StyleType.DEFAULT);
    }

    public boolean endsWith(StyledText styledText) {
        return this.endsWith(styledText.getString(PartStyle.StyleType.DEFAULT), PartStyle.StyleType.DEFAULT);
    }

    public boolean endsWith(String codedString, PartStyle.StyleType styleType) {
        return this.getString(styleType).endsWith(codedString);
    }

    public boolean endsWith(StyledText styledText, PartStyle.StyleType styleType) {
        return this.endsWith(styledText.getString(styleType), styleType);
    }

    public Matcher getMatcher(Pattern pattern) {
        return this.getMatcher(pattern, PartStyle.StyleType.DEFAULT);
    }

    public Matcher getMatcher(Pattern pattern, PartStyle.StyleType styleType) {
        return pattern.matcher(this.getString(styleType));
    }

    public boolean matches(Pattern pattern) {
        return this.matches(pattern, PartStyle.StyleType.DEFAULT);
    }

    public boolean matches(Pattern pattern, PartStyle.StyleType styleType) {
        return pattern.matcher(this.getString(styleType)).matches();
    }

    public boolean find(Pattern pattern) {
        return this.find(pattern, PartStyle.StyleType.DEFAULT);
    }

    public boolean find(Pattern pattern, PartStyle.StyleType styleType) {
        return pattern.matcher(this.getString(styleType)).find();
    }

    public StyledText append(StyledText styledText) {
        return concat(this, styledText);
    }

    public StyledText append(String codedString) {
        return this.append(fromString(codedString));
    }

    public StyledText appendPart(StyledTextPart part) {
        List<StyledTextPart> newParts = new ArrayList(this.parts);
        newParts.add(part);
        return fromParts(newParts);
    }

    public StyledText prepend(StyledText styledText) {
        return concat(styledText, this);
    }

    public StyledText prepend(String codedString) {
        return this.prepend(fromString(codedString));
    }

    public StyledText prependPart(StyledTextPart part) {
        List<StyledTextPart> newParts = new ArrayList(this.parts);
        newParts.addFirst(part);
        return fromParts(newParts);
    }

    public StyledText[] split(String regex) {
        return this.split(regex, false);
    }

    public StyledText[] split(String regex, boolean keepTrailingEmpty) {
        if (this.parts.isEmpty()) {
            return new StyledText[]{EMPTY};
        } else {
            Pattern pattern = Pattern.compile(regex);
            List<StyledText> splitTexts = new ArrayList();
            List<StyledTextPart> splitParts = new ArrayList();

            for(int i = 0; i < this.parts.size(); ++i) {
                StyledTextPart part = (StyledTextPart)this.parts.get(i);
                String partString = part.getString((PartStyle)null, PartStyle.StyleType.NONE);
                int maxSplit = !keepTrailingEmpty && i == this.parts.size() - 1 ? 0 : -1;
                List<String> stringParts = Arrays.stream(pattern.split(partString, maxSplit)).toList();
                Matcher matcher = pattern.matcher(partString);
                if (matcher.find()) {
                    for(int j = 0; j < stringParts.size(); ++j) {
                        String stringPart = (String)stringParts.get(j);
                        splitParts.add(new StyledTextPart(stringPart, part.getPartStyle().getStyle(), (StyledText)null, Style.EMPTY));
                        if (j != stringParts.size() - 1) {
                            splitTexts.add(fromParts(splitParts));
                            splitParts.clear();
                        }
                    }
                } else {
                    splitParts.add(part);
                }
            }

            if (!splitParts.isEmpty()) {
                splitTexts.add(fromParts(splitParts));
            }

            return (StyledText[])splitTexts.toArray((x$0) -> new StyledText[x$0]);
        }
    }

    public StyledText substring(int beginIndex) {
        return this.substring(beginIndex, this.length(), PartStyle.StyleType.NONE);
    }

    public StyledText substring(int beginIndex, PartStyle.StyleType styleType) {
        return this.substring(beginIndex, this.length(styleType), styleType);
    }

    public StyledText substring(int beginIndex, int endIndex) {
        return this.substring(beginIndex, endIndex, PartStyle.StyleType.NONE);
    }

    public StyledText substring(int beginIndex, int endIndex, PartStyle.StyleType styleType) {
        if (endIndex < beginIndex) {
            throw new IndexOutOfBoundsException("endIndex must be greater than beginIndex");
        } else if (beginIndex < 0) {
            throw new IndexOutOfBoundsException("beginIndex must be greater than or equal to 0");
        } else if (endIndex > this.length(styleType)) {
            throw new IndexOutOfBoundsException("endIndex must be less than or equal to length(styleType)");
        } else {
            List<StyledTextPart> includedParts = new ArrayList();
            int currentIndex = 0;
            PartStyle previousPartStyle = null;

            for(StyledTextPart part : this.parts) {
                if (currentIndex >= beginIndex && currentIndex + part.length() < endIndex) {
                    includedParts.add(part);
                } else if (MathUtils.rangesIntersect(currentIndex, currentIndex + part.length(), beginIndex, endIndex - 1)) {
                    int startIndexInPart = Math.max(0, beginIndex - currentIndex);
                    int endIndexInPart = Math.min(part.length(), endIndex - currentIndex);
                    String fullString = part.getString(previousPartStyle, styleType);
                    String beforeSubstring = fullString.substring(0, startIndexInPart);
                    String includedSubstring = fullString.substring(startIndexInPart, endIndexInPart);
                    if (beforeSubstring.endsWith(String.valueOf('§')) || includedSubstring.endsWith(String.valueOf('§'))) {
                        throw new IllegalArgumentException("The substring splits a formatting code.");
                    }

                    includedParts.addAll(StyledTextPart.fromCodedString(includedSubstring, part.getPartStyle().getStyle(), (StyledText)null, Style.EMPTY));
                }

                currentIndex += part.getString(previousPartStyle, styleType).length();
                previousPartStyle = part.getPartStyle();
            }

            return fromParts(includedParts);
        }
    }

    public StyledText[] partition(int... indexes) {
        return this.partition(PartStyle.StyleType.NONE, indexes);
    }

    public StyledText[] partition(PartStyle.StyleType styleType, int... indexes) {
        if (indexes.length == 0) {
            return new StyledText[]{this};
        } else {
            List<StyledText> splitTexts = new ArrayList();
            int currentIndex = 0;

            for(int index : indexes) {
                if (index < currentIndex) {
                    throw new IllegalArgumentException("Indexes must be in ascending order");
                }

                splitTexts.add(this.substring(currentIndex, index, styleType));
                currentIndex = index;
            }

            splitTexts.add(this.substring(currentIndex, styleType));
            return (StyledText[])splitTexts.toArray((x$0) -> new StyledText[x$0]);
        }
    }

    public StyledText replaceFirst(String regex, String replacement) {
        return this.replaceFirst(Pattern.compile(regex), replacement);
    }

    public StyledText replaceFirst(Pattern pattern, String replacement) {
        List<StyledTextPart> newParts = new ArrayList();

        for(StyledTextPart part : this.parts) {
            String partString = part.getString((PartStyle)null, PartStyle.StyleType.NONE);
            Matcher matcher = pattern.matcher(partString);
            if (matcher.find()) {
                String replacedString = matcher.replaceFirst(replacement);
                newParts.add(new StyledTextPart(replacedString, part.getPartStyle().getStyle(), (StyledText)null, Style.EMPTY));
                newParts.addAll(this.parts.subList(this.parts.indexOf(part) + 1, this.parts.size()));
                break;
            }

            newParts.add(part);
        }

        return fromParts(newParts);
    }

    public StyledText replaceAll(String regex, String replacement) {
        return this.replaceAll(Pattern.compile(regex), replacement);
    }

    public StyledText replaceAll(Pattern pattern, String replacement) {
        List<StyledTextPart> newParts = new ArrayList();

        for(StyledTextPart part : this.parts) {
            String partString = part.getString((PartStyle)null, PartStyle.StyleType.NONE);
            Matcher matcher = pattern.matcher(partString);
            if (matcher.find()) {
                String replacedString = matcher.replaceAll(replacement);
                newParts.add(new StyledTextPart(replacedString, part.getPartStyle().getStyle(), (StyledText)null, Style.EMPTY));
            } else {
                newParts.add(part);
            }
        }

        return fromParts(newParts);
    }

    public StyledText[] getPartsAsTextArray() {
        return (StyledText[])this.parts.stream().map(StyledText::fromPart).toArray((x$0) -> new StyledText[x$0]);
    }

    public StyledText iterate(BiFunction<StyledTextPart, List<StyledTextPart>, IterationDecision> function) {
        List<StyledTextPart> newParts = new ArrayList();

        for(int i = 0; i < this.parts.size(); ++i) {
            StyledTextPart part = (StyledTextPart)this.parts.get(i);
            List<StyledTextPart> functionParts = new ArrayList();
            functionParts.add(part);
            IterationDecision decision = (IterationDecision)function.apply(part, functionParts);
            newParts.addAll(functionParts);
            if (decision == IterationDecision.BREAK) {
                newParts.addAll(this.parts.subList(i + 1, this.parts.size()));
                break;
            }
        }

        return fromParts(newParts);
    }

    public StyledText iterateBackwards(BiFunction<StyledTextPart, List<StyledTextPart>, IterationDecision> function) {
        List<StyledTextPart> newParts = new ArrayList();

        for(int i = this.parts.size() - 1; i >= 0; --i) {
            StyledTextPart part = (StyledTextPart)this.parts.get(i);
            List<StyledTextPart> functionParts = new ArrayList();
            functionParts.add(part);
            IterationDecision decision = (IterationDecision)function.apply(part, functionParts);
            newParts.addAll(0, functionParts);
            if (decision == IterationDecision.BREAK) {
                newParts.addAll(0, this.parts.subList(0, i));
                break;
            }
        }

        return fromParts(newParts);
    }

    public StyledText map(Function<StyledTextPart, StyledTextPart> function) {
        return fromParts((List)this.parts.stream().map(function).collect(Collectors.toList()));
    }

    public StyledText withoutFormatting() {
        return this.iterate((part, functionParts) -> {
            functionParts.set(0, new StyledTextPart(part.getString((PartStyle)null, PartStyle.StyleType.NONE), Style.EMPTY, (StyledText)null, Style.EMPTY));
            return IterationDecision.CONTINUE;
        });
    }

    public boolean equalsString(String string) {
        return this.equalsString(string, PartStyle.StyleType.DEFAULT);
    }

    public boolean equalsString(String string, PartStyle.StyleType styleType) {
        return this.getString(styleType).equals(string);
    }

    public StyledTextPart getFirstPart() {
        return this.parts.isEmpty() ? null : (StyledTextPart)this.parts.getFirst();
    }

    public StyledTextPart getLastPart() {
        return this.parts.isEmpty() ? null : (StyledTextPart)this.parts.getLast();
    }

    public int getPartCount() {
        return this.parts.size();
    }

    int getClickEventIndex(ClickEvent clickEvent) {
        for(int i = 0; i < this.clickEvents.size(); ++i) {
            ClickEvent event = (ClickEvent)this.clickEvents.get(i);
            if (event.equals(clickEvent)) {
                return i + 1;
            }
        }

        return -1;
    }

    ClickEvent getClickEvent(int index) {
        return (ClickEvent)Iterables.get(this.clickEvents, index - 1, (Object)null);
    }

    int getHoverEventIndex(HoverEvent hoverEvent) {
        for(int i = 0; i < this.hoverEvents.size(); ++i) {
            HoverEvent event = (HoverEvent)this.hoverEvents.get(i);
            if (event.equals(hoverEvent)) {
                return i + 1;
            }
        }

        return -1;
    }

    HoverEvent getHoverEvent(int index) {
        return (HoverEvent)Iterables.get(this.hoverEvents, index - 1, (Object)null);
    }

    private StyledTextPart getPartBefore(StyledTextPart part) {
        int index = this.parts.indexOf(part);
        return index == 0 ? null : (StyledTextPart)this.parts.get(index - 1);
    }

    public Iterator<StyledTextPart> iterator() {
        return this.parts.iterator();
    }

    public String toString() {
        return "StyledText{'" + this.getString(PartStyle.StyleType.INCLUDE_EVENTS) + "'}";
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            StyledText that = (StyledText)o;
            return Objects.deepEquals(this.parts, that.parts) && Objects.deepEquals(this.clickEvents, that.clickEvents) && Objects.deepEquals(this.hoverEvents, that.hoverEvents);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return Objects.hash(new Object[]{this.parts, this.clickEvents, this.hoverEvents});
    }

    public static class StyledTextSerializer implements JsonSerializer<StyledText>, JsonDeserializer<StyledText> {
        public StyledText deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return StyledText.fromString(json.getAsString());
        }

        public JsonElement serialize(StyledText src, Type typeOfSrc, JsonSerializationContext context) {
            return context.serialize(src.getString());
        }
    }
}