# PaperTong

极简风格的 Android 论文翻译 App。本地解析文件、本地 OCR，仅将提取出的文本发送至 DeepSeek 接口。

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
| M2 | API Key 加密存储、设置页 | 待开始 |
| M3 | DeepSeek 接入、SSE 流式、分块与风格 Prompt | 待开始 |
| M4 | 文本翻译界面 | 待开始 |
| M5 | 图片 OCR（ML Kit） | 待开始 |
| M6 | PDF / DOCX / MD 解析 | 待开始 |
| M7 | 学术阅读视图（对照、公式保留） | 待开始 |
| M8 | 安全收口、ProGuard、打包 | 待开始 |

## 隐私边界

- 文件解析与 OCR 全部在本地完成，原始文件与图片不外发。
- 翻译所需的**文本**会发送至 `api.deepseek.com`，发送前有明确提示。
- API Key 使用 Android Keystore 加密后存于本机，不参与系统备份。
