package cn.enterprise.kb.document;

import cn.enterprise.kb.common.ApiProblem;
import cn.enterprise.kb.common.PageView;
import cn.enterprise.kb.dify.DifyKnowledgeClient.RemoteDocument;
import cn.enterprise.kb.knowledge.KnowledgeService;
import cn.enterprise.kb.user.AccountService.Identity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {
    public enum Status { UPLOADING, QUEUED, INDEXING, AVAILABLE, FAILED, DELETING, DELETE_FAILED, DELETED }
    public record View(long id, String name, long size, Status status, boolean originalAvailable, String error, Instant createdAt) {}
    public record Source(long id, String name) {}
    record Work(long id, long kbId, String key, String remoteName, long size, Status status, boolean deleteRequested,
                boolean remoteWriteAttempted, String remoteId, String batch, Instant indexStarted, int attempts) {}
    record Download(String key, String name, long size) {}

    private final DocumentRepository documents;
    private final KnowledgeService knowledge;

    DocumentService(DocumentRepository documents, KnowledgeService knowledge) {
        this.documents = documents;
        this.knowledge = knowledge;
    }

    @Transactional
    public Work reserve(Identity actor, long kbId, FileValidation.Validated file, long size) {
        knowledge.lockAccess(actor, kbId, true, false);
        var doc = new DocumentEntry();
        doc.knowledgeBaseId = kbId;
        doc.uploadedBy = actor.id();
        doc.originalName = file.name();
        doc.byteSize = size;
        doc.remoteName = "ekb-" + UUID.randomUUID() + "." + file.extension();
        doc.objectKey = "kb/" + kbId + "/" + doc.remoteName;
        doc.createdAt = Instant.now();
        state(doc, Status.UPLOADING, doc.createdAt.plusSeconds(180), null);
        return work(documents.saveAndFlush(doc));
    }

    @Transactional
    public View uploaded(long id, boolean stored, String error) {
        var doc = lock(id);
        if (doc.status != Status.UPLOADING) return view(doc, true);
        doc.storageReady = stored;
        if (doc.deleteRequested) {
            state(doc, Status.DELETING, Instant.now(), null);
        } else if (stored) {
            queueIndex(doc);
        } else if (error != null) {
            // A timeout can occur after MinIO commits the object. The worker checks before deciding.
            state(doc, Status.UPLOADING, Instant.now().plusSeconds(15), error);
        } else {
            state(doc, Status.FAILED, null, "原文件未保存，请重新上传。");
        }
        return view(doc, true);
    }

    @Transactional(readOnly = true)
    public PageView<View> list(Identity user, long kbId, int page) {
        var kb = knowledge.detail(user, kbId);
        var request = PageView.request(page, Sort.by(Sort.Direction.DESC, "id"));
        var rows = kb.canManage() ? documents.findByKnowledgeBaseIdAndStatusNot(kbId, Status.DELETED, request)
                : documents.findByKnowledgeBaseIdAndStatus(kbId, Status.AVAILABLE, request);
        return PageView.from(rows, d -> view(d, kb.canManage()));
    }

    @Transactional(readOnly = true)
    public Download download(Identity user, long kbId, long docId) {
        knowledge.read(user, kbId);
        var kb = knowledge.detail(user, kbId);
        var doc = documents.findById(docId).filter(d -> d.knowledgeBaseId == kbId).orElseThrow(DocumentService::missing);
        if (!doc.storageReady || doc.deleteRequested || (!kb.canManage() && doc.status != Status.AVAILABLE)) throw missing();
        return new Download(doc.objectKey, doc.originalName, doc.byteSize);
    }

    @Transactional
    public View retry(Identity actor, long kbId, long id) {
        knowledge.lockAccess(actor, kbId, true, true);
        var doc = lockWithin(kbId, id);
        switch (doc.status) {
            case DELETE_FAILED -> requestDelete(doc);
            case FAILED -> {
                if (!doc.storageReady) throw ApiProblem.conflict("没有可重新索引的原文件，请重新上传。");
                queueIndex(doc);
            }
            default -> throw ApiProblem.conflict("当前状态无需重试。");
        }
        return view(doc, true);
    }

    @Transactional
    public void delete(Identity actor, long kbId, long id) {
        knowledge.lockAccess(actor, kbId, true, true);
        var doc = lockWithin(kbId, id);
        if (doc.status == Status.DELETED) return;
        if (!doc.deleteRequested) knowledge.invalidateChatContext(kbId);
        requestDelete(doc);
    }

    @Transactional
    public void deleteKnowledgeBase(Identity user, long id) {
        var kb = knowledge.lockAccess(user, id, true, true);
        if (kb.status() != KnowledgeService.Status.DELETING) knowledge.markDeleting(id);
        var entries = documents.findByKnowledgeBaseIdAndStatusNotOrderById(id, Status.DELETED);
        entries.forEach(DocumentService::requestDelete);
        if (entries.isEmpty()) knowledge.finalizeDeletion(id);
    }

    @Transactional(readOnly = true)
    public Optional<Work> next(Instant now) {
        return documents.findFirstByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(now).map(DocumentService::work);
    }

    @Transactional
    public boolean aboutToCreateRemote(long id) {
        var doc = lock(id);
        if (doc.status != Status.QUEUED || doc.deleteRequested) return false;
        doc.remoteWriteAttempted = true;
        return true;
    }

    @Transactional
    public void observed(long id, RemoteDocument remote) {
        var doc = lock(id);
        if (!remote.id().equals(doc.remoteId)) doc.remoteBatch = null;
        doc.remoteId = remote.id();
        if (remote.batch() != null && remote.batch().length() <= 120 && !remote.batch().isBlank()) doc.remoteBatch = remote.batch();
        if (doc.deleteRequested) return;
        doc.attempts = 0;
        if (remote.available()) state(doc, Status.AVAILABLE, null, null);
        else if (remote.failed()) state(doc, Status.FAILED, null, "Dify 索引失败或已停止，请检查索引配置后重试。");
        else state(doc, Status.INDEXING, Instant.now().plusSeconds(6), null);
    }

    @Transactional
    public void issue(Work job, String error, boolean mayRetry) {
        var doc = lock(job.id());
        if (doc.status != job.status() || !doc.deleteRequested && job.deleteRequested()) return;
        if (!job.deleteRequested() && doc.deleteRequested && doc.status != Status.UPLOADING) return;
        if (mayRetry && ++doc.attempts <= 3) {
            state(doc, doc.status, Instant.now().plusSeconds(5L << doc.attempts), error);
        } else {
            state(doc, doc.deleteRequested ? Status.DELETE_FAILED : Status.FAILED, null, error);
        }
    }

    @Transactional
    public void cleaned(Work job) {
        var kb = knowledge.lockForJob(job.kbId());
        var doc = lock(job.id());
        doc.storageReady = false;
        state(doc, Status.DELETED, null, null);
        if (kb.status() == KnowledgeService.Status.DELETING
                && documents.countByKnowledgeBaseIdAndStatusNot(kb.id(), Status.DELETED) == 0) {
            knowledge.finalizeDeletion(kb.id());
        }
    }

    @Transactional(readOnly = true)
    public boolean hasReadyDocuments(long kbId) { return documents.existsByKnowledgeBaseIdAndStatus(kbId, Status.AVAILABLE); }

    @Transactional(readOnly = true)
    public Map<String, Source> sources(long kbId, Collection<String> remoteIds) {
        if (remoteIds.isEmpty()) return Map.of();
        return documents.findByKnowledgeBaseIdAndStatusAndRemoteIdIn(kbId, Status.AVAILABLE, remoteIds).stream()
                .collect(Collectors.toMap(d -> d.remoteId, d -> new Source(d.id, d.originalName)));
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> counts(List<Long> knowledgeIds) {
        if (knowledgeIds.isEmpty()) return Map.of();
        return documents.counts(knowledgeIds, Status.AVAILABLE).stream()
                .collect(Collectors.toMap(DocumentRepository.CountRow::getKbId, DocumentRepository.CountRow::getAvailable));
    }

    private static void requestDelete(DocumentEntry doc) {
        doc.deleteRequested = true;
        if (doc.status == Status.UPLOADING) return;
        doc.attempts = 0;
        state(doc, Status.DELETING, Instant.now(), null);
    }

    private static void queueIndex(DocumentEntry doc) {
        doc.attempts = 0;
        doc.indexStartedAt = Instant.now();
        state(doc, Status.QUEUED, doc.indexStartedAt, null);
    }

    private static void state(DocumentEntry doc, Status state, Instant next, String error) {
        doc.status = state;
        doc.nextAttemptAt = next;
        doc.errorMessage = error;
        doc.updatedAt = Instant.now();
    }

    private DocumentEntry lock(long id) { return documents.lock(id).orElseThrow(DocumentService::missing); }
    private DocumentEntry lockWithin(long kbId, long id) {
        var doc = lock(id);
        if (doc.knowledgeBaseId != kbId) throw missing();
        return doc;
    }

    private static ApiProblem missing() { return ApiProblem.missing("文档不存在或不可访问。"); }
    private static Work work(DocumentEntry d) {
        return new Work(d.id, d.knowledgeBaseId, d.objectKey, d.remoteName, d.byteSize, d.status, d.deleteRequested,
                d.remoteWriteAttempted, d.remoteId, d.remoteBatch, d.indexStartedAt, d.attempts);
    }
    private static View view(DocumentEntry d, boolean manager) {
        return new View(d.id, d.originalName, d.byteSize, d.status, d.storageReady && !d.deleteRequested,
                manager ? d.errorMessage : null, d.createdAt);
    }
}
