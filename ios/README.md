# 学习闹钟（iOS / iPadOS）

原生 SwiftUI + AlarmKit 学习计划闹钟，最低支持 iOS / iPadOS 26。项目完全离线运行，不使用账号、服务器、网络请求、后台轮询或保活。

## 打开并运行

环境要求：

- Xcode 26 或更高版本
- iOS 26+ iPhone，或 iPadOS 26+ iPad
- 用于真机签名的 Apple Developer Team

步骤：

1. 用 Xcode 打开 `StudyAlarm.xcodeproj`。
2. 选择 `StudyAlarm` target，进入 **Signing & Capabilities**。
3. 选择自己的 Team；如 Bundle ID 冲突，将 `com.xzygis.studybuddy.alarm` 改为自己的唯一标识。
4. 连接 iPhone 或 iPad，选择真机后运行。
5. 直接编辑或启用默认计划，也可以新建/复制计划；第一次启用时允许系统闹钟权限。

权限被拒绝后，计划会明确显示未生效。可从 App 中点击“打开系统设置”，重新允许后返回并点击“重试启用”。

## 实现说明

- App 使用“今日 / 计划”两个标签，不提供账号、登录或个人中心。
- “今日”显示所有已启用且系统核对成功的当天安排；“计划”集中管理全部计划。
- 点击计划可查看详情；点击计划卡片中的“编辑”会进入该计划详情页并直接编辑，保存后仍停留在详情页。
- 首次安装自动生成“工作日（不含周二）”“周二”“周末”三个普通计划，包含图片里的学习、休息、用餐、活动、洗澡和睡觉事项，可直接编辑或启用。
- 所有计划都可复制；复制件使用全新计划/提醒 ID，且不会继承启用状态或 AlarmKit ID。
- 每组计划支持名称、多个提醒和整组重复星期；“每天”保存为七个星期日。
- 启用时先持久化全部 AlarmKit ID，再逐项创建系统闹钟。
- 只有整组创建并回读核对成功后，界面才显示“已启用”。
- 部分创建失败时回滚本次整组；清理失败时保留 ID 和风险提示，供后续重试。
- 编辑已启用计划时先清理旧闹钟，再提交整组新规则。
- 关闭或删除计划时先核对并清理整组闹钟；删除失败时保留待清理记录。
- App 启动、返回前台、系统闹钟变化或权限变化时重新核对状态。
- 数据存储在 Application Support 的原子 JSON 文件中，并排除云备份。
- AlarmKit 闹钟属于本 App，不要求显示在苹果“时钟”App 中。
- 仅使用 alert 型闹钟，不包含倒计时、贪睡、休息循环、完成打卡或 Widget Extension。

## GitHub 自动发布

工作流参考 SilentGuard 的发布方式：

- PR 合并到 `main` 后自动递增补丁版本，例如 `v1.0.0` 到 `v1.0.1`。
- 使用 `macos-26` 和 Xcode 26 运行测试、归档并导出 `release-testing` IPA。
- 创建同版本 GitHub Release，并附加一个同时支持 iPhone 和 iPad 的 IPA。

仓库需要配置以下 GitHub Actions Secrets：

| Secret | 内容 |
| --- | --- |
| `APPLE_TEAM_ID` | Apple Developer Team ID |
| `IOS_DISTRIBUTION_CERTIFICATE_BASE64` | Apple Distribution `.p12` 文件的 Base64 |
| `IOS_DISTRIBUTION_CERTIFICATE_PASSWORD` | 导出 `.p12` 时设置的密码 |
| `IOS_PROVISIONING_PROFILE_BASE64` | Bundle ID `com.xzygis.studybuddy.alarm` 对应的 Ad Hoc 描述文件 Base64 |

可在本机生成 Base64 后直接写入 GitHub Secrets，不要把证书、描述文件或密码放进仓库：

```bash
base64 < ~/path/to/distribution.p12 | pbcopy
base64 < ~/path/to/StudyAlarm.mobileprovision | pbcopy
```

Ad Hoc IPA 只能安装到描述文件中已登记的设备。工作流定义见
[`../.github/workflows/release-ios.yml`](../.github/workflows/release-ios.yml)。

## 自动验证

在仓库根目录运行：

```bash
bash ios/scripts/verify.sh
```

脚本执行：

1. 26 项核心逻辑测试。
2. 面向 iPhone / iPad 的无签名 iOS 26 编译。

当前已通过：

- 26 项测试，0 失败。
- `arm64-apple-ios26.0` 无签名构建成功。
- iOS 26.5 Simulator 构建、安装和启动成功。
- iPhone 17 Pro 首屏渲染成功：[截图](Screenshots/iphone-home.png)。
- iPad Pro 13-inch（M5）自适应首屏渲染成功：[截图](Screenshots/ipad-home.png)。
- 产物 `UIDeviceFamily` 同时包含 iPhone（1）和 iPad（2）。
- `NSAlarmKitUsageDescription` 已写入最终 Info.plist。

自动测试覆盖新增、编辑、删除、重复星期、每天、跨午夜时刻、整组启停、拒绝授权、部分失败回滚、取消失败、状态不一致、孤立闹钟清理、持久化失败和重启恢复。

工程包含 `StudyAlarmUITests` 交互用例，用于验证复制现有计划、改名保存和删除。当前机器能编译并签名该测试包，但 Xcode 的 `DTServiceHub/lockdown` 无法连接模拟器，因此没有将该用例标记为执行通过。

## 真机验收

模拟器结果不能替代以下测试。每项应分别在 iPhone 和 iPad 上验证，并将提醒设置在未来 2 至 3 分钟：

| 场景 | 操作与预期 | iPhone | iPad |
| --- | --- | --- | --- |
| 首次授权 | 首次启用出现权限框；允许后整组显示“已启用” | 未实测 | 未实测 |
| 拒绝授权 | 拒绝后显示未生效；系统设置重新允许后可重试 | 未实测 | 未实测 |
| 新增与多提醒 | 同组多个时刻分别按名称响铃 | 未实测 | 未实测 |
| 重复星期 | 今天被选中时响铃；未选中时不响铃 | 未实测 | 未实测 |
| 每天 | 七天重复规则均已提交并按日响铃 | 未实测 | 未实测 |
| 编辑已启用计划 | 旧名称/时间不再响，新规则按时响铃 | 未实测 | 未实测 |
| 关闭计划 | 整组旧提醒不再响铃 | 未实测 | 未实测 |
| 删除计划 | 删除后整组旧提醒不再响铃 | 未实测 | 未实测 |
| 锁屏 | 锁屏状态正常出现系统闹钟界面和声音 | 未实测 | 未实测 |
| 切到后台 | App 在后台时正常响铃 | 未实测 | 未实测 |
| 强制退出 App | 从多任务界面退出后正常响铃 | 未实测 | 未实测 |
| 断网 | 飞行模式或断网后正常响铃 | 未实测 | 未实测 |
| 设备重启 | 重启后不打开 App，原计划仍正常响铃 | 未实测 | 未实测 |
| 静音模式 | 开启静音后记录实际声音、震动与系统界面 | 未实测 | 未实测 |
| 专注模式 | 开启专注模式后记录实际声音、震动与系统界面 | 未实测 | 未实测 |

Apple AlarmKit 文档说明系统闹钟在必要时会突破静音与专注模式，但本项目没有连接真机，因此未将该文档结论标记为设备实测结果。设备关机期间无法响铃。

## 当前验证环境限制

CoreSimulator 1051.55 与 iOS 26.5 Runtime 已正常安装；iPhone 和 iPad 模拟器均可启动 App 并完成首屏视觉检查。当前 Xcode 的 `DTServiceHub/lockdown` 测试通道不可用，因此自动点击式 XCUITest 尚未执行。未连接真机，所以 AlarmKit 授权、系统响铃、锁屏、后台、退出 App、断网、重启、静音和专注模式仍需按上表真机验收。
