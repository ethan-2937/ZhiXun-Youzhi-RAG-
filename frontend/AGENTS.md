# Vue Frontend Guide

## 边界

- `src/api/`：请求、错误处理和响应协议。
- `src/components/`：可复用展示组件，不复制 API 调用。
- `src/data/`：仅允许完全虚构的演示回退数据。
- `App.vue`：应用壳和当前 MVP 组合；功能增长时及时拆分。

## 规则

- 页面必须明确区分演示能力与已接生产能力。
- 前端隐藏不构成权限控制；未来 ACL 结果只消费后端授权响应。
- 不在 UI、URL、存储、测试快照或错误中显示 Token、员工数据或内部配置。
- 保持 PC 双栏和移动端抽屉可用；颜色不能替代文字状态。
- API 或认证变化必须使用 mock fetch 增加 Vitest，不访问真实网络。

## 命令

```powershell
npm ci
npm run lint
npm run typecheck
npm test
npm run build
```
