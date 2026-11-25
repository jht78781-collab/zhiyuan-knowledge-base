# 架构与接口

浏览器通过同源接口访问一个 Spring Boot 应用。Spring Security 管理 Cookie 会话和 CSRF 防护。业务层负责知识库授权、文件生命周期、对话历史和统计。JPA 持久化业务数据，MinIO 保存原件，Dify 负责文档解析、切分、嵌入、检索与生成。

## 模块

| 模块 | 职责 |
|---|---|
| `security`、`user` | 会话、密码、用户及系统角色。 |
| `knowledge` | 知识库及成员范围，独立的 Dify Dataset 和 Chatflow 绑定。 |
| `document`、`storage` | 格式验证、私有原件、入库重试与异步清理。 |
| `dify` | 知识库和 Chatflow 的服务端 API，外部凭证通过环境配置。 |
| `chat`、`statistics` | 持久化多轮消息、引用、失败状态及用量。 |
| `frontend` | Vue 工作台，登录、管理和问答页面。 |

## 文档状态

`UPLOADING → QUEUED → INDEXING → AVAILABLE`。原文件保存失败、Dify 索引失败或超时进入 `FAILED`。知识库或文档删除后立即撤销读写，异步协调 Dify 与 MinIO 删除。清理失败进入 `DELETE_FAILED`，后台可重新排队。普通用户只看到可用文档。

数据库与外部服务使用短事务和持久化状态协调。非幂等的 Dify 上传以稳定的外部文件名核对，遇到网络结果不明时等待管理端重试。实例重启后，文档任务根据计划时间恢复。一次应用只启动一个任务执行实例。

## 问答边界

会话按用户和知识库固定，单个会话一次执行一个提问。客户端 UUID 用于请求去重。服务收到 Dify 结果后，重新核对账户、知识库及来源权限，再提交消息和统计。知识库文档被删除时，会话进入新的远程上下文；历史仍受当前授权约束，已删除文档没有下载入口和引用片段。

项目通过 Dify 控制台预先建立 Dataset 和独立发布的 Chatflow，再绑定到 Java 知识库。Java 管理自己上传的文档及记录，外部 Dataset 与 Chatflow 的创建和回收由 Dify 管理员操作。

## 接口索引

| 路径前缀 | 操作 |
|---|---|
| `/api/auth` | GET CSRF、GET 当前账号、POST 登录、POST 退出。 |
| `/api/admin/users` | 用户分页、新建、编辑角色/启用状态、重置密码。 |
| `/api/knowledge-bases` | 知识库分页、详情、基本设置、成员授权。创建及删除位于 `/api/admin/knowledge-bases`。 |
| `/api/knowledge-overview` | 可访问知识库与已索引文档计数。 |
| `/api/knowledge-bases/{kbId}/documents` | 文档分页、上传、下载、重试、清理。 |
| `/api/knowledge-bases/{kbId}/conversations` | 创建及查询会话，发送问题、查看历史和删除。 |
| `/api/admin/statistics` | 查询日期范围内的调用、耗时、用量。 |

变更操作携带 CSRF 接口返回的请求头。业务错误体包含 `code` 和 `message`。权限之外的知识库、文档及会话返回 404。
