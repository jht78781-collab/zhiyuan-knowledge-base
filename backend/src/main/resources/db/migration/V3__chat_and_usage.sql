CREATE TABLE chat_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    title VARCHAR(140) NOT NULL,
    remote_conversation_id VARCHAR(80),
    corpus_revision BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_owner FOREIGN KEY (owner_id) REFERENCES sys_user (id),
    CONSTRAINT fk_chat_kb FOREIGN KEY (knowledge_base_id) REFERENCES kb_knowledge_base (id),
    INDEX ix_chat_owner_kb (owner_id, knowledge_base_id, deleted_at, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    question VARCHAR(2000) NOT NULL,
    answer LONGTEXT,
    status VARCHAR(16) NOT NULL,
    remote_message_id VARCHAR(80),
    error_message VARCHAR(240),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_turn_request UNIQUE (conversation_id, request_id),
    CONSTRAINT fk_turn_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversation (id),
    INDEX ix_turn_pending (conversation_id, status, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE chat_message_source (
    id BIGINT NOT NULL AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    document_name VARCHAR(240) NOT NULL,
    remote_dataset_id VARCHAR(36) NOT NULL,
    remote_document_id VARCHAR(80) NOT NULL,
    remote_segment_id VARCHAR(80),
    excerpt TEXT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_source_message FOREIGN KEY (message_id) REFERENCES chat_message (id),
    CONSTRAINT fk_source_document FOREIGN KEY (document_id) REFERENCES kb_document (id),
    INDEX ix_source_message (message_id, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE ai_call_log (
    message_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    latency_ms BIGINT,
    input_tokens BIGINT,
    output_tokens BIGINT,
    total_tokens BIGINT,
    error_code VARCHAR(32),
    called_at DATETIME(6) NOT NULL,
    PRIMARY KEY (message_id),
    CONSTRAINT fk_call_message FOREIGN KEY (message_id) REFERENCES chat_message (id),
    INDEX ix_call_time (called_at, status)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
