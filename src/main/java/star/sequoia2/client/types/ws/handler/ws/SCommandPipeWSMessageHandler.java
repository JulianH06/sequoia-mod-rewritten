package star.sequoia2.client.types.ws.handler.ws;

import org.apache.commons.lang3.StringUtils;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.message.WSMessage;
import star.sequoia2.client.types.ws.message.ws.SCommandPipeWSMessage;
import star.sequoia2.features.impl.ws.WebSocketFeature;

import java.util.Optional;

import static star.sequoia2.client.types.ws.WSConstants.GSON;

public final class SCommandPipeWSMessageHandler implements FeaturesAccessor {
    private static final SCommandPipeWSMessageHandler INSTANCE = new SCommandPipeWSMessageHandler();
    private SCommandPipeWSMessageHandler() {}

    public static void handle(WSMessage wsMessage) {
        INSTANCE.handleInternal(wsMessage);
    }

    private void handleInternal(WSMessage wsMessage) {
        Optional<WebSocketFeature> wsFeature = features().getIfActive(WebSocketFeature.class);
        SCommandPipeWSMessage decoded = GSON.fromJson(wsMessage.getData(), SCommandPipeWSMessage.class);
        if (StringUtils.equals("Invalid token", decoded.getData().getAsString())) {
            SeqClient.debug("Received invalid token response. Requesting a new token.");
            wsFeature.ifPresent(webSocketFeature -> webSocketFeature.authenticate(true));
        } else if (StringUtils.equals("Authenticated.", decoded.getData().getAsString())) {
            SeqClient.debug("Authenticated with WebSocket server.");
            wsFeature.ifPresent(webSocketFeature -> webSocketFeature.setAuthenticating(false));
            wsFeature.ifPresent(webSocketFeature -> webSocketFeature.setAuthenticated(true));
        }
    }
}
