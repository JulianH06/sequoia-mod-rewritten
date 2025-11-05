package star.sequoia2.utils.cache;

import star.sequoia2.client.SeqClient;

public class Threading implements Runnable {
    @Override
    public void run() {
        try {
            GuildCache.init();
        } catch (Exception ex) {
            SeqClient.warn("Failed to initialise guild cache", ex);
        }

        try {
            SequoiaMemberCache.init();
        } catch (Exception ex) {
            SeqClient.warn("Failed to initialise member cache", ex);
        }
    }
}
