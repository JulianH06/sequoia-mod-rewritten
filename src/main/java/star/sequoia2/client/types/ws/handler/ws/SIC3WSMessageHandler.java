package star.sequoia2.client.types.ws.handler.ws;

import net.minecraft.util.math.BlockPos;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.message.WSMessage;
import star.sequoia2.client.types.ws.message.ws.SIC3DataWSMessage;
import star.sequoia2.client.types.ws.type.PosCodec;

import java.util.List;

import static star.sequoia2.client.types.ws.WSConstants.GSON;

public final class SIC3WSMessageHandler implements FeaturesAccessor {
    private static final SIC3WSMessageHandler INSTANCE = new SIC3WSMessageHandler();
    private SIC3WSMessageHandler() {}

    public static void handle(WSMessage wsMessage) {
        INSTANCE.handleInternal(wsMessage);
    }

    private void handleInternal(WSMessage wsMessage) {
        SIC3DataWSMessage sic3DataWSMessage = GSON.fromJson(wsMessage.getData(), SIC3DataWSMessage.class);
        SIC3DataWSMessage.Data data = sic3DataWSMessage.getChatMessage();

        // RTSWar removed; no-op for IC3 data
    }
}
