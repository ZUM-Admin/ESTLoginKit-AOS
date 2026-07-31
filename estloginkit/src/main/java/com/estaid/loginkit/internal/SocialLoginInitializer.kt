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
package com.estaid.loginkit.internal

import android.content.Context
import com.estaid.loginkit.EstLoginConfiguration
import com.kakao.sdk.common.KakaoSdk
import com.navercorp.nid.NidOAuth
import com.navercorp.nid.core.data.datastore.NidOAuthInitializingCallback

/**
 * 카카오/네이버 SDK 1회 초기화.
 */
internal class SocialLoginInitializer(
  private val context: Context,
  private val config: EstLoginConfiguration,
) {
  @Volatile
  private var initialized = false

  fun ensureInitialized() {
    if (initialized) return
    synchronized(this) {
      if (initialized) return

      config.kakaoConfig?.let { kakao ->
        KakaoSdk.init(
          context = context,
          appKey = kakao.appKey,
          customScheme = kakao.customScheme?.takeIf { it.isNotBlank() },
        )
      }

      config.naverConfig?.let { naver ->
        NidOAuth.initialize(
          context = context,
          clientId = naver.clientId,
          clientSecret = naver.clientSecret,
          clientName = naver.appName,
          callback = object : NidOAuthInitializingCallback {
            override fun onSuccess() = Unit
            override fun onFailure(exception: Exception) {
              EstLog.error("Failed to initialize Naver OAuth", exception)
            }
          },
        )
        NidOAuth.setLogEnabled(config.debugMode)
      }

      initialized = true
    }
  }
}
