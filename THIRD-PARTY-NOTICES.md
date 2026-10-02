# Third-Party Notices

本文件记录当前 `:app:releaseRuntimeClasspath` 及构建配置中识别到的第三方组件。版本以项目当前 Gradle 配置和解析结果为准；升级依赖后应重新检查并更新本文件。

## 项目源代码

除另有说明外，本仓库中的项目源代码按 Apache License 2.0 发布，见根目录 [LICENSE](LICENSE)。本声明不覆盖第三方依赖、外部素材、商标或 `app/src/main/res/drawable-nodpi/ic_launcher_art.png` 图像文件。

## 正式 APK 的运行时依赖

| 组件或组件组 | 当前版本/范围 | 许可证 | 上游项目 |
| --- | --- | --- | --- |
| AndroidX Core、Activity、Compose、Material、Material 3、Lifecycle、DataStore、Room、SQLite、Startup、Tracing、Profile Installer 等 | 由 `androidx.compose:compose-bom:2024.12.01` 和 `app/build.gradle.kts` 解析 | Apache-2.0 | [androidx/androidx](https://github.com/androidx/androidx) |
| Kotlin Standard Library | 2.2.21（解析版本） | Apache-2.0 | [JetBrains/kotlin](https://github.com/JetBrains/kotlin) |
| Kotlin Coroutines | 1.9.0 | Apache-2.0 | [Kotlin/kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) |
| HiveMQ MQTT Client | 1.3.3 | Apache-2.0 | [hivemq/hivemq-mqtt-client](https://github.com/hivemq/hivemq-mqtt-client) |
| Netty（buffer、codec、common、handler、resolver、transport 等） | 4.1.99.Final | Apache-2.0 | [netty/netty](https://github.com/netty/netty) |
| RxJava | 2.2.21 | Apache-2.0 | [ReactiveX/RxJava](https://github.com/ReactiveX/RxJava) |
| Okio | 3.4.0 | Apache-2.0 | [square/okio](https://github.com/square/okio) |
| Dagger、JCTools、`javax.inject`、JetBrains Annotations、JSpecify、Guava `listenablefuture` | 当前解析的传递依赖 | Apache-2.0 | 各上游项目许可证文件 |
| Reactive Streams API | 1.0.4 | MIT-0 | [reactive-streams/reactive-streams-jvm](https://github.com/reactive-streams/reactive-streams-jvm) |

Apache-2.0 的许可证文本见 [LICENSE](LICENSE)。MIT-0 的文本见 [licenses/MIT-0.txt](licenses/MIT-0.txt)。

