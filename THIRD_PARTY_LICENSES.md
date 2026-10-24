# 第三方组件与许可

PaperTong 自身代码未经开源许可授权（保留所有权利）。以下为随 APK 分发的
第三方组件及其许可。完整许可原文见各组件官方仓库。

> 版本号与 `gradle/libs.versions.toml` 保持一致；升级依赖时请同步更新本文件。

## 运行时依赖

| 组件 | 版本 | 许可 | 用途 |
| --- | --- | --- | --- |
| Kotlin / kotlinx.coroutines | 2.0.21 / 1.9.0 | Apache-2.0 | 语言与协程运行时 |
| kotlinx.serialization | 1.7.3 | Apache-2.0 | 历史记录与请求体的 JSON 序列化 |
| AndroidX Core KTX | 1.13.1 | Apache-2.0 | 平台 API 的 Kotlin 扩展 |
| AndroidX Activity Compose | 1.9.3 | Apache-2.0 | Compose 宿主、ActivityResult |
| AndroidX Lifecycle | 2.8.6 | Apache-2.0 | ViewModel、生命周期感知收集 |
| AndroidX Navigation Compose | 2.8.3 | Apache-2.0 | 页面路由 |
| AndroidX DataStore | 1.1.1 | Apache-2.0 | 设置存储 |
| Jetpack Compose (BOM) | 2024.10.01 | Apache-2.0 | 全部界面 |
| Retrofit | 2.11.0 | Apache-2.0 | HTTP 客户端声明 |
| OkHttp | 4.12.0 | Apache-2.0 | HTTP 传输、SSE 流式读取 |
| ML Kit Text Recognition v2 | 16.0.1 | Google APIs Terms of Service | 离线文字识别（模型随 APK 分发） |
| PdfBox-Android | 2.0.27.0 | Apache-2.0 | PDF 文字层抽取 |
| BouncyCastle (bcprov/bcpkix/bcutil) | 由 PdfBox 传递引入 | MIT 风格（Bouncy Castle License） | PdfBox 的加密与证书支持 |

## 特别说明

### ML Kit

ML Kit 不适用 Apache-2.0，而适用 **Google APIs Terms of Service**。它随 APK
分发识别模型，识别过程在本机完成、不联网，但使用该组件受 Google 的条款约束。
这是本项目唯一一个非 Apache/MIT 的依赖，特此单独列出。

### BouncyCastle

由 PdfBox-Android 作为 `compile` 级依赖强制引入（约 11MB），不是本项目
主动选择，也无法在不放弃 PdfBox 的前提下移除。许可为 MIT 风格的
Bouncy Castle License。

## 开发期工具（不随 APK 分发）

Android Gradle Plugin 8.7.2、Gradle 8.9、JDK 17 (Temurin)——
均为 Apache-2.0 / GPL-2.0 with Classpath Exception，仅用于构建。
