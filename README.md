# PaperTong

极简风格的 Android 论文翻译 App。本地解析文件、本地 OCR，仅将提取出的文本发送至 DeepSeek 接口。

当前版本 **0.1.8**（versionCode 8）。

## 环境要求

- Android Studio Ladybug (2024.2) 或更新
- JDK 17
- Android SDK Platform 35
- Gradle 8.9（wrapper 已配置，首次打开时自动下载）

## 首次打开

1. Android Studio → `Open` → 选择本目录（`papertong/`，不是上层目录）。
2. 等待 Gradle Sync 完成。若提示缺少 SDK 组件，按提示安装 Platform 35。
3. 运行 `app` 配置。

> `gradle-wrapper.properties` 已配置，但 `gradlew` 脚本与 `gradle-wrapper.jar` 是二进制文件，
> 需要在有 Gradle 的机器上生成。Android Studio 打开工程时会提示补全，点确认即可；
> 或者执行 `gradle wrapper --gradle-version 8.9`。

命令行构建：

```bash
./gradlew :app:assembleDebug
```

## 里程碑进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| M1 | 工程骨架、主题、导航、空壳页面 | 已完成 |
| M2 | API Key 加密存储、设置页 | 已完成 |
| M3 | DeepSeek 接入、SSE 流式、分块与风格 Prompt | 已完成 |
| M4 | 文本翻译界面 | 已完成 |
| M5 | 图片 OCR（ML Kit，模型随包内置、完全离线） | 已完成 |
| M6 | PDF / DOCX / MD 本地解析，扫描件 PDF 走本地 OCR 回退 | 已完成 |
| M7 | 学术阅读视图（上下/左右对照、公式等宽高亮、导出 Markdown） | 已完成 |
| M8 | 安全收口、ProGuard/R8、release 打包与体积优化 | 已完成 |

### 已实现的功能

- 中英互译，三种风格：学术专业（严谨）、地道口语、直译
- 文本直译、拍照识别、导入 `.pdf` / `.docx` / `.md`(`.markdown`) / `.txt`（旧版 `.doc` 不支持，需另存为 `.docx`）
- 长文自动分块（2500 字、120 字重叠）后流式翻译，单次导入上限约 40 万字符（≈200 页论文）
- 翻译历史（本地加密存储，上限 100 条），可进入对照阅读页
- 对照阅读：原文译文逐段上下或左右并排，字号可调，公式等宽高亮
- 导出 Markdown（带 YAML front matter），走系统文件选择器，应用无存储权限

## 隐私边界

- 文件解析与 OCR 全部在本地完成，原始文件与图片不外发。
- 翻译所需的**文本**会发送至 `api.deepseek.com`，发送前有明确提示。
- API Key 使用 Android Keystore 加密后存于本机，不参与系统备份。
- 翻译历史同样用 Keystore 的 AES-256-GCM 加密后落盘。密钥不可导出，
  因此清数据、恢复出厂或换机后历史**永久无法恢复**——这是"没有云端副本"的必然代价。
- 应用只申请 `INTERNET` / `ACCESS_NETWORK_STATE` / `CAMERA`，没有任何存储权限；
  拍照写入 `cacheDir/ocr/` 并经 FileProvider 交给相机应用，放弃拍照或采用识别结果时删除该文件，
  异常退出则留给系统回收缓存。
- 全局禁止明文 HTTP（`network_security_config.xml`），仅允许系统信任锚。
- 不接入任何崩溃上报 / 统计分析 SDK，请求里也没有 OkHttp 日志拦截器。
