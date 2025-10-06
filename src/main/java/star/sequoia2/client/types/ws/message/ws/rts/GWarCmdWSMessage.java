package star.sequoia2.client.types.ws.message.ws.rts;

import star.sequoia2.client.types.ws.message.WSMessage;
import star.sequoia2.client.types.ws.type.WSMessageType;

import static star.sequoia2.client.types.ws.WSConstants.GSON;

public class GWarCmdWSMessage extends WSMessage {

    public GWarCmdWSMessage(Data data) {
        super(WSMessageType.G_WAR_CMD.getValue(), GSON.toJsonTree(data));
    }

    public record Data(
        int type,
        //        0 : assume controller
        //		  1 : attack
        //		  2 : move
        //		  3 : switch role
        //		  4 : take command of player
        //		  5 : drop player
        //		  6 : drop team
        //		  7 : OPT IN
        //		  8 : opt out
        String affectedTeam,
        String[] affected,
        int[] coords,
        int warRole
        //      0 : null
        //		1 : tank
        //		2 : dps
        //		3 : emotional support
        //      4 : cat
    ){}
}
