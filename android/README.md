# StudyBuddy for Android

原生 Android 学习计划闹钟，使用 Kotlin、Jetpack Compose 和 `AlarmManager`。最低支持 Android 8.0（API 26）。

## 功能

- 创建、编辑、复制和删除多组计划
- 为整组计划选择每周重复日期
- 每组包含多个具名提醒时间
- 今日时间轴仅展示已启用且适用于当天的提醒
- 手机底部双标签导航，平板自动使用多栏布局
- 数据仅保存在本机，无账号、网络或后台轮询

## 闹钟机制

每个提醒使用 `AlarmManager.setAlarmClock` 提交下一次精确触发，`PendingIntent` 直接指向 `AlarmActivity`，不走广播中转，规避 Android 12+ 后台启动限制。锁屏时 Activity 全屏弹出、唤醒屏幕、循环播放系统闹钟铃声和震动，用户按停止后 App 立即计算并提交下一周的同项提醒。

应用还会在以下事件后重建全部已启用闹钟：

- 设备启动完成
- 应用升级完成
- 系统时间或时区变化

启用计划前，应用会按 Android 版本检查并逐项引导：

- 通知权限
- 精确闹钟权限
- 全屏提醒权限
- 电池优化不受限

部分厂商（Xiaomi、OPPO 等）会额外限制后台自启动与保活，首次安装后建议在真机做一次锁屏试响，并在系统设置里允许 StudyBuddy 自启动。

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
