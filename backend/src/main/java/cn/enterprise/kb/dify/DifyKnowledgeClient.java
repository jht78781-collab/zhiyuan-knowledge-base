package cn.enterprise.kb.dify;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;

@Component
public class DifyKnowledgeClient {
    public record RemoteDocument(String id, String batch, String status) {
        public boolean available() { return "completed".equals(status); }
        public boolean failed() { return "error".equals(status) || "paused".equals(status) || "stopped".equals(status); }
    }

    private final DifyHttp http;
    private final DifyConfiguration config;

    DifyKnowledgeClient(DifyHttp http, DifyConfiguration config) {
        this.http = http;
        this.config = config;
    }

    public RemoteDocument create(String dataset, String filename, long size, InputStream stream) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("data", "{\"indexing_technique\":\"high_quality\",\"process_rule\":{\"mode\":\"automatic\"}}");
        var fileHeaders = new HttpHeaders();
        fileHeaders.setContentDisposition(ContentDisposition.formData().name("file").filename(filename).build());
        fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        fileHeaders.setContentLength(size);
        body.add("file", new HttpEntity<>(new InputStreamResource(stream), fileHeaders));
        JsonNode result = http.call(client -> client.post().uri("/datasets/{dataset}/document/create-by-file", dataset)
                .headers(headers -> headers.setBearerAuth(config.knowledgeKey())).contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body).retrieve().body(JsonNode.class));
        if (result == null || !result.has("document")) throw DifyFailure.invalid();
        var item = document(result.path("document"));
        var batch = result.path("batch").asText("");
        return new RemoteDocument(item.id(), batch.isBlank() ? item.batch() : batch, item.status());
    }

    public Optional<RemoteDocument> findByName(String dataset, String filename) {
        RemoteDocument match = null;
        for (int page = 1; page <= 10; page++) {
            int number = page;
            JsonNode result = http.call(client -> client.get()
                    .uri(uri -> uri.path("/datasets/{dataset}/documents").queryParam("keyword", filename)
                            .queryParam("page", number).queryParam("limit", 50).build(dataset))
                    .headers(headers -> headers.setBearerAuth(config.knowledgeKey())).retrieve().body(JsonNode.class));
            if (result == null || !result.path("data").isArray()) throw DifyFailure.invalid();
            for (JsonNode candidate : result.path("data")) {
                if (!filename.equals(candidate.path("name").asText())) continue;
                if (match != null) {
                    throw new DifyFailure("DIFY_DUPLICATE", "发现多个同名 Dify 文档，请先在 Dify 核对并清理。", false, false);
                }
                match = document(candidate);
            }
            if (!result.path("has_more").asBoolean(false)) return Optional.ofNullable(match);
        }
        throw DifyFailure.invalid();
    }

    public RemoteDocument status(String dataset, String id, String batch) {
        if (batch != null && !batch.isBlank()) {
            JsonNode result = http.call(client -> client.get()
                    .uri("/datasets/{dataset}/documents/{batch}/indexing-status", dataset, batch)
                    .headers(headers -> headers.setBearerAuth(config.knowledgeKey())).retrieve().body(JsonNode.class));
            if (result == null || !result.path("data").isArray()) throw DifyFailure.invalid();
            for (JsonNode item : result.path("data")) {
                if (id.equals(item.path("id").asText())) return document(item);
            }
        }
        JsonNode result = http.call(client -> client.get().uri("/datasets/{dataset}/documents/{id}", dataset, id)
                .headers(headers -> headers.setBearerAuth(config.knowledgeKey())).retrieve().body(JsonNode.class));
        return document(result);
    }

    public void delete(String dataset, String id) {
        try {
            http.call(client -> client.delete().uri("/datasets/{dataset}/documents/{id}", dataset, id)
                    .headers(headers -> headers.setBearerAuth(config.knowledgeKey())).retrieve().toBodilessEntity());
        } catch (DifyFailure failure) {
            if (!failure.missing()) throw failure;
        }
    }

    private static RemoteDocument document(JsonNode node) {
        if (node == null || !node.path("id").asText("").matches("[a-zA-Z0-9_-]{1,80}")) throw DifyFailure.invalid();
        var status = node.path("indexing_status").asText("").toLowerCase(Locale.ROOT);
        var batch = node.path("batch").asText(null);
        return new RemoteDocument(node.path("id").asText(), batch, status);
    }
}
