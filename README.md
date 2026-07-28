# 智询

> 让公司信息，有据可问。

“智询”是面向公司内部员工的信息问答应用，计划以钉钉企业内部网页应用为主入口。当前版本是供领导评估产品形态的 `MVP 0.1`，使用完全虚构的目录、答案和引用，不连接真实钉钉、公司资料或模型。

## 当前可以体验什么

- PC 端“资料导航 + 问答”双栏工作台。
- 移动端资料抽屉和完整问答布局。
- 三类虚构示例问题、来源引用和后续问题。
- 未命中资料时明确返回“资料不足”，不编造公司规则。
- Spring API 建立服务端 Session 后才允许读取演示资料和问答。
- 本地默认使用显式 `demo` 身份；钉钉模式通过一次性免登码建立会话。
- 问题最大 1000 字，前后端均有校验。

## 本地启动

推荐使用 Docker Compose，使本地运行结构与服务器保持一致：

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose ps
```

打开 `http://127.0.0.1:18080`。页面和 `/api` 都经过同一个 Nginx 入口，因此 Session 和 CSRF 不需要跨域配置。查看日志或停止服务：

```powershell
docker compose logs -f --tail 100
docker compose down
```

`.env` 只用于本机或服务器，不能提交。默认只绑定 `127.0.0.1`；如需供局域网临时访问，可显式设置 `APP_BIND_ADDRESS=0.0.0.0`，但钉钉正式入口必须使用 HTTPS。

不使用 Docker 时仍可分别启动：

后端：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

前端：

```powershell
cd frontend
npm ci
npm run dev
```

打开 `http://127.0.0.1:5173`。Vite 会把 `/api` 代理到 `http://127.0.0.1:8088`。

## 服务器部署基线

服务器使用同一份 `compose.yaml` 和镜像构建方式。建议由服务器已有的 Nginx、Caddy 或其他入口网关终止 HTTPS，再反向代理到 `127.0.0.1:18080`，不要把 Spring 的 `8088` 端口暴露到公网。

1. 在服务器安装 Docker Engine 和 Compose Plugin。
2. 将代码或已发布镜像部署到服务器，并从 `.env.example` 创建仅服务器可读的 `.env`。
3. 设置 `APP_AUTH_MODE=dingtalk`、`APP_SESSION_COOKIE_SECURE=true`、钉钉凭据和 `DINGTALK_ALLOWED_USER_IDS`；凭据与真实用户 ID 由部署环境注入，不写入镜像。
4. 执行 `docker compose up --build -d`，确认两个健康检查均为 `healthy`。
5. 配置 HTTPS 域名反向代理到 `127.0.0.1:18080`，入口代理应覆盖并传递 `X-Forwarded-Proto=https`；再将该域名填写为钉钉 PC/移动端首页。

当前 Compose 不包含业务持久卷，因为 MVP 仍使用内存虚构数据。后续引入 PostgreSQL、Redis、MinIO 时，应分别增加持久卷、备份和恢复验证，不能把数据写入应用容器层。

Compose 使用隔离的应用内网连接前后端；前端另接入口网络用于发布宿主机端口，后端另接出站网络用于调用钉钉 OpenAPI。只有前端容器入口映射到宿主机。默认内存上限为后端 `768m`、前端 `128m`，可通过 `.env` 调整。

与 Weekly Report 共用服务器时的隔离、HTTPS、私有知识传输、验收和回退步骤见 `docs/SERVER_DEPLOYMENT.md`。

## 统一验证

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify.ps1
```

## 真实语义检索试运行

系统已提供默认关闭的真实 Embedding 与受限知识导入切片。批准的知识导出放在未跟踪的 `data/knowledge/documents.jsonl`，通过 `.env` 显式启用；未启用时继续使用完全虚构的演示问答。配置格式、ACL 和 payload 预算见 `docs/KNOWLEDGE_IMPORT.md`。

## 敏捷路线

| 迭代 | 可观察结果 | 当前状态 |
|---|---|---|
| MVP 0.1 | 领导可操作的双栏产品切片 | 已完成 |
| Sprint 1 | 钉钉工作台端内免登，稳定获得测试用户身份 | 代码完成，待开发者后台配置与真机联调 |
| Sprint 2 | 上传少量批准的测试文档，完成真实解析、检索和引用 | 待开始 |
| Sprint 3 | 部门/人员 ACL、删除失效和试点审计 | 待开始 |
| Sprint 4 | 钉钉内小范围试用，按评测与反馈优化 | 待开始 |

钉钉工作台发布暂因管理员权限延期，恢复条件和操作清单见 `docs/tasks/2026-07-19-dingtalk-workbench-access-todo.md`。

每个 Sprint 都应交付一个可演示、可验证的纵向切片，不提前建设尚未被验证的复杂基础设施。

## 认证模式

本地演示保持默认配置即可：

```powershell
$env:APP_AUTH_MODE="demo"
```

钉钉测试环境需要由部署平台注入，不要写入仓库：

```powershell
$env:APP_AUTH_MODE="dingtalk"
$env:DINGTALK_CORP_ID="<测试组织 CorpId>"
$env:DINGTALK_ALLOWED_USER_IDS="<测试用户一 userId>,<测试用户二 userId>"
$env:DINGTALK_CLIENT_ID="<应用 Client ID>"
$env:DINGTALK_CLIENT_SECRET="<应用 Client Secret>"
$env:APP_SESSION_COOKIE_SECURE="true"
```

应用 PC/移动端首页建议配置为 `https://<受控域名>/?corpid=$CORPID$`。钉钉模式缺少组织、稳定用户白名单或应用凭据时会失败关闭，不会自动回退到演示身份。工作台可见范围仍应只包含相同测试人员，但不能替代服务端白名单。
