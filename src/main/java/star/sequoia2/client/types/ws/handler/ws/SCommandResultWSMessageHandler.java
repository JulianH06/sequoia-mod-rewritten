package star.sequoia2.client.types.ws.handler.ws;

import star.sequoia2.accessors.NotificationsAccessor;
import star.sequoia2.accessors.TeXParserAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.message.WSMessage;
import star.sequoia2.client.types.ws.message.ws.SCommandResultWSMessage;

import static star.sequoia2.client.types.ws.WSConstants.GSON;
import static star.sequoia2.utils.XMLUtils.extractTextFromXml;

public final class SCommandResultWSMessageHandler implements TeXParserAccessor, NotificationsAccessor {
    private static final SCommandResultWSMessageHandler INSTANCE = new SCommandResultWSMessageHandler();
    private SCommandResultWSMessageHandler() {}

    public static void handle(WSMessage wsMessage) {
        INSTANCE.handleInternal(wsMessage);
    }

    private void handleInternal(WSMessage wsMessage) {
        SCommandResultWSMessage msg = GSON.fromJson(wsMessage.getData(), SCommandResultWSMessage.class);
        SCommandResultWSMessage.Data data = msg.getChatMessage();
        if (data == null) return;

        String result = data.result() == null ? "" : data.result();
        String tex = extractTextFromXml(result);

        notify(teXParser().parseMutableText(tex), "command-result");
    }
}
