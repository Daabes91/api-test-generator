package support;

public class World {
    private static final ThreadLocal<String> LAST_VERB = new ThreadLocal<>();
    private static final ThreadLocal<String> LAST_PATH = new ThreadLocal<>();
    private static final ThreadLocal<String> LAST_BODY = new ThreadLocal<>();
    private static final ThreadLocal<java.util.Map<String,String>> MEMORY = ThreadLocal.withInitial(java.util.HashMap::new);
    private static final ThreadLocal<java.util.Set<String>> IGNORED_JSON_PATHS = ThreadLocal.withInitial(java.util.HashSet::new);

    public static void set(String verb, String path, String body) {
        LAST_VERB.set(verb);
        LAST_PATH.set(path);
        LAST_BODY.set(body);
    }
    public static String verb(){ return LAST_VERB.get(); }
    public static String path(){ return LAST_PATH.get(); }
    public static String body(){ return LAST_BODY.get(); }
    public static void remember(String key, String value){ MEMORY.get().put(key, value); }
    public static String recall(String key){ return MEMORY.get().get(key); }

    public static void addIgnoredJsonPath(String normalizedPath){
        if (normalizedPath == null || normalizedPath.isBlank()) return;
        IGNORED_JSON_PATHS.get().add(normalizedPath);
    }

    public static java.util.Set<String> ignoredJsonPaths(){
        return java.util.Collections.unmodifiableSet(IGNORED_JSON_PATHS.get());
    }

    public static void reset(){
        LAST_VERB.remove();
        LAST_PATH.remove();
        LAST_BODY.remove();
        MEMORY.get().clear();
        IGNORED_JSON_PATHS.get().clear();
    }
}
