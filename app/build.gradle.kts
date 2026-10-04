import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------- التوقيع: متغيرات بيئة (CI) أو ملف keystore.properties محلي (غير مرفوع) ----------
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, key: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(key)
val ksPath = signingValue("KEYSTORE_PATH", "storeFile")
val ksPass = signingValue("KEYSTORE_PASSWORD", "storePassword")
val ksAlias = signingValue("KEY_ALIAS", "keyAlias")
val ksKeyPass = signingValue("KEY_PASSWORD", "keyPassword") ?: ksPass
val hasReleaseSigning = ksPath != null && ksPass != null && ksAlias != null && rootProject.file(ksPath).exists()

// اسم الإصدار بلا حرف v، ورمز الإصدار يُشتق منه (1.6.3 → 10603) ليرتفع دائماً مع الوسم ويصلح للتحديث من داخل التطبيق
val appVersionName = (System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.7.0").removePrefix("v")
val appVersionCode = appVersionName.substringBefore('-').split('.').let { p ->
    (p.getOrNull(0)?.toIntOrNull() ?: 0) * 10000 + (p.getOrNull(1)?.toIntOrNull() ?: 0) * 100 + (p.getOrNull(2)?.toIntOrNull() ?: 0)
}
// مستودع GitHub الذي يُفحص منه التحديث: يُؤخذ تلقائياً من GitHub Actions، أو من gradle.properties (updateRepo)، أو عدّله هنا
val updateRepo = System.getenv("GITHUB_REPOSITORY")?.takeIf { it.contains('/') } ?: (project.findProperty("updateRepo") as String?) ?: "OWNER/NovaBrowser"

android {
    namespace = "com.nova.browser"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nova.browser"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        ndk { abiFilters += listOf("arm64-v8a") }   // يقلّل حجم الـ APK كثيراً (مكتبات FFmpeg)
    }
    signingConfigs {
        if (hasReleaseSigning) create("release") {
            storeFile = rootProject.file(ksPath!!)
            storePassword = ksPass
            keyAlias = ksAlias
            keyPassword = ksKeyPass
        }
    }
    androidResources { localeFilters += listOf("ar", "en") }   // يقلّل حجم الـ APK (نصوص المكتبات بلغتين فقط)
    lint { abortOnError = false; checkReleaseBuilds = false }
    packaging { jniLibs { pickFirsts += "**/libc++_shared.so" } }
    packaging { resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST", "DebugProbesKt.bin", "kotlin-tooling-metadata.json", "META-INF/*.version") } }
    testOptions { unitTests.isReturnDefaultValues = true }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"      // يتعايش مع نسخة الإصدار على نفس الجهاز
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // بلا مفاتيح (مثل طلبات الدمج من forks) يُوقَّع بمفتاح debug كي لا يفشل البناء
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true      // مطلوب لمكتبة NewPipeExtractor
    }
    buildFeatures { compose = true; buildConfig = true }
    dependenciesInfo { includeInApk = false; includeInBundle = false }   // يحذف كتلة بيانات الاعتماديات (حجم أصغر)
}

// بديل kotlinOptions { jvmTarget } المحذوف في Kotlin 2.2+
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.webkit:webkit:1.12.1")             // حقن سكربت الحماية قبل الصفحة
    implementation("androidx.browser:browser:1.8.0")              // Chrome Custom Tabs لصفحات تسجيل الدخول الحساسة
    implementation("androidx.profileinstaller:profileinstaller:1.4.1") // ملفات Baseline لتسريع بدء التشغيل وتقليل التقطيع
    implementation("com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1")   // تحويل الصوت إلى MP3 وغيره (FFmpeg)
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5") // استخراج روابط الفيديو/الصوت من يوتيوب (مجاني ومفتوح المصدر)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation("junit:junit:4.13.2")
}
