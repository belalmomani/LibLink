plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ربط Firebase عند اللزوم: ضع google-services.json داخل مجلد app
val hasFirebase = file("google-services.json").exists()
if (hasFirebase) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.liblink.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.liblink.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // دعم العربية والإنجليزية فقط
        resourceConfigurations += listOf("ar", "en")
        // لعرض حالة Firebase داخل الإعدادات
        buildConfigField("boolean", "HAS_FIREBASE", hasFirebase.toString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // مطلوب لـ JGit
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources {
            // استبعاد ملفات متعارضة من JGit
            excludes += listOf(
                "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA",
                "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST", "META-INF/LICENSE*"
            )
        }
    }
}

dependencies {
    // واجهة التطبيق
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0") // تبديل اللغة عربي/إنجليزي
    implementation("androidx.documentfile:documentfile:1.0.1") // اختيار مكان الحفظ
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // عمليات Git (نفس مكتبة MGit)
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.13.3.202401111512-r")
    implementation("org.slf4j:slf4j-nop:1.7.36")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // إعلانات Start.io — تحقق من أحدث إصدار في لوحة Start.io
    implementation("com.startapp:inapp-sdk:5.0.1")

    // Firebase اختياري
    if (hasFirebase) {
        implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
        implementation("com.google.firebase:firebase-analytics")
    }
}
