# MQTT Mobile Client

这是一个面向 Android 的 MQTT 调试客户端，vibecoding开发：

- Kotlin + Jetpack Compose + MVVM
- MQTT 3.1.1 / MQTT 5.0
- 原生 MQTT TCP 与 MQTT over TLS（不依赖 WebSocket）
- Broker 配置持久化，密码使用 Android Keystore 加密
- Topic 订阅、QoS 0/1/2、自动重连、消息列表、JSON/文本/Hex/Base64 展示
- Published 消息记录、发布前确认、连接日志和消息详情复制
- 多 Broker 并行连接、消息历史持久化、JSON/CSV 导出和 MQTT 5 属性查看
- 自定义 CA、客户端证书与 mTLS；客户端私钥使用 Android Keystore 加密保存
- 前台服务后台监听和收到消息时的系统通知
- 首页支持连接配置搜索，以及不包含密码和私钥的 JSON 配置导入导出
- DNS、TCP、超时、TLS 和证书错误会转换为可读的连接诊断信息
- 订阅支持添加、编辑、删除，网络恢复后会主动检查并恢复失效连接
- 消息保留数量可在消息页选择 100 至 5000 条，并持久化保存
- 消息列表支持正序和倒序查看
- MQTT 5 订阅支持 No Local、Retain As Published 和 Retain Handling
- 消息详情可直接带入发布页；发布页支持 MQTT 5 Message Expiry、Content Type、Response Topic 和发布前确认
- 默认消息显示格式、消息/日志保留数量和应用信息可在首页设置中修改；日志只保留当前会话
- 消息历史使用 Room 持久化，首次启动兼容迁移旧 JSON 文件和 SharedPreferences 数据，设置项使用 DataStore
- 前台服务在进程异常后会根据最近活动配置尝试恢复连接
- 连接编辑页支持独立的“测试连接”入口；工作区提供连接时间、错误摘要和日志清空操作
- 平板或横向宽屏下，消息列表与消息详情采用双栏布局
- JSON 详情支持对象和数组节点逐层展开/折叠
- JVM 单元测试覆盖 Topic 通配符、Payload Hex/文本/JSON 处理和配置导入导出
- `:app:lintDebug` 已通过，通知权限和应用图标配置完整
- 多 Broker 重连使用连接代次隔离旧客户端回调，避免快速编辑/重连时覆盖新状态

## 打开和运行

使用 Android Studio 打开本目录，等待 Gradle 同步后运行 `app`。工程要求：

- JDK 17（项目编译目标仍为 Java 17；JDK 25 可执行 `assembleDebug`，完整 Lint 检查请使用 JDK 17）
- Android SDK 35
- minSdk 24

首次连接时，在首页创建 Broker 配置，填写 Host、Port 和可选认证信息；进入工作区后在 Subscriptions 添加 Topic Filter。

## 代码结构

```text
app/src/main/java/com/mqttmobile/app/
├── data/model/       领域模型和枚举
├── data/local/       Broker、订阅、消息历史与 Keystore 凭据存储
├── data/mqtt/        HiveMQ MQTT 3/5 异步客户端适配
├── ui/               Compose 页面、ViewModel 和工作区交互
└── util/             Payload 格式转换和 JSON 格式化
```

Android SDK 路径通过未纳入版本控制的 `local.properties` 配置；请在 Android Studio 中同步 Gradle 后进行设备或模拟器验证。

## 许可证

本项目源代码默认使用 Apache License 2.0，详见 [LICENSE](LICENSE)。第三方依赖及其许可证见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。

