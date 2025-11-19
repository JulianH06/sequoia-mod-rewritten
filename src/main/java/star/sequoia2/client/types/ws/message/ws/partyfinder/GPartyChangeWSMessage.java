package star.sequoia2.client.types.ws.message.ws.partyfinder;

import com.google.gson.annotations.SerializedName;
import star.sequoia2.client.types.ws.message.WSMessage;
import star.sequoia2.client.types.ws.type.WSMessageType;

import static star.sequoia2.client.types.ws.WSConstants.GSON;

public class GPartyChangeWSMessage extends WSMessage {
    public GPartyChangeWSMessage(Data data) {
        super(WSMessageType.G_PARTY_CHANGE.getValue(), GSON.toJsonTree(data));
    }

    public record Data(
            int event,
            String[] username) {}
}
