package star.sequoia2.client.types.ws.handler.ws;

import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.accessors.NotificationsAccessor;
import star.sequoia2.accessors.TeXParserAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.message.ws.SChannelMessageWSMessage;
import star.sequoia2.client.types.ws.message.WSMessage;
import star.sequoia2.features.impl.ws.DiscordChatBridgeFeature;

import java.util.List;

import static star.sequoia2.client.types.ws.WSConstants.GSON;
import static star.sequoia2.utils.XMLUtils.extractTextFromXml;

public final class SChannelMessageWSMessageHandler implements TeXParserAccessor, FeaturesAccessor, NotificationsAccessor {
    private static final SChannelMessageWSMessageHandler INSTANCE = new SChannelMessageWSMessageHandler();
    private SChannelMessageWSMessageHandler() {}

    private final String MESSAGE_FORMAT = "\\gradient%s{%s}\\={:} \\-{%s}";

    public static void handle(WSMessage wsMessage) {
        INSTANCE.handleInternal(wsMessage);
    }

    private void handleInternal(WSMessage wsMessage) {
        SeqClient.debug(wsMessage.toString());
        if (features().getIfActive(DiscordChatBridgeFeature.class).map(DiscordChatBridgeFeature::isActive).orElse(false)
                && features().getIfActive(DiscordChatBridgeFeature.class).map(discordChatBridgeFeature -> discordChatBridgeFeature.getSendDiscordMessageToChat().get()).orElse(false)) {
            SChannelMessageWSMessage sChannelMessageWSMessage = GSON.fromJson(wsMessage.getData(), SChannelMessageWSMessage.class);
            SChannelMessageWSMessage.Data d = sChannelMessageWSMessage.getSChannelMessageData();
            String name = d.displayName() == null ? "" : d.displayName();
            String msg = d.message() == null ? "" : d.message();
            String messageTeX = isLikelyXml(msg) ? extractTextFromXml(msg) : teXParser().sanitize(msg);
            notify(teXParser().parseMutableText(
                    MESSAGE_FORMAT,
                    formatColorArgs(d.color()),
                    teXParser().sanitize(name),
                    messageTeX
            ), "discord-bridge");
        }
    }

    private static boolean isLikelyXml(String s) {
        if (s == null) return false;
        String t = s.trim();
        return !t.isEmpty() && t.charAt(0) == '<';
    }

    public static String formatColorArgs(List<Integer> colors) {
        if (colors == null || colors.isEmpty()) return "{1}{ffffff}";
        StringBuilder sb = new StringBuilder();
        sb.append("{").append(colors.size()).append("}");
        for (Integer color : colors) {
            int c = color == null ? 0xffffff : color;
            sb.append("{").append(String.format("%06x", c)).append("}");
        }
        return sb.toString();
    }
}
