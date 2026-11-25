# 部署可登录的在线知识库

这套配置在一台支持 Docker Compose 的 Linux 服务器上运行知源、MariaDB、MinIO 和 HTTPS 入口。Dify 可使用 Cloud 或已有的自托管实例。项目的 GitHub 仓库用于展示源码，用户通过独立的 HTTPS 地址登录并使用知识库。

## 服务器和域名

用于少量面试演示和试用时，可从香港地区的 2 核 4 GB Linux 轻量服务器开始，磁盘按预计文档量选取。安装 Docker Engine 和 Compose，准备一个解析到服务器的域名，允许访问 80 和 443 端口。Caddy 负责申请 HTTPS 证书。数据库、对象存储和 Java 应用只在容器网络中开放。

## 配置真实 Dify

根据 [Dify 接入说明](../README.md#2-接入-dify) 准备知识库和 Chatflow，启用引用并配置模型。每个有独立资料权限的知识库使用不同的 Dataset 和 Chatflow。

在仓库根目录执行以下命令，打开服务器上的 `.env.deploy`，填写强密码和 Dify 配置。不要把这个文件提交到 GitHub。

```sh
umask 077
cp .env.deploy.example .env.deploy
```

- 将 `PUBLIC_DOMAIN` 设置为实际域名，例如 `kb.example.com`。
- 首次启动填入 `APP_ADMIN_PASSWORD`。MinIO 的初始化账号与应用账号使用不同名称和密码。初始化服务会建立私有桶，并授予应用账号访问此桶的权限。
- 填入 `DIFY_KNOWLEDGE_KEY`。将 `DIFY_CHAT_KEYS` 设置为应用别名到 API 密钥的 JSON 映射，例如 `{"finance":"你的应用密钥"}`。自托管 Dify 的 `DIFY_BASE_URL` 保留 `/v1`。

```sh
docker compose --env-file .env.deploy -f deploy/compose.yml up -d --build
docker compose --env-file .env.deploy -f deploy/compose.yml ps -a
```

等待应用启动后打开 `https://你的域名/`。首个管理员创建成功后，可以从文件里清空初始化密码并重新启动应用。数据库和文件保存在具名卷中。更换服务器前同时备份数据库和 MinIO 数据。

## 给试用者使用

1. 管理员创建普通用户，并为该用户准备一个独立知识库。设置对应的 Dify Dataset ID 和应用别名，授予该用户这个知识库的「管理知识库」权限。普通用户通过独立账号登录后，可以在授权库上传自己的 PDF、DOCX 或 TXT。
2. 试用者上传时，原件保存在当前服务器的私有桶，并会提交到配置的 Dify 服务解析及检索。面试演示可以先用 [虚构样例](../demo/演示与验收.md)。
3. 上线验收用两个不同账号。检查各自能上传、等待索引、提问并点开引用，同时无法访问对方的文档下载地址或对话历史。按实际情况检验模型用量和响应时间。

公网部署完成后，把这个 HTTPS 入口与 GitHub 代码链接分别分享给对方。实际模型解析、引用与权限验收通过后再分发试用账号。
