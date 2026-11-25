# 数据库与迁移

数据库版本为 MariaDB 10.5。表采用 InnoDB 和 `utf8mb4_unicode_ci`，应用连接建立后设置 `utf8mb4`，时间字段按 UTC 保存。项目只使用 Spring Data JPA 作为业务持久层。

| 文件 | 表与关键约束 |
|---|---|
| `V1__users_and_knowledge_bases.sql` | `sys_user` 的账号唯一，系统角色存于账号。 `kb_knowledge_base` 的 Dataset 和应用别名分别唯一。 `kb_member` 的知识库和用户组合唯一。 |
| `V2__document_ingestion.sql` | `kb_document` 保存对象键、外部文档 ID、状态和任务续跑字段。对象键和外部上传名唯一，按知识库和状态查询，并按下一次计划时间拉取任务。 |
| `V3__chat_and_usage.sql` | `chat_conversation` 绑定用户和知识库。 `chat_message` 按会话与请求 ID 去重。 `chat_message_source` 绑定消息与本地文档。 `ai_call_log` 与问答消息一对一，保存耗时及可空 Token。 |

通过迁移新增表和字段，已经应用的迁移文件保持原样。Flyway 从空数据库开始建表。可用新建数据库和一个空迁移目录检查骨架启动，默认迁移目录仍包括正式的业务迁移。不启用自动 baseline。
