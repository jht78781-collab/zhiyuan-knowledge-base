package cn.enterprise.kb.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "sys_user")
class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String username;
    String displayName;
    String passwordHash;
    @Enumerated(EnumType.STRING)
    AccountService.Role role;
    boolean enabled;
    long credentialVersion;
    Instant createdAt;

    protected Account() {}
}
