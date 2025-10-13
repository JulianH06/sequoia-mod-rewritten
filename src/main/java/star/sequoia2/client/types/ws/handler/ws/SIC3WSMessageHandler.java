package star.sequoia2.client.types.ws.handler.ws;

import net.minecraft.util.math.BlockPos;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.handler.WSMessageHandler;
import star.sequoia2.client.types.ws.message.ws.SIC3DataWSMessage;
import star.sequoia2.client.types.ws.type.PosCodec;

import java.util.List;

import static star.sequoia2.client.types.ws.WSConstants.GSON;

public class SIC3WSMessageHandler extends WSMessageHandler {
    public SIC3WSMessageHandler(String message) {
        super(GSON.fromJson(message, SIC3DataWSMessage.class), message);
    }

    @Override
    public void handle() {
        SIC3DataWSMessage sic3DataWSMessage = (SIC3DataWSMessage) wsMessage;
        SIC3DataWSMessage.Data data = sic3DataWSMessage.getChatMessage();

        List<BlockPos> blocks = PosCodec.decode(data.payload());


        if (data.method().equals("position")) {
            SeqClient.info("Received Position data: " + blocks.getFirst());
        }
    }
}
