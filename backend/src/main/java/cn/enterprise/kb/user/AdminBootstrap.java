package cn.enterprise.kb.user;

import javax.sql.DataSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class AdminBootstrap implements ApplicationRunner {
    private final AccountService accounts;
    private final JdbcTemplate jdbc;
    private final String username;
    private final String password;

    AdminBootstrap(AccountService accounts, DataSource source,
                   @Value("${app.admin.username}") String username, @Value("${app.admin.password}") String password) {
        this.accounts = accounts;
        this.jdbc = new JdbcTemplate(source);
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean schemaExists = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user')", Boolean.class));
        if (!schemaExists) return; // The initial skeleton may start before any migration exists.
        if (password.isBlank()) {
            var count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class);
            if (count != null && count == 0) {
                LoggerFactory.getLogger(AdminBootstrap.class).warn("Set APP_ADMIN_PASSWORD to create the first administrator.");
            }
            return;
        }
        accounts.bootstrap(username, password);
    }
}
