package star.sequoia2.client.notifications;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import star.sequoia2.client.SeqClient;

import java.util.List;

import static star.sequoia2.client.SeqClient.mc;

public class Notifications {

    private Text lastMessage;
    private String lastSignature;

    public void sendAlert(Text alert) {
        if (notReady()) return;
        mc.inGameHud.setTitle(alert);
    }

    public void sendNotification(Text notification) {
        if (notReady()) return;
        mc.inGameHud.setOverlayMessage(notification, false);
    }

    public void sendMessage(Text message) {
        sendMessage(message, message == null ? "" : String.valueOf(message));
    }

    /**
     *
     * @param message message to send
     * @param sig signature to suppress duplicates
     */
    public void sendMessage(Text message, String sig) {
        if (notReady()) return;
        if (sig != null && sig.equals(lastSignature) && lastMessage != null && lastMessage.equals(message)) {
            return;
        }

        MutableText rendered = message instanceof MutableText mt ? mt.copy() : message.copy();
        if (rendered.getStyle().getColor() == null) {
            rendered = rendered.formatted(Formatting.WHITE);
        }

        mc.inGameHud.getChatHud().addMessage(rendered);

        SeqClient.debug(rendered.getString());
        lastSignature = sig;
        lastMessage = rendered.copy();
    }


    public void sendMultilineMessage(List<Text> messages) {
        if (messages.isEmpty()) {
            return;
        }
        sendMessage(messages.getFirst(), messages.getFirst().toString());
        for (int i = 1; i < messages.size(); i++) {
            mc.inGameHud.getChatHud().addMessage(messages.get(i));
        }
    }

    private static boolean notReady() {
        return mc.inGameHud == null || !SeqClient.initialized;
    }
}
