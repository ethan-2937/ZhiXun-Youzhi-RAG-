# 智询服务器部署

本文用于把智询以独立 Docker Compose 项目部署到已运行 Weekly Report 的 Ubuntu 服务器。智询不复用 Weekly Report 的容器、网络、数据库或配置文件。

## 目标拓扑

```text
钉钉工作台
  -> https://<智询测试域名>
  -> 宿主机 Nginx/Caddy
  -> 127.0.0.1:18080
  -> zhixun-frontend
  -> zhixun-backend:8088
  -> 钉钉 OpenAPI / 公司模型服务
```

服务器只发布前端 Nginx 的回环地址；Spring 端口不映射到宿主机。Weekly Report 继续使用自己的 `22080/22081` 端口。

## 部署前检查

```bash
free -h
df -h
docker stats --no-stream
docker ps --format 'table {{.Names}}\t{{.Ports}}\t{{.Status}}'
ss -lntp
```

确认以下条件：

- `18080` 未被占用，Weekly Report 容器健康。
- 至少保留约 2 GB 可用内存和 5 GB 可用磁盘用于 MVP 构建与镜像。
- 服务器可以访问钉钉 OpenAPI 和已批准的公司模型地址。
- 已准备独立 HTTPS 测试域名和证书。
- 已取得智询应用的 CorpId、Client ID、Client Secret，以及两位试用者的稳定钉钉 `userId`。

## 目录与私有配置

推荐部署到独立目录：

```bash
cd /data2/person_path/yzzhang
git clone <Codeup 智询仓库地址> zhixun-rag
cd zhixun-rag
cp .env.example .env
```

编辑服务器 `.env`，至少覆盖以下值：

```dotenv
APP_AUTH_MODE=dingtalk
APP_SESSION_COOKIE_SECURE=true
APP_BIND_ADDRESS=127.0.0.1
APP_HTTP_PORT=18080
IMAGE_TAG=<本次 Git 提交短哈希>

DINGTALK_CORP_ID=<企业 CorpId>
DINGTALK_ALLOWED_USER_IDS=<测试用户一 userId>,<测试用户二 userId>
DINGTALK_CLIENT_ID=<应用 Client ID>
DINGTALK_CLIENT_SECRET=<应用 Client Secret>

RAG_ENABLED=true
AGENTIC_RAG_ENABLED=true
EMBEDDING_BASE_URL=<已批准的公司模型网关 /v1>
EMBEDDING_API_KEY=<服务器私有值>
EMBEDDING_MODEL=text-embedding-v4
CHAT_MODEL=<已确认可用于对话的模型名称>
RAG_DIAGNOSTICS_ENABLED=false
```

`CHAT_BASE_URL` 和 `CHAT_API_KEY` 未设置时沿用 Embedding 网关与凭据。真实值只保存在服务器 `.env`；测试用户必须使用稳定 `userId`，不能填写姓名。

知识文件从批准来源单独传入服务器的 `data/knowledge/`，不要通过 Git、构建上下文或普通日志传输。Compose 以只读方式挂载该目录。

## 构建和启动

```bash
docker compose config --quiet
docker compose up --build -d
docker compose ps
curl --fail --silent http://127.0.0.1:18080/api/health
```

两个容器必须为 `healthy`。查看启动错误时限制日志量：

```bash
docker compose logs --tail=100 backend
docker compose logs --tail=100 frontend
```

不要把完整环境、免登请求、模型请求或知识正文打印到终端记录。

## HTTPS 反向代理

为智询创建独立子域名，不建议挂在 Weekly Report 的路径前缀下。Nginx 示例：

```nginx
server {
    listen 443 ssl http2;
    server_name <智询测试域名>;

    ssl_certificate <证书路径>;
    ssl_certificate_key <证书私钥路径>;

    location / {
        proxy_pass http://127.0.0.1:18080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

加载配置前先执行 `nginx -t`。钉钉 PC 和移动端首页均填写：

```text
https://<智询测试域名>/?corpid=$CORPID$
```

应用首页、端内免登地址与钉钉安全域名使用同一个受控域名。工作台可见范围只选择两位试用者；服务端 `DINGTALK_ALLOWED_USER_IDS` 仍必须同时生效。

## 上线验收

1. 直接访问 HTTPS 首页，普通浏览器不能获得演示身份。
2. 两位白名单用户分别从 PC、Android 或 iOS 钉钉工作台进入并完成免登。
3. 名单外测试账号或直达 URL 返回稳定拒绝，不建立 Session。
4. 重复提交同一免登码被拒绝；退出后受保护 API 返回 `401`。
5. 提问只返回授权来源；资料不足时拒答；来源预览只能读取允许目录和大小范围内的原文件。
6. `docker stats --no-stream` 无持续内存增长，Weekly Report 健康和端口保持不变。

## 更新与回退

更新前记录当前提交和镜像标签，先运行仓库统一验证，再拉取已发布提交：

```bash
git rev-parse --short HEAD
git pull --ff-only codeup main
docker compose up --build -d
curl --fail --silent http://127.0.0.1:18080/api/health
```

若新版本健康检查失败，切回已记录的上一已验证提交并重新构建。不要强推、重写服务器仓库历史或删除知识目录；远端结果不确定时先检查容器状态和有限日志，再决定是否重试。
