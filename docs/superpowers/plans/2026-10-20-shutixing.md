# 刷题星（ShuaTiXing）Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个完全离线的 Android 刷题 App：导入文字版 PDF 生成题库，按每日题量自动组卷、即时判分、错题末尾循环重测，并以改良艾宾浩斯间隔复习。

**Architecture:** 单 module（`:app`）分层：`parser`（PDF 文本 → 题目，纯 Kotlin 可单测）、`domain`（判分/调度/组卷，纯函数）、`data`（Room + DataStore + JSON 导入导出）、`ui`（Compose + ViewModel）。PdfBox-Android 只在 `PdfTextExtractor` 中接触 Android API，其余解析逻辑保持 JVM 可测。

**Tech Stack:** Kotlin 2.0.21、Jetpack Compose (BOM 2024.12.01, Material 3)、Room 2.6.1 (KSP)、DataStore 1.1.1、Navigation Compose 2.8.5、kotlinx.serialization 1.7.3、PdfBox-Android 2.0.27.0、AGP 8.7.3 / Gradle 8.9 / JDK 17。

---

## 0. 环境事实与全局约束

已在本机确认（2026-10-20）：

- Ubuntu 22.04，28 核 / 31GB 内存 / 360GB 可用磁盘
- 系统只有 JDK 11 → 需要独立安装 JDK 17（**免 sudo**，装在 `~/.local/opt`）
- 无 Android SDK → 用命令行工具装在 `~/android-sdk`
- 无 Gradle → 用独立 Gradle 8.9 生成 wrapper
- `/dev/kvm` 存在，但当前用户不在 `kvm` 组，`sudo` 需要密码 → **优先使用 USB 真机**；模拟器仅在用户手动 `sudo usermod -aG kvm $USER` 并重新登录后可用
- 网络可访问 dl.google.com、maven central、services.gradle.org（已验证）

**UI 质量硬性要求（用户明确指出“界面做精美一点”）：**

- 统一设计系统：色彩、字体层级、圆角、间距、动效全部来自 `ui/theme`，禁止页面内硬编码颜色/字号
- 每个页面必须有：空态、加载态、错误态；深色模式完整适配
- 动效：进度环动画、选项选中弹性反馈、判对/判错动效（正确脉冲、错误抖动）、页面切换淡入滑动、列表错峰入场
- 触感反馈：选中、提交、判对/判错使用 `HapticFeedback`
- 视觉验收：每个主要页面在真机/模拟器截图检查，确保无文字溢出、无重叠、对齐稳定
- 中文字体使用系统默认（`FontFamily.Default`），数字统计使用 tabular figures

**通用命令（每个任务在项目根目录 `/home/jh/Vs_coding/刷题星` 执行）：**

```bash
export JAVA_HOME=$HOME/.local/opt/jdk-17
export ANDROID_HOME=$HOME/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$HOME/.local/opt/gradle-8.9/bin:$PATH
```

---

## 1. 文件结构总览

```text
刷题星/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── local.properties                 # sdk.dir，不提交
├── .gitignore
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── res/values/{strings.xml,colors.xml,themes.xml}
│       │   ├── res/drawable/ic_launcher_foreground.xml
│       │   ├── res/mipmap-anydpi-v26/ic_launcher.xml
│       │   └── java/com/shutixing/app/
│       │       ├── MainActivity.kt
│       │       ├── ShuatiXingApp.kt
│       │       ├── di/AppContainer.kt
│       │       ├── domain/model/{Models.kt,Stats.kt}
│       │       ├── domain/answer/AnswerChecker.kt
│       │       ├── domain/review/Scheduler.kt
│       │       ├── domain/session/{SessionBuilder.kt,SessionEngine.kt}
│       │       ├── domain/stats/StatsCalculator.kt
│       │       ├── parser/model/ParserModels.kt
│       │       ├── parser/{Normalizer.kt,StemHasher.kt,QuestionParser.kt,PdfTextExtractor.kt}
│       │       ├── data/db/{Entities.kt,Daos.kt,Mappers.kt,AppDatabase.kt}
│       │       ├── data/repository/{BankRepository.kt,PracticeRepository.kt,StatsRepository.kt,SettingsRepository.kt}
│       │       ├── data/export/{BankJson.kt,BankExporter.kt,BankImporter.kt}
│       │       └── ui/
│       │           ├── theme/{Color.kt,Type.kt,Shape.kt,Motion.kt,Theme.kt}
│       │           ├── components/{ProgressRing.kt,Cards.kt,OptionRow.kt,EmptyState.kt,Badges.kt}
│       │           ├── navigation/{Routes.kt,AppNav.kt}
│       │           ├── home/{HomeScreen.kt,HomeViewModel.kt}
│       │           ├── practice/{PracticeScreen.kt,PracticeViewModel.kt,SessionStore.kt}
│       │           ├── result/ResultScreen.kt
│       │           ├── wrongbook/{WrongBookScreen.kt,WrongBookViewModel.kt}
│       │           ├── banks/{BanksScreen.kt,BankListViewModel.kt}
│       │           ├── bankdetail/{BankDetailScreen.kt,BankDetailViewModel.kt}
│       │           ├── importwizard/{ImportScreen.kt,ImportViewModel.kt}
│       │           ├── stats/{StatsScreen.kt,StatsViewModel.kt}
│       │           └── settings/{SettingsScreen.kt,SettingsViewModel.kt}
│       └── test/java/com/shutixing/app/
│           ├── domain/{ModelsTest.kt,AnswerCheckerTest.kt,SchedulerTest.kt,SessionBuilderTest.kt,SessionEngineTest.kt,StatsCalculatorTest.kt}
│           ├── parser/{NormalizerTest.kt,QuestionParserTest.kt}
│           └── data/{BankJsonTest.kt,MappersTest.kt}
│       └── androidTest/java/com/shutixing/app/
│           ├── data/DatabaseTest.kt
│           └── parser/PdfTextExtractorTest.kt
└── docs/superpowers/{specs,plans}/…
```

---

## Phase 0：工具链与项目骨架

### Task 1: 安装 JDK 17、Android SDK、Gradle

**Files:**
- Modify: `~/.bashrc`（追加环境变量）

- [ ] **Step 1: 安装 Temurin JDK 17（免 sudo）**

```bash
mkdir -p ~/.local/opt && cd ~/.local/opt
curl -L --fail -o jdk17.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
tar xzf jdk17.tar.gz
JDK_DIR=$(ls -d jdk-17* | head -1)
mv "$JDK_DIR" jdk-17
rm jdk17.tar.gz
~/.local/opt/jdk-17/bin/java -version
```

预期输出包含 `openjdk version "17.0.20.1"`。

- [ ] **Step 2: 安装 Android 命令行工具与 SDK 包**

```bash
mkdir -p ~/android-sdk/cmdline-tools
curl -L --fail -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q /tmp/cmdline-tools.zip -d ~/android-sdk/cmdline-tools
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest
export JAVA_HOME=$HOME/.local/opt/jdk-17
export ANDROID_HOME=$HOME/android-sdk
yes | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
~/android-sdk/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

预期：`sdkmanager --list_installed` 显示 `platforms;android-35`、`build-tools;35.0.0`、`platform-tools`。

- [ ] **Step 3: 安装 Gradle 8.9**

```bash
cd ~/.local/opt
curl -L --fail -o gradle.zip https://services.gradle.org/distributions/gradle-8.9-bin.zip
unzip -q gradle.zip
rm gradle.zip
~/.local/opt/gradle-8.9/bin/gradle --version
```

预期输出 `Gradle 8.9`，且 `JVM: 17.0.20.1`（需先 `export JAVA_HOME`）。

- [ ] **Step 4: 持久化环境变量**

```bash
cat >> ~/.bashrc <<'EOF'

# 刷题星 Android 开发环境
export JAVA_HOME=$HOME/.local/opt/jdk-17
export ANDROID_HOME=$HOME/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$HOME/.local/opt/gradle-8.9/bin:$PATH
EOF
source ~/.bashrc
```

- [ ] **Step 5: 连接调试设备（二选一）**

真机（推荐）：手机开启“开发者选项 → USB 调试”，插线后：

```bash
adb devices
```

预期列表中设备状态为 `device`。

模拟器（需要 `/dev/kvm` 权限）：若 `adb devices` 为空且用户愿意执行一次 `sudo usermod -aG kvm $USER` 并重新登录，则：

```bash
sdkmanager "emulator" "system-images;android-35;google_apis;x86_64"
avdmanager create avd -n shutixing -k "system-images;android-35;google_apis;x86_64" -d pixel_6
emulator -avd shutixing -no-snapshot -no-boot-anim -gpu swiftshader_indirect &
adb wait-for-device
```

预期 `adb devices` 出现 `emulator-5554  device`。若无 KVM 权限，跳过本步，后续 UI 验收使用真机。

- [ ] **Step 6: 提交（本任务无可提交文件，跳过）**

---

### Task 2: 创建 Gradle + Compose 项目骨架

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.gitignore`
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/{strings.xml,colors.xml,themes.xml}`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/java/com/shutixing/app/MainActivity.kt`
- Create: `app/src/main/java/com/shutixing/app/ShuatiXingApp.kt`

- [ ] **Step 1: 写根构建文件**

`settings.gradle.kts`：

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "ShuaTiXing"
include(":app")
```

`build.gradle.kts`：

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle.properties`：

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

`.gitignore`：

```gitignore
*.iml
.gradle/
/local.properties
/.idea/
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
/app/build
/app/release
*.jks
*.keystore
```

`local.properties`（不提交）：

```bash
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

- [ ] **Step 2: 写版本目录 `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
composeBom = "2024.12.01"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
navigation = "2.8.5"
room = "2.6.1"
datastore = "1.1.1"
coroutines = "1.9.0"
serialization = "1.7.3"
pdfbox = "2.0.27.0"
material = "1.12.0"
junit = "4.13.2"
androidxJunit = "1.2.1"
espresso = "3.6.1"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
pdfbox-android = { module = "com.tom-roush:pdfbox-android", version.ref = "pdfbox" }
google-material = { module = "com.google.android.material:material", version.ref = "material" }
junit = { module = "junit:junit", version.ref = "junit" }
androidx-junit = { module = "androidx.test.ext:junit", version.ref = "androidxJunit" }
androidx-test-runner = { module = "androidx.test:runner", version = "1.6.2" }
androidx-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 3: 写 `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.shutixing.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shutixing.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.pdfbox.android)
    implementation(libs.google.material)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

`app/proguard-rules.pro` 保持空文件即可（release 未开启混淆）。

- [ ] **Step 4: 写 Manifest、资源与启动页**

`app/src/main/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".ShuatiXingApp"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.ShuaTiXing">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`res/values/strings.xml`：

```xml
<resources>
    <string name="app_name">刷题星</string>
</resources>
```

`res/values/colors.xml`：

```xml
<resources>
    <color name="ic_launcher_background">#1B4B8F</color>
    <color name="launch_background">#F6F7FB</color>
</resources>
```

`res/values/themes.xml`：

```xml
<resources>
    <style name="Theme.ShuaTiXing" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="android:windowBackground">@color/launch_background</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

`res/drawable/ic_launcher_foreground.xml`（星形 + 对勾，108dp 画布）：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M54,24 L62.2,44.4 L84,46.1 L67.4,60.2 L72.6,81.4 L54,69.8 L35.4,81.4 L40.6,60.2 L24,46.1 L45.8,44.4 Z" />
</vector>
```

`res/mipmap-anydpi-v26/ic_launcher.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

`ShuatiXingApp.kt`（Task 18 扩展为持有容器）：

```kotlin
package com.shutixing.app

import android.app.Application

class ShuatiXingApp : Application()
```

`MainActivity.kt`（临时占位，Task 24 替换）：

```kotlin
package com.shutixing.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("刷题星")
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 5: 生成 Gradle wrapper 并构建**

```bash
~/.local/opt/gradle-8.9/bin/gradle wrapper --gradle-version 8.9
./gradlew :app:assembleDebug
```

预期：`BUILD SUCCESSFUL`，产物在 `app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 6: 安装并启动占位应用**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.shutixing.app/.MainActivity
```

预期：手机/模拟器显示“刷题星”三个字。

- [ ] **Step 7: 提交**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/libs.versions.toml .gitignore \
  app/build.gradle.kts app/proguard-rules.pro app/src/main
git commit -m "build: scaffold Android Compose project"
```

---

## Phase 1：领域核心（纯 Kotlin，JVM 单测驱动）

### Task 3: 领域模型与派生状态

**Files:**
- Create: `app/src/main/java/com/shutixing/app/domain/model/Models.kt`
- Create: `app/src/main/java/com/shutixing/app/domain/model/Stats.kt`
- Test: `app/src/test/java/com/shutixing/app/domain/ModelsTest.kt`

- [ ] **Step 1: 写失败测试**

`ModelsTest.kt`：

```kotlin
package com.shutixing.app.domain

import com.shutixing.app.domain.model.ReviewState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ModelsTest {
    private val today = LocalDate.of(2026, 10, 20)

    private fun state(level: Int = 0, consecutive: Int = 0, wrong: Int = 0) = ReviewState(
        questionId = "q1",
        level = level,
        nextReviewDate = today,
        consecutiveCorrect = consecutive,
        wrongCount = wrong,
    )

    @Test
    fun `wrong question stays in wrong book until two consecutive correct`() {
        assertTrue(state(wrong = 1, consecutive = 0).isInWrongBook)
        assertTrue(state(wrong = 1, consecutive = 1).isInWrongBook)
        assertFalse(state(wrong = 1, consecutive = 2).isInWrongBook)
    }

    @Test
    fun `never wrong question is not in wrong book`() {
        assertFalse(state(wrong = 0, consecutive = 5).isInWrongBook)
    }

    @Test
    fun `level six is mastered`() {
        assertFalse(state(level = 5).isMastered)
        assertTrue(state(level = 6).isMastered)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.domain.ModelsTest"`
Expected: 编译失败，`Unresolved reference: ReviewState`。

- [ ] **Step 3: 写模型**

`Models.kt`：

```kotlin
package com.shutixing.app.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

enum class QuestionType { SINGLE, MULTIPLE, JUDGE }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class Option(val key: String, val text: String)

data class Question(
    val id: String,
    val bankId: String,
    val type: QuestionType,
    val stem: String,
    val options: List<Option> = emptyList(),
    val answer: String,
    val explanation: String? = null,
    val orderIndex: Int = 0,
    val stemHash: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = 0L,
)

object ReviewLevel {
    const val MASTERED = 6
}

data class ReviewState(
    val questionId: String,
    val level: Int = 0,
    val nextReviewDate: LocalDate,
    val consecutiveCorrect: Int = 0,
    val wrongCount: Int = 0,
    val lastAnsweredAt: Long? = null,
) {
    val isInWrongBook: Boolean get() = wrongCount > 0 && consecutiveCorrect < 2
    val isMastered: Boolean get() = level >= ReviewLevel.MASTERED
}

enum class AttemptMode { DAILY, RETEST, WRONG_BOOK }

data class Attempt(
    val id: Long = 0L,
    val questionId: String,
    val localDate: LocalDate,
    val answeredAt: Long,
    val isCorrect: Boolean,
    val selectedAnswer: String,
    val durationMs: Long,
    val mode: AttemptMode,
)

data class QuestionWithState(
    val question: Question,
    val review: ReviewState,
)

data class Bank(
    val id: String,
    val name: String,
    val sourceFileName: String? = null,
    val createdAt: Long = 0L,
    val sortOrder: Int = 0,
)
```

`Stats.kt`：

```kotlin
package com.shutixing.app.domain.model

import java.time.LocalDate

data class DailyStat(
    val date: LocalDate,
    val answered: Int,
    val correct: Int,
) {
    val accuracy: Float get() = if (answered == 0) 0f else correct.toFloat() / answered
}

data class BankMastery(
    val bankId: String,
    val bankName: String,
    val total: Int,
    val mastered: Int,
) {
    val mastery: Float get() = if (total == 0) 0f else mastered.toFloat() / total
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.domain.ModelsTest"`
Expected: `BUILD SUCCESSFUL`，3 个测试通过。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/shutixing/app/domain/model app/src/test/java/com/shutixing/app/domain/ModelsTest.kt
git commit -m "feat(domain): add core models and derived review state"
```

---

### Task 4: 答案归一化与判分

**Files:**
- Create: `app/src/main/java/com/shutixing/app/domain/answer/AnswerChecker.kt`
- Test: `app/src/test/java/com/shutixing/app/domain/AnswerCheckerTest.kt`

- [ ] **Step 1: 写失败测试**

`AnswerCheckerTest.kt`：

```kotlin
package com.shutixing.app.domain

import com.shutixing.app.domain.answer.AnswerChecker
import com.shutixing.app.domain.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerCheckerTest {
    @Test
    fun `single choice normalizes case and letters`() {
        assertEquals("B", AnswerChecker.normalize(QuestionType.SINGLE, " b "))
    }

    @Test
    fun `multiple choice normalizes order and separators`() {
        assertEquals("AC", AnswerChecker.normalize(QuestionType.MULTIPLE, "C、a"))
        assertEquals("ABC", AnswerChecker.normalize(QuestionType.MULTIPLE, "B A C"))
    }

    @Test
    fun `judge answers map to T and F`() {
        assertEquals("T", AnswerChecker.normalize(QuestionType.JUDGE, "对"))
        assertEquals("T", AnswerChecker.normalize(QuestionType.JUDGE, "√"))
        assertEquals("T", AnswerChecker.normalize(QuestionType.JUDGE, "正确"))
        assertEquals("F", AnswerChecker.normalize(QuestionType.JUDGE, "错"))
        assertEquals("F", AnswerChecker.normalize(QuestionType.JUDGE, "×"))
        assertEquals("F", AnswerChecker.normalize(QuestionType.JUDGE, "错误"))
    }

    @Test
    fun `multiple choice grading is order insensitive`() {
        assertTrue(AnswerChecker.isCorrect(QuestionType.MULTIPLE, "AC", "CA"))
        assertFalse(AnswerChecker.isCorrect(QuestionType.MULTIPLE, "AC", "A"))
    }

    @Test
    fun `empty answer is never correct`() {
        assertFalse(AnswerChecker.isCorrect(QuestionType.SINGLE, "", ""))
        assertFalse(AnswerChecker.isCorrect(QuestionType.JUDGE, "", ""))
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.domain.AnswerCheckerTest"`
Expected: 编译失败，`Unresolved reference: AnswerChecker`。

- [ ] **Step 3: 实现**

`AnswerChecker.kt`：

```kotlin
package com.shutixing.app.domain.answer

import com.shutixing.app.domain.model.QuestionType

object AnswerChecker {
    private val judgeTrue = setOf("对", "正确", "√", "T", "TRUE", "是", "Y", "YES")
    private val judgeFalse = setOf("错", "错误", "×", "X", "F", "FALSE", "否", "N", "NO")

    fun normalize(type: QuestionType, raw: String): String {
        val trimmed = raw.trim()
        return when (type) {
            QuestionType.JUDGE -> when (trimmed.uppercase()) {
                in judgeTrue -> "T"
                in judgeFalse -> "F"
                else -> trimmed.uppercase()
            }
            QuestionType.SINGLE, QuestionType.MULTIPLE ->
                trimmed.uppercase().filter { it in 'A'..'H' }.toSortedSet().joinToString("")
        }
    }

    fun isCorrect(type: QuestionType, answer: String, selected: String): Boolean {
        val normalizedAnswer = normalize(type, answer)
        return normalizedAnswer.isNotEmpty() && normalizedAnswer == normalize(type, selected)
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.domain.AnswerCheckerTest"`
Expected: `BUILD SUCCESSFUL`，5 个测试通过。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/shutixing/app/domain/answer app/src/test/java/com/shutixing/app/domain/AnswerCheckerTest.kt
git commit -m "feat(domain): normalize and grade answers"
```

---

### Task 5: 改良艾宾浩斯调度器

**Files:**
- Create: `app/src/main/java/com/shutixing/app/domain/review/Scheduler.kt`
- Test: `app/src/test/java/com/shutixing/app/domain/SchedulerTest.kt`

- [ ] **Step 1: 写失败测试**

`SchedulerTest.kt`：

```kotlin
package com.shutixing.app.domain

import com.shutixing.app.domain.model.ReviewState
import com.shutixing.app.domain.review.Scheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SchedulerTest {
    private val today = LocalDate.of(2026, 10, 20)
    private val now = 1_761_000_000_000L

    private fun state(level: Int = 0, consecutive: Int = 0, wrong: Int = 0) = ReviewState(
        questionId = "q1",
        level = level,
        nextReviewDate = today,
        consecutiveCorrect = consecutive,
        wrongCount = wrong,
    )

    @Test
    fun `first correct answer moves to level one and schedules tomorrow`() {
        val next = Scheduler.onAnswer(state(), correct = true, today = today, answeredAt = now)
        assertEquals(1, next.level)
        assertEquals(1, next.consecutiveCorrect)
        assertEquals(today.plusDays(1), next.nextReviewDate)
        assertEquals(now, next.lastAnsweredAt)
    }

    @Test
    fun `correct answers follow the interval table`() {
        assertEquals(today.plusDays(2), Scheduler.onAnswer(state(level = 1), true, today, now).nextReviewDate)
        assertEquals(today.plusDays(4), Scheduler.onAnswer(state(level = 2), true, today, now).nextReviewDate)
        assertEquals(today.plusDays(7), Scheduler.onAnswer(state(level = 3), true, today, now).nextReviewDate)
        assertEquals(today.plusDays(15), Scheduler.onAnswer(state(level = 4), true, today, now).nextReviewDate)
        assertEquals(today.plusDays(30), Scheduler.onAnswer(state(level = 5), true, today, now).nextReviewDate)
    }

    @Test
    fun `mastered question stays at level six`() {
        val next = Scheduler.onAnswer(state(level = 6, consecutive = 5), true, today, now)
        assertEquals(6, next.level)
        assertEquals(today.plusDays(30), next.nextReviewDate)
        assertTrue(next.isMastered)
    }

    @Test
    fun `wrong answer resets level and schedules tomorrow`() {
        val next = Scheduler.onAnswer(state(level = 4, consecutive = 3, wrong = 1), false, today, now)
        assertEquals(0, next.level)
        assertEquals(0, next.consecutiveCorrect)
        assertEquals(2, next.wrongCount)
        assertEquals(today.plusDays(1), next.nextReviewDate)
    }

    @Test
    fun `cross month scheduling uses real calendar`() {
        val oct31 = LocalDate.of(2026, 10, 31)
        val next = Scheduler.onAnswer(state(level = 1), true, oct31, now)
        assertEquals(LocalDate.of(2026, 11, 2), next.nextReviewDate)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.domain.SchedulerTest"`
Expected: 编译失败，`Unresolved reference: Scheduler`。

- [ ] **Step 3: 实现**

`Scheduler.kt`：

```kotlin
package com.shutixing.app.domain.review

import com.shutixing.app.domain.model.ReviewLevel
import com.shutixing.app.domain.model.ReviewState
import java.time.LocalDate

object Scheduler {
    /** 索引 0..5 对应等级 1..6 的间隔天数。 */
    val INTERVALS = intArrayOf(1, 2, 4, 7, 15, 30)

    fun intervalDaysForLevel(level: Int): Int =
        INTERVALS[(level - 1).coerceIn(0, INTERVALS.size - 1)]

    fun onAnswer(
        state: ReviewState,
        correct: Boolean,
        today: LocalDate,
        answeredAt: Long,
    ): ReviewState = if (correct) {
        val newLevel = (state.level + 1).coerceAtMost(ReviewLevel.MASTERED)
        state.copy(
            level = newLevel,
            consecutiveCorrect = state.consecutiveCorrect + 1,
            nextReviewDate = today.plusDays(intervalDaysForLevel(newLevel).toLong()),
            lastAnsweredAt = answeredAt,
        )
    } else {
        state.copy(
            level = 0,
            consecutiveCorrect = 0,
            wrongCount = state.wrongCount + 1,
            nextReviewDate = today.plusDays(1),
            lastAnsweredAt = answeredAt,
        )
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.domain.SchedulerTest"`
Expected: `BUILD SUCCESSFUL`，5 个测试通过。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/shutixing/app/domain/review app/src/test/java/com/shutixing/app/domain/SchedulerTest.kt
git commit -m "feat(domain): add Ebbinghaus review scheduler"
```

---

### Task 6: 每日组卷

**Files:**
- Create: `app/src/main/java/com/shutixing/app/domain/session/SessionBuilder.kt`
- Test: `app/src/test/java/com/shutixing/app/domain/SessionBuilderTest.kt`

- [ ] **Step 1: 写失败测试**

`SessionBuilderTest.kt`：

```kotlin
package com.shutixing.app.domain

import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.domain.model.QuestionWithState
import com.shutixing.app.domain.model.ReviewState
import com.shutixing.app.domain.session.SessionBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SessionBuilderTest {
    private val today = LocalDate.of(2026, 10, 20)

    private fun question(id: String, bank: String = "b1", order: Int = 0) = Question(
        id = id,
        bankId = bank,
        type = QuestionType.SINGLE,
        stem = "stem $id",
        options = listOf(com.shutixing.app.domain.model.Option("A", "a")),
        answer = "A",
        orderIndex = order,
    )

    private fun withState(
        id: String,
        wrong: Int = 0,
        next: LocalDate = today,
        consecutive: Int = 0,
    ) = QuestionWithState(
        question = question(id),
        review = ReviewState(
            questionId = id,
            nextReviewDate = next,
            wrongCount = wrong,
            consecutiveCorrect = consecutive,
        ),
    )

    @Test
    fun `wrong book questions come first then due reviews then new questions`() {
        val queue = SessionBuilder.build(
            dueWrong = listOf(withState("wrong1", wrong = 2)),
            dueReview = listOf(withState("due1"), withState("due2", next = today.minusDays(1))),
            newQuestions = listOf(question("new1"), question("new2")),
            target = 10,
            today = today,
        )
        assertEquals(listOf("wrong1", "due2", "due1", "new1", "new2"), queue.map { it.id })
    }

    @Test
    fun `target truncates the queue`() {
        val queue = SessionBuilder.build(
            dueWrong = emptyList(),
            dueReview = listOf(withState("due1")),
            newQuestions = listOf(question("new1"), question("new2")),
            target = 2,
            today = today,
        )
        assertEquals(listOf("due1", "new1"), queue.map { it.id })
    }

    @Test
    fun `no duplicates when a question appears in two pools`() {
        val q = withState("q1", wrong = 1)
        val queue = SessionBuilder.build(
            dueWrong = listOf(q),
            dueReview = listOf(q),
            newQuestions = emptyList(),
            target = 10,
            today = today,
        )
        assertEquals(listOf("q1"), queue.map { it.id })
    }

    @Test
    fun `zero target returns empty queue`() {
        val queue = SessionBuilder.build(
            dueWrong = listOf(withState("wrong1")),
            dueReview = listOf(withState("due1")),
            newQuestions = listOf(question("new1")),
            target = 0,
            today = today,
        )
        assertTrue(queue.isEmpty())
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.domain.SessionBuilderTest"`
Expected: 编译失败，`Unresolved reference: SessionBuilder`。

- [ ] **Step 3: 实现**

`SessionBuilder.kt`：

```kotlin
package com.shutixing.app.domain.session

import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionWithState
import java.time.LocalDate

object SessionBuilder {
    fun build(
        dueWrong: List<QuestionWithState>,
        dueReview: List<QuestionWithState>,
        newQuestions: List<Question>,
        target: Int,
        today: LocalDate,
    ): List<Question> {
        if (target <= 0) return emptyList()
        val wrongSorted = dueWrong.sortedWith(compareBy({ it.review.nextReviewDate }, { it.question.bankId }, { it.question.orderIndex }))
        val reviewSorted = dueReview.sortedWith(compareBy({ it.review.nextReviewDate }, { it.question.bankId }, { it.question.orderIndex }))
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Question>()
        fun add(candidate: Question) {
            if (result.size >= target) return
            if (seen.add(candidate.id)) result.add(candidate)
        }
        wrongSorted.forEach { add(it.question) }
        reviewSorted.forEach { add(it.question) }
        newQuestions.forEach { add(it) }
        return result
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.domain.SessionBuilderTest"`
Expected: `BUILD SUCCESSFUL`，4 个测试通过。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/shutixing/app/domain/session app/src/test/java/com/shutixing/app/domain/SessionBuilderTest.kt
git commit -m "feat(domain): build daily practice queue by priority"
```

---

### Task 7: 统计计算（连续打卡、正确率）

**Files:**
- Create: `app/src/main/java/com/shutixing/app/domain/stats/StatsCalculator.kt`
- Test: `app/src/test/java/com/shutixing/app/domain/StatsCalculatorTest.kt`

- [ ] **Step 1: 写失败测试**

`StatsCalculatorTest.kt`：

```kotlin
package com.shutixing.app.domain

import com.shutixing.app.domain.model.DailyStat
import com.shutixing.app.domain.stats.StatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsCalculatorTest {
    private val today = LocalDate.of(2026, 10, 20)

    @Test
    fun `streak counts consecutive days ending today`() {
        val dates = listOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(3, StatsCalculator.streak(dates, today))
    }

    @Test
    fun `streak may end yesterday`() {
        val dates = listOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, StatsCalculator.streak(dates, today))
    }

    @Test
    fun `streak stops at a gap`() {
        val dates = listOf(today, today.minusDays(2), today.minusDays(3))
        assertEquals(1, StatsCalculator.streak(dates, today))
    }

    @Test
    fun `no activity means zero streak`() {
        assertEquals(0, StatsCalculator.streak(emptyList(), today))
    }

    @Test
    fun `accuracy handles empty and non-empty`() {
        assertEquals(0f, StatsCalculator.accuracy(emptyList()), 0.0001f)
        val stats = listOf(DailyStat(today, answered = 10, correct = 7))
        assertEquals(0.7f, StatsCalculator.accuracy(stats), 0.0001f)
    }

    @Test
    fun `accuracy aggregates across days`() {
        val stats = listOf(
            DailyStat(today, answered = 10, correct = 8),
            DailyStat(today.minusDays(1), answered = 10, correct = 6),
        )
        assertEquals(0.7f, StatsCalculator.accuracy(stats), 0.0001f)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.domain.StatsCalculatorTest"`
Expected: 编译失败，`Unresolved reference: StatsCalculator`。

- [ ] **Step 3: 实现**

`StatsCalculator.kt`：

```kotlin
package com.shutixing.app.domain.stats

import com.shutixing.app.domain.model.DailyStat
import java.time.LocalDate

object StatsCalculator {
    fun streak(activeDates: Collection<LocalDate>, today: LocalDate): Int {
        val set = activeDates.toSet()
        var cursor = when {
            today in set -> today
            today.minusDays(1) in set -> today.minusDays(1)
            else -> return 0
        }
        var count = 0
        while (cursor in set) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    fun accuracy(stats: List<DailyStat>): Float {
        val answered = stats.sumOf { it.answered }
        if (answered == 0) return 0f
        return stats.sumOf { it.correct }.toFloat() / answered
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.domain.StatsCalculatorTest"`
Expected: `BUILD SUCCESSFUL`，6 个测试通过。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/shutixing/app/domain/stats app/src/test/java/com/shutixing/app/domain/StatsCalculatorTest.kt
git commit -m "feat(domain): add streak and accuracy calculations"
```

---

## Phase 2：PDF 解析

### Task 8: 文本清洗（页眉页脚、页码、全半角）

**Files:**
- Create: `app/src/main/java/com/shutixing/app/parser/model/ParserModels.kt`
- Create: `app/src/main/java/com/shutixing/app/parser/Normalizer.kt`
- Test: `app/src/test/java/com/shutixing/app/parser/NormalizerTest.kt`

- [ ] **Step 1: 定义解析模型**

`ParserModels.kt`：

```kotlin
package com.shutixing.app.parser.model

import com.shutixing.app.domain.model.Option
import com.shutixing.app.domain.model.QuestionType

data class TextLine(
    val text: String,
    val page: Int,
    val top: Float,
)

enum class ParseWarning {
    MISSING_ANSWER,
    INVALID_ANSWER,
    ANSWER_NOT_IN_OPTIONS,
    MISSING_OPTIONS,
    EMPTY_STEM,
}

data class ParsedQuestion(
    val type: QuestionType,
    val stem: String,
    val options: List<Option> = emptyList(),
    val answer: String = "",
    val explanation: String? = null,
    val warnings: List<ParseWarning> = emptyList(),
)

data class ParseResult(
    val questions: List<ParsedQuestion>,
    val rawText: String,
)
```

- [ ] **Step 2: 写失败测试**

`NormalizerTest.kt`：

```kotlin
package com.shutixing.app.parser

import com.shutixing.app.parser.model.TextLine
import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizerTest {
    @Test
    fun `collapses whitespace and converts full width characters`() {
        val input = listOf(TextLine("１．  ＴＣＰ  协议", page = 0, top = 100f))
        assertEquals(listOf("1. TCP 协议"), Normalizer.normalize(input).map { it.text })
    }

    @Test
    fun `removes page numbers`() {
        val input = listOf(
            TextLine("1. 第一题", 0, 100f),
            TextLine("- 12 -", 1, 800f),
            TextLine("第 13 页", 2, 800f),
        )
        assertEquals(listOf("1. 第一题"), Normalizer.normalize(input).map { it.text })
    }

    @Test
    fun `removes headers repeated on most pages at the same position`() {
        val input = (0..3).flatMap { page ->
            listOf(
                TextLine("计算机网络题库", page, top = 40f),
                TextLine("${page + 1}. 题目内容", page, top = 100f + page),
            )
        }
        val result = Normalizer.normalize(input)
        assertEquals(4, result.size)
        assertEquals(
            listOf("1. 题目内容", "2. 题目内容", "3. 题目内容", "4. 题目内容"),
            result.map { it.text },
        )
    }

    @Test
    fun `keeps lines repeated only on a minority of pages`() {
        val input = listOf(
            TextLine("答案：A", 0, 200f),
            TextLine("答案：A", 1, 200f),
            TextLine("别的内容", 2, 300f),
            TextLine("别的内容2", 3, 400f),
        )
        val result = Normalizer.normalize(input)
        assertEquals(4, result.size)
    }

    @Test
    fun `drops blank lines`() {
        val input = listOf(TextLine("   ", 0, 10f), TextLine("题干", 0, 20f))
        assertEquals(listOf("题干"), Normalizer.normalize(input).map { it.text })
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.parser.NormalizerTest"`
Expected: 编译失败，`Unresolved reference: Normalizer`。

- [ ] **Step 4: 实现**

`Normalizer.kt`：

```kotlin
package com.shutixing.app.parser

import com.shutixing.app.parser.model.TextLine

object Normalizer {
    private val spaces = Regex("""\s+""")
    private val pageNumber = Regex("""^\s*(?:[-—–]\s*)?\d{1,4}\s*(?:[-—–])?\s*$""")
    private val pageNumberCn = Regex("""^\s*第\s*\d+\s*页(?:\s*[/／]\s*共?\s*\d+\s*页)?\s*$""")

    fun normalize(lines: List<TextLine>): List<TextLine> {
        val cleaned = lines
            .map { it.copy(text = normalizeText(it.text)) }
            .filter { it.text.isNotEmpty() }

        val pageCount = cleaned.map { it.page }.distinct().size
        val repeatedKeys = if (pageCount >= 3) {
            cleaned.groupBy { headerKey(it) }
                .filterValues { group -> group.map { it.page }.distinct().size > pageCount / 2.0 }
                .keys
        } else {
            emptySet()
        }

        return cleaned.filterNot { line ->
            headerKey(line) in repeatedKeys ||
                pageNumber.matches(line.text) ||
                pageNumberCn.matches(line.text)
        }
    }

    private fun headerKey(line: TextLine): String = "${line.text}@${(line.top / 6f).toInt()}"

    private fun normalizeText(raw: String): String {
        val converted = buildString(raw.length) {
            for (ch in raw) {
                append(
                    when (ch) {
                        in '０'..'９' -> '0' + (ch - '０')
                        in 'Ａ'..'Ｚ' -> 'A' + (ch - 'Ａ')
                        in 'ａ'..'ｚ' -> 'a' + (ch - 'ａ')
                        '　' -> ' '
                        '．' -> '.'
                        else -> ch
                    },
                )
            }
        }
        return converted.replace(spaces, " ").trim()
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.parser.NormalizerTest"`
Expected: `BUILD SUCCESSFUL`，5 个测试通过。注意第 2 个测试中 `- 12 -` 与 `第 13 页` 被删，第 3 个测试中页眉在 4 页里出现 4 次（达到 50%）被删。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/shutixing/app/parser/model app/src/main/java/com/shutixing/app/parser/Normalizer.kt \
  app/src/test/java/com/shutixing/app/parser/NormalizerTest.kt
git commit -m "feat(parser): normalize extracted text lines"
```

---

### Task 9: 题目结构化解析

**Files:**
- Create: `app/src/main/java/com/shutixing/app/parser/QuestionParser.kt`
- Create: `app/src/main/java/com/shutixing/app/parser/StemHasher.kt`
- Test: `app/src/test/java/com/shutixing/app/parser/QuestionParserTest.kt`

- [ ] **Step 1: 写失败测试**

`QuestionParserTest.kt`：

```kotlin
package com.shutixing.app.parser

import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.parser.model.ParseWarning
import com.shutixing.app.parser.model.TextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionParserTest {
    private fun lines(vararg text: String) = text.mapIndexed { i, s -> TextLine(s, 0, i * 10f) }

    @Test
    fun `parses single choice with answer on next line`() {
        val result = QuestionParser.parse(
            lines(
                "1. TCP 协议工作在 OSI 的哪一层？",
                "A. 物理层",
                "B. 传输层",
                "C. 网络层",
                "D. 应用层",
                "答案：B",
            ),
        )
        val q = result.questions.single()
        assertEquals(QuestionType.SINGLE, q.type)
        assertEquals("TCP 协议工作在 OSI 的哪一层？", q.stem)
        assertEquals(listOf("A", "B", "C", "D"), q.options.map { it.key })
        assertEquals("B", q.answer)
        assertTrue(q.warnings.isEmpty())
    }

    @Test
    fun `parses answer appended to last option line`() {
        val result = QuestionParser.parse(
            lines(
                "2. 下列属于传输层协议的是？",
                "A. IP",
                "B. TCP  答案：B",
            ),
        )
        val q = result.questions.single()
        assertEquals("TCP", q.options[1].text)
        assertEquals("B", q.answer)
    }

    @Test
    fun `parses multiple choice with separators`() {
        val result = QuestionParser.parse(
            lines(
                "3. 以下哪些是面向连接的？",
                "A. TCP",
                "B. UDP",
                "C. SCTP",
                "正确答案：A、C",
            ),
        )
        val q = result.questions.single()
        assertEquals(QuestionType.MULTIPLE, q.type)
        assertEquals("AC", q.answer)
    }

    @Test
    fun `parses judge question without options`() {
        val result = QuestionParser.parse(
            lines(
                "4. 判断：TCP 是面向连接的协议。",
                "答案：对",
            ),
        )
        val q = result.questions.single()
        assertEquals(QuestionType.JUDGE, q.type)
        assertEquals("T", q.answer)
    }

    @Test
    fun `parses explanation across multiple lines`() {
        val result = QuestionParser.parse(
            lines(
                "5. 下列哪个是私有地址？",
                "A. 8.8.8.8",
                "B. 192.168.1.1",
                "答案：B",
                "解析：192.168.0.0/16 是私有地址段。",
                "其他选项均为公网地址。",
            ),
        )
        val q = result.questions.single()
        assertEquals("192.168.0.0/16 是私有地址段。 其他选项均为公网地址。", q.explanation)
    }

    @Test
    fun `splits multiple options on one line`() {
        val result = QuestionParser.parse(
            lines(
                "6. 以下哪个是操作系统？",
                "A. Windows B. Linux C. MySQL D. Oracle",
                "答案：A",
            ),
        )
        val q = result.questions.single()
        assertEquals(listOf("A", "B", "C", "D"), q.options.map { it.key })
        assertEquals("Linux", q.options[1].text)
    }

    @Test
    fun `continues option text on following lines`() {
        val result = QuestionParser.parse(
            lines(
                "7. 题干",
                "A. 第一行",
                "继续的说明",
                "B. 另一个选项",
                "答案：A",
            ),
        )
        val q = result.questions.single()
        assertEquals("第一行 继续的说明", q.options[0].text)
    }

    @Test
    fun `supports chinese question number format`() {
        val result = QuestionParser.parse(
            lines("第8题 下列哪个正确？", "A. 甲", "B. 乙", "答案：A"),
        )
        assertEquals(1, result.questions.size)
        assertEquals("下列哪个正确？", result.questions.single().stem)
    }

    @Test
    fun `decimal numbers do not start a new question`() {
        val result = QuestionParser.parse(
            lines("9. 圆的面积是多少？", "A. 3.14", "B. 6.28", "答案：A"),
        )
        assertEquals(1, result.questions.size)
        assertEquals(listOf("A", "B"), result.questions.single().options.map { it.key })
    }

    @Test
    fun `missing answer produces warning`() {
        val result = QuestionParser.parse(
            lines("10. 没有答案的题目", "A. 甲", "B. 乙"),
        )
        val q = result.questions.single()
        assertEquals("", q.answer)
        assertTrue(ParseWarning.MISSING_ANSWER in q.warnings)
    }

    @Test
    fun `answer letter not among options produces warning`() {
        val result = QuestionParser.parse(
            lines("11. 题干", "A. 甲", "B. 乙", "答案：D"),
        )
        assertTrue(ParseWarning.ANSWER_NOT_IN_OPTIONS in result.questions.single().warnings)
    }

    @Test
    fun `text before first question is ignored`() {
        val result = QuestionParser.parse(
            lines("第一章 计算机网络", "12. 题干", "A. 甲", "B. 乙", "答案：A"),
        )
        assertEquals(1, result.questions.size)
        assertNull(result.questions.single().explanation)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.parser.QuestionParserTest"`
Expected: 编译失败，`Unresolved reference: QuestionParser`。

- [ ] **Step 3: 实现解析器**

`QuestionParser.kt`：

```kotlin
package com.shutixing.app.parser

import com.shutixing.app.domain.answer.AnswerChecker
import com.shutixing.app.domain.model.Option
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.parser.model.ParseResult
import com.shutixing.app.parser.model.ParseWarning
import com.shutixing.app.parser.model.ParsedQuestion
import com.shutixing.app.parser.model.TextLine

object QuestionParser {
    private val questionStart = Regex(
        """^\s*(?:(\d{1,3})\s*[.、．)）](?!\d)|第\s*(\d{1,3})\s*题)\s*(.*)$""",
    )
    private val optionStart = Regex("""^([A-H])\s*[.、．)）]\s*(.*)$""")
    private val answerLine = Regex(
        """^\s*[【\[]?\s*(答案|正确答案|参考答案|Answer|Ans)\s*[】\]]?\s*[:：]?\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val explanationLine = Regex(
        """^\s*[【\[]?\s*(解析|分析|解答|答案解析)\s*[】\]]?\s*[:：]?\s*(.*)$""",
    )
    private val inlineAnswer = Regex(
        """\s*[【\[]?\s*(?:答案|正确答案|参考答案|Answer|Ans)\s*[】\]]?\s*[:：]\s*(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val optionSplit = Regex("""(?=\s*[A-H]\s*[.、．)）])""")

    private enum class Mode { STEM, OPTIONS, EXPLANATION }

    fun parse(lines: List<TextLine>, rawText: String = lines.joinToString("\n") { it.text }): ParseResult {
        val blocks = mutableListOf<MutableList<String>>()
        var current: MutableList<String>? = null

        for (line in lines) {
            val match = questionStart.find(line.text)
            if (match != null) {
                current?.let { blocks.add(it) }
                current = mutableListOf<String>().apply {
                    match.groupValues[3].trim().takeIf { it.isNotEmpty() }?.let(::add)
                }
            } else {
                current?.add(line.text)
            }
        }
        current?.let { blocks.add(it) }

        val questions = blocks.mapNotNull { parseBlock(it) }
        return ParseResult(questions = questions, rawText = rawText)
    }

    private fun parseBlock(block: List<String>): ParsedQuestion? {
        val stemParts = mutableListOf<String>()
        val options = mutableListOf<Option>()
        val explanationParts = mutableListOf<String>()
        var answer = ""
        var mode = Mode.STEM

        for (raw in block) {
            var line = raw.trim()
            if (line.isEmpty()) continue

            val expMatch = explanationLine.find(line)
            if (expMatch != null) {
                mode = Mode.EXPLANATION
                expMatch.groupValues[2].trim().takeIf { it.isNotEmpty() }?.let(explanationParts::add)
                continue
            }
            val ansMatch = answerLine.find(line)
            if (ansMatch != null) {
                answer = ansMatch.groupValues[2].trim()
                mode = Mode.EXPLANATION
                continue
            }

            val inline = inlineAnswer.find(line)
            if (inline != null && line.substring(0, inline.range.first).isNotBlank()) {
                answer = inline.groupValues[1].trim()
                line = line.substring(0, inline.range.first).trim()
            }

            when (mode) {
                Mode.STEM -> {
                    val opt = optionStart.find(line)
                    if (opt != null && opt.groupValues[1][0] == expectedKey(options.size)) {
                        mode = Mode.OPTIONS
                        parseOptionLine(opt, options)
                    } else {
                        stemParts.add(line)
                    }
                }
                Mode.OPTIONS -> {
                    val opt = optionStart.find(line)
                    if (opt != null && opt.groupValues[1][0] == expectedKey(options.size)) {
                        parseOptionLine(opt, options)
                    } else if (options.isEmpty()) {
                        stemParts.add(line)
                    } else {
                        options[options.lastIndex] = options.last().copy(
                            text = (options.last().text + " " + line).trim(),
                        )
                    }
                }
                Mode.EXPLANATION -> explanationParts.add(line)
            }
        }

        val stem = stemParts.joinToString(" ").trim()
        if (stem.isEmpty() && options.isEmpty() && answer.isEmpty()) return null

        val warnings = mutableListOf<ParseWarning>()
        val type = inferType(options, answer)
        val normalizedAnswer = AnswerChecker.normalize(type, answer)

        if (stem.isEmpty()) warnings.add(ParseWarning.EMPTY_STEM)
        if (answer.isBlank()) warnings.add(ParseWarning.MISSING_ANSWER)
        if (options.isEmpty() && type != QuestionType.JUDGE) warnings.add(ParseWarning.MISSING_OPTIONS)
        if (answer.isNotBlank() && normalizedAnswer.isEmpty()) warnings.add(ParseWarning.INVALID_ANSWER)
        if (type != QuestionType.JUDGE && normalizedAnswer.isNotEmpty()) {
            val available = options.map { it.key }.toSet()
            if (normalizedAnswer.toSet().any { it !in available }) {
                warnings.add(ParseWarning.ANSWER_NOT_IN_OPTIONS)
            }
        }

        return ParsedQuestion(
            type = type,
            stem = stem,
            options = options,
            answer = normalizedAnswer,
            explanation = explanationParts.joinToString(" ").trim().takeIf { it.isNotEmpty() },
            warnings = warnings,
        )
    }

    private fun parseOptionLine(match: MatchResult, options: MutableList<Option>) {
        val key = match.groupValues[1]
        val rest = match.groupValues[2].trim()
        val parts = rest.split(optionSplit).filter { it.isNotBlank() }
        options.add(Option(key, parts.firstOrNull()?.trim().orEmpty()))
        for (part in parts.drop(1)) {
            val m = optionStart.find(part.trim()) ?: continue
            options.add(Option(m.groupValues[1], m.groupValues[2].trim()))
        }
    }

    private fun expectedKey(index: Int): Char = ('A' + index)

    private fun inferType(options: List<Option>, answer: String): QuestionType {
        if (options.isEmpty()) return QuestionType.JUDGE
        val letters = answer.uppercase().filter { it in 'A'..'H' }
        return if (letters.length > 1) QuestionType.MULTIPLE else QuestionType.SINGLE
    }
}
```

`StemHasher.kt`：

```kotlin
package com.shutixing.app.parser

import com.shutixing.app.domain.model.Option
import java.security.MessageDigest

object StemHasher {
    fun hash(stem: String, options: List<Option>): String {
        val payload = buildString {
            append(stem.trim())
            options.sortedBy { it.key }.forEach { option ->
                append('|').append(option.key).append('.').append(option.text.trim())
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.parser.QuestionParserTest"`
Expected: `BUILD SUCCESSFUL`，12 个测试通过。

若失败，重点检查：
- `3.14` 不会被 `questionStart` 误判（负向前瞻 `(?!\d)` 生效）
- 解析行 `解析：…` 的冒号被正则吞掉，输出不含“解析：”前缀

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/shutixing/app/parser/QuestionParser.kt \
  app/src/main/java/com/shutixing/app/parser/StemHasher.kt \
  app/src/test/java/com/shutixing/app/parser/QuestionParserTest.kt
git commit -m "feat(parser): extract structured questions from text lines"
```

---

### Task 10: PdfBox 文字提取（Android 层）

**Files:**
- Create: `app/src/main/java/com/shutixing/app/parser/PdfTextExtractor.kt`
- Test: `app/src/androidTest/java/com/shutixing/app/parser/PdfTextExtractorTest.kt`

- [ ] **Step 1: 写仪器测试**

`PdfTextExtractorTest.kt`：

```kotlin
package com.shutixing.app.parser

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfTextExtractorTest {
    @Test
    fun `extracts text lines from a pdf`() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val file = File(context.cacheDir, "sample.pdf")
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText("1. What is 2 + 2?")
                cs.newLineAtOffset(0f, -20f)
                cs.showText("A. 3")
                cs.newLineAtOffset(0f, -20f)
                cs.showText("B. 4")
                cs.newLineAtOffset(0f, -20f)
                cs.showText("Answer: B")
                cs.endText()
            }
            doc.save(file)
        }

        val progress = mutableListOf<Pair<Int, Int>>()
        val lines = PdfTextExtractor(context).extract(Uri.fromFile(file)) { done, total ->
            progress.add(done to total)
        }

        assertTrue(lines.any { it.text.contains("What is 2 + 2") })
        assertTrue(lines.any { it.text.contains("Answer: B") })
        assertTrue(lines.all { it.page == 0 })
        assertTrue(progress.isNotEmpty())
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.shutixing.app.parser.PdfTextExtractorTest`
Expected: 编译失败，`Unresolved reference: PdfTextExtractor`。

- [ ] **Step 3: 实现提取器**

`PdfTextExtractor.kt`：

```kotlin
package com.shutixing.app.parser

import android.content.Context
import android.net.Uri
import com.shutixing.app.parser.model.TextLine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.StringWriter
import kotlin.coroutines.coroutineContext

class PdfImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

class PdfTextExtractor(private val context: Context) {

    suspend fun extract(
        uri: Uri,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<TextLine> = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context)
        val lines = mutableListOf<TextLine>()
        val input = context.contentResolver.openInputStream(uri)
            ?: throw PdfImportException("无法打开文件")
        input.use { stream ->
            val document = try {
                PDDocument.load(stream)
            } catch (e: InvalidPasswordException) {
                throw PdfImportException("暂不支持加密 PDF", e)
            } catch (e: Exception) {
                throw PdfImportException("PDF 读取失败", e)
            }
            document.use { doc ->
                val total = doc.numberOfPages
                if (total == 0) throw PdfImportException("PDF 没有页面")
                for (page in 1..total) {
                    coroutineContext.ensureActive()
                    val stripper = PositionalStripper().apply {
                        startPage = page
                        endPage = page
                        pageIndex = page - 1
                        sortByPosition = true
                        output = StringWriter()
                    }
                    stripper.writeText(doc, stripper.output)
                    lines.addAll(stripper.collected)
                    onProgress(page, total)
                }
            }
        }
        lines
    }

    private class PositionalStripper : PDFTextStripper() {
        val collected = mutableListOf<TextLine>()
        var pageIndex: Int = 0

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            val cleaned = text.replace("\r", "").trim()
            if (cleaned.isEmpty()) return
            val top = textPositions.firstOrNull()?.yDirAdj ?: 0f
            collected.add(TextLine(text = cleaned, page = pageIndex, top = top))
        }
    }
}
```

注意：`output` 是 PDFTextStripper 的 `Writer` 属性，Kotlin 中可直接赋值。若编译报平台类型错误，改为 `setOutput(StringWriter())`。

- [ ] **Step 4: 运行仪器测试确认通过**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.shutixing.app.parser.PdfTextExtractorTest`
Expected: `BUILD SUCCESSFUL`，1 个测试通过（需已连接设备/模拟器）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/shutixing/app/parser/PdfTextExtractor.kt \
  app/src/androidTest/java/com/shutixing/app/parser/PdfTextExtractorTest.kt
git commit -m "feat(parser): extract positioned text lines from PDF"
```

---

## Phase 3：数据层

### Task 11: Room 实体、DAO 与数据库

**Files:**
- Create: `app/src/main/java/com/shutixing/app/data/db/Entities.kt`
- Create: `app/src/main/java/com/shutixing/app/data/db/Daos.kt`
- Create: `app/src/main/java/com/shutixing/app/data/db/AppDatabase.kt`
- Test: `app/src/androidTest/java/com/shutixing/app/data/DatabaseTest.kt`

- [ ] **Step 1: 写失败测试**

`DatabaseTest.kt`：

```kotlin
package com.shutixing.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shutixing.app.data.db.AppDatabase
import com.shutixing.app.data.db.AttemptEntity
import com.shutixing.app.data.db.BankEntity
import com.shutixing.app.data.db.QuestionEntity
import com.shutixing.app.data.db.ReviewStateEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun seed() = runBlocking {
        db.bankDao().insert(BankEntity("b1", "题库一", "a.pdf", 1L, 0))
        db.questionDao().insertAll(
            listOf(
                QuestionEntity("q1", "b1", "SINGLE", "题干1", "[]", "A", null, 0, "h1", false, 1L),
                QuestionEntity("q2", "b1", "SINGLE", "题干2", "[]", "A", null, 1, "h2", false, 1L),
            ),
        )
        db.reviewDao().insertAll(
            listOf(
                ReviewStateEntity("q1", 2, "2026-10-20", 0, 0, null),
                ReviewStateEntity("q2", 0, "2026-10-20", 0, 2, null),
            ),
        )
    }

    @Test
    fun `due questions require an attempt`() = runBlocking {
        seed()
        assertTrue(db.questionDao().getDueQuestions("2026-10-20").isEmpty())

        db.attemptDao().insert(
            AttemptEntity(
                questionId = "q1", localDate = "2026-10-20", answeredAt = 1L,
                isCorrect = true, selectedAnswer = "A", durationMs = 100, mode = "DAILY",
            ),
        )
        assertEquals(listOf("q1"), db.questionDao().getDueQuestions("2026-10-20").map { it.id })
    }

    @Test
    fun `wrong book contains questions with unresolved mistakes`() = runBlocking {
        seed()
        assertEquals(listOf("q2"), db.questionDao().getWrongBook().map { it.id })
    }

    @Test
    fun `new questions exclude attempted`() = runBlocking {
        seed()
        assertEquals(listOf("q1", "q2"), db.questionDao().getNewQuestions().map { it.id })
        db.attemptDao().insert(
            AttemptEntity(
                questionId = "q1", localDate = "2026-10-20", answeredAt = 1L,
                isCorrect = false, selectedAnswer = "B", durationMs = 100, mode = "DAILY",
            ),
        )
        assertEquals(listOf("q2"), db.questionDao().getNewQuestions().map { it.id })
    }

    @Test
    fun `deleting bank cascades to questions`() = runBlocking {
        seed()
        db.bankDao().delete("b1")
        assertEquals(0, db.questionDao().countAll())
    }

    @Test
    fun `daily aggregates group attempts by local date`() = runBlocking {
        seed()
        db.attemptDao().insert(
            AttemptEntity(questionId = "q1", localDate = "2026-10-20", answeredAt = 1L, isCorrect = true, selectedAnswer = "A", durationMs = 10, mode = "DAILY"),
        )
        db.attemptDao().insert(
            AttemptEntity(questionId = "q2", localDate = "2026-10-20", answeredAt = 2L, isCorrect = false, selectedAnswer = "B", durationMs = 10, mode = "DAILY"),
        )
        val stats = db.attemptDao().dailyStatsOnce()
        assertEquals(1, stats.size)
        assertEquals(2, stats[0].answered)
        assertEquals(1, stats[0].correct)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.shutixing.app.data.DatabaseTest`
Expected: 编译失败，`Unresolved reference: AppDatabase`。

- [ ] **Step 3: 实现实体**

`Entities.kt`：

```kotlin
package com.shutixing.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "banks")
data class BankEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceFileName: String?,
    val createdAt: Long,
    val sortOrder: Int,
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = BankEntity::class,
            parentColumns = ["id"],
            childColumns = ["bankId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bankId"), Index("stemHash")],
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val bankId: String,
    val type: String,
    val stem: String,
    val optionsJson: String,
    val answer: String,
    val explanation: String?,
    val orderIndex: Int,
    val stemHash: String,
    val isFavorite: Boolean,
    val createdAt: Long,
)

@Entity(
    tableName = "review_states",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReviewStateEntity(
    @PrimaryKey val questionId: String,
    val level: Int,
    val nextReviewDate: String,
    val consecutiveCorrect: Int,
    val wrongCount: Int,
    val lastAnsweredAt: Long?,
)

@Entity(
    tableName = "attempts",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("localDate"), Index("questionId")],
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val questionId: String,
    val localDate: String,
    val answeredAt: Long,
    val isCorrect: Boolean,
    val selectedAnswer: String,
    val durationMs: Long,
    val mode: String,
)

data class DailyAggregate(
    val localDate: String,
    val answered: Int,
    val correct: Int,
)
```

- [ ] **Step 4: 实现 DAO**

`Daos.kt`：

```kotlin
package com.shutixing.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BankDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bank: BankEntity)

    @Update
    suspend fun update(bank: BankEntity)

    @Query("DELETE FROM banks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM banks ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<BankEntity>>

    @Query("SELECT * FROM banks ORDER BY sortOrder, createdAt")
    suspend fun getAll(): List<BankEntity>

    @Query("SELECT * FROM banks WHERE id = :id")
    suspend fun get(id: String): BankEntity?
}

@Dao
interface QuestionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuestionEntity>)

    @Update
    suspend fun update(question: QuestionEntity)

    @Query("DELETE FROM questions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun get(id: String): QuestionEntity?

    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE bankId = :bankId ORDER BY orderIndex")
    fun observeByBank(bankId: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE bankId = :bankId ORDER BY orderIndex")
    suspend fun getByBank(bankId: String): List<QuestionEntity>

    @Query(
        """SELECT q.* FROM questions q
           JOIN banks b ON b.id = q.bankId
           WHERE NOT EXISTS (SELECT 1 FROM attempts a WHERE a.questionId = q.id)
           ORDER BY b.sortOrder, b.createdAt, q.orderIndex""",
    )
    suspend fun getNewQuestions(): List<QuestionEntity>

    @Query(
        """SELECT q.* FROM questions q
           JOIN banks b ON b.id = q.bankId
           JOIN review_states r ON r.questionId = q.id
           WHERE r.nextReviewDate <= :today
             AND EXISTS (SELECT 1 FROM attempts a WHERE a.questionId = q.id)
           ORDER BY b.sortOrder, b.createdAt, q.orderIndex""",
    )
    suspend fun getDueQuestions(today: String): List<QuestionEntity>

    @Query(
        """SELECT q.* FROM questions q
           JOIN banks b ON b.id = q.bankId
           JOIN review_states r ON r.questionId = q.id
           WHERE r.wrongCount > 0 AND r.consecutiveCorrect < 2
           ORDER BY r.nextReviewDate, b.sortOrder, b.createdAt, q.orderIndex""",
    )
    fun observeWrongBook(): Flow<List<QuestionEntity>>

    @Query(
        """SELECT q.* FROM questions q
           JOIN banks b ON b.id = q.bankId
           JOIN review_states r ON r.questionId = q.id
           WHERE r.wrongCount > 0 AND r.consecutiveCorrect < 2
           ORDER BY r.nextReviewDate, b.sortOrder, b.createdAt, q.orderIndex""",
    )
    suspend fun getWrongBook(): List<QuestionEntity>

    @Query("SELECT COUNT(*) FROM questions WHERE bankId = :bankId")
    suspend fun countByBank(bankId: String): Int

    @Query(
        """SELECT COUNT(*) FROM questions q
           JOIN review_states r ON r.questionId = q.id
           WHERE q.bankId = :bankId AND r.level >= 6""",
    )
    suspend fun masteredByBank(bankId: String): Int

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM questions WHERE stemHash = :hash")
    suspend fun countByHash(hash: String): Int

    @Query("SELECT stemHash FROM questions")
    suspend fun allHashes(): List<String>

    @Query("SELECT * FROM questions WHERE stem LIKE '%' || :query || '%' ORDER BY bankId, orderIndex")
    fun search(query: String): Flow<List<QuestionEntity>>

    @Query("DELETE FROM questions WHERE bankId = :bankId")
    suspend fun deleteByBank(bankId: String)
}

@Dao
interface ReviewDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(states: List<ReviewStateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: ReviewStateEntity)

    @Update
    suspend fun update(state: ReviewStateEntity)

    @Query("SELECT * FROM review_states WHERE questionId = :questionId")
    suspend fun get(questionId: String): ReviewStateEntity?

    @Query("SELECT * FROM review_states WHERE questionId IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ReviewStateEntity>

    @Query("SELECT * FROM review_states")
    suspend fun getAll(): List<ReviewStateEntity>
}

@Dao
interface AttemptDao {
    @Insert
    suspend fun insert(attempt: AttemptEntity): Long

    @Query("SELECT * FROM attempts WHERE localDate = :date")
    suspend fun byDate(date: String): List<AttemptEntity>

    @Query(
        """SELECT localDate, COUNT(*) AS answered,
                  SUM(CASE WHEN isCorrect THEN 1 ELSE 0 END) AS correct
           FROM attempts GROUP BY localDate ORDER BY localDate""",
    )
    fun observeDailyStats(): Flow<List<DailyAggregate>>

    @Query(
        """SELECT localDate, COUNT(*) AS answered,
                  SUM(CASE WHEN isCorrect THEN 1 ELSE 0 END) AS correct
           FROM attempts GROUP BY localDate ORDER BY localDate""",
    )
    suspend fun dailyStatsOnce(): List<DailyAggregate>

    @Query("SELECT DISTINCT localDate FROM attempts")
    suspend fun activeDates(): List<String>

    @Query("SELECT COUNT(*) FROM attempts WHERE localDate = :date AND mode = 'DAILY'")
    suspend fun countByDate(date: String): Int

    @Query("SELECT COUNT(*) FROM attempts WHERE localDate = :date AND mode = 'DAILY' AND isCorrect = 1")
    suspend fun correctByDate(date: String): Int

    @Query("DELETE FROM attempts")
    suspend fun clear()
}
```

- [ ] **Step 5: 实现数据库**

`AppDatabase.kt`：

```kotlin
package com.shutixing.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BankEntity::class, QuestionEntity::class, ReviewStateEntity::class, AttemptEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bankDao(): BankDao
    abstract fun questionDao(): QuestionDao
    abstract fun reviewDao(): ReviewDao
    abstract fun attemptDao(): AttemptDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "shuatixing.db")
                .build()
    }
}
```

- [ ] **Step 6: 运行仪器测试确认通过**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.shutixing.app.data.DatabaseTest`
Expected: `BUILD SUCCESSFUL`，5 个测试通过。若 `allowMainThreadQueries` 警告可忽略（仅测试用）。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/shutixing/app/data/db app/src/androidTest/java/com/shutixing/app/data/DatabaseTest.kt app/schemas
git commit -m "feat(data): add Room schema and DAOs"
```

---

### Task 12: 实体映射与仓库

**Files:**
- Create: `app/src/main/java/com/shutixing/app/data/db/Mappers.kt`
- Create: `app/src/main/java/com/shutixing/app/data/repository/BankRepository.kt`
- Create: `app/src/main/java/com/shutixing/app/data/repository/PracticeRepository.kt`
- Create: `app/src/main/java/com/shutixing/app/data/repository/StatsRepository.kt`
- Test: `app/src/test/java/com/shutixing/app/data/MappersTest.kt`

- [ ] **Step 1: 写映射往返测试**

`MappersTest.kt`：

```kotlin
package com.shutixing.app.data

import com.shutixing.app.data.db.AttemptEntity
import com.shutixing.app.data.db.BankEntity
import com.shutixing.app.data.db.QuestionEntity
import com.shutixing.app.data.db.ReviewStateEntity
import com.shutixing.app.data.db.toDomain
import com.shutixing.app.data.db.toEntity
import com.shutixing.app.domain.model.Attempt
import com.shutixing.app.domain.model.AttemptMode
import com.shutixing.app.domain.model.Bank
import com.shutixing.app.domain.model.Option
import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.domain.model.ReviewState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MappersTest {
    @Test
    fun `question survives entity round trip`() {
        val question = Question(
            id = "q1", bankId = "b1", type = QuestionType.MULTIPLE,
            stem = "题干", options = listOf(Option("A", "甲"), Option("B", "乙")),
            answer = "AB", explanation = "解析", orderIndex = 3,
            stemHash = "hash", isFavorite = true, createdAt = 9L,
        )
        assertEquals(question, question.toEntity().toDomain())
    }

    @Test
    fun `review state survives entity round trip`() {
        val state = ReviewState(
            questionId = "q1", level = 3,
            nextReviewDate = LocalDate.of(2026, 10, 23),
            consecutiveCorrect = 2, wrongCount = 1, lastAnsweredAt = 5L,
        )
        assertEquals(state, state.toEntity().toDomain())
    }

    @Test
    fun `attempt survives entity round trip`() {
        val attempt = Attempt(
            questionId = "q1", localDate = LocalDate.of(2026, 10, 20),
            answeredAt = 7L, isCorrect = true, selectedAnswer = "A",
            durationMs = 1500L, mode = AttemptMode.RETEST,
        )
        assertEquals(attempt, attempt.toEntity().toDomain())
    }

    @Test
    fun `bank survives entity round trip`() {
        val bank = Bank("b1", "题库", "a.pdf", 1L, 2)
        assertEquals(bank, bank.toEntity().toDomain())
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.data.MappersTest"`
Expected: 编译失败，`Unresolved reference: toDomain`。

- [ ] **Step 3: 实现映射**

`Mappers.kt`：

```kotlin
package com.shutixing.app.data.db

import com.shutixing.app.domain.model.Attempt
import com.shutixing.app.domain.model.AttemptMode
import com.shutixing.app.domain.model.Bank
import com.shutixing.app.domain.model.Option
import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.domain.model.ReviewState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val json = Json { ignoreUnknownKeys = true }

fun BankEntity.toDomain() = Bank(id, name, sourceFileName, createdAt, sortOrder)
fun Bank.toEntity() = BankEntity(id, name, sourceFileName, createdAt, sortOrder)

fun QuestionEntity.toDomain() = Question(
    id = id,
    bankId = bankId,
    type = QuestionType.valueOf(type),
    stem = stem,
    options = runCatching { json.decodeFromString<List<Option>>(optionsJson) }.getOrDefault(emptyList()),
    answer = answer,
    explanation = explanation,
    orderIndex = orderIndex,
    stemHash = stemHash,
    isFavorite = isFavorite,
    createdAt = createdAt,
)

fun Question.toEntity() = QuestionEntity(
    id = id,
    bankId = bankId,
    type = type.name,
    stem = stem,
    optionsJson = json.encodeToString(options),
    answer = answer,
    explanation = explanation,
    orderIndex = orderIndex,
    stemHash = stemHash,
    isFavorite = isFavorite,
    createdAt = createdAt,
)

fun ReviewStateEntity.toDomain() = ReviewState(
    questionId = questionId,
    level = level,
    nextReviewDate = LocalDate.parse(nextReviewDate),
    consecutiveCorrect = consecutiveCorrect,
    wrongCount = wrongCount,
    lastAnsweredAt = lastAnsweredAt,
)

fun ReviewState.toEntity() = ReviewStateEntity(
    questionId = questionId,
    level = level,
    nextReviewDate = nextReviewDate.toString(),
    consecutiveCorrect = consecutiveCorrect,
    wrongCount = wrongCount,
    lastAnsweredAt = lastAnsweredAt,
)

fun AttemptEntity.toDomain() = Attempt(
    id = id,
    questionId = questionId,
    localDate = LocalDate.parse(localDate),
    answeredAt = answeredAt,
    isCorrect = isCorrect,
    selectedAnswer = selectedAnswer,
    durationMs = durationMs,
    mode = AttemptMode.valueOf(mode),
)

fun Attempt.toEntity() = AttemptEntity(
    id = id,
    questionId = questionId,
    localDate = localDate.toString(),
    answeredAt = answeredAt,
    isCorrect = isCorrect,
    selectedAnswer = selectedAnswer,
    durationMs = durationMs,
    mode = mode.name,
)
```

- [ ] **Step 4: 实现三个仓库**

`BankRepository.kt`：

```kotlin
package com.shutixing.app.data.repository

import androidx.room.withTransaction
import com.shutixing.app.data.db.AppDatabase
import com.shutixing.app.data.db.BankEntity
import com.shutixing.app.data.db.QuestionEntity
import com.shutixing.app.data.db.ReviewStateEntity
import com.shutixing.app.data.db.toDomain
import com.shutixing.app.data.db.toEntity
import com.shutixing.app.domain.model.Bank
import com.shutixing.app.domain.model.Question
import com.shutixing.app.parser.StemHasher
import com.shutixing.app.parser.model.ParsedQuestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

class BankRepository(private val db: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeBanks(): Flow<List<Bank>> =
        db.bankDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeQuestions(bankId: String): Flow<List<Question>> =
        db.questionDao().observeByBank(bankId).map { rows -> rows.map { it.toDomain() } }

    suspend fun allBanks(): List<Bank> = db.bankDao().getAll().map { it.toDomain() }

    suspend fun getBank(id: String): Bank? = db.bankDao().get(id)?.toDomain()

    suspend fun countQuestions(bankId: String): Int = db.questionDao().countByBank(bankId)

    suspend fun countMastered(bankId: String): Int = db.questionDao().masteredByBank(bankId)

    suspend fun createBank(
        name: String,
        sourceFileName: String?,
        questions: List<ParsedQuestion>,
    ): String = db.withTransaction {
        val bankId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val sortOrder = (db.bankDao().getAll().maxOfOrNull { it.sortOrder } ?: -1) + 1
        db.bankDao().insert(BankEntity(bankId, name, sourceFileName, now, sortOrder))
        insertQuestions(bankId, questions, now, startIndex = 0)
        bankId
    }

    suspend fun appendQuestions(bankId: String, questions: List<ParsedQuestion>) = db.withTransaction {
        val start = db.questionDao().countByBank(bankId)
        insertQuestions(bankId, questions, System.currentTimeMillis(), startIndex = start)
    }

    private suspend fun insertQuestions(
        bankId: String,
        parsed: List<ParsedQuestion>,
        now: Long,
        startIndex: Int,
    ) {
        val today = LocalDate.now().toString()
        val entities = parsed.mapIndexed { index, item ->
            QuestionEntity(
                id = UUID.randomUUID().toString(),
                bankId = bankId,
                type = item.type.name,
                stem = item.stem,
                optionsJson = json.encodeToString(item.options),
                answer = item.answer,
                explanation = item.explanation,
                orderIndex = startIndex + index,
                stemHash = StemHasher.hash(item.stem, item.options),
                isFavorite = false,
                createdAt = now,
            )
        }
        if (entities.isEmpty()) return
        db.questionDao().insertAll(entities)
        db.reviewDao().insertAll(
            entities.map { ReviewStateEntity(it.id, 0, today, 0, 0, null) },
        )
    }

    suspend fun deleteBank(id: String) = db.bankDao().delete(id)

    suspend fun renameBank(id: String, name: String) {
        val bank = db.bankDao().get(id) ?: return
        db.bankDao().update(bank.copy(name = name))
    }

    suspend fun getQuestion(id: String): Question? = db.questionDao().get(id)?.toDomain()

    suspend fun updateQuestion(question: Question) = db.questionDao().update(question.toEntity())

    suspend fun deleteQuestion(id: String) = db.questionDao().delete(id)

    suspend fun setFavorite(id: String, favorite: Boolean) {
        val entity = db.questionDao().get(id) ?: return
        db.questionDao().update(entity.copy(isFavorite = favorite))
    }

    suspend fun addQuestion(bankId: String, parsed: ParsedQuestion): Question = db.withTransaction {
        val order = db.questionDao().countByBank(bankId)
        val now = System.currentTimeMillis()
        val entity = QuestionEntity(
            id = UUID.randomUUID().toString(),
            bankId = bankId,
            type = parsed.type.name,
            stem = parsed.stem,
            optionsJson = json.encodeToString(parsed.options),
            answer = parsed.answer,
            explanation = parsed.explanation,
            orderIndex = order,
            stemHash = StemHasher.hash(parsed.stem, parsed.options),
            isFavorite = false,
            createdAt = now,
        )
        db.questionDao().insertAll(listOf(entity))
        db.reviewDao().insert(
            ReviewStateEntity(entity.id, 0, LocalDate.now().toString(), 0, 0, null),
        )
        entity.toDomain()
    }

    suspend fun exportData(bankId: String): Pair<Bank, List<Question>>? {
        val bank = db.bankDao().get(bankId)?.toDomain() ?: return null
        return bank to db.questionDao().getByBank(bankId).map { it.toDomain() }
    }

    suspend fun findByHash(hash: String): Int = db.questionDao().countByHash(hash)

    suspend fun allHashes(): Set<String> = db.questionDao().allHashes().toSet()
}
```

`PracticeRepository.kt`：

```kotlin
package com.shutixing.app.data.repository

import androidx.room.withTransaction
import com.shutixing.app.data.db.AppDatabase
import com.shutixing.app.data.db.toDomain
import com.shutixing.app.data.db.toEntity
import com.shutixing.app.domain.answer.AnswerChecker
import com.shutixing.app.domain.model.Attempt
import com.shutixing.app.domain.model.AttemptMode
import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionWithState
import com.shutixing.app.domain.model.ReviewState
import com.shutixing.app.domain.review.Scheduler
import com.shutixing.app.domain.session.SessionBuilder
import java.time.LocalDate

data class AnswerOutcome(val isCorrect: Boolean, val state: ReviewState)

class PracticeRepository(private val db: AppDatabase) {

    suspend fun buildDailyQueue(target: Int, today: LocalDate): List<Question> {
        val due = dueQuestions(today)
        val (wrong, review) = due.partition { it.review.isInWrongBook }
        val newQuestions = db.questionDao().getNewQuestions().map { it.toDomain() }
        return SessionBuilder.build(wrong, review, newQuestions, target, today)
    }

    suspend fun dueQuestions(today: LocalDate): List<QuestionWithState> {
        val entities = db.questionDao().getDueQuestions(today.toString())
        val states = db.reviewDao().getByIds(entities.map { it.id }).associateBy { it.questionId }
        return entities.mapNotNull { entity ->
            states[entity.questionId]?.let { QuestionWithState(entity.toDomain(), it.toDomain()) }
        }
    }

    suspend fun wrongBook(): List<QuestionWithState> {
        val entities = db.questionDao().getWrongBook()
        val states = db.reviewDao().getByIds(entities.map { it.id }).associateBy { it.questionId }
        return entities.mapNotNull { entity ->
            states[entity.questionId]?.let { QuestionWithState(entity.toDomain(), it.toDomain()) }
        }
    }

    suspend fun answer(
        question: Question,
        selected: String,
        mode: AttemptMode,
        today: LocalDate,
        now: Long,
        durationMs: Long,
    ): AnswerOutcome = db.withTransaction {
        val current = db.reviewDao().get(question.id)?.toDomain()
            ?: ReviewState(question.id, nextReviewDate = today)
        val correct = AnswerChecker.isCorrect(question.type, question.answer, selected)
        val updated = Scheduler.onAnswer(current, correct, today, now)
        db.reviewDao().insert(updated.toEntity())
        db.attemptDao().insert(
            Attempt(
                questionId = question.id,
                localDate = today,
                answeredAt = now,
                isCorrect = correct,
                selectedAnswer = AnswerChecker.normalize(question.type, selected),
                durationMs = durationMs,
                mode = mode,
            ).toEntity(),
        )
        AnswerOutcome(correct, updated)
    }

    suspend fun getQuestion(id: String): Question? = db.questionDao().get(id)?.toDomain()

    suspend fun getQuestions(ids: List<String>): List<Question> {
        val byId = db.questionDao().getByIds(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toDomain() }
    }

    suspend fun reviewState(id: String): ReviewState? = db.reviewDao().get(id)?.toDomain()

    suspend fun countNew(): Int = db.questionDao().getNewQuestions().size

    suspend fun countDue(today: LocalDate): Int = dueQuestions(today).size
}
```

`StatsRepository.kt`：

```kotlin
package com.shutixing.app.data.repository

import com.shutixing.app.data.db.AppDatabase
import com.shutixing.app.domain.model.BankMastery
import com.shutixing.app.domain.model.DailyStat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class StatsRepository(private val db: AppDatabase) {
    fun observeDailyStats(): Flow<List<DailyStat>> =
        db.attemptDao().observeDailyStats().map { rows ->
            rows.map { DailyStat(LocalDate.parse(it.localDate), it.answered, it.correct) }
        }

    suspend fun activeDates(): List<LocalDate> =
        db.attemptDao().activeDates().map { LocalDate.parse(it) }

    suspend fun todayAnswered(today: LocalDate): Int = db.attemptDao().countByDate(today.toString())

    suspend fun todayCorrect(today: LocalDate): Int = db.attemptDao().correctByDate(today.toString())

    suspend fun totalQuestions(): Int = db.questionDao().countAll()

    suspend fun totalMastered(): Int =
        db.bankDao().getAll().sumOf { db.questionDao().masteredByBank(it.id) }

    suspend fun bankMastery(): List<BankMastery> = db.bankDao().getAll().map { bank ->
        BankMastery(
            bankId = bank.id,
            bankName = bank.name,
            total = db.questionDao().countByBank(bank.id),
            mastered = db.questionDao().masteredByBank(bank.id),
        )
    }
}
```

- [ ] **Step 5: 运行映射测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.data.MappersTest"`
Expected: `BUILD SUCCESSFUL`，4 个测试通过。

- [ ] **Step 6: 编译全部代码**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/shutixing/app/data app/src/test/java/com/shutixing/app/data/MappersTest.kt
git commit -m "feat(data): add repositories and entity mappers"
```

---

### Task 13: 设置与刷题会话持久化

**Files:**
- Create: `app/src/main/java/com/shutixing/app/data/repository/SettingsRepository.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/practice/SessionStore.kt`

- [ ] **Step 1: 实现设置仓库**

`SettingsRepository.kt`：

```kotlin
package com.shutixing.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shutixing.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val targetKey = intPreferencesKey("daily_target")
    private val themeKey = stringPreferencesKey("theme_mode")

    val dailyTarget: Flow<Int> = context.settingsDataStore.data.map { it[targetKey] ?: DEFAULT_DAILY_TARGET }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[themeKey] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setDailyTarget(value: Int) {
        context.settingsDataStore.edit { it[targetKey] = value.coerceIn(1, 500) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeKey] = mode.name }
    }

    companion object {
        const val DEFAULT_DAILY_TARGET = 30
    }
}
```

- [ ] **Step 2: 实现会话存储**

`SessionStore.kt`：

```kotlin
package com.shutixing.app.ui.practice

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

@Serializable
data class SavedSession(
    val date: String,
    val mode: String,
    val queue: List<String>,
    val retestQueue: List<String> = emptyList(),
    val index: Int = 0,
    val inRetest: Boolean = false,
    val answeredMain: Int = 0,
    val totalAnswered: Int = 0,
    val correctCount: Int = 0,
)

class SessionStore(private val context: Context) {
    private val key = stringPreferencesKey("session_json")
    private val json = Json { ignoreUnknownKeys = true }

    val session: Flow<SavedSession?> = context.sessionDataStore.data.map { prefs ->
        prefs[key]?.let { text ->
            runCatching { json.decodeFromString<SavedSession>(text) }.getOrNull()
        }
    }

    suspend fun save(session: SavedSession) {
        context.sessionDataStore.edit { it[key] = json.encodeToString(session) }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.remove(key) }
    }
}
```

- [ ] **Step 3: 编译**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/shutixing/app/data/repository/SettingsRepository.kt \
  app/src/main/java/com/shutixing/app/ui/practice/SessionStore.kt
git commit -m "feat(data): persist settings and practice session"
```

---

### Task 14: 题库 JSON 导出与导入

**Files:**
- Create: `app/src/main/java/com/shutixing/app/data/export/BankJson.kt`
- Create: `app/src/main/java/com/shutixing/app/data/export/BankExporter.kt`
- Create: `app/src/main/java/com/shutixing/app/data/export/BankImporter.kt`
- Test: `app/src/test/java/com/shutixing/app/data/BankJsonTest.kt`

- [ ] **Step 1: 写失败测试**

`BankJsonTest.kt`：

```kotlin
package com.shutixing.app.data

import com.shutixing.app.data.export.BankExporter
import com.shutixing.app.data.export.BankImporter
import com.shutixing.app.data.export.ImportResult
import com.shutixing.app.domain.model.Bank
import com.shutixing.app.domain.model.Option
import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankJsonTest {
    private val bank = Bank("b1", "网络题库", "net.pdf")
    private val questions = listOf(
        Question(
            id = "q1", bankId = "b1", type = QuestionType.SINGLE,
            stem = "TCP 属于哪一层？",
            options = listOf(Option("A", "网络层"), Option("B", "传输层")),
            answer = "B", explanation = "TCP 是传输层协议。",
        ),
        Question(
            id = "q2", bankId = "b1", type = QuestionType.MULTIPLE,
            stem = "以下哪些是传输层协议？",
            options = listOf(Option("A", "TCP"), Option("B", "UDP"), Option("C", "IP")),
            answer = "AB",
        ),
        Question(
            id = "q3", bankId = "b1", type = QuestionType.JUDGE,
            stem = "TCP 是面向连接的。", options = emptyList(), answer = "T",
        ),
    )

    @Test
    fun `export then import preserves questions`() {
        val text = BankExporter.toJson(bank, questions, exportedAt = "2026-10-20T00:00:00Z")
        val result = BankImporter.parse(text)
        assertTrue(result is ImportResult.Success)
        result as ImportResult.Success
        assertEquals("网络题库", result.bankName)
        assertEquals(3, result.questions.size)
        assertEquals(QuestionType.MULTIPLE, result.questions[1].type)
        assertEquals("AB", result.questions[1].answer)
        assertEquals(QuestionType.JUDGE, result.questions[2].type)
        assertEquals("T", result.questions[2].answer)
    }

    @Test
    fun `rejects foreign format`() {
        val text = BankExporter.toJson(bank, questions).replace("shuatixing.bank", "other.format")
        val result = BankImporter.parse(text)
        assertTrue(result is ImportResult.Failure)
    }

    @Test
    fun `rejects unsupported version`() {
        val text = BankExporter.toJson(bank, questions).replace("\"version\": 1", "\"version\": 2")
        val result = BankImporter.parse(text)
        assertTrue(result is ImportResult.Failure)
    }

    @Test
    fun `skips invalid questions and reports count`() {
        val text = BankExporter.toJson(
            bank,
            questions + questions[0].copy(id = "q4", stem = " ", options = emptyList(), answer = ""),
        )
        val result = BankImporter.parse(text)
        assertTrue(result is ImportResult.Success)
        result as ImportResult.Success
        assertEquals(3, result.questions.size)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `normalizes multiple answer order on import`() {
        val text = BankExporter.toJson(
            bank,
            listOf(questions[1].copy(answer = "BA")),
        )
        val result = BankImporter.parse(text) as ImportResult.Success
        assertEquals("AB", result.questions.single().answer)
    }

    @Test
    fun `rejects garbage json`() {
        assertTrue(BankImporter.parse("not json") is ImportResult.Failure)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.data.BankJsonTest"`
Expected: 编译失败，`Unresolved reference: BankExporter`。

- [ ] **Step 3: 实现 DTO 与导出**

`BankJson.kt`：

```kotlin
package com.shutixing.app.data.export

import kotlinx.serialization.Serializable

const val BANK_FORMAT = "shuatixing.bank"
const val BANK_FORMAT_VERSION = 1

@Serializable
data class BankFile(
    val format: String = BANK_FORMAT,
    val version: Int = BANK_FORMAT_VERSION,
    val exportedAt: String,
    val bank: BankInfoDto,
    val questions: List<QuestionDto>,
)

@Serializable
data class BankInfoDto(
    val name: String,
    val sourceFileName: String? = null,
)

@Serializable
data class QuestionDto(
    val type: String,
    val stem: String,
    val options: List<OptionDto> = emptyList(),
    val answer: String,
    val explanation: String? = null,
)

@Serializable
data class OptionDto(
    val key: String,
    val text: String,
)
```

`BankExporter.kt`：

```kotlin
package com.shutixing.app.data.export

import com.shutixing.app.domain.model.Bank
import com.shutixing.app.domain.model.Question
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.time.OffsetDateTime

object BankExporter {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun toJson(
        bank: Bank,
        questions: List<Question>,
        exportedAt: String = OffsetDateTime.now().toString(),
    ): String {
        val file = BankFile(
            exportedAt = exportedAt,
            bank = BankInfoDto(name = bank.name, sourceFileName = bank.sourceFileName),
            questions = questions.map { question ->
                QuestionDto(
                    type = question.type.name,
                    stem = question.stem,
                    options = question.options.map { OptionDto(it.key, it.text) },
                    answer = question.answer,
                    explanation = question.explanation,
                )
            },
        )
        return json.encodeToString(file)
    }

    fun write(stream: OutputStream, bank: Bank, questions: List<Question>) {
        stream.use { it.write(toJson(bank, questions).toByteArray(Charsets.UTF_8)) }
    }
}
```

- [ ] **Step 4: 实现导入与校验**

`BankImporter.kt`：

```kotlin
package com.shutixing.app.data.export

import com.shutixing.app.domain.answer.AnswerChecker
import com.shutixing.app.domain.model.Option
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.parser.model.ParsedQuestion
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.InputStream

sealed interface ImportResult {
    data class Success(
        val bankName: String,
        val sourceFileName: String?,
        val questions: List<ParsedQuestion>,
        val skipped: Int,
        val skippedReasons: List<String>,
    ) : ImportResult

    data class Failure(val message: String) : ImportResult
}

object BankImporter {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): ImportResult {
        val file = try {
            json.decodeFromString<BankFile>(text)
        } catch (e: Exception) {
            return ImportResult.Failure("文件格式不正确")
        }
        if (file.format != BANK_FORMAT) return ImportResult.Failure("不是刷题星题库文件")
        if (file.version != BANK_FORMAT_VERSION) {
            return ImportResult.Failure("不支持的题库版本：${file.version}")
        }
        if (file.bank.name.isBlank()) return ImportResult.Failure("题库名称为空")

        val parsed = mutableListOf<ParsedQuestion>()
        val reasons = mutableListOf<String>()
        var skipped = 0
        file.questions.forEachIndexed { index, dto ->
            val question = validate(dto)
            if (question == null) {
                skipped++
                reasons.add("第 ${index + 1} 题数据无效")
            } else {
                parsed.add(question)
            }
        }
        if (parsed.isEmpty()) return ImportResult.Failure("没有可导入的题目")
        return ImportResult.Success(
            bankName = file.bank.name.trim(),
            sourceFileName = file.bank.sourceFileName,
            questions = parsed,
            skipped = skipped,
            skippedReasons = reasons,
        )
    }

    fun read(stream: InputStream): ImportResult =
        stream.use { parse(it.readBytes().toString(Charsets.UTF_8)) }

    private fun validate(dto: QuestionDto): ParsedQuestion? {
        if (dto.stem.isBlank()) return null
        val options = mutableListOf<Option>()
        for (option in dto.options) {
            val key = option.key.trim().uppercase()
            if (key.length != 1 || key[0] !in 'A'..'H' || option.text.isBlank()) return null
            options.add(Option(key, option.text.trim()))
        }
        if (options.map { it.key }.toSet().size != options.size) return null

        val letterCount = dto.answer.uppercase().count { it in 'A'..'H' }
        val type = when {
            options.isEmpty() -> QuestionType.JUDGE
            letterCount > 1 -> QuestionType.MULTIPLE
            else -> QuestionType.SINGLE
        }
        val answer = AnswerChecker.normalize(type, dto.answer)
        if (answer.isEmpty()) return null

        if (type == QuestionType.JUDGE) {
            if (answer != "T" && answer != "F") return null
            return ParsedQuestion(
                type = type,
                stem = dto.stem.trim(),
                options = emptyList(),
                answer = answer,
                explanation = dto.explanation?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

        val keys = options.map { it.key }.toSet()
        if (answer.toSet().any { it !in keys }) return null
        return ParsedQuestion(
            type = type,
            stem = dto.stem.trim(),
            options = options,
            answer = answer,
            explanation = dto.explanation?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.data.BankJsonTest"`
Expected: `BUILD SUCCESSFUL`，6 个测试通过。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/shutixing/app/data/export \
  app/src/test/java/com/shutixing/app/data/BankJsonTest.kt
git commit -m "feat(data): export and import shared question banks as JSON"
```

---

## Phase 4：设计系统与通用组件

### Task 15: 主题、色彩、字体、形状与动效

**Files:**
- Create: `app/src/main/java/com/shutixing/app/ui/theme/Color.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/theme/Type.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/theme/Shape.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/theme/Motion.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/theme/Theme.kt`

- [ ] **Step 1: 色彩系统**

`Color.kt`：

```kotlin
package com.shutixing.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val InkBlue = Color(0xFF1B4B8F)
val DeepTeal = Color(0xFF0E7C86)
val Amber = Color(0xFFF59E0B)
val CorrectGreen = Color(0xFF2E9E6B)
val WrongRed = Color(0xFFE5484D)

val BgLight = Color(0xFFF6F7FB)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFEDF0F7)
val TextPrimaryLight = Color(0xFF101828)
val TextSecondaryLight = Color(0xFF667085)
val OutlineLight = Color(0xFFD0D5DD)

val BgDark = Color(0xFF0B1220)
val SurfaceDark = Color(0xFF131C2E)
val SurfaceVariantDark = Color(0xFF1C2740)
val TextPrimaryDark = Color(0xFFE6EAF2)
val TextSecondaryDark = Color(0xFFA3B0C6)
val OutlineDark = Color(0xFF3A4763)

val HeroGradientStart = Color(0xFF1B4B8F)
val HeroGradientEnd = Color(0xFF0E7C86)
val AmberGradientStart = Color(0xFFF59E0B)
val AmberGradientEnd = Color(0xFFF97316)

val LightColors = lightColorScheme(
    primary = InkBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9FF),
    onPrimaryContainer = Color(0xFF0A2547),
    secondary = DeepTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F2F0),
    onSecondaryContainer = Color(0xFF05332F),
    tertiary = Amber,
    onTertiary = Color(0xFF3A2500),
    background = BgLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    outlineVariant = Color(0xFFE4E8F0),
    error = WrongRed,
    onError = Color.White,
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC3FF),
    onPrimary = Color(0xFF0B2A52),
    primaryContainer = Color(0xFF1C3A66),
    onPrimaryContainer = Color(0xFFD7E6FF),
    secondary = Color(0xFF5FD3C4),
    onSecondary = Color(0xFF04352F),
    secondaryContainer = Color(0xFF124F4A),
    onSecondaryContainer = Color(0xFFC7F4ED),
    tertiary = Color(0xFFFFC44D),
    onTertiary = Color(0xFF3A2500),
    background = BgDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    outlineVariant = Color(0xFF26324B),
    error = Color(0xFFFF8A8F),
    onError = Color(0xFF3A0004),
)
```

- [ ] **Step 2: 字体层级**

`Type.kt`：

```kotlin
package com.shutixing.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 27.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 23.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)
```

- [ ] **Step 3: 形状与动效**

`Shape.kt`：

```kotlin
package com.shutixing.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
```

`Motion.kt`：

```kotlin
package com.shutixing.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object AppMotion {
    const val QUICK = 150
    const val MEDIUM = 260
    const val SLOW = 700

    val spring = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
    val settle = tween<Float>(durationMillis = SLOW, easing = FastOutSlowInEasing)
}
```

- [ ] **Step 4: 主题入口**

`Theme.kt`：

```kotlin
package com.shutixing.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun ShuaTiXingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
```

- [ ] **Step 5: 编译并查看预览**

在 `app/src/main/java/com/shutixing/app/ui/theme/Theme.kt` 末尾追加预览：

```kotlin
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun ThemePreview() {
    ShuaTiXingTheme {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier) {
            androidx.compose.material3.Text("刷题星 · 主题预览")
        }
    }
}
```

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/shutixing/app/ui/theme
git commit -m "feat(ui): add material3 design system and motion tokens"
```

---

### Task 16: 通用组件（进度环、卡片、选项、空态）

**Files:**
- Create: `app/src/main/java/com/shutixing/app/ui/components/ProgressRing.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/components/Cards.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/components/OptionRow.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/components/Badges.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/components/EmptyState.kt`

- [ ] **Step 1: 进度环**

`ProgressRing.kt`：

```kotlin
package com.shutixing.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shutixing.app.ui.theme.AppMotion
import com.shutixing.app.ui.theme.HeroGradientEnd
import com.shutixing.app.ui.theme.HeroGradientStart

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 148.dp,
    strokeWidth: Dp = 12.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    brush: Brush = Brush.linearGradient(listOf(HeroGradientStart, HeroGradientEnd)),
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(AppMotion.SLOW, easing = FastOutSlowInEasing),
        label = "progressRing",
    )
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = strokeWidth.toPx()
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = brush,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        content()
    }
}
```

- [ ] **Step 2: 卡片、统计块、按钮、标签**

`Cards.kt`：

```kotlin
package com.shutixing.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shutixing.app.ui.theme.HeroGradientEnd
import com.shutixing.app.ui.theme.HeroGradientStart

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) {
        content()
    }
}

@Composable
fun GradientHeroCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(HeroGradientStart, HeroGradientEnd))),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.95f, size.height * 0.05f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = size.minDimension * 0.35f,
                center = Offset(size.width * 0.05f, size.height * 0.95f),
            )
        }
        Box(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        action?.invoke()
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
            color = accent,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    brush: Brush = Brush.horizontalGradient(listOf(HeroGradientStart, HeroGradientEnd)),
) {
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) brush else SolidColor(MaterialTheme.colorScheme.surfaceVariant))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PillChip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}
```

- [ ] **Step 3: 选项行与动效**

`OptionRow.kt`：

```kotlin
package com.shutixing.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shutixing.app.ui.theme.AppMotion
import com.shutixing.app.ui.theme.CorrectGreen
import com.shutixing.app.ui.theme.WrongRed

enum class OptionVisualState { IDLE, SELECTED, CORRECT, WRONG, DISABLED }

@Composable
fun OptionRow(
    key: String,
    text: String,
    state: OptionVisualState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val targetBorder = when (state) {
        OptionVisualState.SELECTED -> colors.primary
        OptionVisualState.CORRECT -> CorrectGreen
        OptionVisualState.WRONG -> WrongRed
        else -> colors.outlineVariant
    }
    val targetBackground = when (state) {
        OptionVisualState.SELECTED -> colors.primary.copy(alpha = 0.08f)
        OptionVisualState.CORRECT -> CorrectGreen.copy(alpha = 0.10f)
        OptionVisualState.WRONG -> WrongRed.copy(alpha = 0.10f)
        else -> colors.surface
    }
    val border by animateColorAsState(targetBorder, tween(AppMotion.QUICK), label = "optionBorder")
    val background by animateColorAsState(targetBackground, tween(AppMotion.QUICK), label = "optionBackground")
    val scale by animateFloatAsState(
        if (state == OptionVisualState.SELECTED) 1.01f else 1f,
        AppMotion.spring,
        label = "optionScale",
    )
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state) {
        if (state == OptionVisualState.WRONG) {
            repeat(3) {
                shake.animateTo(7f, tween(45))
                shake.animateTo(-7f, tween(45))
            }
            shake.animateTo(0f, tween(45))
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .offset(x = shake.value.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .border(1.5.dp, border, RoundedCornerShape(18.dp))
            .clickable(enabled = state != OptionVisualState.DISABLED, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val badgeColor = when (state) {
            OptionVisualState.SELECTED -> colors.primary
            OptionVisualState.CORRECT -> CorrectGreen
            OptionVisualState.WRONG -> WrongRed
            else -> colors.surfaceVariant
        }
        val badgeTextColor = when (state) {
            OptionVisualState.IDLE, OptionVisualState.DISABLED -> colors.onSurfaceVariant
            else -> Color.White
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(key, style = MaterialTheme.typography.labelLarge, color = badgeTextColor)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        when (state) {
            OptionVisualState.CORRECT -> Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = "正确",
                tint = CorrectGreen,
            )
            OptionVisualState.WRONG -> Icon(
                Icons.Rounded.Cancel,
                contentDescription = "错误",
                tint = WrongRed,
            )
            else -> Unit
        }
    }
}
```

- [ ] **Step 4: 题型标签与空态**

`Badges.kt`：

```kotlin
package com.shutixing.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.ui.theme.Amber
import com.shutixing.app.ui.theme.DeepTeal
import com.shutixing.app.ui.theme.InkBlue

@Composable
fun QuestionTypeBadge(type: QuestionType) {
    val (label, color) = when (type) {
        QuestionType.SINGLE -> "单选" to InkBlue
        QuestionType.MULTIPLE -> "多选" to DeepTeal
        QuestionType.JUDGE -> "判断" to Amber
    }
    PillChip(
        text = label,
        container = color.copy(alpha = 0.12f),
        contentColor = color,
    )
}

@Composable
fun CountBadge(count: Int, color: Color = MaterialTheme.colorScheme.primary) {
    PillChip(
        text = "$count",
        container = color.copy(alpha = 0.12f),
        contentColor = color,
    )
}
```

`EmptyState.kt`：

```kotlin
package com.shutixing.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            GradientButton(text = actionLabel, onClick = onAction)
        }
    }
}
```

- [ ] **Step 5: 编译**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。若 `matchParentSize` 或 `fontFeatureSettings` 报错，确认 Compose BOM 为 2024.12.01，`fontFeatureSettings` 位于 `TextStyle` 构造参数中。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/shutixing/app/ui/components
git commit -m "feat(ui): add polished reusable components"
```

---

## Phase 5：应用外壳与页面

### Task 17: 刷题会话引擎（错题循环重测）

**Files:**
- Create: `app/src/main/java/com/shutixing/app/domain/session/SessionEngine.kt`
- Test: `app/src/test/java/com/shutixing/app/domain/SessionEngineTest.kt`

- [ ] **Step 1: 写失败测试**

`SessionEngineTest.kt`：

```kotlin
package com.shutixing.app.domain

import com.shutixing.app.domain.session.SessionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEngineTest {
    @Test
    fun `empty queue finishes immediately`() {
        assertTrue(SessionEngine.start(emptyList()).finished)
    }

    @Test
    fun `correct answers finish the main queue`() {
        var state = SessionEngine.start(listOf("q1", "q2"))
        assertEquals("q1", state.currentId)
        state = SessionEngine.advance(SessionEngine.onAnswered(state, true))
        assertEquals("q2", state.currentId)
        state = SessionEngine.advance(SessionEngine.onAnswered(state, true))
        assertTrue(state.finished)
        assertEquals(2, state.totalAnswered)
        assertEquals(2, state.correctCount)
        assertEquals(2, state.answeredMain)
    }

    @Test
    fun `wrong answer is requeued and retested after the main queue`() {
        var state = SessionEngine.start(listOf("q1", "q2"))
        state = SessionEngine.onAnswered(state, false)
        assertEquals(listOf("q1"), state.retestQueue)
        state = SessionEngine.advance(state)
        assertEquals("q2", state.currentId)
        state = SessionEngine.advance(SessionEngine.onAnswered(state, true))
        assertTrue(state.inRetest)
        assertEquals("q1", state.currentId)
        state = SessionEngine.advance(SessionEngine.onAnswered(state, true))
        assertTrue(state.finished)
    }

    @Test
    fun `wrong retest answer re-enters the queue`() {
        var state = SessionEngine.start(listOf("q1"))
        state = SessionEngine.onAnswered(state, false)
        state = SessionEngine.advance(state)
        assertEquals("q1", state.currentId)
        state = SessionEngine.onAnswered(state, false)
        assertEquals(listOf("q1", "q1"), state.retestQueue)
        state = SessionEngine.advance(state)
        assertTrue(state.inRetest)
        assertEquals("q1", state.currentId)
        state = SessionEngine.advance(SessionEngine.onAnswered(state, true))
        assertTrue(state.finished)
    }

    @Test
    fun `progress tracks main queue completion`() {
        var state = SessionEngine.start(listOf("q1", "q2"))
        assertEquals(0f, state.progress, 0.001f)
        state = SessionEngine.advance(SessionEngine.onAnswered(state, true))
        assertEquals(0.5f, state.progress, 0.001f)
        state = SessionEngine.advance(SessionEngine.onAnswered(state, true))
        assertEquals(1f, state.progress, 0.001f)
        assertTrue(state.finished)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.domain.SessionEngineTest"`
Expected: 编译失败，`Unresolved reference: SessionEngine`。

- [ ] **Step 3: 实现会话引擎**

`SessionEngine.kt`：

```kotlin
package com.shutixing.app.domain.session

data class SessionState(
    val mainQueue: List<String>,
    val index: Int = 0,
    val retestQueue: List<String> = emptyList(),
    val inRetest: Boolean = false,
    val answeredMain: Int = 0,
    val totalAnswered: Int = 0,
    val correctCount: Int = 0,
    val finished: Boolean = false,
) {
    val currentId: String?
        get() = if (inRetest) retestQueue.firstOrNull() else mainQueue.getOrNull(index)

    val progress: Float
        get() = if (mainQueue.isEmpty()) 0f else answeredMain.toFloat() / mainQueue.size
}

object SessionEngine {
    fun start(mainQueue: List<String>): SessionState =
        SessionState(mainQueue = mainQueue, finished = mainQueue.isEmpty())

    fun onAnswered(state: SessionState, correct: Boolean): SessionState {
        val current = state.currentId ?: return state
        return state.copy(
            retestQueue = if (correct) state.retestQueue else state.retestQueue + current,
            totalAnswered = state.totalAnswered + 1,
            correctCount = state.correctCount + if (correct) 1 else 0,
        )
    }

    fun advance(state: SessionState): SessionState {
        if (state.finished) return state
        if (state.inRetest) {
            val remaining = state.retestQueue.drop(1)
            return if (remaining.isEmpty()) {
                state.copy(retestQueue = emptyList(), finished = true)
            } else {
                state.copy(retestQueue = remaining)
            }
        }
        val nextIndex = state.index + 1
        val answered = state.answeredMain + 1
        return when {
            nextIndex < state.mainQueue.size ->
                state.copy(index = nextIndex, answeredMain = answered)
            state.retestQueue.isNotEmpty() ->
                state.copy(index = state.mainQueue.size, inRetest = true, answeredMain = answered)
            else ->
                state.copy(index = state.mainQueue.size, answeredMain = answered, finished = true)
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shutixing.app.domain.SessionEngineTest"`
Expected: `BUILD SUCCESSFUL`，5 个测试通过。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/shutixing/app/domain/session/SessionEngine.kt \
  app/src/test/java/com/shutixing/app/domain/SessionEngineTest.kt
git commit -m "feat(domain): retest wrong answers until correct"
```

---

### Task 18: 依赖容器与首页（今日）

**Files:**
- Create: `app/src/main/java/com/shutixing/app/di/AppContainer.kt`
- Modify: `app/src/main/java/com/shutixing/app/ShuatiXingApp.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/home/HomeScreen.kt`

- [ ] **Step 1: 依赖容器与应用类**

`AppContainer.kt`：

```kotlin
package com.shutixing.app.di

import android.content.Context
import com.shutixing.app.data.db.AppDatabase
import com.shutixing.app.data.repository.BankRepository
import com.shutixing.app.data.repository.PracticeRepository
import com.shutixing.app.data.repository.SettingsRepository
import com.shutixing.app.data.repository.StatsRepository
import com.shutixing.app.parser.PdfTextExtractor
import com.shutixing.app.ui.practice.SessionStore

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.build(context)
    val bankRepository = BankRepository(database)
    val practiceRepository = PracticeRepository(database)
    val statsRepository = StatsRepository(database)
    val settingsRepository = SettingsRepository(context)
    val sessionStore = SessionStore(context)
    val pdfTextExtractor = PdfTextExtractor(context.applicationContext)
}
```

`ShuatiXingApp.kt`：

```kotlin
package com.shutixing.app

import android.app.Application
import com.shutixing.app.di.AppContainer

class ShuatiXingApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

- [ ] **Step 2: 首页 ViewModel**

`HomeViewModel.kt`：

```kotlin
package com.shutixing.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shutixing.app.data.repository.PracticeRepository
import com.shutixing.app.data.repository.SettingsRepository
import com.shutixing.app.data.repository.StatsRepository
import com.shutixing.app.di.AppContainer
import com.shutixing.app.domain.stats.StatsCalculator
import com.shutixing.app.ui.practice.PracticeMode
import com.shutixing.app.ui.practice.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val loading: Boolean = true,
    val dailyTarget: Int = 30,
    val todayAnswered: Int = 0,
    val todayCorrect: Int = 0,
    val streak: Int = 0,
    val wrongCount: Int = 0,
    val mastered: Int = 0,
    val total: Int = 0,
    val hasSession: Boolean = false,
) {
    val progress: Float
        get() = if (dailyTarget <= 0) 0f else (todayAnswered.toFloat() / dailyTarget).coerceIn(0f, 1f)

    val todayAccuracy: Float
        get() = if (todayAnswered == 0) 0f else todayCorrect.toFloat() / todayAnswered
}

class HomeViewModel(
    private val practiceRepository: PracticeRepository,
    private val statsRepository: StatsRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionStore: SessionStore,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val today = todayProvider()
            val target = settingsRepository.dailyTarget.first()
            val answered = statsRepository.todayAnswered(today)
            val correct = statsRepository.todayCorrect(today)
            val streak = StatsCalculator.streak(statsRepository.activeDates(), today)
            val wrong = practiceRepository.wrongBook().size
            val mastered = statsRepository.totalMastered()
            val total = statsRepository.totalQuestions()
            val hasSession = sessionStore.session.first()?.mode == PracticeMode.DAILY.name
            _state.value = HomeUiState(
                loading = false,
                dailyTarget = target,
                todayAnswered = answered,
                todayCorrect = correct,
                streak = streak,
                wrongCount = wrong,
                mastered = mastered,
                total = total,
                hasSession = hasSession,
            )
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                HomeViewModel(
                    practiceRepository = container.practiceRepository,
                    statsRepository = container.statsRepository,
                    settingsRepository = container.settingsRepository,
                    sessionStore = container.sessionStore,
                )
            }
        }
    }
}
```

- [ ] **Step 3: 首页 UI**

`HomeScreen.kt`：

```kotlin
package com.shutixing.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shutixing.app.ui.components.AppCard
import com.shutixing.app.ui.components.GradientButton
import com.shutixing.app.ui.components.GradientHeroCard
import com.shutixing.app.ui.components.ProgressRing
import com.shutixing.app.ui.components.QuickStatRow
import com.shutixing.app.ui.components.SectionHeader
import com.shutixing.app.ui.theme.AmberGradientEnd
import com.shutixing.app.ui.theme.AmberGradientStart
import com.shutixing.app.ui.theme.CorrectGreen
import com.shutixing.app.ui.theme.DeepTeal
import com.shutixing.app.ui.theme.InkBlue
import com.shutixing.app.ui.theme.ShuaTiXingTheme
import com.shutixing.app.ui.theme.WrongRed
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartDaily: () -> Unit,
    onContinueSession: () -> Unit,
    onOpenWrongBook: () -> Unit,
    onOpenBanks: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    HomeContent(
        state = state,
        onStartDaily = onStartDaily,
        onContinueSession = onContinueSession,
        onOpenWrongBook = onOpenWrongBook,
        onOpenBanks = onOpenBanks,
        onOpenImport = onOpenImport,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onStartDaily: () -> Unit,
    onContinueSession: () -> Unit,
    onOpenWrongBook: () -> Unit,
    onOpenBanks: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(), style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formattedDate(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "设置")
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 8 },
            ) {
                HeroSection(state, onStartDaily, onContinueSession)
            }
        }

        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(450, delayMillis = 80)) +
                    slideInVertically(tween(450, delayMillis = 80)) { it / 8 },
            ) {
                AppCard(Modifier.fillMaxWidth()) {
                    QuickStatRow(
                        accuracy = state.todayAccuracy,
                        hasAnswers = state.todayAnswered > 0,
                        wrongCount = state.wrongCount,
                        mastered = state.mastered,
                    )
                }
            }
        }

        item {
            SectionHeader(title = "快捷入口")
        }

        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(450, delayMillis = 160)) +
                    slideInVertically(tween(450, delayMillis = 160)) { it / 8 },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickActionCard(
                        label = "错题本",
                        caption = "${state.wrongCount} 题待复习",
                        icon = Icons.Rounded.ErrorOutline,
                        accent = WrongRed,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenWrongBook,
                    )
                    QuickActionCard(
                        label = "题库",
                        caption = "${state.total} 题",
                        icon = Icons.Rounded.MenuBook,
                        accent = InkBlue,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenBanks,
                    )
                    QuickActionCard(
                        label = "导入",
                        caption = "PDF 生成题库",
                        icon = Icons.Rounded.FileUpload,
                        accent = DeepTeal,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenImport,
                    )
                }
            }
        }

        item {
            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("每日一点点", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "坚持刷题，让记忆按节奏生长。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSection(
    state: HomeUiState,
    onStartDaily: () -> Unit,
    onContinueSession: () -> Unit,
) {
    val remaining = (state.dailyTarget - state.todayAnswered).coerceAtLeast(0)
    GradientHeroCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "今日目标",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${state.todayAnswered}",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                        )
                        Text(
                            " / ${state.dailyTarget} 题",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.78f),
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color(0xFFFFC44D),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "连续 ${state.streak} 天",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = state.progress,
                    diameter = 116.dp,
                    strokeWidth = 10.dp,
                    trackColor = Color.White.copy(alpha = 0.18f),
                    brush = Brush.linearGradient(listOf(Color(0xFFFFD36E), Color(0xFFFFA94D))),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Text(
                            "完成",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (remaining > 0) "还差 $remaining 题" else "今日任务已完成",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (state.hasSession) "上次进度已保存" else "按计划推进，稳扎稳打",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                    Spacer(Modifier.height(14.dp))
                    GradientButton(
                        text = if (state.hasSession) "继续本轮" else "开始刷题",
                        onClick = if (state.hasSession) onContinueSession else onStartDaily,
                        brush = Brush.horizontalGradient(listOf(AmberGradientStart, AmberGradientEnd)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    label: String,
    caption: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AppCard(modifier = modifier.clickable(onClick = onClick)) {
        Column {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "早上好"
    in 12..17 -> "下午好"
    else -> "晚上好"
}

private fun formattedDate(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomePreview() {
    ShuaTiXingTheme {
        HomeContent(
            state = HomeUiState(
                loading = false,
                dailyTarget = 30,
                todayAnswered = 12,
                todayCorrect = 9,
                streak = 6,
                wrongCount = 8,
                mastered = 120,
                total = 300,
                hasSession = false,
            ),
            onStartDaily = {},
            onContinueSession = {},
            onOpenWrongBook = {},
            onOpenBanks = {},
            onOpenImport = {},
            onOpenSettings = {},
        )
    }
}
```

其中 `QuickStatRow` 是新增组件，放到 `Cards.kt` 末尾：

```kotlin
@Composable
fun QuickStatRow(
    accuracy: Float,
    hasAnswers: Boolean,
    wrongCount: Int,
    mastered: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatTile(
            label = "今日正确率",
            value = if (hasAnswers) "${(accuracy * 100).toInt()}%" else "—",
            accent = CorrectGreen,
        )
        Box(Modifier.width(1.dp).height(36.dp).background(MaterialTheme.colorScheme.outlineVariant))
        StatTile(label = "错题", value = "$wrongCount", accent = WrongRed)
        Box(Modifier.width(1.dp).height(36.dp).background(MaterialTheme.colorScheme.outlineVariant))
        StatTile(label = "已掌握", value = "$mastered", accent = MaterialTheme.colorScheme.primary)
    }
}
```

`Cards.kt` 需要补充 import：`androidx.compose.foundation.layout.height`、`androidx.compose.foundation.layout.width`、`com.shutixing.app.ui.theme.CorrectGreen`、`com.shutixing.app.ui.theme.WrongRed`。

- [ ] **Step 4: 编译并预览**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

在 Android Studio / IntelliJ 中打开 `HomeScreen.kt` 的 `HomePreview` 检查视觉效果：渐变头图、进度环、三列统计、快捷卡片。需求自检：无文字溢出、间距统一（16/20）、深色模式切换正常。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/shutixing/app/di app/src/main/java/com/shutixing/app/ShuatiXingApp.kt \
  app/src/main/java/com/shutixing/app/ui/home app/src/main/java/com/shutixing/app/ui/components/Cards.kt
git commit -m "feat(ui): add app container and today home screen"
```

---

### Task 19: 刷题 ViewModel 与会话流程

**Files:**
- Create: `app/src/main/java/com/shutixing/app/ui/practice/PracticeViewModel.kt`

- [ ] **Step 1: 实现刷题 ViewModel**

`PracticeViewModel.kt`：

```kotlin
package com.shutixing.app.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shutixing.app.data.repository.PracticeRepository
import com.shutixing.app.data.repository.SettingsRepository
import com.shutixing.app.di.AppContainer
import com.shutixing.app.domain.model.AttemptMode
import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.domain.session.SessionEngine
import com.shutixing.app.domain.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class PracticeMode { DAILY, WRONG_BOOK }

data class PracticeUiState(
    val loading: Boolean = true,
    val mode: PracticeMode = PracticeMode.DAILY,
    val current: Question? = null,
    val selected: Set<String> = emptySet(),
    val submitted: Boolean = false,
    val lastCorrect: Boolean? = null,
    val inRetest: Boolean = false,
    val retestRemaining: Int = 0,
    val progress: Float = 0f,
    val answeredMain: Int = 0,
    val mainTotal: Int = 0,
    val totalAnswered: Int = 0,
    val correctCount: Int = 0,
    val finished: Boolean = false,
    val empty: Boolean = false,
    val error: String? = null,
    val dailyTarget: Int = 30,
)

class PracticeViewModel(
    private val practiceRepository: PracticeRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionStore: SessionStore,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _state = MutableStateFlow(PracticeUiState())
    val state: StateFlow<PracticeUiState> = _state.asStateFlow()

    private var session: SessionState? = null
    private var questions: Map<String, Question> = emptyMap()
    private var currentMode: PracticeMode = PracticeMode.DAILY
    private var questionStartedAt: Long = 0L

    fun start(mode: PracticeMode) {
        currentMode = mode
        viewModelScope.launch {
            _state.value = PracticeUiState(loading = true, mode = mode)
            try {
                val today = todayProvider()
                val target = settingsRepository.dailyTarget.first()
                val saved = sessionStore.session.first()
                if (saved != null && saved.mode == mode.name && saved.date == today.toString()) {
                    restore(saved, target)
                } else {
                    sessionStore.clear()
                    val ids = when (mode) {
                        PracticeMode.DAILY ->
                            practiceRepository.buildDailyQueue(target, today).map { it.id }
                        PracticeMode.WRONG_BOOK ->
                            practiceRepository.wrongBook().map { it.question.id }
                    }
                    if (ids.isEmpty()) {
                        _state.value = PracticeUiState(
                            loading = false,
                            mode = mode,
                            empty = true,
                            dailyTarget = target,
                        )
                        return@launch
                    }
                    questions = practiceRepository.getQuestions(ids).associateBy { it.id }
                    val fresh = SessionEngine.start(ids)
                    session = fresh
                    questionStartedAt = nowProvider()
                    publish(fresh, mode, target, loading = false)
                }
            } catch (e: Exception) {
                _state.value = PracticeUiState(
                    loading = false,
                    mode = mode,
                    error = e.message ?: "加载失败",
                )
            }
        }
    }

    fun select(key: String) {
        val current = _state.value
        if (current.submitted) return
        val question = current.current ?: return
        val next = if (question.type == QuestionType.MULTIPLE) {
            if (key in current.selected) current.selected - key else current.selected + key
        } else {
            setOf(key)
        }
        _state.update { it.copy(selected = next) }
    }

    fun submit() {
        val current = _state.value
        val question = current.current ?: return
        if (current.submitted || current.selected.isEmpty()) return
        viewModelScope.launch {
            try {
                val snapshot = session ?: return@launch
                val today = todayProvider()
                val now = nowProvider()
                val mode = when {
                    snapshot.inRetest -> AttemptMode.RETEST
                    current.mode == PracticeMode.WRONG_BOOK -> AttemptMode.WRONG_BOOK
                    else -> AttemptMode.DAILY
                }
                val outcome = practiceRepository.answer(
                    question = question,
                    selected = current.selected.sorted().joinToString(""),
                    mode = mode,
                    today = today,
                    now = now,
                    durationMs = (now - questionStartedAt).coerceAtLeast(0L),
                )
                val updated = SessionEngine.onAnswered(snapshot, outcome.isCorrect)
                session = updated
                _state.update {
                    it.copy(
                        submitted = true,
                        lastCorrect = outcome.isCorrect,
                        totalAnswered = updated.totalAnswered,
                        correctCount = updated.correctCount,
                    )
                }
                persist()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "提交失败") }
            }
        }
    }

    fun next() {
        val current = _state.value
        if (!current.submitted) return
        val snapshot = session ?: return
        val updated = SessionEngine.advance(snapshot)
        session = updated
        questionStartedAt = nowProvider()

        if (updated.finished) {
            viewModelScope.launch { sessionStore.clear() }
            _state.update {
                it.copy(
                    current = null,
                    finished = true,
                    submitted = false,
                    selected = emptySet(),
                    lastCorrect = null,
                    inRetest = false,
                    retestRemaining = 0,
                    progress = updated.progress,
                    answeredMain = updated.answeredMain,
                    mainTotal = updated.mainQueue.size,
                    totalAnswered = updated.totalAnswered,
                    correctCount = updated.correctCount,
                )
            }
        } else {
            _state.update {
                it.copy(
                    current = updated.currentId?.let { id -> questions[id] },
                    submitted = false,
                    selected = emptySet(),
                    lastCorrect = null,
                    inRetest = updated.inRetest,
                    retestRemaining = updated.retestQueue.size,
                    progress = updated.progress,
                    answeredMain = updated.answeredMain,
                    mainTotal = updated.mainQueue.size,
                )
            }
            persist()
        }
    }

    private suspend fun restore(saved: SavedSession, target: Int) {
        val loaded = practiceRepository.getQuestions(saved.queue)
        if (loaded.isEmpty()) {
            sessionStore.clear()
            _state.value = PracticeUiState(loading = false, mode = currentMode, empty = true, dailyTarget = target)
            return
        }
        questions = loaded.associateBy { it.id }
        val restored = SessionState(
            mainQueue = saved.queue,
            index = saved.index.coerceIn(0, saved.queue.size),
            retestQueue = saved.retestQueue,
            inRetest = saved.inRetest,
            answeredMain = saved.answeredMain,
            totalAnswered = saved.totalAnswered,
            correctCount = saved.correctCount,
            finished = false,
        )
        session = restored
        questionStartedAt = nowProvider()
        publish(restored, currentMode, target)
    }

    private fun publish(sessionState: SessionState, mode: PracticeMode, target: Int, loading: Boolean = false) {
        _state.value = PracticeUiState(
            loading = loading,
            mode = mode,
            current = sessionState.currentId?.let { questions[it] },
            submitted = false,
            selected = emptySet(),
            lastCorrect = null,
            inRetest = sessionState.inRetest,
            retestRemaining = sessionState.retestQueue.size,
            progress = sessionState.progress,
            answeredMain = sessionState.answeredMain,
            mainTotal = sessionState.mainQueue.size,
            totalAnswered = sessionState.totalAnswered,
            correctCount = sessionState.correctCount,
            finished = sessionState.finished || sessionState.currentId == null,
            empty = sessionState.mainQueue.isEmpty(),
            dailyTarget = target,
        )
    }

    private fun persist() {
        val snapshot = session ?: return
        viewModelScope.launch {
            sessionStore.save(
                SavedSession(
                    date = todayProvider().toString(),
                    mode = currentMode.name,
                    queue = snapshot.mainQueue,
                    retestQueue = snapshot.retestQueue,
                    index = snapshot.index,
                    inRetest = snapshot.inRetest,
                    answeredMain = snapshot.answeredMain,
                    totalAnswered = snapshot.totalAnswered,
                    correctCount = snapshot.correctCount,
                ),
            )
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                PracticeViewModel(
                    practiceRepository = container.practiceRepository,
                    settingsRepository = container.settingsRepository,
                    sessionStore = container.sessionStore,
                )
            }
        }
    }
}
```

- [ ] **Step 2: 编译**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/shutixing/app/ui/practice/PracticeViewModel.kt
git commit -m "feat(practice): add session view model with retest loop"
```

---

### Task 20: 刷题页、判分反馈与结果页

**Files:**
- Create: `app/src/main/java/com/shutixing/app/ui/practice/PracticeScreen.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/result/ResultScreen.kt`

- [ ] **Step 1: 结果页**

`ResultScreen.kt`：

```kotlin
package com.shutixing.app.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shutixing.app.ui.components.GradientButton
import com.shutixing.app.ui.components.ProgressRing

@Composable
fun ResultScreen(
    totalAnswered: Int,
    correctCount: Int,
    onDone: () -> Unit,
    onRetryWrong: () -> Unit,
) {
    val accuracy = if (totalAnswered == 0) 0f else correctCount.toFloat() / totalAnswered
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 8 },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ProgressRing(
                    progress = accuracy,
                    diameter = 190.dp,
                    strokeWidth = 14.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(accuracy * 100).toInt()}%",
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text(
                            "本轮正确率",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = if (accuracy >= 0.8f) "漂亮，继续保持" else "稳步前进，再练一轮",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "本轮共作答 $totalAnswered 题，答对 $correctCount 题",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
                if (totalAnswered > correctCount) {
                    GradientButton(
                        text = "复习本轮错题",
                        onClick = onRetryWrong,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("返回首页")
                }
            }
        }
    }
}
```

- [ ] **Step 2: 刷题页**

`PracticeScreen.kt`：

```kotlin
package com.shutixing.app.ui.practice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shutixing.app.domain.model.Option
import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.ui.components.AppCard
import com.shutixing.app.ui.components.EmptyState
import com.shutixing.app.ui.components.GradientButton
import com.shutixing.app.ui.components.OptionRow
import com.shutixing.app.ui.components.OptionVisualState
import com.shutixing.app.ui.components.PillChip
import com.shutixing.app.ui.components.QuestionTypeBadge
import com.shutixing.app.ui.result.ResultScreen
import com.shutixing.app.ui.theme.AmberGradientEnd
import com.shutixing.app.ui.theme.AmberGradientStart
import com.shutixing.app.ui.theme.CorrectGreen
import com.shutixing.app.ui.theme.DeepTeal
import com.shutixing.app.ui.theme.InkBlue
import com.shutixing.app.ui.theme.WrongRed
import androidx.compose.ui.graphics.Brush

@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel,
    mode: PracticeMode,
    onExit: () -> Unit,
    onOpenImport: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(mode) { viewModel.start(mode) }

    when {
        state.loading -> LoadingBox("正在组卷…")
        state.error != null -> CenteredError(state.error.orEmpty(), onExit)
        state.empty -> EmptyState(
            icon = Icons.Rounded.Inbox,
            title = "今天没有要刷的题",
            message = if (state.mode == PracticeMode.DAILY) {
                "导入一份 PDF 题库，或调整每日题量后再来。"
            } else {
                "错题本还是空的，继续保持。"
            },
            actionLabel = if (state.mode == PracticeMode.DAILY) "去导入题库" else null,
            onAction = if (state.mode == PracticeMode.DAILY) onOpenImport else null,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        )
        state.finished -> ResultScreen(
            totalAnswered = state.totalAnswered,
            correctCount = state.correctCount,
            onDone = onExit,
            onRetryWrong = { viewModel.start(PracticeMode.WRONG_BOOK) },
        )
        else -> PracticeContent(
            state = state,
            onSelect = viewModel::select,
            onSubmit = viewModel::submit,
            onNext = viewModel::next,
            onExit = onExit,
        )
    }
}

@Composable
private fun PracticeContent(
    state: PracticeUiState,
    onSelect: (String) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
) {
    var showExitDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    BackHandler { showExitDialog = true }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { showExitDialog = true }) {
                Icon(Icons.Rounded.Close, contentDescription = "退出")
            }
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (state.inRetest) "错题重测" else "今日刷题",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${state.answeredMain} / ${state.mainTotal}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                )
            }
        }

        AnimatedVisibility(visible = state.inRetest, enter = fadeIn(tween(200)) + expandVertically(tween(220))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Replay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "答错的题会一直重测，直到答对为止 · 剩余 ${state.retestRemaining}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        AnimatedContent(
            targetState = state.current,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 24 })
                    .togetherWith(fadeOut(tween(120)))
            },
            label = "question",
        ) { question ->
            if (question != null) {
                QuestionBody(
                    question = question,
                    state = state,
                    onSelect = { key ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(key)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            GradientButton(
                text = if (state.submitted) "下一题" else "提交答案",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (state.submitted) onNext() else onSubmit()
                },
                enabled = state.submitted || state.selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                brush = if (state.submitted) {
                    Brush.horizontalGradient(listOf(InkBlue, DeepTeal))
                } else {
                    Brush.horizontalGradient(listOf(AmberGradientStart, AmberGradientEnd))
                },
            )
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("退出本轮？") },
            text = { Text("进度已自动保存，下次进入可以继续。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onExit()
                    },
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("继续刷题") }
            },
        )
    }
}

@Composable
private fun QuestionBody(
    question: Question,
    state: PracticeUiState,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = displayOptions(question)
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QuestionTypeBadge(question.type)
                if (state.inRetest) {
                    Spacer(Modifier.width(8.dp))
                    PillChip(
                        text = "重测",
                        container = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                        contentColor = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = question.stem,
                style = MaterialTheme.typography.titleLarge.copy(lineHeight = 32.sp),
            )
        }
        items(options, key = { it.key }) { option ->
            OptionRow(
                key = displayKey(question, option.key),
                text = option.text,
                state = optionVisualState(question, option, state),
                onClick = { onSelect(option.key) },
            )
        }
        item {
            AnimatedVisibility(
                visible = state.submitted,
                enter = fadeIn(tween(220)) + expandVertically(tween(260)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
            ) {
                FeedbackCard(question, state)
            }
        }
    }
}

@Composable
private fun FeedbackCard(question: Question, state: PracticeUiState) {
    val correct = state.lastCorrect == true
    AppCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (correct) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                    contentDescription = null,
                    tint = if (correct) CorrectGreen else WrongRed,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (correct) "回答正确" else "回答错误",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (correct) CorrectGreen else WrongRed,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "正确答案：${displayAnswer(question)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            question.explanation?.takeIf { it.isNotBlank() }?.let { explanation ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LoadingBox(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CenteredError(message: String, onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("加载失败", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        GradientButton(text = "返回", onClick = onExit)
    }
}

private fun displayOptions(question: Question): List<Option> =
    if (question.type == QuestionType.JUDGE) {
        listOf(Option("T", "正确"), Option("F", "错误"))
    } else {
        question.options
    }

private fun displayKey(question: Question, key: String): String =
    if (question.type == QuestionType.JUDGE) {
        if (key == "T") "对" else "错"
    } else {
        key
    }

private fun displayAnswer(question: Question): String =
    if (question.type == QuestionType.JUDGE) {
        if (question.answer == "T") "正确" else "错误"
    } else {
        question.answer.toList().joinToString("、")
    }

private fun optionVisualState(
    question: Question,
    option: Option,
    state: PracticeUiState,
): OptionVisualState {
    val isSelected = option.key in state.selected
    val isCorrectOption = question.answer.contains(option.key)
    return when {
        state.submitted && isCorrectOption -> OptionVisualState.CORRECT
        state.submitted && isSelected -> OptionVisualState.WRONG
        state.submitted -> OptionVisualState.DISABLED
        isSelected -> OptionVisualState.SELECTED
        else -> OptionVisualState.IDLE
    }
}
```

- [ ] **Step 3: 编译**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。若 `LinearProgressIndicator(progress = { … })` 在当前 Material3 版本不存在，退回 `LinearProgressIndicator(progress = state.progress, modifier = …)`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/shutixing/app/ui/practice/PracticeScreen.kt \
  app/src/main/java/com/shutixing/app/ui/result/ResultScreen.kt
git commit -m "feat(practice): add practice and result screens"
```

---

### Task 21: 错题本与题库列表

**Files:**
- Create: `app/src/main/java/com/shutixing/app/ui/wrongbook/WrongBookViewModel.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/wrongbook/WrongBookScreen.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/banks/BankListViewModel.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/banks/BanksScreen.kt`

- [ ] **Step 1: 错题本 ViewModel**

`WrongBookViewModel.kt`：

```kotlin
package com.shutixing.app.ui.wrongbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shutixing.app.data.repository.PracticeRepository
import com.shutixing.app.di.AppContainer
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.domain.model.QuestionWithState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WrongBookUiState(
    val loading: Boolean = true,
    val items: List<QuestionWithState> = emptyList(),
    val filter: QuestionType? = null,
) {
    val filtered: List<QuestionWithState>
        get() = if (filter == null) items else items.filter { it.question.type == filter }
}

class WrongBookViewModel(
    private val practiceRepository: PracticeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(WrongBookUiState())
    val state: StateFlow<WrongBookUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val items = practiceRepository.wrongBook()
            _state.value = _state.value.copy(loading = false, items = items)
        }
    }

    fun setFilter(type: QuestionType?) {
        _state.value = _state.value.copy(filter = type)
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { WrongBookViewModel(container.practiceRepository) }
        }
    }
}
```

- [ ] **Step 2: 错题本页面**

`WrongBookScreen.kt`：

```kotlin
package com.shutixing.app.ui.wrongbook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.ui.components.AppCard
import com.shutixing.app.ui.components.EmptyState
import com.shutixing.app.ui.components.GradientButton
import com.shutixing.app.ui.components.QuestionTypeBadge
import java.time.format.DateTimeFormatter

@Composable
fun WrongBookScreen(
    viewModel: WrongBookViewModel,
    onStartSession: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("错题本", style = MaterialTheme.typography.headlineLarge)
            Text(
                "${state.items.size} 道题等待巩固",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopicFilter("全部", state.filter == null) { viewModel.setFilter(null) }
                TopicFilter("单选", state.filter == QuestionType.SINGLE) { viewModel.setFilter(QuestionType.SINGLE) }
                TopicFilter("多选", state.filter == QuestionType.MULTIPLE) { viewModel.setFilter(QuestionType.MULTIPLE) }
                TopicFilter("判断", state.filter == QuestionType.JUDGE) { viewModel.setFilter(QuestionType.JUDGE) }
            }
        }

        if (state.items.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.TaskAlt,
                title = "错题本是空的",
                message = "刷题时答错的题目会自动收进来，连续答对两次后移出。",
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.filtered, key = { it.question.id }) { item ->
                    AppCard(Modifier.fillMaxWidth()) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                QuestionTypeBadge(item.question.type)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "错 ${item.review.wrongCount} 次",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                item.question.stem,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "下次复习 " + item.review.nextReviewDate.format(
                                    DateTimeFormatter.ofPattern("M月d日"),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Box(Modifier.padding(20.dp)) {
            GradientButton(
                text = "开始错题专项",
                onClick = onStartSession,
                enabled = state.items.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TopicFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
        border = null,
    )
}
```

- [ ] **Step 3: 题库列表 ViewModel**

`BankListViewModel.kt`：

```kotlin
package com.shutixing.app.ui.banks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shutixing.app.data.repository.StatsRepository
import com.shutixing.app.di.AppContainer
import com.shutixing.app.domain.model.BankMastery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BankListUiState(
    val loading: Boolean = true,
    val banks: List<BankMastery> = emptyList(),
    val query: String = "",
) {
    val filtered: List<BankMastery>
        get() = if (query.isBlank()) banks else banks.filter { it.bankName.contains(query.trim(), ignoreCase = true) }
}

class BankListViewModel(
    private val statsRepository: StatsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BankListUiState())
    val state: StateFlow<BankListUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = false, banks = statsRepository.bankMastery())
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { BankListViewModel(container.statsRepository) }
        }
    }
}
```

- [ ] **Step 4: 题库列表页面**

`BanksScreen.kt`：

```kotlin
package com.shutixing.app.ui.banks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shutixing.app.ui.components.AppCard
import com.shutixing.app.ui.components.EmptyState
import com.shutixing.app.ui.components.GradientButton

@Composable
fun BanksScreen(
    viewModel: BankListViewModel,
    onOpenBank: (String) -> Unit,
    onImport: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("题库", style = MaterialTheme.typography.headlineLarge)
            Text(
                "共 ${state.banks.sumOf { it.total }} 道题",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索题库") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            )
        }

        if (state.banks.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.MenuBook,
                title = "还没有题库",
                message = "导入一份文字版 PDF，自动提取题目和答案。",
                actionLabel = "导入 PDF",
                onAction = onImport,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.filtered, key = { it.bankId }) { bank ->
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenBank(bank.bankId) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(bank.bankName, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${bank.total} 题 · 已掌握 ${(bank.mastery * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { bank.mastery },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                )
                            }
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Box(Modifier.padding(20.dp)) {
                GradientButton(
                    text = "导入 PDF 题库",
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
```

`BanksScreen.kt` 需要补 `import androidx.compose.foundation.clickable`。

- [ ] **Step 5: 编译**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/shutixing/app/ui/wrongbook app/src/main/java/com/shutixing/app/ui/banks
git commit -m "feat(ui): add wrong book and bank list screens"
```

---

### Task 22: 题库详情与题目编辑

**Files:**
- Create: `app/src/main/java/com/shutixing/app/ui/bankdetail/BankDetailViewModel.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/bankdetail/BankDetailScreen.kt`

- [ ] **Step 1: 题库详情 ViewModel**

`BankDetailViewModel.kt`：

```kotlin
package com.shutixing.app.ui.bankdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shutixing.app.data.repository.BankRepository
import com.shutixing.app.di.AppContainer
import com.shutixing.app.domain.model.Bank
import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.parser.StemHasher
import com.shutixing.app.parser.model.ParsedQuestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DetailFilter { ALL, FAVORITE, SINGLE, MULTIPLE, JUDGE }

data class BankDetailUiState(
    val loading: Boolean = true,
    val bankName: String = "",
    val questions: List<Question> = emptyList(),
    val query: String = "",
    val filter: DetailFilter = DetailFilter.ALL,
) {
    val filtered: List<Question>
        get() {
            val byType = when (filter) {
                DetailFilter.ALL -> questions
                DetailFilter.FAVORITE -> questions.filter { it.isFavorite }
                DetailFilter.SINGLE -> questions.filter { it.type == QuestionType.SINGLE }
                DetailFilter.MULTIPLE -> questions.filter { it.type == QuestionType.MULTIPLE }
                DetailFilter.JUDGE -> questions.filter { it.type == QuestionType.JUDGE }
            }
            return if (query.isBlank()) byType else byType.filter { it.stem.contains(query.trim(), ignoreCase = true) }
        }
}

class BankDetailViewModel(
    private val bankId: String,
    private val bankRepository: BankRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BankDetailUiState())
    val state: StateFlow<BankDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val bank = bankRepository.getBank(bankId)
            if (bank != null) _state.update { it.copy(bankName = bank.name) }
            bankRepository.observeQuestions(bankId).collect { questions ->
                _state.update { it.copy(loading = false, questions = questions) }
            }
        }
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun setFilter(filter: DetailFilter) = _state.update { it.copy(filter = filter) }

    fun toggleFavorite(question: Question) {
        viewModelScope.launch {
            bankRepository.setFavorite(question.id, !question.isFavorite)
        }
    }

    fun updateQuestion(question: Question) {
        val updated = question.copy(stemHash = StemHasher.hash(question.stem, question.options))
        viewModelScope.launch { bankRepository.updateQuestion(updated) }
    }

    fun addQuestion(parsed: ParsedQuestion) {
        viewModelScope.launch { bankRepository.addQuestion(bankId, parsed) }
    }

    fun deleteQuestion(id: String) {
        viewModelScope.launch { bankRepository.deleteQuestion(id) }
    }

    suspend fun exportData(): Pair<Bank, List<Question>>? = bankRepository.exportData(bankId)

    fun deleteBank(onDone: () -> Unit) {
        viewModelScope.launch {
            bankRepository.deleteBank(bankId)
            onDone()
        }
    }

    companion object {
        fun factory(container: AppContainer, bankId: String) = viewModelFactory {
            initializer { BankDetailViewModel(bankId, container.bankRepository) }
        }
    }
}
```

- [ ] **Step 2: 题库详情页面与编辑弹窗**

`BankDetailScreen.kt`：

```kotlin
package com.shutixing.app.ui.bankdetail

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shutixing.app.data.export.BankExporter
import com.shutixing.app.domain.answer.AnswerChecker
import com.shutixing.app.domain.model.Option
import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.parser.model.ParsedQuestion
import com.shutixing.app.ui.components.AppCard
import com.shutixing.app.ui.components.GradientButton
import com.shutixing.app.ui.components.QuestionTypeBadge
import com.shutixing.app.ui.theme.Amber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BankDetailScreen(
    viewModel: BankDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editorTarget by remember { mutableStateOf<Question?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showDeleteBank by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val data = viewModel.exportData()
                if (data != null) {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { stream ->
                                BankExporter.write(stream, data.first, data.second)
                            }
                        }
                    }.onSuccess {
                        Toast.makeText(context, "题库已导出", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回") }
            Column(Modifier.weight(1f)) {
                Text(
                    state.bankName.ifBlank { "题库" },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${state.questions.size} 道题",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { editorTarget = null; showAdd = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "添加题目")
            }
            IconButton(
                onClick = { exportLauncher.launch("${state.bankName.ifBlank { "题库" }}.shuatixing.json") },
            ) {
                Icon(Icons.Rounded.FileDownload, contentDescription = "导出题库")
            }
            IconButton(onClick = { showDeleteBank = true }) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除题库")
            }
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索题目") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailFilterChip("全部", state.filter == DetailFilter.ALL) { viewModel.setFilter(DetailFilter.ALL) }
                DetailFilterChip("收藏", state.filter == DetailFilter.FAVORITE) { viewModel.setFilter(DetailFilter.FAVORITE) }
                DetailFilterChip("单选", state.filter == DetailFilter.SINGLE) { viewModel.setFilter(DetailFilter.SINGLE) }
                DetailFilterChip("多选", state.filter == DetailFilter.MULTIPLE) { viewModel.setFilter(DetailFilter.MULTIPLE) }
                DetailFilterChip("判断", state.filter == DetailFilter.JUDGE) { viewModel.setFilter(DetailFilter.JUDGE) }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (state.filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.questions.isEmpty()) "题库还是空的，点击右上角添加题目" else "没有匹配的题目",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.filtered, key = { it.id }) { question ->
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editorTarget = question },
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                QuestionTypeBadge(question.type)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    question.stem,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "答案：${displayAnswer(question)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.toggleFavorite(question) }) {
                                Icon(
                                    imageVector = if (question.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    contentDescription = "收藏",
                                    tint = if (question.isFavorite) Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorTarget != null) {
        val target = editorTarget!!
        QuestionEditDialog(
            initial = target,
            onDismiss = { editorTarget = null },
            onSave = { parsed ->
                viewModel.updateQuestion(
                    target.copy(
                        type = parsed.type,
                        stem = parsed.stem,
                        options = parsed.options,
                        answer = parsed.answer,
                        explanation = parsed.explanation,
                    ),
                )
                editorTarget = null
            },
            onDelete = {
                viewModel.deleteQuestion(target.id)
                editorTarget = null
            },
        )
    }

    if (showAdd) {
        QuestionEditDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { parsed ->
                viewModel.addQuestion(parsed)
                showAdd = false
            },
        )
    }

    if (showDeleteBank) {
        AlertDialog(
            onDismissRequest = { showDeleteBank = false },
            title = { Text("删除题库？") },
            text = { Text("题库和其中的刷题记录会一并删除，无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteBank = false
                        viewModel.deleteBank(onBack)
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBank = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DetailFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(999.dp),
        border = null,
    )
}

@Composable
fun QuestionEditDialog(
    initial: Question?,
    onDismiss: () -> Unit,
    onSave: (ParsedQuestion) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val initialOptions = initial?.options?.takeIf { it.isNotEmpty() }
        ?: listOf(Option("A", ""), Option("B", ""), Option("C", ""), Option("D", ""))
    val options = remember(initial?.id) {
        mutableStateListOf<Option>().apply { addAll(initialOptions) }
    }
    var type by remember(initial?.id) { mutableStateOf(initial?.type ?: QuestionType.SINGLE) }
    var stem by remember(initial?.id) { mutableStateOf(initial?.stem.orEmpty()) }
    var answer by remember(initial?.id) {
        mutableStateOf(
            when (initial?.answer) {
                "T" -> "对"
                "F" -> "错"
                else -> initial?.answer.orEmpty()
            },
        )
    }
    var explanation by remember(initial?.id) { mutableStateOf(initial?.explanation.orEmpty()) }
    var errorText by remember(initial?.id) { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (initial == null) "添加题目" else "编辑题目",
                    style = MaterialTheme.typography.titleLarge,
                )
                OutlinedTextField(
                    value = stem,
                    onValueChange = { stem = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("题干") },
                    minLines = 2,
                    shape = RoundedCornerShape(14.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuestionType.entries.forEach { entry ->
                        FilterChip(
                            selected = type == entry,
                            onClick = { type = entry },
                            label = {
                                Text(
                                    when (entry) {
                                        QuestionType.SINGLE -> "单选"
                                        QuestionType.MULTIPLE -> "多选"
                                        QuestionType.JUDGE -> "判断"
                                    },
                                )
                            },
                            shape = RoundedCornerShape(999.dp),
                        )
                    }
                }

                if (type != QuestionType.JUDGE) {
                    options.forEachIndexed { index, option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(option.key, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = option.text,
                                onValueChange = { value ->
                                    options[index] = option.copy(text = value)
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                placeholder = { Text("选项 ${option.key}") },
                            )
                            TextButton(
                                onClick = {
                                    options.removeAt(index)
                                    options.forEachIndexed { i, o ->
                                        options[i] = o.copy(key = ('A' + i).toString())
                                    }
                                },
                                enabled = options.size > 2,
                            ) { Text("删") }
                        }
                    }
                    if (options.size < 8) {
                        TextButton(
                            onClick = {
                                options.add(Option(('A' + options.size).toString(), ""))
                            },
                        ) { Text("添加选项") }
                    }
                }

                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("答案") },
                    supportingText = {
                        Text(
                            when (type) {
                                QuestionType.JUDGE -> "填写“对”或“错”"
                                QuestionType.MULTIPLE -> "多选请连写，例如 AC"
                                QuestionType.SINGLE -> "填写选项字母，例如 B"
                            },
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("解析（可选）") },
                    minLines = 2,
                    shape = RoundedCornerShape(14.dp),
                )
                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text("删除题目", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        GradientButton(
                            text = "保存",
                            onClick = {
                                val normalized = AnswerChecker.normalize(type, answer)
                                val filled = options.map { Option(it.key, it.text.trim()) }.filter { it.text.isNotEmpty() }
                                val error = when {
                                    stem.isBlank() -> "题干不能为空"
                                    normalized.isEmpty() -> "请填写有效答案"
                                    type != QuestionType.JUDGE && filled.isEmpty() -> "请至少填写一个选项"
                                    type != QuestionType.JUDGE &&
                                        normalized.toSet().any { letter -> filled.none { it.key == letter.toString() } } ->
                                        "答案字母不在选项中"
                                    else -> null
                                }
                                if (error != null) {
                                    errorText = error
                                } else {
                                    onSave(
                                        ParsedQuestion(
                                            type = type,
                                            stem = stem.trim(),
                                            options = if (type == QuestionType.JUDGE) emptyList() else filled,
                                            answer = normalized,
                                            explanation = explanation.trim().takeIf { it.isNotEmpty() },
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun displayAnswer(question: Question): String =
    if (question.type == QuestionType.JUDGE) {
        if (question.answer == "T") "正确" else "错误"
    } else {
        question.answer.toList().joinToString("、")
    }
```

注意：`QuestionType.entries` 需要 Kotlin 1.9+（项目使用 2.0.21，可用）。

- [ ] **Step 3: 编译**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/shutixing/app/ui/bankdetail
git commit -m "feat(ui): add bank detail, editing, export and delete"
```

---

### Task 23: PDF 导入向导（解析 → 校对 → 入库）

**Files:**
- Create: `app/src/main/java/com/shutixing/app/ui/importwizard/ImportViewModel.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/importwizard/ImportScreen.kt`

- [ ] **Step 1: 导入 ViewModel**

`ImportViewModel.kt`：

```kotlin
package com.shutixing.app.ui.importwizard

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shutixing.app.data.repository.BankRepository
import com.shutixing.app.di.AppContainer
import com.shutixing.app.domain.model.Bank
import com.shutixing.app.parser.Normalizer
import com.shutixing.app.parser.PdfImportException
import com.shutixing.app.parser.PdfTextExtractor
import com.shutixing.app.parser.QuestionParser
import com.shutixing.app.parser.StemHasher
import com.shutixing.app.parser.model.ParsedQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ImportPhase { IDLE, PARSING, REVIEW, IMPORTING, DONE, ERROR }

data class ImportItem(
    val question: ParsedQuestion,
    val hash: String,
    val include: Boolean = true,
    val duplicate: Boolean = false,
)

data class ImportUiState(
    val phase: ImportPhase = ImportPhase.IDLE,
    val fileName: String = "",
    val progress: Float = 0f,
    val items: List<ImportItem> = emptyList(),
    val banks: List<Bank> = emptyList(),
    val targetBankId: String? = null,
    val bankName: String = "",
    val skipDuplicates: Boolean = true,
    val error: String? = null,
    val importedBankId: String? = null,
    val importedCount: Int = 0,
) {
    val duplicateCount: Int get() = items.count { it.duplicate }
    val selectedCount: Int get() = items.count { it.include && !(skipDuplicates && it.duplicate) }
}

class ImportViewModel(
    private val extractor: PdfTextExtractor,
    private val bankRepository: BankRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private var knownHashes: Set<String> = emptySet()

    fun parse(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(phase = ImportPhase.PARSING, fileName = fileName, progress = 0f, error = null)
            }
            try {
                val lines = extractor.extract(uri) { done, total ->
                    _state.update {
                        it.copy(progress = if (total == 0) 0f else done.toFloat() / total)
                    }
                }
                val parsed = withContext(Dispatchers.Default) {
                    val normalized = Normalizer.normalize(lines)
                    QuestionParser.parse(normalized).questions
                }
                if (parsed.isEmpty()) {
                    _state.update {
                        it.copy(
                            phase = ImportPhase.ERROR,
                            error = "未识别到题目。可能是扫描版 PDF 或排版特殊，暂不支持。",
                        )
                    }
                    return@launch
                }
                knownHashes = bankRepository.allHashes()
                val items = parsed.map { question ->
                    val hash = StemHasher.hash(question.stem, question.options)
                    ImportItem(
                        question = question,
                        hash = hash,
                        include = hash !in knownHashes,
                        duplicate = hash in knownHashes,
                    )
                }
                _state.update {
                    it.copy(
                        phase = ImportPhase.REVIEW,
                        progress = 1f,
                        items = items,
                        banks = bankRepository.allBanks(),
                        bankName = fileName.substringBeforeLast('.', fileName).ifBlank { "导入题库" },
                    )
                }
            } catch (e: PdfImportException) {
                _state.update {
                    it.copy(phase = ImportPhase.ERROR, error = e.message ?: "PDF 读取失败")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(phase = ImportPhase.ERROR, error = e.message ?: "解析失败")
                }
            }
        }
    }

    fun toggleInclude(index: Int) {
        _state.update { current ->
            current.copy(
                items = current.items.mapIndexed { i, item ->
                    if (i == index) item.copy(include = !item.include) else item
                },
            )
        }
    }

    fun editItem(index: Int, parsed: ParsedQuestion) {
        val hash = StemHasher.hash(parsed.stem, parsed.options)
        _state.update { current ->
            current.copy(
                items = current.items.mapIndexed { i, item ->
                    if (i == index) {
                        item.copy(
                            question = parsed,
                            hash = hash,
                            duplicate = hash in knownHashes,
                            include = hash !in knownHashes,
                        )
                    } else {
                        item
                    }
                },
            )
        }
    }

    fun setBankName(value: String) = _state.update { it.copy(bankName = value) }

    fun selectTarget(bankId: String?) = _state.update { it.copy(targetBankId = bankId) }

    fun setSkipDuplicates(value: Boolean) = _state.update { it.copy(skipDuplicates = value) }

    fun importQuestions() {
        val current = _state.value
        val chosen = current.items
            .filter { it.include && !(current.skipDuplicates && it.duplicate) }
            .map { it.question }
        if (chosen.isEmpty()) {
            _state.update { it.copy(error = "没有可导入的题目") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(phase = ImportPhase.IMPORTING, error = null) }
            try {
                val targetId = current.targetBankId
                val bankId = if (targetId != null) {
                    bankRepository.appendQuestions(targetId, chosen)
                    targetId
                } else {
                    bankRepository.createBank(
                        name = current.bankName.ifBlank { "导入题库" },
                        sourceFileName = current.fileName,
                        questions = chosen,
                    )
                }
                _state.update {
                    it.copy(
                        phase = ImportPhase.DONE,
                        importedBankId = bankId,
                        importedCount = chosen.size,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(phase = ImportPhase.REVIEW, error = e.message ?: "导入失败")
                }
            }
        }
    }

    fun reset() {
        _state.value = ImportUiState(phase = ImportPhase.IDLE)
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                ImportViewModel(container.pdfTextExtractor, container.bankRepository)
            }
        }
    }
}
```

- [ ] **Step 2: 导入页面**

`ImportScreen.kt`：

```kotlin
package com.shutixing.app.ui.importwizard

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shutixing.app.domain.model.Question
import com.shutixing.app.parser.model.ParseWarning
import com.shutixing.app.ui.bankdetail.QuestionEditDialog
import com.shutixing.app.ui.components.AppCard
import com.shutixing.app.ui.components.GradientButton
import com.shutixing.app.ui.components.PillChip
import com.shutixing.app.ui.components.ProgressRing
import com.shutixing.app.ui.components.QuestionTypeBadge
import com.shutixing.app.ui.theme.CorrectGreen

@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onDone: (String?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.parse(uri, queryDisplayName(context, uri))
        }
    }

    when (state.phase) {
        ImportPhase.IDLE -> IdleContent { pickLauncher.launch(arrayOf("application/pdf")) }
        ImportPhase.PARSING -> ParsingContent(state)
        ImportPhase.REVIEW -> ReviewContent(
            state = state,
            onPick = { pickLauncher.launch(arrayOf("application/pdf")) },
            onToggle = viewModel::toggleInclude,
            onEdit = { editingIndex = it },
            onBankName = viewModel::setBankName,
            onSelectBank = viewModel::selectTarget,
            onSkipDuplicates = viewModel::setSkipDuplicates,
            onImport = viewModel::importQuestions,
        )
        ImportPhase.IMPORTING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        ImportPhase.DONE -> DoneContent(state, onDone = { onDone(state.importedBankId) }, onAgain = viewModel::reset)
        ImportPhase.ERROR -> ErrorContent(state.error.orEmpty(), viewModel::reset)
    }

    editingIndex?.let { index ->
        val item = state.items.getOrNull(index)
        if (item != null) {
            val draft = Question(
                id = "draft-$index",
                bankId = "",
                type = item.question.type,
                stem = item.question.stem,
                options = item.question.options,
                answer = item.question.answer,
                explanation = item.question.explanation,
            )
            QuestionEditDialog(
                initial = draft,
                onDismiss = { editingIndex = null },
                onSave = { parsed ->
                    viewModel.editItem(index, parsed)
                    editingIndex = null
                },
            )
        }
    }
}

@Composable
private fun IdleContent(onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("导入 PDF 题库", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "支持文字版 PDF，自动识别题目、选项、答案和解析；导入前可逐题校对。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        GradientButton("选择 PDF 文件", onClick = onPick, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ParsingContent(state: ImportUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ProgressRing(progress = state.progress, diameter = 160.dp, strokeWidth = 14.dp) {
            Text(
                "${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("正在解析 PDF…", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            state.fileName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReviewContent(
    state: ImportUiState,
    onPick: () -> Unit,
    onToggle: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onBankName: (String) -> Unit,
    onSelectBank: (String?) -> Unit,
    onSkipDuplicates: (Boolean) -> Unit,
    onImport: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("校对题目", style = MaterialTheme.typography.headlineLarge)
            Text(
                "识别 ${state.items.size} 题 · 重复 ${state.duplicateCount} 题 · 已选 ${state.selectedCount} 题",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.bankName,
                onValueChange = onBankName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("题库名称") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                enabled = state.targetBankId == null,
            )
            if (state.banks.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.targetBankId == null,
                        onClick = { onSelectBank(null) },
                        label = { Text("新建题库") },
                        shape = RoundedCornerShape(999.dp),
                    )
                    state.banks.forEach { bank ->
                        FilterChip(
                            selected = state.targetBankId == bank.id,
                            onClick = { onSelectBank(bank.id) },
                            label = { Text(bank.name) },
                            shape = RoundedCornerShape(999.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("跳过重复题目", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = state.skipDuplicates, onCheckedChange = onSkipDuplicates)
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(state.items) { index, item ->
                AppCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Checkbox(checked = item.include, onCheckedChange = { onToggle(index) })
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                QuestionTypeBadge(item.question.type)
                                if (item.duplicate) {
                                    Spacer(Modifier.width(6.dp))
                                    PillChip(
                                        "重复",
                                        container = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                                        contentColor = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                item.question.stem.ifBlank { "（空题干）" },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val warnings = item.question.warnings
                            if (warnings.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    warnings.joinToString(" · ", transform = ::warningLabel),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        IconButton(onClick = { onEdit(index) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "编辑")
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onPick,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("重新选择") }
            GradientButton(
                text = "导入 ${state.selectedCount} 题",
                onClick = onImport,
                enabled = state.selectedCount > 0,
                modifier = Modifier.weight(2f),
            )
        }
    }
}

@Composable
private fun DoneContent(state: ImportUiState, onDone: () -> Unit, onAgain: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = CorrectGreen,
        )
        Spacer(Modifier.height(16.dp))
        Text("导入成功", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "已导入 ${state.importedCount} 道题",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        GradientButton("完成", onClick = onDone, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onAgain) { Text("继续导入其他题库") }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.PictureAsPdf,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Text("解析失败", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        GradientButton("重新选择文件", onClick = onRetry, modifier = Modifier.fillMaxWidth())
    }
}

private fun warningLabel(warning: ParseWarning): String = when (warning) {
    ParseWarning.MISSING_ANSWER -> "缺答案"
    ParseWarning.INVALID_ANSWER -> "答案无效"
    ParseWarning.ANSWER_NOT_IN_OPTIONS -> "答案不在选项"
    ParseWarning.MISSING_OPTIONS -> "缺选项"
    ParseWarning.EMPTY_STEM -> "题干为空"
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index)
        }
    }
    return "题库.pdf"
}
```

- [ ] **Step 3: 编译**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/shutixing/app/ui/importwizard
git commit -m "feat(ui): add PDF import wizard with review step"
```

---

### Task 24: 学习统计页

**Files:**
- Create: `app/src/main/java/com/shutixing/app/ui/stats/StatsViewModel.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/stats/StatsScreen.kt`

- [ ] **Step 1: 统计 ViewModel**

`StatsViewModel.kt`：

```kotlin
package com.shutixing.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shutixing.app.data.repository.StatsRepository
import com.shutixing.app.di.AppContainer
import com.shutixing.app.domain.model.BankMastery
import com.shutixing.app.domain.model.DailyStat
import com.shutixing.app.domain.stats.StatsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

data class StatsUiState(
    val loading: Boolean = true,
    val daily: List<DailyStat> = emptyList(),
    val streak: Int = 0,
    val banks: List<BankMastery> = emptyList(),
    val totalQuestions: Int = 0,
    val totalMastered: Int = 0,
) {
    val totalAnswered: Int get() = daily.sumOf { it.answered }
    val overallAccuracy: Float get() = StatsCalculator.accuracy(daily)
}

class StatsViewModel(
    private val statsRepository: StatsRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val today = todayProvider()
            val daily = statsRepository.observeDailyStats().first()
            _state.value = StatsUiState(
                loading = false,
                daily = daily,
                streak = StatsCalculator.streak(statsRepository.activeDates(), today),
                banks = statsRepository.bankMastery(),
                totalQuestions = statsRepository.totalQuestions(),
                totalMastered = statsRepository.totalMastered(),
            )
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { StatsViewModel(container.statsRepository) }
        }
    }
}
```

- [ ] **Step 2: 统计页面**

`StatsScreen.kt`：

```kotlin
package com.shutixing.app.ui.stats

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shutixing.app.domain.model.BankMastery
import com.shutixing.app.domain.model.DailyStat
import com.shutixing.app.ui.components.AppCard
import com.shutixing.app.ui.components.GradientHeroCard
import com.shutixing.app.ui.components.SectionHeader
import com.shutixing.app.ui.components.StatTile
import com.shutixing.app.ui.theme.DeepTeal
import com.shutixing.app.ui.theme.InkBlue
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    var rangeDays by remember { mutableStateOf(7) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("学习统计", style = MaterialTheme.typography.headlineLarge)
            Text(
                "每一道题，都算数。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            GradientHeroCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "连续打卡",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${state.streak}",
                                style = MaterialTheme.typography.displaySmall,
                                color = Color.White,
                            )
                            Text(
                                " 天",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.78f),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "累计作答",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                        Text(
                            "${state.totalAnswered} 题",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "总正确率 ${(state.overallAccuracy * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                    }
                }
            }
        }

        item {
            AppCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatTile("题库总量", "${state.totalQuestions}", accent = InkBlue)
                    StatTile("已掌握", "${state.totalMastered}", accent = DeepTeal)
                    StatTile("掌握率", "${((if (state.totalQuestions == 0) 0f else state.totalMastered.toFloat() / state.totalQuestions) * 100).toInt()}%")
                }
            }
        }

        item {
            SectionHeader(
                title = "做题量",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = rangeDays == 7,
                            onClick = { rangeDays = 7 },
                            label = { Text("7 天") },
                            shape = RoundedCornerShape(999.dp),
                        )
                        FilterChip(
                            selected = rangeDays == 30,
                            onClick = { rangeDays = 30 },
                            label = { Text("30 天") },
                            shape = RoundedCornerShape(999.dp),
                        )
                    }
                },
            )
        }

        item {
            AppCard(Modifier.fillMaxWidth()) {
                BarChart(state.daily, rangeDays)
            }
        }

        item { SectionHeader(title = "正确率趋势") }

        item {
            AppCard(Modifier.fillMaxWidth()) {
                AccuracyLine(state.daily, rangeDays)
            }
        }

        item { SectionHeader(title = "本月打卡") }

        item {
            AppCard(Modifier.fillMaxWidth()) {
                MonthHeatmap(state.daily)
            }
        }

        if (state.banks.isNotEmpty()) {
            item { SectionHeader(title = "题库掌握度") }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        state.banks.forEach { bank ->
                            BankMasteryRow(bank)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarChart(daily: List<DailyStat>, days: Int) {
    val end = LocalDate.now()
    val series = (days - 1 downTo 0).map { offset -> end.minusDays(offset.toLong()) }
        .map { date -> date to (daily.firstOrNull { it.date == date }?.answered ?: 0) }
    val max = (series.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val labelFormatter = DateTimeFormatter.ofPattern("E", Locale.CHINA)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(if (days > 14) 2.dp else 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        series.forEach { (date, count) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fraction = count.toFloat() / max
                val height by animateDpAsState(
                    targetValue = (70 * fraction).dp.coerceAtLeast(3.dp),
                    animationSpec = tween(600),
                    label = "barHeight",
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(if (days > 14) 6.dp else 18.dp)
                        .height(height)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (count == 0) {
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                )
                            } else {
                                Brush.verticalGradient(listOf(InkBlue, DeepTeal))
                            },
                        ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (days <= 7) date.format(labelFormatter) else "${date.dayOfMonth}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AccuracyLine(daily: List<DailyStat>, days: Int) {
    val end = LocalDate.now()
    val values = (days - 1 downTo 0).map { offset ->
        daily.firstOrNull { it.date == end.minusDays(offset.toLong()) }?.accuracy ?: 0f
    }
    val lineColor = InkBlue
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (values.size < 2) return@Canvas
        val step = size.width / (values.size - 1)
        val points = values.mapIndexed { index, value ->
            Offset(index * step, size.height - value * size.height * 0.88f - 4f)
        }
        val path = Path().apply {
            points.forEachIndexed { index, point ->
                if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
        }
        drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        points.forEach { drawCircle(lineColor, radius = 3.5.dp.toPx(), center = it) }
    }
}

@Composable
private fun MonthHeatmap(daily: List<DailyStat>) {
    val today = LocalDate.now()
    val month = YearMonth.from(today)
    val counts = daily.associate { it.date to it.answered }
    val offset = month.atDay(1).dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val rows = ((offset + daysInMonth) + 6) / 7
    val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    Column {
        Row(Modifier.fillMaxWidth()) {
            weekLabels.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val day = row * 7 + column - offset + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .aspectRatio(1f),
                    ) {
                        if (day in 1..daysInMonth) {
                            val date = month.atDay(day)
                            val count = counts[date] ?: 0
                            val intensity = (count / 40f).coerceIn(0f, 1f)
                            val background = when {
                                count == 0 -> MaterialTheme.colorScheme.surfaceVariant
                                else -> InkBlue.copy(alpha = 0.18f + 0.72f * intensity)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(background),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$day",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (intensity > 0.55f) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BankMasteryRow(bank: BankMastery) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(
                bank.bankName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${bank.mastered} / ${bank.total}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { bank.mastery },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
        )
    }
}
```

- [ ] **Step 3: 编译**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/shutixing/app/ui/stats
git commit -m "feat(ui): add learning statistics charts"
```

---

### Task 25: 设置页、导航与应用入口

**Files:**
- Create: `app/src/main/java/com/shutixing/app/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/settings/SettingsScreen.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/navigation/Routes.kt`
- Create: `app/src/main/java/com/shutixing/app/ui/navigation/AppNav.kt`
- Modify: `app/src/main/java/com/shutixing/app/MainActivity.kt`

- [ ] **Step 1: 设置 ViewModel**

`SettingsViewModel.kt`：

```kotlin
package com.shutixing.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shutixing.app.data.db.AppDatabase
import com.shutixing.app.data.export.BankImporter
import com.shutixing.app.data.export.ImportResult
import com.shutixing.app.data.repository.BankRepository
import com.shutixing.app.data.repository.SettingsRepository
import com.shutixing.app.di.AppContainer
import com.shutixing.app.domain.model.ThemeMode
import com.shutixing.app.ui.practice.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val dailyTarget: Int = 30,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val message: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val bankRepository: BankRepository,
    private val database: AppDatabase,
    private val sessionStore: SessionStore,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.dailyTarget.collect { value ->
                _state.update { it.copy(dailyTarget = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.themeMode.collect { mode ->
                _state.update { it.copy(themeMode = mode) }
            }
        }
    }

    fun setDailyTarget(value: Int) {
        viewModelScope.launch { settingsRepository.setDailyTarget(value) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun importBankJson(text: String) {
        viewModelScope.launch {
            when (val result = BankImporter.parse(text)) {
                is ImportResult.Success -> {
                    bankRepository.createBank(
                        name = result.bankName,
                        sourceFileName = result.sourceFileName,
                        questions = result.questions,
                    )
                    _state.update { it.copy(message = "已导入 ${result.questions.size} 题") }
                }
                is ImportResult.Failure -> {
                    _state.update { it.copy(message = result.message) }
                }
            }
        }
    }

    fun clearAll(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { database.clearAllTables() }
            sessionStore.clear()
            _state.update { it.copy(message = "数据已清空") }
            onDone()
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsRepository = container.settingsRepository,
                    bankRepository = container.bankRepository,
                    database = container.database,
                    sessionStore = container.sessionStore,
                )
            }
        }
    }
}
```

- [ ] **Step 2: 设置页面**

`SettingsScreen.kt`：

```kotlin
package com.shutixing.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shutixing.app.domain.model.ThemeMode
import com.shutixing.app.ui.components.AppCard
import com.shutixing.app.ui.components.GradientButton
import com.shutixing.app.ui.components.SectionHeader

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTargetDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text != null) {
                viewModel.importBankJson(text)
            } else {
                Toast.makeText(context, "无法读取文件", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回") }
            Text("设置", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SectionHeader("学习设置")
            Spacer(Modifier.height(8.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("每日题量", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "先错题，再复习，最后新题",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "${state.dailyTarget} 题",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = { showTargetDialog = true }) { Text("修改") }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("外观")
            Spacer(Modifier.height(8.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("深色模式", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip("跟随系统", state.themeMode == ThemeMode.SYSTEM) { viewModel.setThemeMode(ThemeMode.SYSTEM) }
                        ThemeChip("浅色", state.themeMode == ThemeMode.LIGHT) { viewModel.setThemeMode(ThemeMode.LIGHT) }
                        ThemeChip("深色", state.themeMode == ThemeMode.DARK) { viewModel.setThemeMode(ThemeMode.DARK) }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("数据")
            Spacer(Modifier.height(8.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.FileOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("导入题库文件", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "支持朋友分享的 .json 题库",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearDialog = true }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("清空所有数据", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "题库、错题与统计会全部删除",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("关于")
            Spacer(Modifier.height(8.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("刷题星 0.1.0", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "完全离线 · 数据只保存在本机",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showTargetDialog) {
        var input by remember { mutableStateOf(state.dailyTarget.toString()) }
        AlertDialog(
            onDismissRequest = { showTargetDialog = false },
            title = { Text("每日题量") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value -> input = value.filter { it.isDigit() }.take(3) },
                    label = { Text("1 – 500") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = input.toIntOrNull()
                        if (value != null && value in 1..500) {
                            viewModel.setDailyTarget(value)
                            showTargetDialog = false
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showTargetDialog = false }) { Text("取消") }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空所有数据？") },
            text = { Text("此操作不可恢复，建议先导出需要的题库。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearAll {}
                    },
                ) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(999.dp),
    )
}
```

- [ ] **Step 3: 路由与导航**

`Routes.kt`：

```kotlin
package com.shutixing.app.ui.navigation

object Routes {
    const val HOME = "home"
    const val BANKS = "banks"
    const val WRONG_BOOK = "wrongbook"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
    const val PRACTICE_DAILY = "practice/daily"
    const val PRACTICE_WRONG = "practice/wrong"
    const val BANK_DETAIL = "bank/{bankId}"

    fun bankDetail(bankId: String) = "bank/$bankId"
}
```

`AppNav.kt`：

```kotlin
package com.shutixing.app.ui.navigation

import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shutixing.app.di.AppContainer
import com.shutixing.app.ui.bankdetail.BankDetailScreen
import com.shutixing.app.ui.bankdetail.BankDetailViewModel
import com.shutixing.app.ui.banks.BankListViewModel
import com.shutixing.app.ui.banks.BanksScreen
import com.shutixing.app.ui.home.HomeScreen
import com.shutixing.app.ui.home.HomeViewModel
import com.shutixing.app.ui.importwizard.ImportScreen
import com.shutixing.app.ui.importwizard.ImportViewModel
import com.shutixing.app.ui.practice.PracticeMode
import com.shutixing.app.ui.practice.PracticeScreen
import com.shutixing.app.ui.practice.PracticeViewModel
import com.shutixing.app.ui.settings.SettingsScreen
import com.shutixing.app.ui.settings.SettingsViewModel
import com.shutixing.app.ui.stats.StatsScreen
import com.shutixing.app.ui.stats.StatsViewModel
import com.shutixing.app.ui.wrongbook.WrongBookScreen
import com.shutixing.app.ui.wrongbook.WrongBookViewModel

@Composable
fun AppNav(container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in setOf(
        Routes.HOME,
        Routes.BANKS,
        Routes.WRONG_BOOK,
        Routes.STATS,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(currentRoute) { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
                HomeScreen(
                    viewModel = viewModel,
                    onStartDaily = { navController.navigate(Routes.PRACTICE_DAILY) },
                    onContinueSession = { navController.navigate(Routes.PRACTICE_DAILY) },
                    onOpenWrongBook = { navController.navigate(Routes.WRONG_BOOK) },
                    onOpenBanks = { navController.navigate(Routes.BANKS) },
                    onOpenImport = { navController.navigate(Routes.IMPORT) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.BANKS) {
                val viewModel: BankListViewModel = viewModel(factory = BankListViewModel.factory(container))
                BanksScreen(
                    viewModel = viewModel,
                    onOpenBank = { id -> navController.navigate(Routes.bankDetail(id)) },
                    onImport = { navController.navigate(Routes.IMPORT) },
                )
            }
            composable(Routes.WRONG_BOOK) {
                val viewModel: WrongBookViewModel = viewModel(factory = WrongBookViewModel.factory(container))
                WrongBookScreen(
                    viewModel = viewModel,
                    onStartSession = { navController.navigate(Routes.PRACTICE_WRONG) },
                )
            }
            composable(Routes.STATS) {
                val viewModel: StatsViewModel = viewModel(factory = StatsViewModel.factory(container))
                StatsScreen(viewModel = viewModel)
            }
            composable(Routes.SETTINGS) {
                val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
                SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.IMPORT) {
                val viewModel: ImportViewModel = viewModel(factory = ImportViewModel.factory(container))
                ImportScreen(
                    viewModel = viewModel,
                    onDone = { bankId ->
                        navController.popBackStack()
                        if (bankId != null) navController.navigate(Routes.bankDetail(bankId))
                    },
                )
            }
            composable(Routes.PRACTICE_DAILY) {
                val viewModel: PracticeViewModel = viewModel(factory = PracticeViewModel.factory(container))
                PracticeScreen(
                    viewModel = viewModel,
                    mode = PracticeMode.DAILY,
                    onExit = { navController.popBackStack() },
                    onOpenImport = { navController.navigate(Routes.IMPORT) },
                )
            }
            composable(Routes.PRACTICE_WRONG) {
                val viewModel: PracticeViewModel = viewModel(factory = PracticeViewModel.factory(container))
                PracticeScreen(
                    viewModel = viewModel,
                    mode = PracticeMode.WRONG_BOOK,
                    onExit = { navController.popBackStack() },
                    onOpenImport = { navController.navigate(Routes.IMPORT) },
                )
            }
            composable(
                route = Routes.BANK_DETAIL,
                arguments = listOf(navArgument("bankId") { type = NavType.StringType }),
            ) { entry ->
                val bankId = entry.arguments?.getString("bankId").orEmpty()
                val viewModel: BankDetailViewModel = viewModel(
                    key = bankId,
                    factory = BankDetailViewModel.factory(container, bankId),
                )
                BankDetailScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun AppBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            BottomItem("今日", Icons.Rounded.Today, currentRoute == Routes.HOME) { onNavigate(Routes.HOME) }
            BottomItem("题库", Icons.Rounded.MenuBook, currentRoute == Routes.BANKS) { onNavigate(Routes.BANKS) }
            BottomItem("错题", Icons.Rounded.ErrorOutline, currentRoute == Routes.WRONG_BOOK) { onNavigate(Routes.WRONG_BOOK) }
            BottomItem("统计", Icons.Rounded.Insights, currentRoute == Routes.STATS) { onNavigate(Routes.STATS) }
        }
    }
}

@Composable
private fun BottomItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
        animationSpec = tween(220),
        label = "bottomBackground",
    )
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
```

`NavHostController` import 未使用可删除。

- [ ] **Step 4: 应用入口**

`MainActivity.kt`：

```kotlin
package com.shutixing.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shutixing.app.domain.model.ThemeMode
import com.shutixing.app.ui.navigation.AppNav
import com.shutixing.app.ui.theme.ShuaTiXingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ShuatiXingApp).container
        setContent {
            val themeMode by container.settingsRepository.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM,
            )
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            ShuaTiXingTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNav(container)
                }
            }
        }
    }
}
```

- [ ] **Step 5: 构建、安装、跑通主流程**

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.shutixing.app/.MainActivity
```

预期：应用启动进入“今日”首页，底部四个标签可切换；设置页可改每日题量、深色模式。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/shutixing/app/ui/settings app/src/main/java/com/shutixing/app/ui/navigation \
  app/src/main/java/com/shutixing/app/MainActivity.kt
git commit -m "feat(ui): wire navigation, settings and app entry"
```

---

### Task 26: 视觉打磨、深色模式与截图验收

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/shutixing/app/ui/navigation/AppNav.kt`
- Create: `scripts/screenshots.sh`

- [ ] **Step 1: 输入法与系统栏适配**

`AndroidManifest.xml` 的 `<activity>` 增加：

```xml
android:windowSoftInputMode="adjustResize"
```

新增 `app/src/main/res/values-night/colors.xml`，避免深色模式下启动闪白：

```xml
<resources>
    <color name="launch_background">#0B1220</color>
</resources>
```

`AppNav.kt` 的 `Surface` 内 `Row` 改为：

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 12.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.SpaceAround,
)
```

补充 import：`androidx.compose.foundation.layout.navigationBarsPadding`。

- [ ] **Step 2: 建立截图脚本**

`scripts/screenshots.sh`：

```bash
#!/usr/bin/env bash
set -euo pipefail
OUT=${1:-screenshots}
mkdir -p "$OUT"
adb shell am start -n com.shutixing.app/.MainActivity >/dev/null
sleep 2
adb exec-out screencap -p > "$OUT/01-home.png"
```

执行：

```bash
chmod +x scripts/screenshots.sh
./scripts/screenshots.sh
```

然后用 Read 工具查看 `screenshots/01-home.png`。

- [ ] **Step 3: 逐页截图并检查清单**

依次在设备上进入以下页面，每页执行 `adb exec-out screencap -p > screenshots/<name>.png`：

| 截图 | 页面 | 检查点 |
|---|---|---|
| 01-home | 今日首页 | 渐变卡片无断层；进度环居中；三列统计不换行；快捷卡片等高 |
| 02-practice | 刷题页 | 题干与选项间距一致；选中态对比明显；提交后反馈卡不遮挡下一题 |
| 03-retest | 错题重测 | 顶部重测横幅可见；按钮配色为蓝绿而非橙色 |
| 04-result | 结果页 | 大环居中；按钮宽度一致；无文字截断 |
| 05-wrongbook | 错题本 | 卡片高度随内容自适应；筛选 chip 可横向滑动不挤压 |
| 06-banks | 题库 | 搜索框圆角统一；进度条与文字对齐 |
| 07-bankdetail | 题库详情 | 题目列表两行截断；收藏星标点击不误触编辑 |
| 08-import | 导入页 | 选择文件按钮醒目；校对列表警告文案为红色且不溢出 |
| 09-stats | 统计 | 柱状图在 7/30 天切换时不变形；热力图格子为正方形 |
| 10-settings | 设置 | 分组卡片间距一致；危险操作使用红色 |

任何一项不满足：修改对应页面后重新截图，直到通过。

- [ ] **Step 4: 深色模式验收**

在设置中切换“深色”，重新截取 01/02/04/09 四页。检查：
- 背景为 `#0B1220`，卡片为 `#131C2E`，无纯白刺眼块
- 渐变卡片文字仍可读（对比度 ≥ 4.5:1）
- 判对/判错颜色在深色下仍可区分

- [ ] **Step 5: 大字体与无障碍检查**

```bash
adb shell settings put system font_scale 1.3
```

重启应用后检查首页、刷题页、设置页无文字溢出；完成后恢复：

```bash
adb shell settings put system font_scale 1.0
```

为所有图标按钮确认有 `contentDescription`（TalkBack 可读）。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/shutixing/app/ui/navigation/AppNav.kt scripts/screenshots.sh
git commit -m "polish: adapt system bars, large fonts and screenshot QA"
```

---

### Task 27: 签名 Release、端到端验收与交付

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `.gitignore`
- Create: `keystore.properties`（本地，不提交）

- [ ] **Step 1: 配置 Release 签名**

`app/build.gradle.kts` 顶部增加：

```kotlin
import java.util.Properties

val keystoreFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}
```

在 `android { }` 内增加：

```kotlin
signingConfigs {
    if (keystoreFile.exists()) {
        create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
}
```

`buildTypes.release` 内增加：

```kotlin
signingConfig = signingConfigs.findByName("release")
```

`.gitignore` 追加：

```gitignore
keystore.properties
```

- [ ] **Step 2: 生成签名密钥（一次性，保存在项目外）**

```bash
mkdir -p ~/.android-keys
keytool -genkeypair -v \
  -keystore ~/.android-keys/shuatixing.jks \
  -alias shuatixing \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=ShuaTiXing, OU=Personal, O=ShuaTiXing, L=Beijing, ST=Beijing, C=CN"
```

按提示设置密码。然后在项目根目录创建 `keystore.properties`（填入刚设置的密码）：

```properties
storeFile=/home/jh/.android-keys/shuatixing.jks
storePassword=你的密码
keyAlias=shuatixing
keyPassword=你的密码
```

- [ ] **Step 3: 构建并安装 Release APK**

```bash
./gradlew :app:assembleRelease
adb uninstall com.shutixing.app || true
adb install app/build/outputs/apk/release/app-release.apk
```

预期：`BUILD SUCCESSFUL`，设备上覆盖安装成功。

- [ ] **Step 4: 端到端验收清单（逐项打勾）**

准备一份真实文字版 PDF 题库，执行：

1. 首次启动：首页显示 0/30，无题库
2. 导入 PDF：解析进度正常 → 校对页题数、重复数正确 → 编辑一题的答案 → 导入
3. 题库详情：题数正确，搜索、题型筛选、收藏、手动添加、删除题目均生效
4. 设置每日题量为 5
5. 刷每日题：顺序为错题→复习→新题；至少答错一题
6. 答错后：本轮末尾出现该题，答错继续重测，答对后结束；结束页正确率正确
7. 首页：今日完成数、正确率、错题数更新；连续打卡 = 1
8. 错题本：能看到刚错的题，显示下次复习日期为明天
9. 杀进程重进：首页显示“继续本轮”（若未完成）或当天已完成状态
10. 导出题库 → 设置“清空所有数据” → 通过设置页导入该 `.json` → 题库恢复、作答记录为空
11. 统计页：今日做题量柱状图、本月打卡、题库掌握度有数据
12. 切换深色模式，所有页面无白块、无不可读文字
13. 大字体 1.3x 下首页与刷题页不溢出（验收后恢复 1.0）

任何一项失败：记录现象、修复、重新验收该项。

- [ ] **Step 5: 提交并打标签**

```bash
git add app/build.gradle.kts .gitignore
git commit -m "build: sign release APK"
git tag v0.1.0
```

不要提交 `keystore.properties` 与 `.jks` 文件。

---

## 附录：验证命令速查

```bash
# 环境
export JAVA_HOME=$HOME/.local/opt/jdk-17
export ANDROID_HOME=$HOME/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$HOME/.local/opt/gradle-8.9/bin:$PATH

# 单元测试（JVM，快）
./gradlew :app:testDebugUnitTest

# 仪器测试（需设备）
./gradlew :app:connectedDebugAndroidTest

# 构建
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease

# 安装与启动
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.shutixing.app/.MainActivity

# 日志
adb logcat -s AndroidRuntime:E ShuaTiXing:V
```
