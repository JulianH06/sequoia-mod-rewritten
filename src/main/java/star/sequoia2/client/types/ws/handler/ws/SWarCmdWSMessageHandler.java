package star.sequoia2.client.types.ws.handler.ws;

import star.sequoia2.Seq;
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
        SWarCmdWSMessage sWarWSMessage = (SWarCmdWSMessage) wsMessage;
        SWarCmdWSMessage.Data data = sWarWSMessage.getSWarCmdData();

        SeqClient.info("Received SWarCMD type: " + data.type());

        switch (data.type()){
            case 0: //enrolled (you got added to a team)

            case 1: //attack (attack request)

            case 2: //move (move request)

            case 3: //switch role (your controller switched your role)

            case 4: //opted out (your team member opted out)

            case 5: //team created (you probably got added to one)

            case 6: //team dropped (your team got dropped)

            case 7: //team data (to each controller send this to update state in ui)

            default:
                SeqClient.warn("Invalid war message type.");
        }

    }
}
