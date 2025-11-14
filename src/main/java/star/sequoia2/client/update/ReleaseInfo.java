package star.sequoia2.client.update;

public record ReleaseInfo(String tag,
                          String name,
                          String downloadUrl,
                          String htmlUrl,
                          String changelog,
                          String publishedAt,
                          UpdateChannel channel) {

    public String displayVersion() {
        return name != null && !name.isBlank() ? name : tag;
    }
}
