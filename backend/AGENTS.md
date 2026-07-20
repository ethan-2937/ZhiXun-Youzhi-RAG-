# Spring Backend Guide

## 边界

- `controller/`：HTTP、校验和响应转换，只依赖 Service 接口。
- `service/`：用例接口；`service/impl/`：演示或业务编排实现。
- `vo/`：API 请求/响应；不直接暴露持久化对象。
- `config/`：安全、跨域、序列化和环境配置。

## 规则

- Controller 不导入 Repository、Mapper、ServiceImpl、VectorStore 或 ChatModel。
- 演示数据必须完全虚构，并通过 `demo` Profile/包边界与未来真实 RAG 区分。
- 问题、回答和引用不写普通日志；错误只返回稳定安全信息。
- 所有字符串/集合请求有上限；未知问题返回资料不足，不编造答案。
- 默认测试不访问网络、钉钉、模型、数据库或对象存储。

## 命令

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```
