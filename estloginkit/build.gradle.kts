/*
 * Copyright 2026 ESTAID, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.compose.compiler)
  id("maven-publish")
}

android {
  namespace = "com.estaid.loginkit"
  compileSdk = (project.property("compileSdk") as String).toInt()

  defaultConfig {
    minSdk = (project.property("minSdk") as String).toInt()
    consumerProguardFiles("consumer-rules.pro")
    // 소비 앱이 자체 카카오 네이티브 앱 키로 override 한다. (가이드 §매니페스트)
    // 라이브러리 단독 빌드 시 매니페스트 머지가 깨지지 않도록 placeholder 기본값을 둔다.
    manifestPlaceholders["kakaoAuthScheme"] = "kakao-placeholder"
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  buildTypes {
    release {
      isMinifyEnabled = false
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_21.toString()
  }

  publishing {
    singleVariant("release") {
      withSourcesJar()
    }
  }

  testOptions {
    unitTests {
      // 순수 JVM 유닛테스트에서 android.util.Log 호출이 예외 대신 기본값을 반환하게 한다.
      isReturnDefaultValues = true
    }
  }
}

dependencies {
  // Kotlin
  implementation(libs.coroutines.core)
  implementation(libs.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  // AndroidX
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.viewModelCompose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.process)

  // Compose
  implementation(platform(libs.compose.bom))
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.runtime)

  // Network — verificationStatus 조회용 (Bearer 토큰은 호출 시 호스트가 주입)
  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.scalars)
  implementation(libs.okhttp.logging.interceptor)
  implementation(libs.retrofit.kotlin.serialization)

  // Social Login SDKs
  implementation(libs.kakao.user)
  implementation(libs.naver.id.login)

  // Logger
  implementation(libs.orhanobut.logger)

  // Test
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlin.test.junit)
}

afterEvaluate {
  publishing {
    publications {
      create<MavenPublication>("release") {
        from(components["release"])
        groupId = "com.estaid"
        artifactId = "loginkit"
        version = "2.0.0"
      }
    }
    repositories {
      maven {
        url = uri("https://maven.zuminternet.com/artifactory/libs-release-local")
        isAllowInsecureProtocol = true
      }
    }
  }
}
