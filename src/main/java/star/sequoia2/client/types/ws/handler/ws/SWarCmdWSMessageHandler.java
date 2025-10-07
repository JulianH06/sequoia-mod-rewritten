package star.sequoia2.client.types.ws.handler.ws;

import com.ibm.icu.impl.Pair;
import star.sequoia2.Seq;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.handler.WSMessageHandler;
import star.sequoia2.client.types.ws.message.ws.rts.SWarCmdWSMessage;
import star.sequoia2.features.impl.RTSWar;

import static star.sequoia2.client.types.ws.WSConstants.GSON;

public class SWarCmdWSMessageHandler extends WSMessageHandler implements FeaturesAccessor {
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
                features().get(RTSWar.class).ifPresent(rtsWar -> {
                    rtsWar.setCurrentTeam(Pair.of(data.commandFrom(), data.team()));
                });
            case 1: //attack (attack request)
                features().get(RTSWar.class).ifPresent(rtsWar -> {
                    rtsWar.getCommandQue().add(new RTSWar.WarCommand(1, data.coords()[0], data.coords()[1]));
                });
            case 2: //move (move request)
                features().get(RTSWar.class).ifPresent(rtsWar -> {
                    rtsWar.getCommandQue().add(new RTSWar.WarCommand(2, data.coords()[0], data.coords()[1]));
                });
            case 3: //switch role (your controller switched your role)
                features().get(RTSWar.class).ifPresent(rtsWar -> {
                    rtsWar.setRole(data.role());
                });
            case 4: //opted out (your team member opted out)

            case 5: //team created (you probably got added to one)

            case 6: //team dropped (your team got dropped)

            case 7: //team data (to each controller send this to update state in ui)

            case 8: //error to find commander role in discord
                SeqClient.warn("Error: " + data.error());
            default:
                SeqClient.warn("Invalid war message type.");
        }

    }
}
