package star.sequoia2.accessors;


import net.minecraft.text.Text;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.notifications.Notifications;

public interface NotificationsAccessor {
    default Notifications notifications() {
        return SeqClient.getNotifications();
    }

    default void notify(Text message) {
        notify(message, message == null ? "" : message.getString());
    }

    default void notify(Text message, String signature) {
        Notifications notifications = notifications();
        if (notifications == null) return;
        Text content = message == null ? Text.empty() : message;
        notifications.sendMessage(SeqClient.prefix(content), signature);
    }

    default Text prefixed(Text message) {
        return SeqClient.prefix(message == null ? Text.empty() : message);
    }
}
