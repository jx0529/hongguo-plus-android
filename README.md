# 红果Plus（hongguo-plus-android）

手机端红果短剧客户端，竖屏沉浸式刷剧。**非官方**，与红果短剧官方无关，仅供个人学习交流，禁止倒卖。

本项目承接 [yangbin1322/hongguo-player](https://github.com/yangbin1322/hongguo-player)（红果短剧电脑版）的产品思路，但该电脑版仓库只发布安装包、不含源码；因此手机版以开源项目 [xycvip/HongGuoTV](https://github.com/xycvip/HongGuoTV)（GPL-3.0）的取流、签名与播放内核为基础，重写成 **手机优先** 的原生 Android 客户端，并补齐手机端更需要的体验。

## 和电脑版相比，手机版做了什么

| 能力 | 电脑版（参考） | 红果Plus（本仓库） |
| --- | --- | --- |
| 形态 | Windows / macOS 桌面客户端 | 原生 Android APK，装完即用，竖屏单手操作 |
| 刷剧方式 | 鼠标滚轮 / 方向键切集 | 全屏竖屏播放，**上滑下一集、下滑上一集**，点按显示/隐藏控制条 |
| 连播 | 自动下一集、下一季 | 自动连播 + **断点续看**（历史记到第几集） |
| 找剧 | 搜索、筛选、排行榜 | 搜索联想 + 剧场多条件筛选 + 综合/短剧/漫剧/AI剧四榜 |
| 追剧管理 | 收藏、历史、点赞、预约 | 收藏 + 历史本地库，单条移除、一键清空 |
| 画质 | 画质增强、软硬解兜底 | 1080 / 720 / 480 一键切换，取流失败可重试 |
| 倍速 | 键盘快捷键 | 播放器内 0.75×–2.0× 循环切换并记住选择 |
| 广告 | 无广告 | 无广告、无弹窗、无后台推送 |

## 功能清单

- 推荐流：短剧 / 漫剧 / AI 剧三频道，三列网格，滚到底自动翻页
- 剧场：按题材等维度筛选
- 排行榜：综合榜、短剧榜、漫剧榜、AI 剧榜
- 搜索：输入联想、组合搜索、结果分页
- 详情页：封面、评分/热度、简介、选集、相关推荐、一键收藏
- 播放器：竖屏全屏、上/下滑切集、进度拖动、倍速、清晰度、选集面板、自动连播、断点续看
- 我的：收藏 / 历史，本地保存，可移除与清空
- 无需登录，本地不采集个人数据

## 技术栈

- Kotlin + Jetpack Compose（Material 3，深色主题）
- Media3 / ExoPlayer 1.11.1 + 自定义 `DataSource`（复用上游 `RemoteVideo` 分段缓存与解密管线）
- 多模块：`:core`（站点解析、搜索、榜单、剧场）、`:playback-*`（取流签名与播放内核）、`:phone`（本手机 App）、`:app`（上游 TV 版，保留以便对照）
- Coil 图片加载、OkHttp 网络层

## 安装

到 [Releases](../../releases/latest) 下载 `HongguoPlus-*.apk` 安装（Android 8.0 / API 26 及以上，arm64 与 armeabi-v7a 通用）。

播放器提示“取流失败”时点重试即可；封面不显示通常是网络访问不到站点域名。

## 自己编译

需要 JDK 17 与 Android SDK 36：

```bash
cd kotlin-tv
echo "sdk.dir=/your/android/sdk" > local.properties
./gradlew :phone:assembleDebug
```

产物：`kotlin-tv/phone/build/outputs/apk/debug/phone-debug.apk`

正式包：

```bash
./gradlew :phone:assembleRelease
```

签名配置在仓库根目录 `keystore.properties`（随仓库提供，方便你后续升级覆盖安装；若要公开发布建议换成自己的 keystore）。

## 目录结构

```
kotlin-tv/
├── core/            站点解析、搜索、榜单、剧场、登录等纯 JVM 逻辑
├── playback-api/    播放源抽象
├── playback-media/  RemoteVideo 分段缓存 + 解密
├── playback-native/ 设备签名
├── playback-phoenix/备用取流通道
├── app/             上游 TV 版客户端（GPL-3.0，保留原样）
└── phone/           本仓库新增的手机版 App（Compose）
```

## 开源许可与致谢

- 本仓库整体以 **GPL-3.0** 发布（见 [LICENSE](LICENSE)），因为复用了 GPL-3.0 项目的核心代码。
- 播放内核、签名、站点解析来自 [xycvip/HongGuoTV](https://github.com/xycvip/HongGuoTV)，原作者保留其版权，本仓库保留原有 SPDX 头与许可证。
- 产品形态参考 [yangbin1322/hongguo-player](https://github.com/yangbin1322/hongguo-player)。
- 内容与商标归红果短剧及其权利人所有；本项目不提供、不存储、不分发任何影视内容，仅作为个人学习用的第三方客户端。

## 免责声明

本项目为非官方第三方客户端，与红果短剧官方无关，仅供个人学习交流，免费分享，禁止倒卖。使用产生的一切后果由使用者自行承担。
