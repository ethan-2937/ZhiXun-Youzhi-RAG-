# Task: 完成钉钉端内免登 Sprint 1 纵向切片

## 问题

MVP 0.1 已能演示双栏问答，但 API 尚未认证。Sprint 1 需要建立钉钉端内 `requestAuthCode -> 服务端换 userId/unionId -> HttpSession` 链路，并保留不访问真实钉钉的本地演示模式。

## 范围

- 包含：Spring Security、Session、CSRF、认证配置、演示登录、钉钉 OpenAPI 适配器、corpId 校验、免登码防重放、当前用户接口、Vue 启动认证流程和失败界面。
- 不包含：通讯录同步、正式用户表、部门 ACL、生产凭据校验、SSO 网页备用入口、真实钉钉联调。

## 验收标准

- [x] 未认证请求不能访问演示资料和问答 API。
- [x] 本地 `demo` 模式只建立虚构测试身份。
- [x] `dingtalk` 模式只接受配置组织的免登码，并使用稳定 ID 建立 Session。
- [x] 同一个免登码不能在本服务中重放。
- [x] Session Cookie、CSRF、退出和安全错误响应有测试。
- [x] 前端在钉钉环境请求免登码，普通浏览器按配置进入演示或显示明确阻塞。

## 约束

- 相关产品不变量：稳定 ID 授权、不按姓名授权、身份不确定时失败关闭。
- 相关架构边界：Controller 只依赖 AuthService 接口；钉钉 HTTP 只存在于 adapter。
- 需要保留的无关工作区修改：MVP 0.1 界面、Harness 和虚构 RAG 评测。

## 验证

- 单元/契约测试：15 个 Spring 测试、7 个 Vitest、10 个 Harness 测试通过；钉钉 HTTP 使用 MockRestServiceServer，不访问网络。
- RAG 离线评测：3 个固定用例通过，禁止来源和禁止术语泄漏率为 0。
- 手工/集成证据：本地浏览器完成配置 -> CSRF -> 演示 Session -> 受保护 workspace -> 带 CSRF 问答；引用出现且控制台 0 错误。
- 统一命令：`powershell -ExecutionPolicy Bypass -File scripts/verify.ps1`

## 交付

- 决策：服务端 Session，不在 URL 或 localStorage 保存长期 Token；CSRF Token 绑定 Session 并只存前端内存；本地演示认证与钉钉认证显式分模式。
- 数据读取/写入与日志：不记录免登码、Token、完整钉钉响应或真实身份；本次只使用虚构测试 ID 和本地服务。
- 剩余风险：真实开发者后台尚未配置，未做钉钉 PC/Android/iOS 真机联调；免登码重放记录当前只在单实例内存，多实例需迁移 Redis；尚未建立正式用户/部门状态检查。
- 延后工作：真实钉钉联调、用户/部门落库、停用/离职处理和 ACL。
