package star.sequoia2.client.types.ws.handler.ws;

import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.message.WSMessage;
import star.sequoia2.client.types.ws.message.ws.rts.SWarCmdWSMessage;

import static star.sequoia2.client.types.ws.WSConstants.GSON;

public final class SWarCmdWSMessageHandler {
    private static final SWarCmdWSMessageHandler INSTANCE = new SWarCmdWSMessageHandler();
    private SWarCmdWSMessageHandler() {}

    public static void handle(WSMessage wsMessage) {
        SeqClient.info("Received SWarCMD message (RTS module removed); ignoring payload.");
    }
}
