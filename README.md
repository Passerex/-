# 校园跑去死
通过Android虚拟位置功能伪造校园跑轨迹,需开启开发者模式并启用"虚拟位置"功能,部分手机无法使用该功能。

> ⚠️ **使用前请务必阅读 [免责声明](DISCLAIMER.md)**

一款基于 Android 的 GPS 轨迹记录应用，使用 OpenStreetMap 地图显示，支持实时记录、查看和管理运动轨迹。

## 功能特点

| 功能 | 说明 |
|------|------|
| 📍 **GPS 轨迹记录** | 实时记录运动路径，支持开始/暂停/停止 |
| 🗺️ **地图显示** | 基于 osmdroid 的 OpenStreetMap 离线/在线地图 |
| 📋 **轨迹管理** | 列表查看、重命名、删除已保存的轨迹 |
| ▶️ **轨迹回放** | 播放已记录的轨迹动画 |
| 📊 **轨迹统计** | 显示点数、距离、时长等统计信息 |

## 技术栈

- **语言**: Java
- **最低 SDK**: Android 9 (API 28)
- **目标 SDK**: Android 16 (API 36)
- **UI**: Material Design 3 (MaterialCardView)
- **地图**: [osmdroid](https://github.com/osmdroid/osmdroid) (OpenStreetMap)
- **定位**: Google Play Services Location
- **架构**: ViewBinding + Navigation Component
- **构建**: Gradle Kotlin DSL

## 快速开始

### 直接安装
也可直接从 [Releases](../../releases) 页面下载最新 APK 安装。
APK无安全签名请自行甄别使用。

## 自行编译
### 环境要求

- Android Studio Hedgehog (2024.1.1) 或更高版本
- JDK 11+
- Android SDK 36

### 构建

```bash
# 克隆项目
git clone https://github.com/<你的用户名>/<仓库名>.git

# 使用 Gradle 构建 Debug APK
cd <仓库名>
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/` 目录。


## 项目结构

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/example/myapplication/
│   │   │   ├── data/          # 数据层（Repository、Adapter）
│   │   │   ├── model/         # 数据模型（Trajectory、TrajectoryPoint）
│   │   │   ├── ui/            # UI 层（Fragment、布局）
│   │   │   └── MainActivity.java
│   │   ├── res/               # 资源文件
│   │   └── AndroidManifest.xml
│   ├── test/                  # 单元测试
│   └── androidTest/           # UI 测试
├── build.gradle.kts
└── ...
```

## 依赖的第三方库

- [osmdroid](https://github.com/osmdroid/osmdroid) — OpenStreetMap 地图渲染
- [Google Play Services Location](https://developers.google.com/android/guides/overview) — 位置服务
- [AndroidX](https://developer.android.com/jetpack/androidx) — Jetpack 组件库
- [Material Components](https://github.com/material-components/material-components-android) — Material Design 组件

## 许可证

本项目采用 [MIT License](LICENSE)。

## 免责声明

详见 [DISCLAIMER.md](DISCLAIMER.md)。
