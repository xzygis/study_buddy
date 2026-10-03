# StudyBuddy for Android

原生 Android 学习计划闹钟，使用 Kotlin、Jetpack Compose 和 `AlarmManager`。最低支持 Android 8.0（API 26）。

## 功能

- 创建、编辑、复制和删除多组计划
- 为整组计划选择每周重复日期
- 每组包含多个具名提醒时间
- 每个计划可选择系统提供的闹钟铃声
- 今日时间轴仅展示已启用且适用于当天的提醒
- 今天页展示下一次调度和最近一次触发结果
- 长按计划卡片可查看调度、接收、服务和音频阶段的本地诊断日志
- 手机底部双标签导航，平板自动使用多栏布局
- 数据仅保存在本机，无账号、网络或后台轮询

## 闹钟机制

每个提醒使用 `AlarmManager.setAlarmClock` 提交下一次精确触发。到点后由 `AlarmReceiver` 在系统授予的后台启动窗口内直接拉起锁屏闹钟页，并立即启动前台 `AlarmRingingService`；Service 持有 WakeLock、循环播放计划选择的铃声和震动，全屏闹钟通知作为页面启动兜底。自定义铃声无法读取时会自动回退系统默认闹钟铃声。Receiver 同时提交下一周的同项提醒。

应用还会在以下事件后重建全部已启用闹钟：

- 设备启动完成
- 应用升级完成
- 系统时间或时区变化

启用计划前，应用会按 Android 版本检查并逐项引导：

- 通知权限
- 精确闹钟权限
- 全屏提醒权限
- 电池优化不受限
- 厂商系统管家的自启动和后台运行权限

对于 Xiaomi/Redmi/POCO、Huawei/Honor、OPPO/Realme/OnePlus、vivo/iQOO 等设备，首次启用计划时 App 会尝试打开厂商自启动管理页；“今天”页也会保留检查入口。Android 无法读取这些私有开关的实际状态，因此仍需用户手动确认。

“今天”页的后台运行设置会始终保留电池优化入口，并在支持的厂商设备上同时提供自启动入口。电池优化状态可由系统 API 检测；自启动属于厂商私有能力，只能提示用户自行确认。

每次闹钟调度都会生成独立触发实例，并依次记录 `scheduledAt`、`triggerAt`、`receiverAt`、`serviceAt`、`audioAt`、`screenAt` 或 `error`。日志仅保存在本机，最多保留最近 200 条。

锁屏或息屏状态下，闹钟广播会直接展示全屏闹钟页面；系统全屏通知负责兜底。页面使用 `showWhenLocked` 和 `turnScreenOn` 覆盖锁屏，不会解除密码或进入应用主界面；用户可直接查看提醒内容并停止闹钟。

系统设置中的“强行停止”会按 Android 安全模型清空应用闹钟，任何第三方 App 都无法绕过；重新打开 StudyBuddy 后会重新核对并安装闹钟。

## 本地构建

需要 JDK 17 和 Android SDK 34：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :android:testDebugUnitTest :android:lintDebug :android:assembleDebug
```

可直接安装的 Debug APK 位于：

```text
android/build/outputs/apk/debug/android-debug.apk
```

安装到已连接设备：

```bash
adb install -r android/build/outputs/apk/debug/android-debug.apk
```

## 发布签名

Android 签名不需要付费。正式 APK 必须长期使用同一份 keystore，否则后续版本无法覆盖安装。

建议将密钥保存在用户目录：

```bash
mkdir -p ~/.config/studybuddy
keytool -genkeypair \
  -keystore ~/.config/studybuddy/studybuddy-release.jks \
  -alias studybuddy \
  -keyalg RSA -keysize 4096 -validity 10000
```

本地构建签名 APK：

```bash
export ANDROID_KEYSTORE_PATH="$HOME/.config/studybuddy/studybuddy-release.jks"
export ANDROID_KEYSTORE_PASSWORD='...'
export ANDROID_KEY_ALIAS='studybuddy'
export ANDROID_KEY_PASSWORD='...'
./gradlew :android:assembleRelease
```

GitHub Actions 发布需配置以下仓库 Secrets：

- `ANDROID_KEYSTORE_BASE64`：`base64 < ~/.config/studybuddy/studybuddy-release.jks | tr -d '\n'`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

配置完成后，以下任一事件都会生成签名 APK 并发布到 GitHub Releases：

- Pull Request 合并到 `main`
- 手动运行 `Build & Release Android APK`
- 推送 `android-v1.0.0` 格式的标签

首次自动发布使用 `android-v1.0.0`，之后按已有 Android 标签递增补丁版本。不要丢失 keystore 和密码，它们是后续覆盖升级的唯一签名身份。
