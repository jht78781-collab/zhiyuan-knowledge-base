package cn.enterprise.kb.knowledge;

import cn.enterprise.kb.common.ApiProblem;
import cn.enterprise.kb.common.PageView;
import cn.enterprise.kb.dify.DifyConfiguration;
import cn.enterprise.kb.user.AccountService;
import cn.enterprise.kb.user.AccountService.Identity;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeService {
    public enum Status { ACTIVE, DELETING, DELETED }
    public enum Permission { VIEWER, MANAGER }
    public record Access(long id, String name, String datasetId, String appAlias, long corpusRevision, Status status) {}
    public record View(long id, String name, String description, Status status, boolean canManage, Instant createdAt,
                       String datasetId, String appAlias, boolean difyConfigured) {}
    public record Member(long userId, String username, String displayName, boolean enabled, Permission permission) {}

    private final KnowledgeRepository knowledge;
    private final MemberRepository members;
    private final AccountService users;
    private final DifyConfiguration dify;

    KnowledgeService(KnowledgeRepository knowledge, MemberRepository members, AccountService users, DifyConfiguration dify) {
        this.knowledge = knowledge;
        this.members = members;
        this.users = users;
        this.dify = dify;
    }

    @Transactional(readOnly = true)
    public PageView<View> list(Identity user, int page) {
        var request = PageView.request(page, Sort.by(Sort.Direction.DESC, "id"));
        var results = user.admin() ? knowledge.findByStatusNot(Status.DELETED, request)
                : knowledge.visible(user.id(), Status.ACTIVE, request);
        var permissions = user.admin() ? Map.<Long, KnowledgeMember>of() :
                members.findByUserIdAndKnowledgeBaseIdIn(user.id(), results.map(k -> k.id).getContent()).stream()
                        .collect(Collectors.toMap(m -> m.knowledgeBaseId, Function.identity()));
        return PageView.from(results, k -> view(k, user.admin(), permissions.containsKey(k.id)
                && permissions.get(k.id).permission == Permission.MANAGER));
    }

    @Transactional(readOnly = true)
    public View detail(Identity user, long id) {
        var kb = lookup(id);
        var permission = permission(user, id);
        if (permission == null || kb.status == Status.DELETED || kb.status == Status.DELETING && !user.admin()) {
            throw ApiProblem.missing("知识库不存在或无权访问。");
        }
        return view(kb, user.admin(), permission == Permission.MANAGER);
    }

    @Transactional
    public View create(String name, String description, String datasetId, String appAlias) {
        var dataset = datasetId.strip().toLowerCase(Locale.ROOT);
        var alias = appAlias.strip();
        if (!dataset.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")) {
            throw ApiProblem.bad("Dify Dataset ID 应为 UUID。");
        }
        if (!DifyConfiguration.validAlias(alias)) throw ApiProblem.bad("应用别名须由小写字母、数字、下划线或短横线组成。");
        var kb = new KnowledgeBase();
        kb.name = name.strip();
        kb.description = description.strip();
        kb.difyDatasetId = dataset;
        kb.difyAppAlias = alias;
        kb.status = Status.ACTIVE;
        kb.createdAt = Instant.now();
        kb.updatedAt = kb.createdAt;
        return view(knowledge.saveAndFlush(kb), true, true);
    }

    @Transactional
    public View update(Identity user, long id, String name, String description) {
        var kb = locked(id);
        check(user, kb, true, false);
        kb.name = name.strip();
        kb.description = description.strip();
        kb.updatedAt = Instant.now();
        return view(kb, user.admin(), true);
    }

    @Transactional(readOnly = true)
    public Access read(Identity user, long id) {
        var kb = lookup(id);
        check(user, kb, false, false);
        return access(kb);
    }

    @Transactional(readOnly = true)
    public boolean eligible(Identity user, long id) {
        return knowledge.findById(id).filter(k -> k.status == Status.ACTIVE && permission(user, id) != null).isPresent();
    }

    @Transactional(readOnly = true)
    public Access documents(Identity user, long id) {
        var kb = lookup(id);
        check(user, kb, false, user.admin());
        return access(kb);
    }

    // Call within the committing transaction so authorization and deletion cannot race.
    @Transactional
    public Access lockAccess(Identity user, long id, boolean manage, boolean allowDeleting) {
        var kb = locked(id);
        check(user, kb, manage, allowDeleting && user.admin());
        return access(kb);
    }

    @Transactional(readOnly = true)
    public Access jobBinding(long id) { return access(lookup(id)); }

    @Transactional
    public Access lockForJob(long id) { return access(locked(id)); }

    @Transactional
    public void markDeleting(long id) {
        var kb = locked(id);
        kb.status = Status.DELETING;
        kb.corpusRevision++;
        kb.updatedAt = Instant.now();
    }

    @Transactional
    public void finalizeDeletion(long id) {
        var kb = locked(id);
        if (kb.status == Status.DELETING) {
            kb.status = Status.DELETED;
            kb.updatedAt = Instant.now();
        }
    }

    @Transactional
    public void invalidateChatContext(long id) { locked(id).corpusRevision++; }

    @Transactional(readOnly = true)
    public PageView<Member> members(Identity user, long kbId, int page) {
        check(user, lookup(kbId), true, false);
        var rows = members.findByKnowledgeBaseId(kbId, PageView.request(page, Sort.by("id")));
        var accounts = users.findAll(rows.map(m -> m.userId).getContent());
        return PageView.from(rows, row -> member(row, accounts.get(row.userId)));
    }

    @Transactional
    public Member grant(Identity user, long kbId, String username, Permission permission) {
        check(user, locked(kbId), true, false);
        var account = users.find(username).orElseThrow(() -> ApiProblem.missing("用户不存在。"));
        if (account.role() == AccountService.Role.ADMIN) throw ApiProblem.bad("系统管理员已拥有管理权限。");
        var row = members.findByKnowledgeBaseIdAndUserId(kbId, account.id()).orElseGet(KnowledgeMember::new);
        row.knowledgeBaseId = kbId;
        row.userId = account.id();
        row.permission = permission;
        return member(members.save(row), account);
    }

    @Transactional
    public void revoke(Identity user, long kbId, long userId) {
        check(user, locked(kbId), true, false);
        members.findByKnowledgeBaseIdAndUserId(kbId, userId).ifPresent(members::delete);
    }

    private static Member member(KnowledgeMember row, AccountService.UserView user) {
        return new Member(row.userId, user.username(), user.displayName(), user.enabled(), row.permission);
    }

    private Permission permission(Identity user, long id) {
        if (user.admin()) return Permission.MANAGER;
        return members.findByKnowledgeBaseIdAndUserId(id, user.id()).map(m -> m.permission).orElse(null);
    }

    private void check(Identity user, KnowledgeBase kb, boolean manage, boolean allowDeleting) {
        var perm = permission(user, kb.id);
        if (perm == null || kb.status == Status.DELETED || kb.status == Status.DELETING && !allowDeleting
                || manage && perm != Permission.MANAGER) {
            throw ApiProblem.missing("知识库不存在或无权执行该操作。");
        }
    }

    private KnowledgeBase lookup(long id) {
        return knowledge.findById(id).orElseThrow(() -> ApiProblem.missing("知识库不存在。"));
    }

    private KnowledgeBase locked(long id) {
        return knowledge.lock(id).orElseThrow(() -> ApiProblem.missing("知识库不存在。"));
    }

    private static Access access(KnowledgeBase k) {
        return new Access(k.id, k.name, k.difyDatasetId, k.difyAppAlias, k.corpusRevision, k.status);
    }

    private View view(KnowledgeBase k, boolean admin, boolean manager) {
        return new View(k.id, k.name, k.description, k.status, admin || manager, k.createdAt,
                admin ? k.difyDatasetId : null, admin ? k.difyAppAlias : null,
                dify.hasKnowledgeKey() && dify.hasChatAlias(k.difyAppAlias));
    }
}
