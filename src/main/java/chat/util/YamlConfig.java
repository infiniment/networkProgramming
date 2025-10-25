package chat.util;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.Map;

@SuppressWarnings("unchecked")
public final class YamlConfig {
    private static Map<String, Object> root;

    public static synchronized Map<String, Object> root() {
        if (root != null) return root;
        try (InputStream in = YamlConfig.class.getClassLoader().getResourceAsStream("application.yml")) {
            if (in == null) throw new IllegalStateException("application.yml not found on classpath");
            LoaderOptions opts = new LoaderOptions();
            Yaml yaml = new Yaml(new SafeConstructor(opts));
            root = (Map<String, Object>) yaml.load(in);
            return root;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load application.yml", e);
        }
    }

    public static String str(String path) {
        Object v = get(path);
        return v == null ? null : String.valueOf(v);
    }

    public static Integer integer(String path) {
        Object v = get(path);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        return Integer.parseInt(String.valueOf(v));
    }

    public static Long lng(String path) {
        Object v = get(path);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.parseLong(String.valueOf(v));
    }

    private static Object get(String path) {
        String[] parts = path.split("\\.");
        Object cur = root();
        for (String p : parts) {
            if (!(cur instanceof Map)) return null;
            cur = ((Map<?, ?>) cur).get(p);
            if (cur == null) return null;
        }
        return cur;
    }

    private YamlConfig() {}
}
