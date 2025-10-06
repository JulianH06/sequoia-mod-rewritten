package star.sequoia2.client.types.ws.message.ws.rts;

import com.google.gson.JsonElement;
import star.sequoia2.client.types.ws.message.WSMessage;
import star.sequoia2.client.types.ws.type.WSMessageType;

import static star.sequoia2.client.types.ws.WSConstants.GSON;

public class SWarCmdWSMessage extends WSMessage {
    public SWarCmdWSMessage(JsonElement data) {
        super(WSMessageType.S_MESSAGE.getValue(), data);
    }

    public Data getSWarCmdData() {
        return GSON.fromJson(getData(), Data.class);
    }

    public record Data(
        int type,
        //        0 : enrolled
        //		  1 : attack
        //		  2 : move
        //		  3 : switch role
        //		  4 : opted out
        //		  5 : Team created
        //		  6 : team dropped
        //		  7 : MEOW MEOW MEOW
        String commandFrom,
        String teamData,
        int[] coords,
        int switchTo
        //      0 : null
        //		1 : tank
        //		2 : dps
        //		3 : emotional support
        //		4 : cat
    ){}
}
