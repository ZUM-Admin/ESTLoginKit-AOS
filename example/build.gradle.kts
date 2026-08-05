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

import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.compose.compiler)
}

// 시크릿은 소스가 아니라 rootProject/local.properties(git 미추적)에서 읽는다.
// (iOS 예제의 Config.local.xcconfig 대칭) 값이 없으면 안전한 기본값으로 폴백한다.
val exampleProps = Properties().apply {
  val file = rootProject.file("local.properties")
  if (file.exists()) file.inputStream().use { load(it) }
}

fun exampleProp(key: String, default: String = ""): String =
  (exampleProps.getProperty(key) ?: default).trim()

// fallback 은 placeholder 다. 실제 값은 git 미추적 local.properties 에만 둔다. (시크릿 커밋 방지)
val estClientId = exampleProp("estloginkit.clientId", "YOUR_CLIENT_ID")
val estEnvironment = exampleProp("estloginkit.environment", "development")
val estApiHost = exampleProp("estloginkit.apiHost", "YOUR_API_HOST")
val estAppCallback = exampleProp("estloginkit.appCallback")
val kakaoAppKey = exampleProp("estloginkit.kakaoAppKey")
val naverClientId = exampleProp("estloginkit.naverClientId")
val naverClientSecret = exampleProp("estloginkit.naverClientSecret")
val naverAppName = exampleProp("estloginkit.naverAppName", "EST")
// applicationId. 카카오/네이버/est 콘솔에 등록된 패키지명과 일치해야 함. 실제 값은 local.properties 에.
val estApplicationId = exampleProp("estloginkit.applicationId", "com.estaid.loginkit.example")

android {
  namespace = "com.estaid.loginkit.example"
  compileSdk = (project.property("compileSdk") as String).toInt()

  defaultConfig {
    applicationId = estApplicationId
    minSdk = (project.property("minSdk") as String).toInt()
    targetSdk = (project.property("targetSdk") as String).toInt()
    versionCode = 1
    versionName = "1.0"

    buildConfigField("String", "EST_CLIENT_ID", "\"$estClientId\"")
    buildConfigField("String", "EST_ENVIRONMENT", "\"$estEnvironment\"")
    buildConfigField("String", "EST_API_HOST", "\"$estApiHost\"")
    buildConfigField("String", "EST_APP_CALLBACK", "\"$estAppCallback\"")
    buildConfigField("String", "KAKAO_APP_KEY", "\"$kakaoAppKey\"")
    buildConfigField("String", "NAVER_CLIENT_ID", "\"$naverClientId\"")
    buildConfigField("String", "NAVER_CLIENT_SECRET", "\"$naverClientSecret\"")
    buildConfigField("String", "NAVER_APP_NAME", "\"$naverAppName\"")

    // 카카오 네이티브 로그인 콜백 스킴. (미사용 시 빈 문자열)
    manifestPlaceholders["kakaoAuthScheme"] = if (kakaoAppKey.isNotBlank()) "kakao$kakaoAppKey" else ""
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
}

dependencies {
  implementation(project(":estloginkit"))

  // gradle.properties 에서 KGP 의 stdlib 자동 추가를 껐으므로 각 모듈이 명시 선언한다.
  implementation(libs.kotlin.stdlib)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)

  implementation(platform(libs.compose.bom))
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.ui.tooling.preview)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
