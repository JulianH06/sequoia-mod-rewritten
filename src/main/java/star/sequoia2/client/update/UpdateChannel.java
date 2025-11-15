package star.sequoia2.client.update;

public enum UpdateChannel {
    STABLE("Stable", "release", false, "Seq.jar"),
    NIGHTLY("Nightly", "nightly", true, "Seq-nightly.jar");

    private final String displayName;
    private final String tagPrefix;
    private final boolean requiresPrerelease;
    private final String assetName;

    UpdateChannel(String displayName, String tagPrefix, boolean requiresPrerelease, String assetName) {
        this.displayName = displayName;
        this.tagPrefix = tagPrefix;
        this.requiresPrerelease = requiresPrerelease;
        this.assetName = assetName;
    }

    public String displayName() {
        return displayName;
    }

    public String tagPrefix() {
        return tagPrefix;
    }

    public boolean requiresPrerelease() {
        return requiresPrerelease;
    }

    public String assetName() {
        return assetName;
    }

    public String storageKey() {
        return name().toLowerCase();
    }
}
