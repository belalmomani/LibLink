plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.quranichorizons"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.quranichorizons"
        minSdk = 26
        targetSdk = 35
        // رقم الإصدار يزداد مع كل بناء على GitHub (run_number) ليُقبل كتحديث فوق النسخة المثبّتة
        versionCode = System.getenv("QH_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = "0.1.0"

        // التلاوة معطّلة حتى يُوثَّق ترخيص التسجيلات (انظر docs/EXTERNAL_DEPENDENCIES.md)
        buildConfigField("boolean", "AUDIO_ENABLED", "false")
        // خدمة وسيط التلاوة (Quran.Foundation) ورقم التلاوة (ayah-by-ayah) بعد التحقق من القارئ المعتمد؛ فارغان حتى الاعتماد
        buildConfigField("String", "AUDIO_API_BASE_URL", "\"\"")
        buildConfigField("int", "RECITATION_ID", "0")
        // الإعلانات (Start.io): المعرّف ليس سرًا، لكن الحزمة غير مدمجة والإعلانات معطّلة حتى تُراجَع قواعد العرض
        // (لا إعلان فوق نص القرآن ولا أثناء التلاوة ولا يعطّل التشغيل). انظر docs/EXTERNAL_DEPENDENCIES.md
        buildConfigField("String", "START_IO_APP_ID", "\"209749236\"")
        buildConfigField("boolean", "ADS_ENABLED", "false")
        // عنوان مستودع المحتوى البعيد (Supabase Storage). فارغ = المحتوى المضمَّن فقط
        buildConfigField("String", "CONTENT_BASE_URL", "\"\"")
    }

    // توقيع ثابت: نفس المفتاح في كل بناء (من أسرار GitHub) كي يعمل التحديث دون حذف التطبيق.
    // المفتاح ليس في المستودع أبدًا.
    val qhKeystore = System.getenv("QH_KEYSTORE_FILE")
    signingConfigs {
        if (!qhKeystore.isNullOrBlank()) {
            create("qh") {
                storeFile = file(qhKeystore)
                storePassword = System.getenv("QH_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("QH_KEY_ALIAS")
                keyPassword = System.getenv("QH_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // نسخة الاختبار فقط: بثّ التلاوة من الخدمة الوسيطة (بيئة prelive، قارئ 7 للتجربة التقنية). الإصدار الرسمي يبقى معطّلًا.
            buildConfigField("boolean", "AUDIO_ENABLED", "true")
            buildConfigField("String", "AUDIO_API_BASE_URL", "\"https://dreams.xo.je/audio-proxy\"")
            buildConfigField("int", "RECITATION_ID", "7")
            if (!qhKeystore.isNullOrBlank()) signingConfig = signingConfigs.getByName("qh")
        }
        release {
            isMinifyEnabled = true
            if (!qhKeystore.isNullOrBlank()) signingConfig = signingConfigs.getByName("qh")
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // المحتوى المضمَّن (محتوى النموذج الأول) مصدره content/bootstrap في جذر المستودع
    sourceSets["main"].assets.srcDirs("src/main/assets", "../content/bootstrap")

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
<<<<<<< HEAD
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
=======
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
>>>>>>> branch 'main' of https://github.com/belalmomani/LibLink.git
}
