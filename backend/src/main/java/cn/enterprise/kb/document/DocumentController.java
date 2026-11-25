package cn.enterprise.kb.document;

import cn.enterprise.kb.common.ApiProblem;
import cn.enterprise.kb.common.PageView;
import cn.enterprise.kb.knowledge.KnowledgeService;
import cn.enterprise.kb.storage.MinioStorage;
import cn.enterprise.kb.user.AccountService.Identity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
class DocumentController {
    record KnowledgeCount(KnowledgeService.View knowledge, long availableDocuments) {}
    private final DocumentService documents;
    private final KnowledgeService knowledge;
    private final MinioStorage storage;

    DocumentController(DocumentService documents, KnowledgeService knowledge, MinioStorage storage) {
        this.documents = documents;
        this.knowledge = knowledge;
        this.storage = storage;
    }

    @GetMapping("/knowledge-bases/{kbId}/documents")
    PageView<DocumentService.View> list(@AuthenticationPrincipal Identity actor, @PathVariable long kbId,
                                        @RequestParam(defaultValue = "0") int page) {
        return documents.list(actor, kbId, page);
    }

    @GetMapping("/knowledge-overview")
    PageView<KnowledgeCount> overview(@AuthenticationPrincipal Identity actor, @RequestParam(defaultValue = "0") int page) {
        var visible = knowledge.list(actor, page);
        var counts = documents.counts(visible.items().stream().map(KnowledgeService.View::id).toList());
        var items = visible.items().stream().map(k -> new KnowledgeCount(k, counts.getOrDefault(k.id(), 0L))).toList();
        return new PageView<>(items, visible.page(), visible.pages(), visible.total());
    }

    @PostMapping(path = "/knowledge-bases/{kbId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    DocumentService.View upload(@AuthenticationPrincipal Identity actor, @PathVariable long kbId,
                               @RequestPart("file") MultipartFile file) {
        var kb = knowledge.detail(actor, kbId);
        if (!kb.canManage() || kb.status() != KnowledgeService.Status.ACTIVE) {
            throw ApiProblem.missing("知识库不存在或无权上传文件。");
        }
        var validation = FileValidation.check(file);
        var slot = documents.reserve(actor, kbId, validation, file.getSize());
        try (var stream = file.getInputStream()) {
            storage.put(slot.key(), slot.size(), stream);
        } catch (MinioStorage.Failure e) {
            return documents.uploaded(slot.id(), false, e.getMessage());
        } catch (IOException e) {
            return documents.uploaded(slot.id(), false, "读取上传文件失败。");
        }
        return documents.uploaded(slot.id(), true, null);
    }

    @GetMapping("/knowledge-bases/{kbId}/documents/{id}/download")
    ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal Identity actor, @PathVariable long kbId,
                                                @PathVariable long id) {
        var item = documents.download(actor, kbId, id);
        try {
            var stream = storage.open(item.key());
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(item.size())
                    .header("Content-Disposition", ContentDisposition.attachment()
                            .filename(item.name(), StandardCharsets.UTF_8).build().toString())
                    .body(new InputStreamResource(stream));
        } catch (MinioStorage.Failure e) {
            throw ApiProblem.unavailable(e.getMessage());
        }
    }

    @PostMapping("/knowledge-bases/{kbId}/documents/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    DocumentService.View retry(@AuthenticationPrincipal Identity actor, @PathVariable long kbId, @PathVariable long id) {
        return documents.retry(actor, kbId, id);
    }

    @DeleteMapping("/knowledge-bases/{kbId}/documents/{id}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void delete(@AuthenticationPrincipal Identity actor, @PathVariable long kbId, @PathVariable long id) {
        documents.delete(actor, kbId, id);
    }

    @DeleteMapping("/admin/knowledge-bases/{kbId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void deleteKnowledge(@AuthenticationPrincipal Identity actor, @PathVariable long kbId) {
        documents.deleteKnowledgeBase(actor, kbId);
    }
}
