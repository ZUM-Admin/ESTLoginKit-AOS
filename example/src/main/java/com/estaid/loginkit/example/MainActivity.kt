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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.estaid.loginkit.EstLoginManager
import com.estaid.loginkit.model.AuthResult
import com.estaid.loginkit.model.LoginPlatform
import com.estaid.loginkit.webview.EstIdentityVerificationWebViewWithAccessToken
import com.estaid.loginkit.webview.EstLoginWebView
import com.estaid.loginkit.webview.EstMyPageWebView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/** 전체 화면 상태. (iOS ContentView 의 sheet 전환 대칭) */
private enum class Screen { MAIN, WEB_LOGIN, MYPAGE, VERIFICATION }

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          ExampleApp(activity = this)
        }
      }
    }
  }
}

@Composable
private fun ExampleApp(activity: ComponentActivity) {
  val scope = rememberCoroutineScope()
  val tokenStore = remember { TokenStore(activity) }

  var screen by remember { mutableStateOf(Screen.MAIN) }
  var status by remember { mutableStateOf("대기 중") }
  var authResult by remember { mutableStateOf<AuthResult?>(null) }
  var estoneToken by remember { mutableStateOf<EstoneToken?>(null) }
  // 웹뷰 부트스트랩용 accessToken — 뷰에 넘기면 SDK 가 ssoToken 발급→세션 수립까지 처리
  var webAccessToken by remember { mutableStateOf<String?>(null) }

  // 앱 재실행 시 저장된 토큰 복원
  LaunchedEffect(Unit) {
    if (estoneToken == null) {
      tokenStore.load()?.let {
        estoneToken = it
        status = "저장된 토큰 복원됨"
      }
    }
  }

  // 앱이 세션 SSoT — SDK 는 유효한 accessToken 을 받는다고 가정하므로,
  // 만료 판단·갱신은 호출 전에 앱이 처리한다. (iOS validAccessToken 대칭)
  suspend fun validAccessToken(): String? {
    val stored = tokenStore.load() ?: return null
    if (stored.expiryDate.time > System.currentTimeMillis()) return stored.accessToken
    return runCatching { EstoneAuth.renewToken(stored) }
      .onSuccess { renewed ->
        tokenStore.save(renewed)
        estoneToken = renewed
      }
      .getOrNull()
      ?.accessToken
  }

  when (screen) {
    Screen.WEB_LOGIN -> EstLoginWebView(
      onBackPressed = { screen = Screen.MAIN },
      onLoginCompleted = { ssoToken ->
        screen = Screen.MAIN
        if (ssoToken == null) {
          status = "웹 로그인 종료 (토큰 없음)"
        } else {
          status = "EST 토큰 발급 중…"
          scope.launch {
            status = try {
              val token = EstoneAuth.issueToken(ssoToken)
              tokenStore.save(token)
              estoneToken = token
              "EST 토큰 발급 완료 (저장됨)"
            } catch (e: Exception) {
              "EST 토큰 발급 실패: ${e.message}"
            }
          }
        }
      },
    )

    Screen.MYPAGE -> {
      val accessToken = webAccessToken
      if (accessToken == null) {
        screen = Screen.MAIN
      } else {
        EstMyPageWebView(
          accessToken = accessToken,
          onBackPressed = { screen = Screen.MAIN },
          onError = { error ->
            status = "마이페이지 SSO 발급 실패: ${error.message}"
            screen = Screen.MAIN
          },
        )
      }
    }

    Screen.VERIFICATION -> {
      val accessToken = webAccessToken
      if (accessToken == null) {
        screen = Screen.MAIN
      } else {
        EstIdentityVerificationWebViewWithAccessToken(
          accessToken = accessToken,
          onBackPressed = { screen = Screen.MAIN },
          onResult = { result ->
            status = result.fold(
              onSuccess = { "본인인증 성공: token=${it.token.take(16)}…" },
              onFailure = { "본인인증 실패: ${it.message}" },
            )
            screen = Screen.MAIN
          },
        )
      }
    }

    Screen.MAIN -> MainScreen(
      status = status,
      authResult = authResult,
      estoneToken = estoneToken,
      onKakaoLogin = {
        status = "카카오 로그인 중…"
        scope.launch {
          status = try {
            authResult = EstLoginManager.login(activity, LoginPlatform.KAKAO)
            "카카오 로그인 성공"
          } catch (e: Exception) {
            "카카오 로그인 실패: ${e.message}"
          }
        }
      },
      onNaverLogin = {
        status = "네이버 로그인 중…"
        scope.launch {
          status = try {
            authResult = EstLoginManager.login(activity, LoginPlatform.NAVER)
            "네이버 로그인 성공"
          } catch (e: Exception) {
            "네이버 로그인 실패: ${e.message}"
          }
        }
      },
      onLogout = {
        scope.launch {
          EstLoginManager.logout()
          tokenStore.clear()
          authResult = null
          estoneToken = null
          status = "로그아웃 완료"
        }
      },
      onWebLogin = { screen = Screen.WEB_LOGIN },
      onMyPage = {
        scope.launch {
          val token = validAccessToken()
          if (token == null) {
            status = "유효한 accessToken 없음 — 로그인 필요"
          } else {
            webAccessToken = token
            screen = Screen.MYPAGE
          }
        }
      },
      onVerification = {
        scope.launch {
          val token = validAccessToken()
          if (token == null) {
            status = "유효한 accessToken 없음 — 로그인 필요"
          } else {
            webAccessToken = token
            screen = Screen.VERIFICATION
          }
        }
      },
      onCheckVerificationStatus = {
        status = "본인인증 여부 조회 중…"
        scope.launch {
          val token = validAccessToken()
          status = if (token == null) {
            "유효한 accessToken 없음 — 로그인 필요"
          } else {
            try {
              val result = EstLoginManager.verificationStatus(token)
              "본인인증 여부: ${if (result.isVerified) "인증됨" else "미인증"}"
            } catch (e: Exception) {
              "본인인증 여부 조회 실패: ${e.message}"
            }
          }
        }
      },
      onRenewToken = {
        val current = estoneToken ?: return@MainScreen
        status = "토큰 갱신 중…"
        scope.launch {
          status = try {
            val renewed = EstoneAuth.renewToken(current)
            tokenStore.save(renewed)
            estoneToken = renewed
            "토큰 갱신 완료"
          } catch (e: Exception) {
            "토큰 갱신 실패: ${e.message}"
          }
        }
      },
      onClearToken = {
        tokenStore.clear()
        estoneToken = null
        status = "저장된 토큰 삭제됨"
      },
    )
  }
}

@Composable
private fun MainScreen(
  status: String,
  authResult: AuthResult?,
  estoneToken: EstoneToken?,
  onKakaoLogin: () -> Unit,
  onNaverLogin: () -> Unit,
  onLogout: () -> Unit,
  onWebLogin: () -> Unit,
  onMyPage: () -> Unit,
  onVerification: () -> Unit,
  onCheckVerificationStatus: () -> Unit,
  onRenewToken: () -> Unit,
  onClearToken: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(text = "ESTLoginKit 테스트", style = MaterialTheme.typography.titleLarge)

    SectionTitle("네이티브 로그인")
    Button(onClick = onKakaoLogin, modifier = Modifier.fillMaxWidth()) { Text("카카오 로그인") }
    Button(onClick = onNaverLogin, modifier = Modifier.fillMaxWidth()) { Text("네이버 로그인") }
    OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("로그아웃") }

    SectionTitle("웹뷰")
    Button(onClick = onWebLogin, modifier = Modifier.fillMaxWidth()) { Text("웹 로그인") }
    Button(onClick = onMyPage, modifier = Modifier.fillMaxWidth()) { Text("마이페이지") }
    Button(onClick = onVerification, modifier = Modifier.fillMaxWidth()) { Text("본인인증") }
    OutlinedButton(onClick = onCheckVerificationStatus, modifier = Modifier.fillMaxWidth()) {
      Text("본인인증 여부 조회")
    }

    SectionTitle("상태")
    Text(text = status, style = MaterialTheme.typography.bodyMedium)

    if (authResult != null) {
      SectionTitle("AuthResult (네이티브)")
      LabeledValue("authorizeToken", authResult.authorizeToken)
      LabeledValue("refreshToken", authResult.refreshToken)
      LabeledValue("ci", authResult.ci)
      LabeledValue("email", authResult.email)
    }

    if (estoneToken != null) {
      SectionTitle("EST 토큰")
      LabeledValue("accessToken", estoneToken.accessToken)
      LabeledValue("refreshToken", estoneToken.refreshToken)
      LabeledValue("만료 시각", formatDate(estoneToken.expiresIn))
      Button(onClick = onRenewToken, modifier = Modifier.fillMaxWidth()) {
        Text("토큰 갱신 (refresh-sso)")
      }
      OutlinedButton(onClick = onClearToken, modifier = Modifier.fillMaxWidth()) {
        Text("저장된 토큰 삭제")
      }
    }
  }
}

@Composable
private fun SectionTitle(title: String) {
  Divider(modifier = Modifier.padding(top = 8.dp))
  Text(
    text = title,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
  )
}

@Composable
private fun LabeledValue(label: String, value: String) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(text = label, style = MaterialTheme.typography.labelSmall)
    Text(
      text = value.ifEmpty { "(빈 값)" },
      style = MaterialTheme.typography.bodySmall,
      maxLines = 3,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

private fun formatDate(msEpoch: Long): String {
  if (msEpoch <= 0L) return "(없음)"
  return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(msEpoch)
}
