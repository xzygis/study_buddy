<p align="center">
  <img src="ios/StudyAlarm/Resources/AppIcon60x60@3x.png" width="120" alt="StudyAlarm App Icon" />
</p>

<h1 align="center">StudyAlarm</h1>

<p align="center">
  <strong>学习计划交给系统准时提醒</strong>
</p>

<p align="center">
  <a href="https://github.com/xzygis/study_buddy/actions/workflows/ci.yml">
    <img src="https://github.com/xzygis/study_buddy/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI" />
  </a>
  <a href="https://github.com/xzygis/study_buddy/releases/latest">
    <img src="https://img.shields.io/github/v/release/xzygis/study_buddy?label=latest" alt="Latest Release" />
  </a>
  <img src="https://img.shields.io/badge/iOS%20%2F%20iPadOS-26%2B-000000?logo=apple&logoColor=white" alt="iOS 26+" />
  <img src="https://img.shields.io/badge/Swift-6-F05138?logo=swift&logoColor=white" alt="Swift 6" />
</p>

---

StudyAlarm 是面向家庭学习安排的原生 iOS / iPadOS 闹钟应用。用户可以将学习、休息和生活事项组织成按星期重复的计划，再由 AlarmKit 交给系统按时提醒。

应用完全离线运行，不需要账号、服务器、网络连接或后台保活。

## 核心能力

- 创建、编辑、复制和删除多组学习计划
- 为每组计划配置统一的重复星期
- 为计划添加多个具名提醒及开始时间
- 通过 AlarmKit 批量启用、停用和核对系统闹钟
- 部分创建失败时整组回滚，避免重复或遗留闹钟
- iPhone 与 iPad 共用同一个工程和安装包

## 界面预览

<p align="center">
  <img src="ios/Screenshots/iphone-home.png" width="260" alt="StudyAlarm iPhone 今日计划" />
  &nbsp;&nbsp;&nbsp;
  <img src="ios/Screenshots/ipad-home.png" width="430" alt="StudyAlarm iPad 今日计划" />
</p>

## 技术栈

- Swift 6
- SwiftUI
- AlarmKit
- iOS / iPadOS 26+
- 本地 JSON 原子持久化
- XCTest 与 XCUITest

## 快速开始

```bash
git clone https://github.com/xzygis/study_buddy.git
cd study_buddy
open ios/StudyAlarm.xcodeproj
```

使用 Xcode 26 或更高版本，选择 `StudyAlarm` Scheme 和一个 iOS 26+ 模拟器即可运行。真机运行前需要在 Signing & Capabilities 中选择 Apple Developer Team。

执行核心测试和无签名设备构建：

```bash
bash ios/scripts/verify.sh
```

## 下载安装

合并到 `main` 的 Pull Request 会触发签名归档和 GitHub Release 发布。签名配置完成后，可从 [Releases](https://github.com/xzygis/study_buddy/releases/latest) 下载同时支持 iPhone 和 iPad 的 IPA。

> Ad Hoc IPA 只能安装到描述文件中已登记的设备。AlarmKit 的实际响铃行为仍需在真机上验证。

## 文档

完整的工程说明、手动模拟器验证步骤、自动发布配置和真机验收清单见 [ios/README.md](ios/README.md)。
