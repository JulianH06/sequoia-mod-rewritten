package star.sequoia2.client.types.ws.handler.ws;

import com.google.gson.JsonElement;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.accessors.TeXParserAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.handler.WSMessageHandler;
import star.sequoia2.client.types.ws.message.ws.SMessageWSMessage;
import star.sequoia2.features.impl.ws.WebSocketFeature;
import star.sequoia2.utils.URLUtils;

import java.util.regex.Matcher;

import static star.sequoia2.client.SeqClient.mc;
import static star.sequoia2.client.types.ws.WSConstants.GSON;
import static star.sequoia2.utils.XMLUtils.extractTextFromXml;

public class SMessageWSMessageHandler extends WSMessageHandler implements FeaturesAccessor, TeXParserAccessor {
    public SMessageWSMessageHandler(String message) {
        super(GSON.fromJson(message, SMessageWSMessage.class), message);
    }

    @Override
    public void handle() {
        SMessageWSMessage sMessageWSMessage = (SMessageWSMessage) wsMessage;
        JsonElement sMessageWSMessageData = sMessageWSMessage.getData();

        if (sMessageWSMessageData.isJsonPrimitive()) {
            String serverMessageText = sMessageWSMessageData.getAsString();
            if (StringUtils.equals(serverMessageText, "Invalid or expired token provided.\\nVisit https://api.sequoia.ooo/oauth2 to obtain a new session.")) {
                SeqClient.debug("Received authentication required message, reauthenticating.");
                features().getIfActive(WebSocketFeature.class).ifPresent(WebSocketFeature::authenticate);
                return;
            }

            String trimmed = serverMessageText == null ? "" : serverMessageText.trim();
            if (trimmed.startsWith("<")) {
                String tex = extractTextFromXml(serverMessageText);
                MutableText messageComponent = Text.literal("Server message ➤ ").append(teXParser().parseMutableText(tex));
                mc.getMessageHandler().onGameMessage(SeqClient.prefix(messageComponent), false);
                return;
            }

            Matcher matcher = URLUtils.getURLMatcher(serverMessageText);
            MutableText messageComponent = Text.literal("Server message ➤ ");
            int lastMatchEnd = 0;

            while (matcher.find()) {
                if (matcher.start() > lastMatchEnd) {
                    String pre = serverMessageText.substring(lastMatchEnd, matcher.start());
                    messageComponent = messageComponent.append(teXParser().parseMutableText(pre));
                }

                String url = matcher.group();
                MutableText urlText = Text.literal(url)
                        .styled(style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to open URL"))));
                messageComponent = messageComponent.append(urlText);

                lastMatchEnd = matcher.end();
            }

            if (lastMatchEnd < serverMessageText.length()) {
                String tail = serverMessageText.substring(lastMatchEnd);
                messageComponent = messageComponent.append(teXParser().parseMutableText(tail));
            }

            mc.getMessageHandler().onGameMessage(SeqClient.prefix(messageComponent), false);
        } else {
            String tex = extractTextFromXml(String.valueOf(sMessageWSMessageData));
            mc.getMessageHandler().onGameMessage(
                    SeqClient.prefix(Text.literal("Server message ➤ ").append(teXParser().parseMutableText(tex))),
                    false
            );
        }
    }
}
