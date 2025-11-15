package star.sequoia2.client.types.ws.handler.ws;


import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.message.ws.SBinaryDataWSMessage;
import star.sequoia2.client.types.ws.message.WSMessage;

import static star.sequoia2.client.types.ws.WSConstants.GSON;

public final class SBinaryDataWSMessageHandler {
    private SBinaryDataWSMessageHandler() {}

    public static void handle(WSMessage wsMessage) {
        SBinaryDataWSMessage sBinaryDataWSMessage = GSON.fromJson(wsMessage.getData(), SBinaryDataWSMessage.class);
        SBinaryDataWSMessage.Data sBinaryDataWSMessageData = sBinaryDataWSMessage.getSBinaryData();

        SeqClient.debug("Received SBinaryData: " + sBinaryDataWSMessageData);
    }
}
