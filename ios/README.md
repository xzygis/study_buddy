# StudyAlarm for iOS and iPadOS

StudyAlarm 是一个使用 SwiftUI 与 AlarmKit 构建的本地学习计划闹钟。用户可以将学习、休息和生活事项组织成按星期重复的计划，再将整组提醒交给系统调度。

应用不依赖账号、服务器、网络请求、后台轮询或保活机制，最低支持 iOS / iPadOS 26。

## 功能范围

- 创建、编辑、复制和删除多组计划
- 为整组计划设置重复星期
- 为每组计划配置多个具名提醒及开始时间
- 整组启用或停用 AlarmKit 闹钟
- 编辑已启用计划后自动替换旧闹钟
- 启动、回到前台及系统状态变化时重新核对闹钟
- 在“今日”中展示已启用且系统核对成功的当天安排
- 检测不同启用计划之间的同日、同分钟冲突
- 原生适配 iPhone 与 iPad

首次安装会生成“工作日（不含周二）”“周二”“周末”三组普通计划。它们与用户新建的计划完全一致，均可直接编辑、复制、启用或删除。

## 技术设计

| 模块 | 职责 |
| --- | --- |
| `StudyAlarmApp.swift` | App 生命周期、标签导航和前台状态核对 |
| `Models.swift` | 计划、提醒、星期规则及 AlarmKit 绑定模型 |
| `PlanStore.swift` | 计划 CRUD、整组同步、状态核对与失败回滚 |
| `FilePlanPersistence.swift` | Application Support 下的原子 JSON 持久化 |
| `AlarmKitService.swift` | AlarmKit 授权、创建、取消和系统状态读取 |
| `DashboardViews.swift` | 今日时间轴与冲突入口 |
| `PlanLibraryView.swift` | 计划列表与详情导航 |
| `PlanEditorView.swift` | 新建计划及详情页编辑 |

### 一致性策略

1. 创建闹钟前先持久化全部 AlarmKit ID，避免进程中断后失去关联。
2. 只有整组闹钟创建并回读核对成功，计划才显示“已启用”。
3. 部分创建失败时回滚本次整组操作。
4. 编辑已启用计划时，先清理旧闹钟，再提交新规则。
5. 关闭或删除计划时先取消系统闹钟；清理失败则保留记录并提示重试。
6. 发现本 App 遗留但未关联的闹钟时尝试清理，并明确暴露失败状态。

AlarmKit 闹钟由本 App 管理，不要求显示在系统“时钟”App 的闹钟列表中。

## 环境要求

- macOS 26 或兼容版本
- Xcode 26+
- iOS 26+ Simulator Runtime
- 真机调试时需要 Apple Developer Team

工程配置：

- Xcode Project：`StudyAlarm.xcodeproj`
- Scheme：`StudyAlarm`
- Bundle ID：`com.xzygis.studybuddy.alarm`
- Deployment Target：iOS 26.0
- Device Family：iPhone、iPad

## 使用 Xcode 运行

1. 打开工程：

   ```bash
   open ios/StudyAlarm.xcodeproj
   ```

2. 在 Xcode 顶部选择 `StudyAlarm` Scheme。
3. 选择 iOS 26+ 的 iPhone 或 iPad 模拟器。
4. 按 `Command-R` 构建并运行。
5. 真机运行时，在 Target 的 Signing & Capabilities 中选择 Team。若 Bundle ID 已被占用，请改成自己账号下的唯一标识。

## 手动启动模拟器验证

以下命令均从仓库根目录执行。这套流程不依赖 Xcode 的 Run 按钮，可单独验证“编译、启动模拟器、安装、启动和截图”链路。

### 1. 确认可用设备

```bash
xcrun simctl list devices available
```

从输出中复制目标设备的 UDID：

```bash
export IPHONE_UDID="<iPhone 模拟器 UDID>"
export IPAD_UDID="<iPad 模拟器 UDID>"
```

本项目已使用 iPhone 17 Pro 和 iPad Pro 13-inch（M5）、iOS 26.5 进行验证。

### 2. 构建模拟器 App

```bash
export DERIVED_DATA="$PWD/ios/.sim-validation"

xcodebuild \
  -project ios/StudyAlarm.xcodeproj \
  -scheme StudyAlarm \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination "generic/platform=iOS Simulator" \
  -derivedDataPath "$DERIVED_DATA" \
  CODE_SIGNING_ALLOWED=NO \
  COMPILER_INDEX_STORE_ENABLE=NO \
  build

export APP_PATH="$DERIVED_DATA/Build/Products/Debug-iphonesimulator/StudyAlarm.app"
test -d "$APP_PATH"
```

### 3. 启动、安装并运行

iPhone：

```bash
xcrun simctl boot "$IPHONE_UDID" 2>/dev/null || true
xcrun simctl bootstatus "$IPHONE_UDID" -b
xcrun simctl install "$IPHONE_UDID" "$APP_PATH"
xcrun simctl launch --terminate-running-process \
  "$IPHONE_UDID" com.xzygis.studybuddy.alarm
```

iPad：

```bash
xcrun simctl boot "$IPAD_UDID" 2>/dev/null || true
xcrun simctl bootstatus "$IPAD_UDID" -b
xcrun simctl install "$IPAD_UDID" "$APP_PATH"
xcrun simctl launch --terminate-running-process \
  "$IPAD_UDID" com.xzygis.studybuddy.alarm
```

打开 Simulator 窗口：

```bash
open -a Simulator
```

### 4. 保存验证截图

```bash
xcrun simctl io "$IPHONE_UDID" screenshot /tmp/studyalarm-iphone.png
xcrun simctl io "$IPAD_UDID" screenshot /tmp/studyalarm-ipad.png
open /tmp/studyalarm-iphone.png
open /tmp/studyalarm-ipad.png
```

仓库中的当前基准截图：

- [iPhone 17 Pro](Screenshots/iphone-home.png)
- [iPad Pro 13-inch（M5）](Screenshots/ipad-home.png)

### 5. 手动检查清单

1. 首屏只有“今日”和“计划”两个入口，不出现登录、账户或模板入口。
2. “今日”只显示已启用且系统核对成功的当天提醒。
3. “计划”展示本机已有计划，并可新建、复制和启停。
4. 点击计划名称进入只读详情。
5. 点击计划卡片中的“编辑”进入详情页编辑状态。
6. 修改名称、星期或提醒时间并保存后，仍停留在该计划详情页。
7. 长按或左滑可以删除单个提醒。
8. 删除计划后，该计划从列表消失。
9. iPhone 和 iPad 上不存在文字截断、控件重叠或横向溢出。

模拟器适合验证数据操作、导航和布局，但不能替代 AlarmKit 响铃、锁屏、静音模式及专注模式的真机测试。

### 常见问题

未安装 iOS Runtime：

```bash
xcodebuild -downloadPlatform iOS -architectureVariant arm64
```

CoreSimulator 状态异常：

```bash
killall -9 com.apple.CoreSimulator.CoreSimulatorService
```

重新启动 Xcode 和 Simulator 后再次执行 `simctl bootstatus`。如果只需重置本 App，可执行以下命令；该操作会删除模拟器中的全部 StudyAlarm 本地数据：

```bash
xcrun simctl uninstall "$IPHONE_UDID" com.xzygis.studybuddy.alarm
```

## 自动验证

执行：

```bash
bash ios/scripts/verify.sh
```

脚本包含：

1. Swift Package 核心逻辑测试。
2. 面向 iPhone / iPad 的无签名设备构建。

当前验证结果：

- 26 项核心测试通过，0 失败。
- iOS Simulator Debug 构建通过。
- `arm64-apple-ios26.0` Release 无签名设备构建通过。
- iPhone 17 Pro 和 iPad Pro 13-inch（M5）安装、启动及首屏截图通过。
- 最终产物 `UIDeviceFamily` 包含 iPhone（1）和 iPad（2）。
- 最终 Info.plist 包含 `NSAlarmKitUsageDescription`。
- `StudyAlarmUITests` 目标编译通过。

当前机器的 Xcode `DTServiceHub/lockdown` 测试通道无法稳定连接模拟器，因此没有将 XCUITest 自动点击执行标记为通过。

## GitHub Actions

### 持续集成

`.github/workflows/ci.yml` 在提交到 `main` 或向 `main` 发起 Pull Request 时执行核心测试和 Simulator 构建。

### IPA 发布

`.github/workflows/release-ios.yml` 提供自动版本管理、签名归档和 GitHub Release 发布：

1. PR 合并到 `main` 后触发。
2. 根据最新 Git Tag 自动递增补丁版本；首次发布为 `v1.0.0`。
3. 运行核心测试。
4. 导入 Apple Distribution 证书和 Ad Hoc 描述文件。
5. 归档并导出 `release-testing` 通用 IPA。
6. 创建 GitHub Release 并上传 `study-alarm-<version>.ipa`。

需要配置以下 GitHub Actions Secrets：

| Secret | 内容 |
| --- | --- |
| `APPLE_TEAM_ID` | Apple Developer Team ID |
| `IOS_DISTRIBUTION_CERTIFICATE_BASE64` | Apple Distribution `.p12` 文件的 Base64 |
| `IOS_DISTRIBUTION_CERTIFICATE_PASSWORD` | `.p12` 导出密码 |
| `IOS_PROVISIONING_PROFILE_BASE64` | Bundle ID 对应的 Ad Hoc 描述文件 Base64 |

证书和描述文件应保存在用户目录，不要写入项目：

```bash
base64 < ~/path/to/distribution.p12 | pbcopy
base64 < ~/path/to/StudyAlarm.mobileprovision | pbcopy
```

Ad Hoc IPA 只能安装到描述文件中已登记的设备。

## 真机验收

将提醒设置在未来 2 至 3 分钟，分别在 iPhone 和 iPad 上验证：

| 场景 | 预期 | iPhone | iPad |
| --- | --- | --- | --- |
| 首次授权 | 出现 AlarmKit 权限请求，允许后整组显示“已启用” | 未实测 | 未实测 |
| 拒绝授权 | 明确显示未生效，可从系统设置恢复权限后重试 | 未实测 | 未实测 |
| 多提醒 | 同一计划的多个提醒分别按名称响铃 | 未实测 | 未实测 |
| 重复星期 | 只在选中的星期响铃 | 未实测 | 未实测 |
| 编辑已启用计划 | 旧规则停止，新名称和时间生效 | 未实测 | 未实测 |
| 关闭计划 | 整组提醒停止 | 未实测 | 未实测 |
| 删除计划 | 计划及其系统闹钟全部清理 | 未实测 | 未实测 |
| 锁屏 | 锁屏状态出现系统闹钟界面并响铃 | 未实测 | 未实测 |
| 后台与强制退出 | App 不在前台时仍由系统按时响铃 | 未实测 | 未实测 |
| 断网 | 无网络时仍正常响铃 | 未实测 | 未实测 |
| 设备重启 | 重启后不打开 App，原计划仍正常响铃 | 未实测 | 未实测 |
| 静音与专注模式 | 记录实际声音、震动和系统界面表现 | 未实测 | 未实测 |

设备关机期间无法响铃。模拟器验证结果不得代替本表中的真机结论。
