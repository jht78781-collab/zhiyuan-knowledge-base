# 知源 · 企业智能知识库问答系统

知源是一个 Java 后端个人实战项目。获授权的用户可以上传 PDF、DOCX 和 UTF-8 TXT，向知识库提问并打开引用原文。管理后台提供成员权限、索引状态、会话历史和问答用量。

项目使用 Java 17、Spring Boot 3.5.16、Spring Security、Spring Data JPA、Flyway、MariaDB 10.5、MinIO 和 Dify，Vue 3 前端与后端一起打包。

| 业务能力 | 实现方式 |
|---|---|
| 文档入库 | 私有 MinIO 保存原件，数据库保存索引状态和重试计划。 |
| 权限隔离 | 后端按用户和知识库校验文档、会话、来源及下载。 |
| 问答与引用 | 每个知识库绑定独立的 Dify Chatflow，Java 核对引用后关联本地文档。 |
| 用量统计 | 记录问答次数、响应时间和外部服务实际返回的 Token。 |

**验证状态**：MariaDB 10.5.29 和 MinIO 已通过本机联调。三种文件上传、原件下载和权限隔离已验证。Dify 接口使用模拟服务验证了入库状态、追问和引用，真实检索及模型输出待接入实际实例验收。

浏览演示可从[样例文件与三分钟流程](demo/演示与验收.md)开始。需要提供能登录、上传并实问的网页时，使用[完整系统部署说明](docs/deploy.md)。

## 1. 本地运行

1. 安装 Java 17、Maven 3.6.3+ 和 Node.js 20.19+ 或 22.12+。如果已取得打包好的 JAR，启动只需要 Java 17。
2. 从项目根目录复制 `.env.example` 为 `.env`，填写 `DB_PASSWORD`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY` 和首次启动用的 `APP_ADMIN_PASSWORD`。初始账号默认是 `admin`，密码至少 12 个字符。配置文件中的值采用不带外层引号的形式。
3. 如果使用 Docker，在项目根目录执行 `docker compose up -d`。通过 [MinIO 本地控制台](http://127.0.0.1:9001) 创建与 `MINIO_BUCKET` 相同名称的私有桶，默认 `enterprise-kb`。也可以使用已有 MariaDB 10.5 和 MinIO，填写对应的连接配置，并在 MariaDB 中准备单独的、采用 `utf8mb4` 的空数据库。默认数据库端口为 3307。
4. 在 PowerShell 中执行 `& .\run.ps1`。脚本会在缺少 JAR 时构建应用。代码变更后可运行 `& .\run.ps1 -Rebuild`。应用默认监听 [127.0.0.1:8080](http://127.0.0.1:8080/)。
5. 使用配置的管理员账号登录。首个账号创建后，可清空 `APP_ADMIN_PASSWORD` 并重启，已创建的账号会保留。

通过命令构建和运行时，在项目根目录执行：

```sh
npm ci --prefix frontend
npm run build --prefix frontend
mvn -f backend/pom.xml clean package
java -jar backend/target/enterprise-kb-1.0.0.jar
```

Windows 建议使用 `run.ps1`，脚本处理了本机 Java 17 回环套接字兼容问题。开发前端时可在 `frontend` 执行 `npm run dev`，API 请求会代理到 `APP_PORT`。

## 2. 接入 Dify

1. 在 Dify 配置可用的模型和向量化服务。为每个业务知识库准备一个独立的 **经典知识库（Dataset）**，以及一个只检索该 Dataset 的 **Chatflow**。Dataset 的原始文件由本项目上传。
2. 在 Chatflow 中接入知识检索和回答节点，让模型使用检索片段作为上下文并开启引用与归属。无命中时让流程返回资料不足。LLM 节点开启会话记忆，追问可先做问题改写，再交给知识检索。发布应用。
3. 在 `.env` 配置 `DIFY_BASE_URL`（包含 `/v1`）、`DIFY_KNOWLEDGE_KEY` 和 `DIFY_CHAT_KEYS`。最后一项是别名到应用 API 密钥的 JSON 映射。不同知识库使用不同的 Chatflow 与应用密钥。重启后生效。
4. 在工作台点击 **新建知识库**，填入 Dify Dataset ID 和应用别名。打开知识库后上传文档，等索引状态为 **可用**，再开始问答。通过 **成员权限** 授权普通账号。

Dify 的 Knowledge Service API 负责上传、索引状态和删除。Java 通过对话 API 把用户标识和会话 ID 传回对应 Chatflow。若引用不属于当前 Dataset、找不到对应的本地可用文档，或返回的会话标识异常，Java 会拦截该回答。若来源列表为空，界面会显示资料不足。部署后应按 [验收步骤](docs/acceptance.md) 确认真实 Chatflow 的引用和用量字段。

官方文档： [Dify Service API](https://docs.dify.ai/en/api-reference/guides/get-started) · [知识检索节点](https://docs.dify.ai/en/cloud/use-dify/nodes/knowledge-retrieval)。

## 3. 数据库与运行方式

- Flyway 在启动时按 V1、V2、V3 建立业务表。骨架检查可将 `APP_MIGRATIONS` 指向独立的空迁移目录。已有表但没有 Flyway 历史的库会拒绝启动迁移。
- `baseline-on-migrate` 为 `false`。表结构由迁移脚本维护，JPA 的 `ddl-auto` 为 `none`。应用连接建立后执行 `SET NAMES utf8mb4`。
- 应用使用服务器端会话，第一版按单后端实例运行。停用账号、变更密码或角色后，旧会话在再次请求时失效。使用 HTTPS 部署时设置 `APP_SECURE_COOKIE=true`。
- 上传大小默认单文件 15 MB。存储桶保持私有，原件下载经 Java 权限校验。真实环境可给应用创建仅访问专用桶的 MinIO 账号。
- Vue 导航按权限显示入口。后端在访问知识库、问答历史及来源时，始终校验当前权限。
- 单次耗时由 Java 测量，包含从发出 Dify 请求到接收完整应答或失败。Tokens 来自 Dify 的实际用量或 Chatflow 返回的总量，缺失项保持为空。统计的日期范围默认采用亚洲上海时区。

进一步说明在 [需求规格](docs/requirements.md)、[架构与接口](docs/architecture.md)、[数据库](docs/database.md) 和 [验收步骤](docs/acceptance.md)。
