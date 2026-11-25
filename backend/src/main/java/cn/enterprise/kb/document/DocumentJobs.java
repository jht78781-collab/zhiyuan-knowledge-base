package cn.enterprise.kb.document;

import cn.enterprise.kb.common.SchemaReady;
import cn.enterprise.kb.dify.DifyFailure;
import cn.enterprise.kb.dify.DifyConfiguration;
import cn.enterprise.kb.dify.DifyKnowledgeClient;
import cn.enterprise.kb.dify.DifyKnowledgeClient.RemoteDocument;
import cn.enterprise.kb.knowledge.KnowledgeService;
import cn.enterprise.kb.storage.MinioStorage;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
class DocumentJobs {
    private static final Logger log = LoggerFactory.getLogger(DocumentJobs.class);
    private final DocumentService documents;
    private final KnowledgeService knowledge;
    private final DifyKnowledgeClient dify;
    private final DifyConfiguration difyConfig;
    private final MinioStorage minio;
    private final SchemaReady schema;

    DocumentJobs(DocumentService documents, KnowledgeService knowledge, DifyKnowledgeClient dify, DifyConfiguration difyConfig,
                 MinioStorage minio, SchemaReady schema) {
        this.documents = documents;
        this.knowledge = knowledge;
        this.dify = dify;
        this.difyConfig = difyConfig;
        this.minio = minio;
        this.schema = schema;
    }

    // V1 runs one backend instance. A DB due time is enough to resume work after restart.
    @Scheduled(fixedDelayString = "${app.jobs.delay-ms}", initialDelay = 2500)
    void drain() {
        if (!schema.documents()) return;
        for (int i = 0; i < 8; i++) {
            var pending = documents.next(Instant.now());
            if (pending.isEmpty()) return;
            execute(pending.get());
        }
    }

    private void execute(DocumentService.Work job) {
        try {
            if (job.status() == DocumentService.Status.UPLOADING) {
                documents.uploaded(job.id(), minio.contains(job.key()), null);
                return;
            }
            var dataset = knowledge.jobBinding(job.kbId()).datasetId();
            switch (job.status()) {
                case QUEUED -> submit(job, dataset);
                case INDEXING -> poll(job, dataset);
                case DELETING -> remove(job, dataset);
                default -> throw new IllegalStateException("Unexpected document state in queue");
            }
        } catch (DifyFailure ex) {
            // A create timeout can mean the remote write succeeded. Retry explicitly after reconciling by name.
            documents.issue(job, ex.getMessage(), ex.transientFailure() && job.status() != DocumentService.Status.QUEUED);
        } catch (MinioStorage.Failure ex) {
            documents.issue(job, ex.getMessage(), true);
        } catch (IOException ex) {
            documents.issue(job, "读取原文件时连接中断，请重试。", false);
        } catch (RuntimeException ex) {
            log.error("Document job {} failed ({})", job.id(), ex.getClass().getSimpleName());
            documents.issue(job, "处理文档时发生错误，请重试。", true);
        }
    }

    private void submit(DocumentService.Work job, String dataset) throws IOException {
        if (!difyConfig.hasKnowledgeKey()) {
            documents.issue(job, "服务端尚未配置 Dify 知识库密钥。", false);
            return;
        }
        RemoteDocument existing = null;
        if (job.remoteId() != null) {
            try {
                existing = dify.status(dataset, job.remoteId(), null);
            } catch (DifyFailure ex) {
                if (!ex.missing()) throw ex;
            }
        }
        if (existing == null && job.remoteWriteAttempted()) {
            existing = dify.findByName(dataset, job.remoteName()).orElse(null);
        }
        if (existing != null && !existing.failed()) {
            documents.observed(job.id(), existing);
            return;
        }
        if (!documents.aboutToCreateRemote(job.id())) return;
        if (existing != null) dify.delete(dataset, existing.id());
        try (var source = minio.open(job.key())) {
            documents.observed(job.id(), dify.create(dataset, job.remoteName(), job.size(), source));
        }
    }

    private void poll(DocumentService.Work job, String dataset) {
        if (job.indexStarted() != null && job.indexStarted().plusSeconds(1800).isBefore(Instant.now())) {
            documents.issue(job, "Dify 索引等待超过 30 分钟，可在服务恢复后重试。", false);
            return;
        }
        documents.observed(job.id(), dify.status(dataset, job.remoteId(), job.batch()));
    }

    private void remove(DocumentService.Work job, String dataset) {
        // Reconcile the name even when a prior remote ID is known. A retry may have replaced that ID.
        if (job.remoteWriteAttempted()) {
            var match = dify.findByName(dataset, job.remoteName());
            if (match.isPresent()) dify.delete(dataset, match.get().id());
            else if (job.remoteId() != null) dify.delete(dataset, job.remoteId());
        }
        minio.delete(job.key());
        documents.cleaned(job);
    }
}
