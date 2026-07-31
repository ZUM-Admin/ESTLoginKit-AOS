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

import android.util.Log

/**
 * ESTLoginKit 공통 로거.
 *
 * SDK 의 모든 로그는 이걸로 찍는다(`android.util.Log` 직접 사용 금지).
 * 모든 라인은 `ESTLoginKit` 태그로 묶인다.
 *
 * 규칙
 * - 레벨: debug(상세 흐름/통신) · info(주요 이벤트) · error(실패/에러).
 * - debug 는 [debugEnabled] (호스트의 `debugMode`) 일 때만 출력된다 — 운영 빌드 소음 차단.
 * - 민감정보(accessToken/refreshToken/비밀번호 등)는 로그에 넣지 않는다.
 * - 메시지는 `동작: 값` 형태로 간결하게.
 */
internal object EstLog {
  private const val TAG = "ESTLoginKit"

  /** 호스트가 `debugMode(true)` 로 초기화하면 켜진다. debug 레벨 로그 게이트. */
  @Volatile
  var debugEnabled: Boolean = false

  /** 상세 흐름/통신. `debugMode` 일 때만 출력된다. */
  fun debug(message: String) {
    if (debugEnabled) Log.d(TAG, message)
  }

  /** 주요 이벤트(로그인 성공 등). */
  fun info(message: String) {
    Log.i(TAG, message)
  }

  /** 실패/에러. */
  fun error(message: String, throwable: Throwable? = null) {
    if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
  }
}
