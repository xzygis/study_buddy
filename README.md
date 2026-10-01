<p align="center">
  <img src="ios/StudyAlarm/Resources/AppIcon60x60@3x.png" width="120" alt="StudyBuddy App Icon" />
</p>

<h1 align="center">StudyBuddy</h1>

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

StudyBuddy 是一款面向家庭学习安排的原生 iOS / iPadOS 应用。你可以将学习、休息和日常活动整理成按周重复的计划，由系统在设定时间准时提醒。

所有数据都保存在设备本地，无需注册账号，也不依赖服务器、网络连接或后台持续运行。

## 核心能力

- 灵活创建多组学习计划，并可随时编辑、复制或删除
- 为每组计划统一设置每周重复日期
- 在单个计划中添加多个提醒，自定义名称和开始时间
- 一键启用或停用整组提醒，并通过 AlarmKit 核对系统闹钟状态
- 创建过程中如有提醒失败，自动回滚整组操作，避免重复或残留闹钟
- 同一安装包同时适配 iPhone 和 iPad

## 界面预览

<p align="center">
  <img src="ios/Screenshots/iphone-home.png" width="260" alt="StudyBuddy iPhone 今日计划" />
  &nbsp;&nbsp;&nbsp;
  <img src="ios/Screenshots/ipad-home.png" width="430" alt="StudyBuddy iPad 今日计划" />
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
open ios/*.xcodeproj
```

使用 Xcode 26 或更高版本，选择应用 Scheme 和一个 iOS 26+ 模拟器即可运行。真机运行前需要在 Signing & Capabilities 中选择 Apple Developer Team。

执行核心测试和无签名设备构建：

```bash
bash ios/scripts/verify.sh
```

## 下载安装

签名 Secrets 配置完成后，合并到 `main` 的 Pull Request 会触发归档和 GitHub Release 发布。可从 [Releases](https://github.com/xzygis/study_buddy/releases/latest) 下载同时支持 iPhone 和 iPad 的 IPA；未配置签名时，工作流会明确跳过发布，不影响常规 CI。

> Ad Hoc IPA 只能安装到描述文件中已登记的设备。AlarmKit 的实际响铃行为仍需在真机上验证。

## 文档

完整的工程说明、手动模拟器验证步骤、自动发布配置和真机验收清单见 [ios/README.md](ios/README.md)。
