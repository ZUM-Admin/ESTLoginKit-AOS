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
import com.estaid.loginkit.EstLoginConfiguration
import com.estaid.loginkit.EstLoginManager
import com.estaid.loginkit.model.KakaoConfiguration
import com.estaid.loginkit.model.NaverConfiguration

/**
 * 최소 통합 데모. 아래 플레이스홀더를 발급받은 실제 값으로 교체하세요.
 */
class ExampleApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    EstLoginManager.initialize(
      context = this,
      config = EstLoginConfiguration.Builder(clientId = "YOUR_CLIENT_ID")
        .useBaseUrl("https://test.estoneid.com")
        // .useKakao(KakaoConfiguration(appKey = "YOUR_KAKAO_NATIVE_APP_KEY"))
        // .useNaver(
        //   NaverConfiguration(
        //     appName = getString(R.string.app_name),
        //     clientId = "YOUR_NAVER_CLIENT_ID",
        //     clientSecret = "YOUR_NAVER_CLIENT_SECRET",
        //   ),
        // )
        .debugMode(true)
        .webViewInspectable(true)
        .build(),
    )
  }
}
