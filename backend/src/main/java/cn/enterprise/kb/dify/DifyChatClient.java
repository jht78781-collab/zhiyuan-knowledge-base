package cn.enterprise.kb.dify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class DifyChatClient {
    public record Reference(String datasetId, String documentId, String segmentId, String content) {}
    public record Usage(Long input, Long output, Long total) {}
    public record Answer(String conversationId, String messageId, String text, List<Reference> references, Usage usage) {}

    private final DifyHttp http;
    private final DifyConfiguration config;
    private final ObjectMapper json;

    DifyChatClient(DifyHttp http, DifyConfiguration config, ObjectMapper json) {
        this.http = http;
        this.config = config;
        this.json = json;
    }

    public Answer ask(String alias, long user, String conversationId, String question) {
        var body = new LinkedHashMap<String, Object>();
        body.put("inputs", Map.of());
        body.put("query", question);
        body.put("response_mode", "streaming");
        body.put("user", "ekb-user-" + user);
        if (conversationId != null) body.put("conversation_id", conversationId);

        return http.call(client -> client.post().uri("/chat-messages").headers(h -> h.setBearerAuth(config.chatKey(alias)))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                .body(body).exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        int status = response.getStatusCode().value();
                        if (status == 401 || status == 403) {
                            throw new DifyFailure("DIFY_AUTH", "Dify 应用凭证无效或权限不足。", false, false);
                        }
                        if (status == 429 || status >= 500) throw DifyFailure.io();
                        throw new DifyFailure("DIFY_REJECTED", "Dify 拒绝问答请求，请检查 Chatflow 和模型。", false, false);
                    }
                    // Keep the browser response blocking so unvalidated tokens never reach end users.
                    byte[] data = response.getBody().readNBytes(4_000_001);
                    if (data.length > 4_000_000) throw DifyFailure.invalid();
                    String payload = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                    return readResponse(payload);
                }));
    }

    Answer readResponse(String payload) {
        try {
            if (payload.stripLeading().startsWith("{")) {
                var item = json.readTree(payload);
                if ("error".equals(item.path("event").asText())) throw workflowFailure();
                return parse(item, item.path("answer").asText(""), null);
            }
            var text = new StringBuilder();
            JsonNode last = null;
            Long workflowTotal = null;
            for (String line : payload.split("\\R")) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).strip();
                if (data.isEmpty() || "[DONE]".equals(data)) continue;
                var item = json.readTree(data);
                switch (item.path("event").asText()) {
                    case "message", "agent_message" -> text.append(item.path("answer").asText(""));
                    case "message_replace" -> { text.setLength(0); text.append(item.path("answer").asText("")); }
                    case "message_end" -> last = item;
                    case "error" -> throw workflowFailure();
                    case "workflow_finished" -> {
                        if ("failed".equals(item.path("data").path("status").asText())) throw workflowFailure();
                        workflowTotal = tokens(item.path("data").path("total_tokens"));
                    }
                    default -> { /* heartbeat and workflow progress */ }
                }
            }
            if (last == null) throw DifyFailure.invalid();
            return parse(last, text.toString(), workflowTotal);
        } catch (IOException e) {
            throw DifyFailure.invalid();
        }
    }

    private static DifyFailure workflowFailure() {
        return new DifyFailure("DIFY_WORKFLOW", "Dify 工作流未完成，请检查模型或应用配置。", false, false);
    }

    private static Answer parse(JsonNode node, String answer, Long workflowTotal) {
        var conversation = id(node.path("conversation_id"));
        var message = id(node.path("message_id"));
        if (message == null) message = id(node.path("id"));
        if (conversation == null || message == null) throw DifyFailure.invalid();

        var metadata = node.path("metadata");
        var resources = metadata.path("retriever_resources");
        if (!resources.isMissingNode() && !resources.isNull() && !resources.isArray()) throw DifyFailure.invalid();
        var references = new ArrayList<Reference>();
        if (resources.isArray()) {
            if (resources.size() > 100) throw DifyFailure.invalid();
            for (var entry : resources) {
                references.add(new Reference(id(entry.path("dataset_id")), id(entry.path("document_id")),
                        id(entry.path("segment_id")), truncated(entry.path("content").asText(""), 2000)));
            }
        }
        var usage = metadata.path("usage");
        Long total = tokens(usage.path("total_tokens"));
        return new Answer(conversation, message, truncated(answer, 32_000), List.copyOf(references),
                new Usage(tokens(usage.path("prompt_tokens")), tokens(usage.path("completion_tokens")),
                        total == null ? workflowTotal : total));
    }

    private static String id(JsonNode node) {
        String id = node.asText("");
        return id.matches("[a-zA-Z0-9_-]{1,80}") ? id : null;
    }

    private static Long tokens(JsonNode node) {
        return node.isIntegralNumber() && node.canConvertToLong() && node.longValue() >= 0 ? node.longValue() : null;
    }

    private static String truncated(String text, int limit) {
        int end = Math.min(text.length(), limit);
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }
}
