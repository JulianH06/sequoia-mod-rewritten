package star.sequoia2.client.types.ws.handler.ws;

import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.handler.WSMessageHandler;
import star.sequoia2.client.types.ws.message.ws.rts.SWarCmdWSMessage;

import static star.sequoia2.client.types.ws.WSConstants.GSON;

public class SWarCmdWSMessageHandler extends WSMessageHandler {
    public SWarCmdWSMessageHandler(String message) {
        super(GSON.fromJson(message, SWarCmdWSMessage.class), message);
    }
    @Override
    public void handle() {
        SeqClient.info("Received SWarCMD message (RTS module removed); ignoring payload.");
    }
}
