# StudyBuddy for Android

原生 Android 学习计划闹钟，使用 Kotlin、Jetpack Compose 和系统 `AlarmClock` Intent。最低支持 Android 8.0（API 26）。

## 功能

- 创建、编辑、复制和删除多组计划
- 为整组计划选择每周重复日期
- 每组包含多个具名提醒时间
- 今日时间轴仅展示已启用且适用于当天的提醒
- 手机底部双标签导航，平板自动使用多栏布局
- 数据仅保存在本机，无账号、网络或后台轮询

## 闹钟机制

启用计划时，每个提醒通过 `AlarmClock.ACTION_SET_ALARM` 写入设备自带的系统时钟，并携带：

- 小时和分钟
- 每周重复日期
- `StudyBuddy · 计划名 · 提醒名` 标签
- 震动开关

闹钟由系统时钟负责持久化、开机恢复、锁屏显示和响铃，不依赖 StudyBuddy 进程、后台服务或通知权限。

Android 公共 API 没有删除系统时钟闹钟的操作。`ACTION_DISMISS_ALARM` 对重复闹钟只跳过下一次，并不会删除或停用整组重复规则。因此：

- 创建可由 StudyBuddy 自动完成
- 修改计划会创建新闹钟，旧闹钟需手动删除
- 停用或删除计划后，App 会打开系统时钟，由用户删除带 `StudyBuddy` 标签的旧闹钟

这种设计优先保证响铃可靠性，并避免依赖厂商后台保活策略。

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
