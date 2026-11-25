package cn.enterprise.kb.user;

import cn.enterprise.kb.common.ApiProblem;
import cn.enterprise.kb.common.PageView;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    public enum Role { ADMIN, USER }
    public record Identity(long id, String username, String displayName, Role role, long credentialVersion) {
        public boolean admin() { return role == Role.ADMIN; }
    }
    public record UserView(long id, String username, String displayName, Role role, boolean enabled) {}

    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final String dummyHash;

    AccountService(AccountRepository accounts, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.encoder = encoder;
        dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public Identity authenticate(String username, String password) {
        var account = accounts.findByUsername(username.strip().toLowerCase(Locale.ROOT));
        var hash = account.map(a -> a.passwordHash).orElse(dummyHash);
        if (!fitsBcrypt(password) || !encoder.matches(password, hash) || account.isEmpty() || !account.get().enabled) {
            throw new ApiProblem(HttpStatus.UNAUTHORIZED, "INVALID_LOGIN", "账号或密码不正确，或账号已停用。");
        }
        return identity(account.get());
    }

    @Transactional(readOnly = true)
    public Optional<Identity> active(long id) {
        return accounts.findById(id).filter(a -> a.enabled).map(AccountService::identity);
    }

    @Transactional(readOnly = true)
    public Optional<UserView> find(String username) {
        return accounts.findByUsername(username.strip().toLowerCase(Locale.ROOT)).map(AccountService::view);
    }

    @Transactional(readOnly = true)
    public Map<Long, UserView> findAll(Collection<Long> ids) {
        return accounts.findAllById(ids).stream().map(AccountService::view)
                .collect(Collectors.toMap(UserView::id, user -> user));
    }

    @Transactional(readOnly = true)
    public PageView<UserView> list(int page) {
        return PageView.from(accounts.findAll(PageView.request(page, Sort.by("id"))), AccountService::view);
    }

    @Transactional
    public UserView create(String username, String displayName, String password, Role role) {
        String normalized = username.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_.-]{3,64}")) {
            throw ApiProblem.bad("账号须为 3–64 位字母、数字、下划线、点或短横线。");
        }
        if (displayName.isBlank() || displayName.strip().length() > 80) {
            throw ApiProblem.bad("姓名必须填写，最多 80 个字符。");
        }
        validatePassword(password);
        if (accounts.findByUsername(normalized).isPresent()) throw ApiProblem.conflict("该账号已存在。");
        var account = new Account();
        account.username = normalized;
        account.displayName = displayName.strip();
        account.passwordHash = encoder.encode(password);
        account.role = role;
        account.enabled = true;
        account.createdAt = Instant.now();
        return view(accounts.saveAndFlush(account));
    }

    @Transactional
    public void bootstrap(String username, String password) {
        if (accounts.count() == 0) create(username, "系统管理员", password, Role.ADMIN);
    }

    @Transactional
    public UserView change(long actor, long id, Role role, boolean enabled, String password) {
        var administrators = accounts.findAllByRoleOrderByIdAsc(Role.ADMIN);
        var account = accounts.lock(id).orElseThrow(() -> ApiProblem.missing("用户不存在。"));
        if (id == actor && (!enabled || role != Role.ADMIN)) {
            throw ApiProblem.conflict("不能停用当前管理账号或移除自己的管理员角色。");
        }
        if (account.role == Role.ADMIN && account.enabled && (!enabled || role != Role.ADMIN)
                && administrators.stream().filter(a -> a.enabled).count() <= 1) {
            throw ApiProblem.conflict("系统至少要保留一个启用的管理员。");
        }
        if (account.role != role || account.enabled != enabled || password != null) account.credentialVersion++;
        if (password != null) {
            validatePassword(password);
            account.passwordHash = encoder.encode(password);
        }
        account.role = role;
        account.enabled = enabled;
        return view(account);
    }

    private static void validatePassword(String password) {
        if (password == null || password.isBlank() || password.codePointCount(0, password.length()) < 12
                || !fitsBcrypt(password)) {
            throw ApiProblem.bad("密码至少 12 个字符，且最多 72 个 UTF-8 字节。");
        }
    }

    private static boolean fitsBcrypt(String password) { return password.getBytes(StandardCharsets.UTF_8).length <= 72; }

    private static Identity identity(Account a) {
        return new Identity(a.id, a.username, a.displayName, a.role, a.credentialVersion);
    }

    private static UserView view(Account a) { return new UserView(a.id, a.username, a.displayName, a.role, a.enabled); }
}
