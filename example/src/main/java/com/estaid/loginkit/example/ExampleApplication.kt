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
package com.estaid.loginkit.example

import android.app.Application
import com.estaid.loginkit.EstEnvironment
import com.estaid.loginkit.EstLoginConfiguration
import com.estaid.loginkit.EstLoginManager
import com.estaid.loginkit.model.KakaoConfiguration
import com.estaid.loginkit.model.NaverConfiguration

/**
 * ESTLoginKit 예제 앱 진입점.
 *
 * 모든 시크릿은 local.properties → BuildConfig 경로로 주입된다. (소스에 하드코딩 없음,
 * iOS 예제의 Config.xcconfig 대칭)
 */
class ExampleApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    val builder = EstLoginConfiguration.Builder(clientId = BuildConfig.EST_CLIENT_ID)
      .useEnvironment(environmentFrom(BuildConfig.EST_ENVIRONMENT))
      .debugMode(true)
      .webViewInspectable(true)

    if (BuildConfig.KAKAO_APP_KEY.isNotBlank()) {
      builder.useKakao(KakaoConfiguration(appKey = BuildConfig.KAKAO_APP_KEY))
    }
    if (BuildConfig.NAVER_CLIENT_ID.isNotBlank()) {
      builder.useNaver(
        NaverConfiguration(
          appName = BuildConfig.NAVER_APP_NAME,
          clientId = BuildConfig.NAVER_CLIENT_ID,
          clientSecret = BuildConfig.NAVER_CLIENT_SECRET,
        ),
      )
    }

    EstLoginManager.initialize(context = this, config = builder.build())
  }

  private fun environmentFrom(value: String): EstEnvironment =
    when (value.lowercase()) {
      "production" -> EstEnvironment.PRODUCTION
      "test" -> EstEnvironment.TEST
      else -> EstEnvironment.DEVELOPMENT
    }
}
