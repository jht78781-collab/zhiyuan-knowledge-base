package cn.enterprise.kb.security;

import cn.enterprise.kb.user.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
class AuthController {
    record Login(@NotBlank(message = "请输入账号。") @Size(max = 64) String username,
                 @NotBlank(message = "请输入密码。") @Size(max = 72) String password) {}
    record PrincipalView(long id, String username, String displayName, AccountService.Role role) {}
    record CsrfView(String headerName, String token) {}

    private final AccountService accounts;
    private final HttpSessionSecurityContextRepository sessions;
    private final HttpSessionCsrfTokenRepository csrfTokens;

    AuthController(AccountService accounts, HttpSessionSecurityContextRepository sessions,
                   HttpSessionCsrfTokenRepository csrfTokens) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.csrfTokens = csrfTokens;
    }

    @GetMapping("/csrf")
    CsrfView csrf(CsrfToken token) { return new CsrfView(token.getHeaderName(), token.getToken()); }

    @PostMapping("/login")
    PrincipalView login(@RequestBody @Valid Login input, HttpServletRequest request, HttpServletResponse response) {
        var identity = accounts.authenticate(input.username(), input.password());
        request.getSession(true);
        request.changeSessionId();
        csrfTokens.saveToken(null, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(SecurityConfiguration.authenticated(identity));
        SecurityContextHolder.setContext(context);
        sessions.saveContext(context, request, response);
        return me(identity);
    }

    @GetMapping("/me")
    PrincipalView me(@AuthenticationPrincipal AccountService.Identity user) {
        return new PrincipalView(user.id(), user.username(), user.displayName(), user.role());
    }
}
