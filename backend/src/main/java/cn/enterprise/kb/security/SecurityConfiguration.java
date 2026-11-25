package cn.enterprise.kb.security;

import cn.enterprise.kb.user.AccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
class SecurityConfiguration {
    @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(12); }
    @Bean HttpSessionSecurityContextRepository sessions() { return new HttpSessionSecurityContextRepository(); }
    @Bean HttpSessionCsrfTokenRepository csrfTokens() { return new HttpSessionCsrfTokenRepository(); }

    @Bean
    SecurityFilterChain api(HttpSecurity http, AccountService accounts, HttpSessionSecurityContextRepository sessions,
                            HttpSessionCsrfTokenRepository csrfTokens) throws Exception {
        return http
                .securityContext(config -> config.securityContextRepository(sessions))
                .requestCache(cache -> cache.disable())
                .csrf(config -> config.csrfTokenRepository(csrfTokens))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/csrf").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .logout(config -> config.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
                .exceptionHandling(config -> config
                        .authenticationEntryPoint((req, res, err) -> json(res, 401, "LOGIN_REQUIRED", "请先登录。"))
                        .accessDeniedHandler((req, res, err) -> {
                            boolean csrf = err instanceof org.springframework.security.web.csrf.CsrfException;
                            json(res, 403, csrf ? "INVALID_CSRF" : "FORBIDDEN",
                                    csrf ? "请求验证已过期，请重试。" : "没有执行该操作的权限。");
                        }))
                .headers(config -> config
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                                        + "object-src 'none'; base-uri 'self'; frame-ancestors 'none'"))
                        .referrerPolicy(policy -> policy.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .addFilterBefore(new CurrentAccountFilter(accounts), AuthorizationFilter.class)
                .build();
    }

    static UsernamePasswordAuthenticationToken authenticated(AccountService.Identity account) {
        return UsernamePasswordAuthenticationToken.authenticated(account, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.role().name())));
    }

    private static void json(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    private static class CurrentAccountFilter extends OncePerRequestFilter {
        private final AccountService accounts;
        CurrentAccountFilter(AccountService accounts) { this.accounts = accounts; }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String path = request.getServletPath();
            return !path.startsWith("/api/") || path.equals("/api/auth/login") || path.equals("/api/auth/csrf");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AccountService.Identity previous) {
                var latest = accounts.active(previous.id())
                        .filter(account -> account.credentialVersion() == previous.credentialVersion());
                if (latest.isEmpty()) {
                    SecurityContextHolder.clearContext();
                    if (req.getSession(false) != null) req.getSession(false).invalidate();
                    json(res, 401, "LOGIN_REQUIRED", "登录已失效，请重新登录。");
                    return;
                }
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authenticated(latest.get()));
                SecurityContextHolder.setContext(context);
            }
            chain.doFilter(req, res);
        }
    }
}
