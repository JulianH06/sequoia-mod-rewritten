package star.sequoia2.features.impl;

import com.collarmc.pounce.Subscribe;
import com.wynntils.core.components.Models;
import com.wynntils.utils.mc.McUtils;
import net.minecraft.text.Text;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.type.WSMessageType;
import star.sequoia2.events.ChatMessageEvent;
import star.sequoia2.features.Feature;

import java.util.List;

public class PartyFinder extends Feature {
    public static boolean publicParty = false;
    public static int memberCount = 0;

    public PartyFinder() {
        super("Party finder", "Custom party finder for SEQ members");
    }

    @Subscribe
    public void onChatMessage(ChatMessageEvent event) {
        if(event.message() == null) return;
        String message = event.message().getString();
        System.out.println(memberCount);

        if(message.contains("MAKEPUBLIC")) {

        }

        if(message.contains("created a party")) {
            publicParty = true;
            memberCount = 1;
            McUtils.sendMessageToClient(SeqClient.prefix(Text.of("§aYou have successfully created a party, if you want to queue for SEQ party finder use §o§b\"/seq public\"")));

        } else if (message.contains("left your current")) {
            publicParty = false;
            memberCount = 0;
            McUtils.sendMessageToClient(Text.of("LEFT")); //when the player itself leaves their party
        } else if (message.contains("have invited")) {
            McUtils.sendMessageToClient(Text.of("INVITED"));
        } else if (message.contains("has joined your")) {
            memberCount++;
            if(publicParty && memberCount == 4) {
                McUtils.sendMessageToClient(SeqClient.prefix(Text.of("§aYour raid party is full. It has been removed SEQ party finder")));
                publicParty = false;
            }
            McUtils.sendMessageToClient(Text.of("JOINED"));
        } else if (message.contains("has left")) {
            if(memberCount == 4) {
                McUtils.sendMessageToClient(SeqClient.prefix(Text.of("§aYour raid party is not full anymore. If you want to queue for SEQ party finder use §b\"/seq public\"")));
            }
            memberCount--;
            McUtils.sendMessageToClient(Text.of("PLAYER LEFT"));
            System.out.println("PLAYER LEFT");
        } else if (message.contains("has been kicked")) {
            if(memberCount == 4) {
                McUtils.sendMessageToClient(SeqClient.prefix(Text.of("§aYour raid party is not full anymore. If you want to queue for SEQ party finder use §b\"/seq public\"")));
            }
            memberCount--;
            McUtils.sendMessageToClient(Text.of("KICKED"));
            System.out.println("KICKED");
        } else if (message.contains("the Party Leader")) {
            publicParty = false; //only the party leader sends the data to the ws if you were leader before you dont need to be anymore
        } else if (message.contains("You are now the leader")) {
            memberCount = Models.Party.getPartyMembers().size();
            McUtils.sendMessageToClient(SeqClient.prefix(Text.of("§aYou are the new Party Leader, if you want to queue for SEQ party finder use §o§b\"/seq public\"")));
        } else if (message.contains("Your party has been disbanded")) {
            System.out.println("disbanded");
            memberCount = 0;
        }
    }

    public static class Party {
        public String owner;
        public List<String> partyMembers;

        public Party(List<String> partyMembers) {
            this.partyMembers = partyMembers;
            this.owner = partyMembers.getFirst();
        }
    }

    private void notifyWS(WSMessageType, PartyChangeEvents, )

    private enum PartyChangeEvents {
        PartyChangeEventCreated,
        PartyChangeEventKicked,
        PartyChangeEventAdded,
        PartyChangeEventTransfered,
        PartyChangeEventDsbanded
    }
}
