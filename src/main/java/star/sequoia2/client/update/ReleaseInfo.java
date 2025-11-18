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

    // Stable fingerprint even when tags are reused (tag + publishedAt + body/sha)
    public String signature() {
        StringBuilder sb = new StringBuilder();
        if (tag != null) sb.append(tag);
        sb.append('|');
        if (publishedAt != null) sb.append(publishedAt);
        sb.append('|');
        if (changelog != null) sb.append(changelog);
        return sb.toString();
    }
}
