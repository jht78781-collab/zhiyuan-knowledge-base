CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL,
    credential_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_username UNIQUE (username),
    INDEX ix_users_role (role, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE kb_knowledge_base (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    dify_dataset_id VARCHAR(36) NOT NULL,
    dify_app_alias VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    corpus_revision BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_kb_dataset UNIQUE (dify_dataset_id),
    CONSTRAINT uq_kb_app UNIQUE (dify_app_alias),
    INDEX ix_kb_status (status, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE kb_member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    knowledge_base_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    permission VARCHAR(16) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_kb_member UNIQUE (knowledge_base_id, user_id),
    CONSTRAINT fk_kb_member_kb FOREIGN KEY (knowledge_base_id) REFERENCES kb_knowledge_base (id),
    CONSTRAINT fk_kb_member_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    INDEX ix_kb_member_user (user_id, knowledge_base_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
