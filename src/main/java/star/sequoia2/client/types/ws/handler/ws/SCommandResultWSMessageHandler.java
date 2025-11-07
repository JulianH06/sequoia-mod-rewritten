package star.sequoia2.client.types.ws.handler.ws;

import star.sequoia2.accessors.NotificationsAccessor;
import star.sequoia2.accessors.TeXParserAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.handler.WSMessageHandler;
import star.sequoia2.client.types.ws.message.ws.SCommandResultWSMessage;

import static star.sequoia2.client.types.ws.WSConstants.GSON;
import static star.sequoia2.utils.XMLUtils.extractTextFromXml;

public class SCommandResultWSMessageHandler extends WSMessageHandler implements TeXParserAccessor, NotificationsAccessor {
    public SCommandResultWSMessageHandler(String message) {
        super(GSON.fromJson(message, SCommandResultWSMessage.class), message);
    }

    @Override
    public void handle() {
        SCommandResultWSMessage msg = (SCommandResultWSMessage) wsMessage;
        SCommandResultWSMessage.Data data = msg.getChatMessage();
        if (data == null) return;

        String result = data.result() == null ? "" : data.result();
        String tex = extractTextFromXml(result);

        notify(teXParser().parseMutableText(tex), "command-result");
    }
}
