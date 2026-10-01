# StudyAlarm

StudyAlarm 是面向家庭学习安排的原生 iOS / iPadOS 闹钟应用。家长可以将一组学习、休息和生活事项配置为按星期重复的计划，再由 AlarmKit 交给系统按时提醒。

项目完全离线运行，不需要账号、服务器、网络连接或后台保活。

## 核心能力

- 创建、编辑、复制和删除多组学习计划
- 为每组计划配置统一的重复星期
- 为计划添加多个具名提醒及开始时间
- 通过 AlarmKit 批量启用、停用和核对系统闹钟
- 部分创建失败时整组回滚，避免重复或遗留闹钟
- iPhone 与 iPad 共用同一个工程和安装包

## 技术栈

- Swift 6
- SwiftUI
- AlarmKit
- iOS / iPadOS 26+
- 本地 JSON 原子持久化
- XCTest 与 XCUITest

## 快速开始

```bash
open ios/StudyAlarm.xcodeproj
```

使用 Xcode 26 或更高版本，选择 `StudyAlarm` Scheme 和一个 iOS 26+ 模拟器即可运行。真机运行前需要在 Signing & Capabilities 中选择 Apple Developer Team。

执行核心测试和无签名设备构建：

```bash
bash ios/scripts/verify.sh
```

## 文档

完整的工程说明、手动模拟器验证步骤、自动发布配置和真机验收清单见 [ios/README.md](ios/README.md)。
