package cn.enterprise.kb.dify;

import cn.enterprise.kb.common.ApiProblem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DifyConfiguration {
    private final URI base;
    private final String knowledgeKey;
    private final Map<String, String> chatKeys;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    DifyConfiguration(ObjectMapper json, @Value("${app.dify.base-url}") String base,
                      @Value("${app.dify.knowledge-key}") String knowledgeKey,
                      @Value("${app.dify.chat-keys}") String applicationKeys,
                      @Value("${app.dify.connect-timeout-ms}") long connectMillis,
                      @Value("${app.dify.read-timeout-ms}") long readMillis) {
        this.base = URI.create(base.strip().replaceAll("/+$", ""));
        if (this.base.getHost() == null || this.base.getRawQuery() != null || this.base.getRawUserInfo() != null
                || this.base.getFragment() != null || !("http".equals(this.base.getScheme())
                        || "https".equals(this.base.getScheme()))) {
            throw new IllegalArgumentException("DIFY_BASE_URL must be an HTTP(S) Service API base URL.");
        }
        this.knowledgeKey = knowledgeKey.strip();
        chatKeys = parse(json, applicationKeys);
        connectTimeout = Duration.ofMillis(connectMillis);
        readTimeout = Duration.ofMillis(readMillis);
        if (connectMillis <= 0 || readMillis <= 0 || readMillis > 120_000) {
            throw new IllegalArgumentException("Dify timeouts must be positive and read timeout at most 120 seconds.");
        }
    }

    private static Map<String, String> parse(ObjectMapper json, String text) {
        if (text.isBlank()) return Map.of();
        Map<String, String> values;
        try {
            values = json.readValue(text, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("DIFY_CHAT_KEYS must be a JSON object of aliases and API keys.");
        }
        if (values == null || values.entrySet().stream().anyMatch(e -> !validAlias(e.getKey())
                || e.getValue() == null || e.getValue().isBlank() || !e.getValue().equals(e.getValue().strip()))
                || new HashSet<>(values.values()).size() != values.size()) {
            throw new IllegalArgumentException("Configure valid Dify aliases with distinct non-blank application keys.");
        }
        return Map.copyOf(values);
    }

    public static boolean validAlias(String alias) { return alias.matches("[a-z0-9][a-z0-9_-]{0,63}"); }
    public boolean hasChatAlias(String alias) { return chatKeys.containsKey(alias); }
    public boolean hasKnowledgeKey() { return !knowledgeKey.isBlank(); }
    URI base() { return base; }
    Duration connectTimeout() { return connectTimeout; }
    public Duration readTimeout() { return readTimeout; }

    String knowledgeKey() {
        if (!hasKnowledgeKey()) throw DifyFailure.config("服务端尚未配置 Dify 知识库密钥。");
        return knowledgeKey;
    }

    String chatKey(String alias) {
        var key = chatKeys.get(alias);
        if (key == null) throw DifyFailure.config("服务端尚未配置该知识库的 Dify 应用密钥。");
        return key;
    }
}
