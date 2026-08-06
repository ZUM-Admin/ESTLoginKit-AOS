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
    // 카카오 리다이렉트 스킴(kakao{nativeAppKey})은 **소비 앱이 자체 값으로 주입**한다.
    // 라이브러리에서 placeholder 를 정의하면 그 값이 AAR 매니페스트에 baked 되어(예: kakao-placeholder)
    // 소비 앱의 manifestPlaceholders 로 override 되지 않아 카카오 리다이렉트가 깨진다. 그래서 여기선
    // 정의하지 않고 `${kakaoAuthScheme}` 를 미해결 상태로 소비 앱에 넘긴다. (:example 은 자체 build.gradle
    // 에서 kakaoAuthScheme 를 제공)
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
    // 컴파일러는 2.2.10 을 그대로 쓰되, **언어/메타데이터 레벨만 2.0 으로 낮춘다.**
    //
    // Kotlin 컴파일러는 자기 버전 +1 마이너까지의 메타데이터만 읽는다. 2.2.10 이 그대로 뱉는
    // mv=[2,2,0] 은 Kotlin 2.0.x 소비 앱에서 "incompatible version of Kotlin" 으로 거부된다.
    // languageVersion 을 2.0 으로 내리면 mv=[2,0,0] 으로 emit 되어 2.0.x~2.2.x 가 모두 읽는다.
    // (컴파일러 자체를 내리면 compose compiler·serialization 플러그인까지 동반 다운그레이드라
    //  리스크가 훨씬 크다. 소비처 하한을 올리기 전까지 이 설정을 유지할 것.)
    languageVersion = "2.0"
    apiVersion = "2.0"
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
  // stdlib 을 명시적으로 선언한다. KGP 기본 동작(자동 추가)에 맡기면 컴파일러 버전과 같은
  // 2.2.10 이 **api variant** 로 발행되어, Kotlin 2.0.x 소비 앱의 컴파일 클래스패스에
  // mv=[2,2,0] stdlib 이 올라가 빌드가 깨진다. (자동 추가는 gradle.properties 에서 끈다)
  // Gradle 은 높은 쪽을 고르므로 Kotlin 2.1/2.2 소비 앱은 계속 자기 stdlib 을 쓴다.
  api(libs.kotlin.stdlib)

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
        version = "2.1.2"
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
