package cn.enterprise.kb.chat;

import cn.enterprise.kb.common.ApiProblem;
import cn.enterprise.kb.common.PageView;
import cn.enterprise.kb.dify.DifyChatClient.Answer;
import cn.enterprise.kb.dify.DifyChatClient.Reference;
import cn.enterprise.kb.dify.DifyConfiguration;
import cn.enterprise.kb.document.DocumentService;
import cn.enterprise.kb.knowledge.KnowledgeService;
import cn.enterprise.kb.statistics.UsageService;
import cn.enterprise.kb.user.AccountService;
import cn.enterprise.kb.user.AccountService.Identity;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatStore {
    enum Status { PENDING, ANSWERED, NO_EVIDENCE, FAILED }
    record ConversationView(long id, long knowledgeBaseId, String title, Instant updatedAt) {}
    record SourceView(long id, String name, String excerpt, boolean originalAvailable) {}
    record TurnView(long id, String question, String answer, Status status, String error, Instant at, List<SourceView> sources) {}
    record History(ConversationView conversation, PageView<TurnView> messages) {}
    record RemoteRequest(long kbId, long conversationId, long turnId, Identity user, long corpusRevision, String alias,
                         String remoteConversation, String question) {}
    record Started(RemoteRequest remote, TurnView existing) {}

    private static final String NO_EVIDENCE = "当前知识库未找到足够资料来回答这个问题。";
    private final ConversationRepository conversations;
    private final TurnRepository turns;
    private final CitationRepository citations;
    private final KnowledgeService knowledge;
    private final DocumentService documents;
    private final AccountService accounts;
    private final DifyConfiguration dify;
    private final UsageService usage;

    ChatStore(ConversationRepository conversations, TurnRepository turns, CitationRepository citations,
              KnowledgeService knowledge, DocumentService documents, AccountService accounts, DifyConfiguration dify,
              UsageService usage) {
        this.conversations = conversations;
        this.turns = turns;
        this.citations = citations;
        this.knowledge = knowledge;
        this.documents = documents;
        this.accounts = accounts;
        this.dify = dify;
        this.usage = usage;
    }

    @Transactional
    public ConversationView create(Identity user, long kbId) {
        var scope = knowledge.lockAccess(user, kbId, false, false);
        var row = new ChatConversation();
        row.ownerId = user.id();
        row.knowledgeBaseId = kbId;
        row.title = "新会话";
        row.corpusRevision = scope.corpusRevision();
        row.createdAt = Instant.now();
        row.updatedAt = row.createdAt;
        return view(conversations.saveAndFlush(row));
    }

    @Transactional(readOnly = true)
    public PageView<ConversationView> list(Identity user, long kbId, int page) {
        knowledge.read(user, kbId);
        var request = PageView.request(page, Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
        return PageView.from(conversations.findByOwnerIdAndKnowledgeBaseIdAndDeletedAtIsNull(user.id(), kbId, request),
                ChatStore::view);
    }

    @Transactional(readOnly = true)
    public History history(Identity user, long kbId, long id, int page) {
        knowledge.read(user, kbId);
        var row = owned(conversations.findById(id).orElseThrow(ChatStore::inaccessible), user, kbId);
        var request = PageView.request(page, Sort.by(Sort.Direction.DESC, "id"));
        var messages = turns.findByConversationId(id, request);
        var byTurn = sources(kbId, messages.map(m -> m.id).getContent());
        return new History(view(row), PageView.from(messages, m -> view(m, byTurn.getOrDefault(m.id, List.of()))));
    }

    @Transactional
    public void delete(Identity user, long kbId, long id) {
        knowledge.lockAccess(user, kbId, false, false);
        var row = owned(conversations.lock(id).orElseThrow(ChatStore::inaccessible), user, kbId);
        row.deletedAt = Instant.now();
    }

    @Transactional
    public Started begin(Identity user, long kbId, long conversationId, UUID clientRequestId, String question) {
        var scope = knowledge.lockAccess(user, kbId, false, false);
        var row = owned(conversations.lock(conversationId).orElseThrow(ChatStore::inaccessible), user, kbId);
        var existing = turns.findByConversationIdAndRequestId(conversationId, clientRequestId.toString());
        var normalized = question.strip();
        if (existing.isPresent()) {
            var previous = existing.get();
            if (!previous.question.equals(normalized)) throw ApiProblem.conflict("同一个请求标识不能用于不同问题。");
            var sourceViews = sources(kbId, List.of(previous.id)).getOrDefault(previous.id, List.of());
            return new Started(null, view(previous, sourceViews));
        }
        if (turns.existsByConversationIdAndStatus(conversationId, Status.PENDING)) {
            throw ApiProblem.conflict("此会话正在处理问题，请稍后再发送。");
        }
        if (!dify.hasChatAlias(scope.appAlias())) throw ApiProblem.unavailable("服务端尚未配置此知识库的 Dify 应用。");
        if (!documents.hasReadyDocuments(kbId)) throw ApiProblem.conflict("该知识库暂无索引完成的文档。");
        if (row.corpusRevision != scope.corpusRevision()) row.remoteConversationId = null;
        row.corpusRevision = scope.corpusRevision();
        if (!turns.existsByConversationId(conversationId)) row.title = title(normalized);

        var turn = new ChatTurn();
        turn.conversationId = row.id;
        turn.requestId = clientRequestId.toString();
        turn.question = normalized;
        turn.status = Status.PENDING;
        turn.createdAt = Instant.now();
        row.updatedAt = turn.createdAt;
        turns.saveAndFlush(turn);
        usage.begin(turn.id, turn.createdAt);
        return new Started(new RemoteRequest(kbId, row.id, turn.id, user, scope.corpusRevision(), scope.appAlias(),
                row.remoteConversationId, normalized), null);
    }

    @Transactional
    public TurnView complete(RemoteRequest request, Answer answer, long elapsedMs, String errorCode, String errorMessage) {
        var scope = knowledge.lockForJob(request.kbId());
        var row = conversations.lock(request.conversationId()).orElseThrow();
        var turn = turns.findById(request.turnId()).orElseThrow();
        var user = accounts.active(request.user().id())
                .filter(active -> active.credentialVersion() == request.user().credentialVersion());
        boolean visible = row.deletedAt == null && user.filter(u -> knowledge.eligible(u, scope.id())).isPresent();
        if (!visible) {
            errorCode = "ACCESS_CHANGED";
            errorMessage = "会话或访问权限已变更。";
        } else if (scope.corpusRevision() != request.corpusRevision()) {
            errorCode = "CORPUS_CHANGED";
            errorMessage = "本轮处理时知识库内容已变更，请重新提问。";
        } else if (answer != null && request.remoteConversation() != null
                && !request.remoteConversation().equals(answer.conversationId())) {
            errorCode = "DIFY_SESSION";
            errorMessage = "Dify 返回的会话标识与当前会话不一致。";
        }

        Map<String, DocumentService.Source> valid = Map.of();
        if (errorCode == null && answer != null && !answer.references().isEmpty()) {
            var ids = answer.references().stream().map(Reference::documentId).filter(Objects::nonNull).collect(Collectors.toSet());
            valid = documents.sources(scope.id(), ids);
            for (var ref : answer.references()) {
                if (!scope.datasetId().equals(ref.datasetId()) || ref.documentId() == null || !valid.containsKey(ref.documentId())) {
                    errorCode = "DIFY_SCOPE";
                    errorMessage = "来源未能验证，已隐藏该回答。请检查知识库绑定和文档状态。";
                    break;
                }
            }
            if (errorCode == null && answer.text().isBlank()) {
                errorCode = "DIFY_RESPONSE";
                errorMessage = "Dify 未返回可显示的回答。";
            }
        }

        row.updatedAt = Instant.now();
        turn.remoteMessageId = answer == null ? null : answer.messageId();
        if (errorCode != null || answer == null) {
            turn.status = Status.FAILED;
            turn.errorMessage = errorMessage == null ? "问答服务暂不可用。" : errorMessage;
            row.remoteConversationId = null;
        } else if (answer.references().isEmpty()) {
            turn.status = Status.NO_EVIDENCE;
            turn.answer = NO_EVIDENCE;
            row.remoteConversationId = null;
        } else {
            turn.status = Status.ANSWERED;
            turn.answer = answer.text();
            row.remoteConversationId = answer.conversationId();
            saveSources(turn.id, answer.references(), valid);
        }
        usage.end(turn.id, elapsedMs, answer == null ? null : answer.usage(),
                turn.status == Status.FAILED && errorCode == null ? "DIFY_RESPONSE" : errorCode);
        if (!visible) return null;
        return view(turn, sources(scope.id(), List.of(turn.id)).getOrDefault(turn.id, List.of()));
    }

    @Transactional
    public void recoverInterrupted() {
        for (var turn : turns.findByStatus(Status.PENDING)) {
            var row = conversations.lock(turn.conversationId).orElseThrow();
            row.remoteConversationId = null;
            turn.status = Status.FAILED;
            turn.errorMessage = "上次服务已中断，本轮未完成。请重新发送问题。";
            usage.end(turn.id, null, null, "INTERRUPTED");
        }
    }

    private void saveSources(long messageId, List<Reference> references, Map<String, DocumentService.Source> valid) {
        var seen = new HashSet<Reference>();
        for (var ref : references) {
            if (!seen.add(ref)) continue;
            var document = valid.get(ref.documentId());
            var item = new ChatCitation();
            item.messageId = messageId;
            item.documentId = document.id();
            item.documentName = document.name();
            item.remoteDatasetId = ref.datasetId();
            item.remoteDocumentId = ref.documentId();
            item.remoteSegmentId = ref.segmentId();
            item.excerpt = ref.content();
            citations.save(item);
        }
    }

    private Map<Long, List<SourceView>> sources(long kbId, List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        var saved = citations.findByMessageIdInOrderById(ids);
        var available = documents.sources(kbId, saved.stream().map(c -> c.remoteDocumentId).collect(Collectors.toSet()));
        return saved.stream().collect(Collectors.groupingBy(c -> c.messageId, Collectors.mapping(c -> {
            var original = available.get(c.remoteDocumentId);
            boolean live = original != null && original.id() == c.documentId;
            return new SourceView(c.documentId, c.documentName, live ? c.excerpt : null, live);
        }, Collectors.toList())));
    }

    private static ChatConversation owned(ChatConversation row, Identity user, long kbId) {
        if (row.ownerId != user.id() || row.knowledgeBaseId != kbId || row.deletedAt != null) throw inaccessible();
        return row;
    }

    private static ApiProblem inaccessible() { return ApiProblem.missing("会话不存在或无权访问。"); }
    private static ConversationView view(ChatConversation c) { return new ConversationView(c.id, c.knowledgeBaseId, c.title, c.updatedAt); }

    private static TurnView view(ChatTurn item, List<SourceView> sources) {
        return new TurnView(item.id, item.question, item.answer, item.status, item.errorMessage, item.createdAt, sources);
    }

    private static String title(String question) {
        var collapsed = question.replaceAll("\\s+", " ");
        int count = collapsed.codePointCount(0, collapsed.length());
        return count <= 42 ? collapsed : collapsed.substring(0, collapsed.offsetByCodePoints(0, 42)) + "…";
    }
}
