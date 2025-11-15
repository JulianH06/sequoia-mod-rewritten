package star.sequoia2.client.types.ws.handler.ws;

import com.google.gson.JsonElement;
import star.sequoia2.client.types.ws.message.WSMessage;
import star.sequoia2.client.types.ws.message.ws.SRewardWSMessage;

import static star.sequoia2.client.types.ws.WSConstants.GSON;

public final class SRewardWSMessageHandler {
    private static final SRewardWSMessageHandler INSTANCE = new SRewardWSMessageHandler();
    private SRewardWSMessageHandler() {}

    public static void handle(WSMessage wsMessage) {
        INSTANCE.handleInternal(wsMessage);
    }

    private void handleInternal(WSMessage wsMessage) {
        SRewardWSMessage sRewardWSMessage = GSON.fromJson(wsMessage.getData(), SRewardWSMessage.class);
        JsonElement sRewardWSMessageData = sRewardWSMessage.getData();


    }
}
