# Task: 问题输入区 Tailwind 视觉与交互试点

## 问题

当前输入区具备基本发送能力，但层级偏弱：键盘换行提示不完整、忙碌状态仍显示“发送”、错误与输入控件关联不足，长问题只能在固定高度内滚动。主问答区已完成 Tailwind 试点，本轮在不改变 API 和 payload 的前提下统一输入体验。

## 范围

- 包含：`QuestionComposer` 布局、输入自动增高、字符预算、正常/聚焦/禁用/忙碌/错误状态、键盘提示、组件测试和多尺寸视觉验收。
- 不包含：问答 API、会话、流式响应、认证、ACL、资料树、消息区、全局 CSS 迁移或新依赖。

## UI Contract

- 用户与任务：员工在阅读资料或回答时快速输入问题，明确知道如何发送、换行以及当前是否可继续提交。
- 层级：问题输入 -> 键盘提示和字符预算 -> 发送动作；错误紧邻输入区并通过 `aria-describedby` 关联。
- 布局：桌面与消息列对齐至 900px；移动端保留完整输入宽度和至少 44px 的发送触点；长问题自动增高至固定上限，之后内部滚动。
- 视觉语言：延续墨色、钴蓝、暖灰和语义红；以清晰的底部动作行作为锚点，不增加装饰卡片或渐变光效。
- 状态：空输入禁用、有效输入可发送、busy 显示“整理中”、错误带文字与图标、800 字后字符预算进入提醒状态。
- 无障碍：保留原生 textarea/form/button；Enter 发送，Shift+Enter 换行；按钮和输入具有可见焦点；状态不只依赖颜色。
- 验收：375x812、768x900、1440x900 无横向溢出；移动端软键盘前的工作面仍清晰；长问题、错误和 busy 状态不遮挡内容。

## Motion Contract

- 聚焦：输入框边界和阴影使用 160ms 过渡，不改变布局尺寸。
- 发送：可用按钮 hover 时箭头位移 1px，active 恢复；不使用弹跳。
- 错误：首次出现使用 180ms opacity + translateY；错误文本始终直接可读。
- 减少动态：`prefers-reduced-motion: reduce` 下专用动画和过渡均禁用。

## Tailwind Contract

- 沿用 Tailwind 4.3.3、`tw:` 前缀和无 Preflight，新增扫描仅包含 `QuestionComposer.vue`。
- 复用现有 CSS 变量；复杂聚焦、状态和动画保留为 `rag-composer-*` 专用类。
- 删除只属于旧 Composer 的选择器和移动覆盖，不迁移同文件中的应用壳与抽屉规则。

## 验收标准

- [x] 空输入与 busy 不提交，有效输入 trim 后提交并清空。
- [x] Enter 发送、Shift+Enter 换行，自动增高不超过固定上限。
- [x] 错误、忙碌、字符预算、禁用和焦点状态语义清楚。
- [x] 不改变 API、认证、ACL、请求体或 1000 字 payload 上限。
- [x] lint、typecheck、test、build、统一 Harness 和多尺寸浏览器验收通过。

## 约束

- 相关产品不变量：回答仍以授权来源为准，输入组件不承担权限或答案判断。
- 相关架构边界：组件只发出问题文本，不直接调用 API 或记录问题内容。
- 需要保留的无关工作区修改：任务开始时工作区干净。

## 验证

- 单元/契约测试：Vue 5 个测试文件、19 个测试通过，其中新增 4 个 `QuestionComposer` 测试；Spring 52 个测试、Harness 28 个测试通过。
- RAG 离线评测：3 个问答案例与 4 个检索案例均通过，禁止文档/词泄漏率为 0。
- 手工/集成证据：Docker 地址 `http://127.0.0.1:18080/` 在 375x812、768x900、1440x900 下无横向溢出；正常、disabled、busy、error、820 字输入和 Shift+Enter 换行均已检查。
- 无障碍/动效证据：输入区通过原生 form/textarea/button 工作；错误使用 `role=alert`、`aria-invalid` 和 `aria-describedby`；移动发送触点 44x44px；减少动态时专用过渡为 0.01ms。
- 控制台与运行时：无浏览器 console error/warning；Docker 前后端健康接口返回 `UP / AGENTIC_RAG`。
- 独立前端门禁：`npm run lint`、`npm run typecheck`、`npm test`、`npm run build` 全部通过；构建 CSS 41.62 kB（gzip 9.01 kB）。
- 统一命令：`powershell -ExecutionPolicy Bypass -File scripts/verify.ps1` 全部 9 步通过。

## 交付

- 决策：将输入区改为与消息列对齐的命令栏；桌面展示键盘提示、移动端聚焦输入与 44px 发送动作；长输入自动增高至 128px；busy 保留草稿但阻止重复提交；Tailwind 继续采用前缀和无 Preflight 隔离。
- 数据读取/写入与日志：不增加数据读取、写入或日志。
- 剩余风险：自动增高依赖浏览器 `scrollHeight`，当前通过 Chromium 和组件测试验证；视觉验收仍为人工检查，尚无截图回归基线。
- 延后工作：流式中断、问题草稿持久化和自动截图回归。
