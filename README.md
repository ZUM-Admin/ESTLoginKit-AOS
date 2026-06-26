# ESTLoginKit (Android)

소셜 로그인(카카오·네이버) · WebView 로그인(구글·애플 포함) · 마이페이지 · **본인인증**을
간편하게 통합하는 Android 라이브러리입니다. iOS [ESTLoginKit](https://github.com/ZUM-Internet/ESTLoginKit-iOS) 의 Android 포팅판으로,
API·동작·문서를 iOS 와 정합시키는 것을 목표로 합니다.

> ## ⚠️ 토큰 관리는 이 SDK 에서 하지 않습니다 (stateless)
> 다음은 전부 **호스트 앱의 책임**입니다:
> - `ssoToken` → `accessToken`/`refreshToken` **교환**
> - 토큰 **저장 · 갱신 · 만료 처리**
> - 로그아웃 시 **앱이 저장한 토큰 삭제**
>
> SDK 는 토큰을 보관하지 않으며, 토큰이 필요한 API(본인인증 조회)에는 **호출 시 호스트가 토큰을 주입**합니다.

## 한눈에 보기

| ✅ SDK 가 제공 | 🙅 호스트 책임 |
|---|---|
| 카카오·네이버 **네이티브 로그인** | ssoToken → accessToken/refreshToken **교환** |
| **WebView 로그인 화면** (구글·애플 포함) + ssoToken 회수 | 토큰 **저장·갱신·만료** 처리 |
| **로그인 URL 빌더** (`loginUrl`, `silent`) | 본인인증을 **언제 띄울지(정책)** |
| **마이페이지 화면** + 계정 이벤트 통지 콜백 | 화면 표시/종료 |
| **로그아웃** (네이티브 토큰 + WebView 세션 데이터 정리) | 로그인 결과 후속 처리(서버 통신 등) |
| **본인인증 여부 조회 API** (스펙 일부 미정) | |

## 요구사항

- minSdk 28 / compileSdk · targetSdk 35
- Kotlin 2.2.10+, JVM 21
- Jetpack Compose (SDK 내부 UI 사용)

## 모듈 구성

- `:estloginkit` — 라이브러리 (artifact `com.estaid:loginkit`)
- `:example` — 최소 통합 데모 앱

## 빠른 시작

### 1. 초기화 (`Application.onCreate`)

```kotlin
EstLoginManager.initialize(
  context = this,
  config = EstLoginConfiguration.Builder(clientId = "YOUR_CLIENT_ID")
    .useBaseUrl("https://estoneid.com")           // 기본값 estoneid.com (개발: test.estoneid.com)
    .useKakao(KakaoConfiguration(appKey = "..."))
    .useNaver(NaverConfiguration(appName = "앱이름", clientId = "...", clientSecret = "..."))
    .build(),
)
```

> 인증 서버(AUTH_API)는 `baseUrl` 에 대응해 자동 결정됩니다. (운영 `api.estoneid.com/auth`, 개발 `dev-api.estoneid.com/auth`)

### 2. 네이티브 로그인 (카카오 / 네이버)

```kotlin
val result = EstLoginManager.login(activity, LoginPlatform.KAKAO)  // AuthResult
// 실패 시 AuthError(Cancelled / Network / Unknown ...) throw
```

### 3. WebView 로그인 (ssoToken 회수)

```kotlin
// Activity 기반 (권장)
val sso = EstLoginManager.startWebLogin(activity).getOrNull()

// 또는 Compose 임베드
EstLoginWebView(onLoginCompleted = { ssoToken -> /* 토큰 교환은 호스트 */ })
```

### 4. 마이페이지 / 로그아웃

```kotlin
EstMyPageWebView(
  onPasswordChanged = { /* silent 재발급 */ },
  onAccountDeleted = { /* 로그아웃 처리 */ },
)

EstLoginManager.logout()  // 네이티브 토큰 + WebView 세션 데이터 정리 (앱 저장 토큰은 호스트가 삭제)
```

### 5. 본인인증 여부 조회

```kotlin
val status = EstLoginManager.verificationStatus(accessToken = myAccessToken)  // VerificationStatus
if (!status.isVerified) { /* 본인인증 화면 진입 — 정책 판단은 호스트 */ }
```

> 본인인증 **화면 / 엔드포인트 경로 / 응답 JSON 은 백엔드 스펙 (미정)** 입니다. `verificationUrl()`,
> `verificationStatus()` 의 경로·응답 매핑은 스펙 확정 시 조정됩니다.

## iOS 와의 매핑

| iOS | Android |
|---|---|
| `ESTLoginManager.shared` | `EstLoginManager` (object) |
| `ESTLoginConfiguration.Builder` | `EstLoginConfiguration.Builder` |
| `login(with:)` | `login(activity, platform)` |
| `loginURL(redirectURL:state:silent:)` | `loginUrl(redirectUrl, state, silent)` |
| `LoginWebView` / `MyPageWebView` | `EstLoginWebView` / `EstMyPageWebView` |
| `verificationStatus(accessToken:)` | `verificationStatus(accessToken)` |
| `logout()` | `logout()` |

연동 키 설정·매니페스트·JS 브릿지 등 상세는 [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) 참고.
