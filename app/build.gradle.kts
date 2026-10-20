plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ربط Firebase عند اللزوم: ضع google-services.json داخل مجلد app
val hasFirebase = file("google-services.json").exists()

// رقم الإصدار يزيد تلقائيًا مع كل بناء على GitHub (GITHUB_RUN_NUMBER) ويكون 1 في البناء المحلي
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
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
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        // دعم العربية والإنجليزية فقط
        resourceConfigurations += listOf("ar", "en")
        // لعرض حالة Firebase داخل الإعدادات
        buildConfigField("boolean", "HAS_FIREBASE", hasFirebase.toString())
    }

    // مفتاح توقيع ثابت: يسمح بتحديث التطبيق فوق النسخة القديمة دون حذفها
    // (بدونه يولّد GitHub مفتاح debug جديدًا في كل بناء فيرفض أندرويد التحديث)
    signingConfigs {
        create("liblink") {
            storeFile = file("liblink.keystore")
            storeType = "pkcs12"
            storePassword = "liblink123"
            keyAlias = "liblink"
            keyPassword = "liblink123"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("liblink") }
        getByName("release") { signingConfig = signingConfigs.getByName("liblink") }
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
    implementation("androidx.work:work-runtime-ktx:2.9.1") // مراقبة البناء وإشعار انتهائه

    // عمليات Git (نفس مكتبة MGit)
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.13.3.202401111512-r")
    implementation("org.slf4j:slf4j-nop:1.7.36")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // إعلانات Start.io — تحقق من أحدث إصدار في لوحة Start.io
    implementation("com.startapp:inapp-sdk:5.0.1")

    // Firebase: المصادقة والمزامنة بين الأجهزة (تعمل عند وجود google-services.json)
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-analytics")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
