package com.liblink.app.storage

// قوالب ملفات أساسية يمكن للتطبيق إنشاؤها عند فقدانها
object Templates {
    val WORKFLOW = """name: Build APK
on:
  push:
    branches: [ main ]
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: 8.7
      - name: Build debug APK
        run: gradle :app:assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/*.apk
"""

    val GITIGNORE = """*.iml
.gradle/
build/
local.properties
.idea/
captures/
.externalNativeBuild/
.cxx/
*.apk
"""

    val GRADLE_PROPERTIES = """org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
"""

    fun settings(projectName: String): String {
        val n = projectName.replace("\"", "")
        return """pluginManagement {
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
rootProject.name = "$n"
include(":app")
"""
    }

    // مفتاح القالب ← محتوى الملف
    fun content(key: String, projectName: String): String? = when (key) {
        "gitignore" -> GITIGNORE
        "gradleprops" -> GRADLE_PROPERTIES
        "settings" -> settings(projectName)
        "workflow" -> WORKFLOW
        else -> null
    }
}
