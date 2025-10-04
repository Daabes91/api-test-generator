package support;

import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

public class Config {
    private static final Properties PROPS = new Properties();
    private static boolean loaded = false;

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        String profile = System.getProperty("profile");
        if (profile == null || profile.isBlank()) {
            profile = System.getenv("TEST_PROFILE");
        }
        // Load base file
        loadResource("test.properties");
        // Load profile-specific overlay if present: test-<profile>.properties
        if (profile != null && !profile.isBlank()) {
            loadResource("test-" + profile + ".properties");
        }
    }

    private static void loadResource(String name) {
        try (InputStream is = Config.class.getClassLoader().getResourceAsStream(name)) {
            if (is != null) {
                PROPS.load(is);
            }
        } catch (Exception ignored) {}
    }

    public static String get(String key) {
        ensureLoaded();
        if (key == null) return null;
        String v = PROPS.getProperty(key);
        if (v != null) return v;
        // try normalized variations: lower, dots, underscores
        String lower = key.toLowerCase(Locale.ROOT);
        v = PROPS.getProperty(lower);
        if (v != null) return v;
        String dotted = lower.replace('_', '.');
        v = PROPS.getProperty(dotted);
        if (v != null) return v;
        return null;
    }

    public static String getFirst(String... keys) {
        for (String k : keys) {
            String v = get(k);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}

