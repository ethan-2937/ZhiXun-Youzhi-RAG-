# Task: 主问答区 Tailwind 视觉试点

## 问题

当前空对话页采用居中大标题和三张等权卡片，桌面端首屏留白偏大，长示例问题不便快速扫读；进入对话后欢迎区仍保留在滚动内容中，降低消息和引用的优先级。上一轮已验证 Tailwind v4 可通过前缀和无 Preflight 方式安全接入，本轮继续在 `ConversationView` 内试点。

## 范围

- 包含：空状态提问启动台、消息/回答/引用/资料不足/思考中状态、ConversationView 对应 Tailwind 迁移、组件测试和多尺寸视觉验收。
- 不包含：资料树、顶部栏、QuestionComposer、文件预览、API、认证、ACL、RAG 输出协议和全量 CSS 迁移。

## UI Contract

- 用户与任务：员工进入有权资料空间后快速发起问题，并在回答后优先检查依据和引用。
- 空状态层级：用途与授权范围 -> 当前检索模式 -> 可直接发起的示例问题；不展示营销式功能介绍。
- 消息状态层级：用户问题 -> 回答结论与依据状态 -> 正文 -> 授权引用 -> 后续问题。
- 布局：1440px 使用不对称启动台，左侧说明、右侧连续问题列表；768px 和 375px 收敛为单列。进入对话后启动台退出，消息流使用稳定的窄阅读列。
- 组件边界：只修改 `ConversationView.vue` 及其专用样式；保留 App、资料树、Composer 和预览组件接口。
- 视觉语言：延续墨色、钴蓝、暖灰和语义绿/琥珀；以连续编号问题列表作为视觉锚点，减少并列卡片和装饰性空白。
- 字体与密度：桌面标题 40px 左右、移动端 30px；回答正文不小于 15px，辅助文字不小于 11px；长标题和引用允许换行且不造成横向溢出。
- 状态：空、消息、answered、insufficient、busy、hover、active、focus-visible 均可辨识；颜色不是唯一状态信号。
- 无障碍：保留 `aria-live`；busy 使用 `role=status`；示例问题和后续问题保持原生按钮与键盘路径；引用文本按阅读顺序排列。
- 视觉验收：375x812、768x900、1440x900 无横向溢出；移动首屏可看到至少一个示例问题；消息和引用状态在桌面/移动端均可读。

## Motion Contract

- 启动台：首次出现使用 260ms opacity + translateY；减少动态时静态显示。
- 示例问题：hover/focus 使用 160ms 颜色、边框和轻微横向位移；不改变布局尺寸。
- 消息：新增消息使用 220ms opacity + translateY，滚动仍由现有逻辑控制。
- 思考状态：三个点使用错峰透明度/位移，同时保留可读状态文字；减少动态时显示静态点。
- 明确不做：背景连续运动、滚动驱动动画、第三方动效依赖或卡片弹跳。

## Tailwind Contract

- 版本与隔离：沿用 Tailwind 4.3.3、`tw:` 前缀、无 Preflight；新增内容扫描仅指向 `ConversationView.vue`。
- Token：复用已有 CSS 语义变量；复杂状态点和动画使用 `rag-*` 专用类，不引入动态拼接的工具类。
- 迁移：移除 ConversationView 已替代的旧选择器和移动覆盖；保留应用壳及 toolbar CSS。

## 验收标准

- [x] 空状态问题列表可点击并触发原有 `ask` 事件。
- [x] 进入消息状态后启动台退出，answered/insufficient/busy 和引用状态正确展示。
- [x] 不改变服务端身份、ACL、答案或引用数据；无新增网络请求或 payload。
- [x] Tailwind 保持无 Preflight，未迁移组件无视觉回归。
- [x] lint、typecheck、test、build、统一 Harness 和多尺寸浏览器验收通过。

## 约束

- 相关产品不变量：答案必须展示授权来源；依据不足时明确拒答；移动端保持完整问答能力。
- 相关架构边界：组件只消费后端授权响应，不执行权限判断或自行补写答案。
- 需要保留的无关工作区修改：任务开始时工作区干净。

## 验证

- 单元/契约测试：Vue 4 个测试文件、15 个测试通过，其中新增 3 个 `ConversationView` 组件测试；Spring 52 个测试、Harness 28 个测试通过。
- RAG 离线评测：3 个问答案例与 4 个检索案例均通过，禁止文档/词泄漏率为 0。
- 手工/集成证据：在真实浏览器渲染中检查 375x812、768x900、1440x900；空状态、busy、answered、引用、insufficient 均可读且无横向溢出，控制台无错误或警告。
- 无障碍/动效证据：示例问题与后续问题均为可聚焦原生按钮，焦点轮廓可见；`prefers-reduced-motion: reduce` 下启动台、消息与思考点动画禁用。
- 独立前端门禁：`npm run lint`、`npm run typecheck`、`npm test`、`npm run build` 全部通过；构建 CSS 39.00 kB（gzip 8.55 kB）。
- 统一命令：`powershell -ExecutionPolicy Bypass -File scripts/verify.ps1` 全部 9 步通过。

## 交付

- 决策：采用连续编号问题列表替代三张等权卡片；首次消息后隐藏启动台；引用改为非交互语义文章，避免无行为按钮；Tailwind 继续限制在两个试点组件并保留无 Preflight 隔离。
- 数据读取/写入与日志：不增加数据读取、写入或日志。
- 剩余风险：Tailwind 仍为逐组件迁移，Composer 与工作台壳保留全局 CSS；当前视觉验收为人工浏览器检查，尚未建立截图回归基线。
- 延后工作：Composer 试点、自动视觉回归和设计 token CSS-first 整理。
