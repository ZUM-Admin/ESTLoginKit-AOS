# ESTLoginKit (Android)

소셜 로그인(카카오·네이버) · WebView 로그인(구글·애플 포함) · 마이페이지 · **본인인증**을
간편하게 통합하는 Android 라이브러리입니다.

> ## ⚠️ 토큰 관리는 이 SDK 에서 하지 않습니다 (stateless)
> 다음은 전부 **호스트 앱의 책임**입니다:
> - `ssoToken` → `accessToken`/`refreshToken` **교환**
> - 토큰 **저장 · 갱신 · 만료 처리**
> - 로그아웃 시 **앱이 저장한 토큰 삭제**
>
> SDK 는 토큰을 보관하지 않으며, 토큰이 필요한 API(본인인증 조회, SSO 부트스트랩)에는 **호출 시 호스트가 토큰을 주입**합니다.
>
> **토큰 저장은 평문 SharedPreferences 대신 Android Keystore 기반 암호화 저장을 권장**합니다
> (Keystore 로 보호한 키로 암호화해 DataStore/파일에 저장). Jetpack Security 의
> `EncryptedSharedPreferences` 는 deprecated 라 신규 적용에는 권장하지 않습니다.
> (iOS 대응: Keychain)

## 한눈에 보기

| ✅ SDK 가 제공 | 🙅 호스트 책임 |
|---|---|
| 카카오·네이버 **네이티브 로그인** | ssoToken → accessToken/refreshToken **교환** |
| **WebView 로그인 화면** (구글·애플 포함) + ssoToken 회수 | 토큰 **저장·갱신·만료** 처리 |
| **로그인 URL 빌더** (`loginUrl`, `silent`) | 본인인증을 **언제 띄울지(정책)** |
| **마이페이지 화면** + 계정 이벤트 통지 콜백 | 화면 표시/종료(dismiss) |
| **로그아웃** (네이티브 SDK 토큰 정리) | 로그인 결과 후속 처리(서버 통신 등) |
| **본인인증 화면** + **인증 여부 조회 API** | |

## 요구사항

- minSdk 28 / compileSdk · targetSdk 35
- Kotlin 2.2.10+, JVM 21
- Jetpack Compose (SDK 내부 UI 사용)

## 모듈 구성

- `:estloginkit` — 라이브러리 (artifact `com.estaid:loginkit`)
- `:example` — 통합 예제 앱. 네이티브 로그인·웹로그인·마이페이지·본인인증·토큰 교환/저장을 모두 다룹니다. (실행법은 아래 [예제 앱](#예제-앱) 참고)

설치(의존성 추가)·매니페스트·배포 등 연동 상세는 [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) 를 참고하세요.

## 예제 앱

`:example` 은 SDK를 실제 앱에 통합하는 방법을 end-to-end로 보여줍니다. iOS 예제와 동일한 흐름입니다.

**시크릿은 소스에 없습니다.** 모든 설정값은 `local.properties`(git 미추적) → `BuildConfig` → 런타임으로
주입됩니다. Android Studio에서 프로젝트를 열고 아래 값만 채운 뒤 `:example` 을 실행하세요.

```properties
# local.properties (git 미추적 — 실제 값은 여기에만)
estloginkit.clientId=YOUR_CLIENT_ID
estloginkit.environment=development          # production | development | test
estloginkit.apiHost=dev-api.estoneid.com     # 토큰 교환 백엔드 host (scheme 제외)
estloginkit.applicationId=com.your.app.debug # 카카오/네이버/est 콘솔 등록 패키지명과 일치
estloginkit.appCallback=                      # 비우면 SDK 기본 콜백 사용
# 네이티브 로그인 테스트 시에만 필요
estloginkit.kakaoAppKey=
estloginkit.naverClientId=
estloginkit.naverClientSecret=
estloginkit.naverAppName=EST
```

| 항목 | 설명 |
|------|------|
| `estloginkit.clientId` | ESTLoginKit 발급 클라이언트 ID (SDK 설정 + 토큰 교환 공통) |
| `estloginkit.environment` | 실행 환경 — 웹/API host가 쌍으로 결정됨 |
| `estloginkit.apiHost` | 예제의 호스트측 토큰 교환(`EstoneAuth`)이 호출할 백엔드 host |
| `estloginkit.applicationId` | 앱 `applicationId`. 콘솔 등록 패키지명과 일치해야 함 |
| `estloginkit.appCallback` | 로그인 콜백 URL. 비우면 `{webBaseUrl}/auth/app-callback` |
| `estloginkit.kakao*` / `estloginkit.naver*` | 네이티브 로그인용. 웹로그인·마이페이지·본인인증만 볼 거면 생략 가능 |

> `local.properties` 가 비어 있으면 `example/build.gradle.kts` 의 placeholder 폴백이 쓰여 빌드는 되지만
> 로그인은 동작하지 않습니다. 값 채우는 곳은 이 파일 한 곳뿐이고, 실제 값은 커밋되지 않습니다.
>
> 웹로그인 → 토큰 교환/저장 → 마이페이지 → 본인인증 순으로 확인하면 SSO 부트스트랩(`/auth/sso-login`)과
> 본인인증(`/auth/verification`) 경로까지 전부 exercise 됩니다.

## 외부에서 주입해야 할 값

통합 시 호스트 앱이 직접 주입해야 하는 값입니다. 유출되면 안 되는 항목(앱 키·시크릿)은
`local.properties`, `BuildConfig`, 빌드 환경 변수 등으로 분리해 소스/VCS 에 노출되지 않게
관리하는 것을 권장합니다.

### 필수

| 항목 | 주입 위치 | 설명 |
|------|----------|------|
| `clientId` | `EstLoginConfiguration.Builder(clientId = ...)` | ESTLoginKit 에서 발급받은 클라이언트 ID |

### 플랫폼 (사용하는 플랫폼만)

| 플랫폼 | 항목 | 주입 위치 |
|-------|------|----------|
| 카카오 | `appKey` (네이티브 앱 키) | `KakaoConfiguration(appKey = ...)` → `.useKakao(...)` |
| 카카오 | URL Scheme `kakao{APP_KEY}` | `manifestPlaceholders["kakaoAuthScheme"]` (아래 빠른 시작 §2 참고) |
| 카카오 | `customScheme` (선택) | `KakaoConfiguration(appKey, customScheme = ...)` — 개발/운영 앱 분리 시 |
| 네이버 | `appName` | `NaverConfiguration(appName = ...)` → `.useNaver(...)` |
| 네이버 | `clientId` | `NaverConfiguration(clientId = ...)` |
| 네이버 | `clientSecret` | `NaverConfiguration(clientSecret = ...)` |

### 선택 (미지정 시 기본값 사용)

| 항목 | 주입 위치 | 기본값 / 설명 |
|------|----------|-------------|
| `environment` | `Builder.useEnvironment(...)` | `EstEnvironment.PRODUCTION`(기본) / `DEVELOPMENT` / `TEST`. 웹·API host 가 환경별로 함께 결정됨 |
| `callbackUrl` | `Builder.useCallbackUrl(...)` | `{baseUrl}/auth/app-callback`. WebView 로그인 완료 감지(prefix + `code` 쿼리) |
| `extraUserAgent` | `Builder.useExtraUserAgent(...)` | null. SDK WebView UA 뒤에 append |
| `webViewInspectable` | `Builder.webViewInspectable(...)` | false. Chrome DevTools inspect |
| `debugMode` | `Builder.debugMode(...)` | false. SSL 우회 + 로깅 (디버그 빌드 전용) |

## 빠른 시작

### 1. 초기화 (`Application.onCreate`)

```kotlin
class MyApp : Application() {
  override fun onCreate() {
    super.onCreate()
    EstLoginManager.initialize(
      context = this,
      config = EstLoginConfiguration.Builder(clientId = "YOUR_CLIENT_ID")
        .useEnvironment(EstEnvironment.PRODUCTION)     // 기본값: PRODUCTION (개발: DEVELOPMENT)
        .useKakao(KakaoConfiguration(appKey = "YOUR_KAKAO_NATIVE_APP_KEY"))
        .useNaver(
          NaverConfiguration(
            appName = getString(R.string.app_name),
            clientId = "YOUR_NAVER_CLIENT_ID",
            clientSecret = "YOUR_NAVER_CLIENT_SECRET",
          ),
        )
        .webViewInspectable(BuildConfig.DEBUG)
        .debugMode(BuildConfig.DEBUG)
        .build(),
    )
  }
}
```

> 웹 host 와 API host 는 `environment` 가 **쌍으로 소유**해 웹/API 불일치를 원천 차단합니다.
> - `PRODUCTION` → 웹 `estoneid.com` · API `api.estoneid.com`
> - `DEVELOPMENT` → 웹 `dev.estoneid.com` · API `dev-api.estoneid.com`
> - `TEST` → 웹 `test.estoneid.com` · API `test-api.estoneid.com`

### 2. 카카오 매니페스트 설정 (카카오 로그인 사용 시)

SDK 가 카카오 로그인용 `AuthCodeHandlerActivity` 와 `<queries>` 를 **자동 등록**합니다.
호스트는 카카오 scheme placeholder 만 설정하면 됩니다.

```kotlin
// app/build.gradle.kts
android {
  buildTypes {
    debug   { manifestPlaceholders["kakaoAuthScheme"] = "kakao{네이티브_앱_키}" }
    release { manifestPlaceholders["kakaoAuthScheme"] = "kakao{네이티브_앱_키}" }
  }
}
```

> 카카오 미사용 시 `manifestPlaceholders["kakaoAuthScheme"] = ""` 로 비워 둡니다.
> `AuthCodeHandlerActivity` 나 `<queries><package android:name="com.kakao.talk" /></queries>` 를
> 호스트 매니페스트에 **직접 선언하지 마세요** — SDK 가 제공합니다. (상세: [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md))

#### 개발/운영 앱 스킴 분리 (선택)

개발 앱과 운영 앱이 **동일한 카카오 앱 키**를 사용하면 URL Scheme(`kakao{APP_KEY}`)이 같아져,
카카오 인증 후 콜백이 의도치 않게 다른 앱으로 열릴 수 있습니다. 이를 막으려면 `customScheme` 으로
개발 앱 전용 스킴을 지정합니다.

```kotlin
// 개발 빌드
.useKakao(
  KakaoConfiguration(
    appKey = "YOUR_KAKAO_NATIVE_APP_KEY",
    customScheme = "kakao{APP_KEY}dev",   // 개발 전용 스킴
  ),
)
```

`customScheme` 을 지정하면 카카오 SDK 초기화에 전달됩니다. 카카오 개발자 콘솔과
`manifestPlaceholders["kakaoAuthScheme"]` 에도 **동일한 스킴**을 등록해야 합니다.
운영 앱은 `customScheme` 을 생략하면 기본 스킴(`kakao{APP_KEY}`)이 사용됩니다.

### 3. 네이티브 로그인 (카카오 / 네이버)

```kotlin
// activity 는 ComponentActivity (AppCompatActivity / ComponentActivity 등)
try {
  val result = EstLoginManager.login(activity, LoginPlatform.KAKAO)  // suspend, AuthResult 반환
  // result.authorizeToken / refreshToken / ci / email
  // → 토큰 교환·저장은 호스트 책임
} catch (e: AuthError.Cancelled) {
  // 사용자 취소
} catch (e: AuthError) {
  // UnsupportedPlatform / Unknown 등
}
```

### 4. WebView 로그인 (ssoToken 회수)

네이티브 SDK 가 없는 구글·애플 등은 WebView OAuth 경로로 일원화됩니다.

```kotlin
// Activity 기반 (권장) — 별도 화면 띄우고 ssoToken 회수
val sso: String? = EstLoginManager.startWebLogin(activity).getOrNull()
// 실패/취소 시 Result.failure(AuthError.Cancelled ...)

// 또는 자체 Compose 네비게이션에 임베드
EstLoginWebView(onLoginCompleted = { ssoToken -> /* 토큰 교환은 호스트 */ })
```

자세한 콜백 흐름·시그니처·화면 종료(dismiss) 책임은 아래 [웹뷰 로그인 상세](#웹뷰-로그인-상세)를 참고하세요.

### 5. 마이페이지 / 로그아웃

**유효한 accessToken을 넘기는 방식을 권장**합니다 — SDK가 일회성 ssoToken 발급
(`GET {apiBaseUrl}/auth/sso/sso-token`) → SSO 부트스트랩
(`GET {baseUrl}/auth/sso-login?code=...&redirect_url=...`) → 마이페이지 진입까지 처리하므로,
WebView 쿠키가 없거나 만료된 상태에서도 로그인 화면 없이 열립니다. (iOS와 동일한 방식)

```kotlin
EstMyPageWebView(
  accessToken = myAccessToken,          // 만료 판단·갱신은 앱 책임 — 만료면 갱신 후 전달
  onPasswordChanged = { /* silent 재발급 등 */ },
  onAccountDeleted = { /* 로그아웃 처리 */ },
  onBackPressed = { /* 화면 종료는 호스트 */ },
  onError = { /* ssoToken 발급 실패 — 401 이면 AuthError.Server(401) */ },
)

EstLoginManager.logout()  // suspend — 카카오/네이버 네이티브 SDK 토큰 정리
```

> - ssoToken은 **유효 60초, 1회용** — SDK가 웹뷰를 열 때마다 새로 발급하며, 저장·로그 출력하지 않습니다.
> - 만료된 accessToken이면 `AuthError.Server(statusCode = 401)` — 앱이 refreshToken으로 갱신 후 재시도하세요.
> - 세션 쿠키가 살아있는 경우에는 `EstMyPageWebView(url = ...)` 직접 진입도 가능합니다.
> - URL만 필요하면(`suspend`) `EstLoginManager.authorizedMypageUrl(accessToken)` / `authorizedVerificationUrl(accessToken, callbackUrl)`.

> **로그아웃은 반드시 호출하세요.** 카카오·네이버 네이티브 SDK 는 인증 토큰을 기기에 보관합니다.
> `logout()` 은 카카오/네이버 네이티브 토큰만 정리합니다. **웹 세션(쿠키/스토리지)은 SDK 가
> 건드리지 않습니다** — est 웹뷰는 열 때마다 accessToken 부트스트랩으로 세션을 새로 검증·수립하므로
> 앱 로그아웃 시 로컬 웹 데이터를 지울 필요가 없습니다(iOS 대칭).
> **호스트 앱이 직접 저장한 accessToken/refreshToken 은 SDK 가 보관하지 않으므로 호스트가 직접 삭제**해야 합니다.

### 6. 본인인증 여부 조회

```kotlin
try {
  val status = EstLoginManager.verificationStatus(accessToken = myAccessToken)  // suspend
  if (!status.isVerified) { /* 본인인증 화면 진입 — 정책 판단은 호스트 */ }
} catch (e: AuthError.Server) {
  // 서버가 2xx 아닌 상태로 응답. e.statusCode == 401 이면 accessToken 만료/무효 — 갱신 후 재시도
} catch (e: AuthError.Unknown) {
  // 네트워크 오류 등 (e.error 에 원본 에러)
}
```

> 조회 API — `GET /members/v1/certification/status` (Bearer 토큰), 응답 `status` 가
> `CERTIFIED`(완료) / `UNCERTIFIED`(미인증·미존재). 본인인증 **화면**과 완료 통지 방식은 확정·구현되어
> 있습니다. 화면을 포함한 전체 흐름은 아래 [본인인증 (Identity Verification)](#본인인증-identity-verification) 섹션을 참고하세요.

## 웹뷰 로그인 상세

WebView 로그인은 **SSO 콜백 방식**으로 동작합니다. 로그인이 완료되면 `callbackUrl` 로 리다이렉트되고,
SDK 가 그 URL 의 `code` 쿼리를 `ssoToken` 으로 추출해 전달합니다.

완료 판정은 로그인 URL 의 `state` 유무에 따라 두 가지로 나뉩니다 (iOS 패리티):

| 흐름 | 동작 |
|---|---|
| `state` 없음 | `callbackUrl` prefix + `code` 매칭 시 **즉시 완료** (네비게이션 중단) |
| `state` 있음 | `callbackUrl` 매칭 시 `code` 를 **최신 값으로 갱신만** 하고 통과시킨 뒤(리다이렉트 체인에서 서비스 세션 쿠키가 발급되는 경우를 보존), 네비게이션 URL 이 `state` prefix 에 **착지하는 시점에 완료** — 이때 세션 쿠키 커밋이 보장됩니다. `code` 는 체인 중 재발급될 수 있으므로 항상 마지막 값이 전달됩니다. |

### 로그인 URL 빌더

```kotlin
// 기본 (redirect_url = {baseUrl}/auth/app-callback)
val url = EstLoginManager.loginUrl()

// redirect_url / state 지정 (둘 다 선택)
val url2 = EstLoginManager.loginUrl(
  redirectUrl = "https://example.com/callback",
  state = "https://m.example.com",
)

// 재인증 없이 조용히 (silent)
val url3 = EstLoginManager.loginUrl(silent = true)
```

생성되는 URL 형식:

```
https://estoneid.com/user/login
  ?type=callback
  &client_id={발급받은 클라이언트 ID}
  &redirect_url=https://estoneid.com/auth/app-callback
  &state={앱이 전달할 임의 값 (선택)}
  &silent=true   (silent = true 일 때만)
```

> `state` 는 선택 파라미터입니다. 생략하면 URL 에 `state` 쿼리 자체가 포함되지 않습니다.

### 컴포저블 시그니처 (`EstLoginWebView`)

```kotlin
@Composable
fun EstLoginWebView(
  url: String = EstLoginManager.bootstrapLoginUrl(),
  callbackUrl: String? = EstLoginManager.getConfig()?.callbackUrl,
  extraUserAgent: String? = null,
  inspectable: Boolean = false,
  onPasswordChanged: () -> Unit = {},
  onAccountDeleted: () -> Unit = {},
  onBackPressed: () -> Unit = {},
  onLoginCompleted: (ssoToken: String?) -> Unit,
)
```

- `callbackUrl` 의 `code` 추출에 성공하면 `onLoginCompleted(ssoToken)` (non-null) 호출.
- `ssoToken` 이 **null** 이면 코드 회수에 실패한 경우입니다(예: 콜백 미도달). 일반적으로 `callbackUrl`
  을 지정해 non-null 케이스로 성공을 판정하세요.

> **화면 종료(dismiss/뒤로가기)는 호출부 책임입니다.** 컴포저블은 화면을 스스로 닫지 않습니다.
> `onLoginCompleted` / `onBackPressed` 안에서 네비게이션 pop 또는 dialog 닫기를 직접 구현하세요.

## 본인인증 (Identity Verification)

본인인증은 로그인/회원가입과 **완전히 분리**되어 있습니다. 앱은 (1) 인증 여부를 조회하고,
(2) 필요할 때 본인인증 화면을 직접 띄웁니다.

> 상태 조회 API와 본인인증 **화면 컴포넌트**(화면 URL / 완료 통지 방식 / 결과 필드)는 모두
> 확정·구현되어 있습니다.

### 책임 분리 — 상태(fact) vs 정책(policy)

| 구분 | 의미 | 담당 |
|------|------|------|
| **상태(fact)** | "이 사용자가 본인인증을 했는가?" — 백엔드가 아는 객관적 사실 | **SDK가 조회 API 제공** |
| **정책(policy)** | "지금 이 시점에 본인인증을 요구할까?" (결제 직전? 글쓰기 직전?) | **앱(호스트)이 결정** |

- 본인인증 화면을 **언제 띄울지(트리거)는 앱이 판단**합니다. SDK는 비즈니스 규칙을 갖지 않습니다.
- 판단 근거가 되는 **인증 여부는 `verificationStatus(...)` 로 조회**합니다.
- 본인인증 **화면은 SDK가 제공**하고, **띄우기/닫기는 앱이 담당**합니다.

### 1. 인증 여부 조회 — `verificationStatus` (구현됨)

```kotlin
data class VerificationStatus(
  val isVerified: Boolean,   // 응답 status == "CERTIFIED" 이면 true
)

// SDK 는 토큰을 보관하지 않으므로 호스트가 accessToken 을 주입한다.
suspend fun EstLoginManager.verificationStatus(accessToken: String): VerificationStatus
```

내부적으로 다음을 호출합니다.

```http
GET /members/v1/certification/status
Authorization: Bearer {accessToken}
```

응답(공통):

```json
{ "result": { "status": "CERTIFIED" },   "message": "" }   // 본인인증 완료 회원
{ "result": { "status": "UNCERTIFIED" }, "message": "" }   // 미인증 회원이거나 존재하지 않는 회원
```

> 인증 상태는 **통합회원 계정 단위**로 관리되어 모든 계열사에서 동일하게 조회됩니다. `status` 가
> `CERTIFIED` 이면 `isVerified == true` 입니다.

### 2. 본인인증 화면 — `EstIdentityVerificationWebView`

화면 콘텐츠와 완료 통지 수신은 SDK가 담당하고, **언제 띄울지(정책)와 화면을 감싸 present/dismiss
하는 것은 호스트**가 합니다. iOS `IdentityVerificationView` / `IdentityVerificationViewController` 와 대칭이며,
Compose 컴포저블 하나로 두 쓰임(선언형 표시 · Activity 호스팅)을 모두 커버합니다.

```kotlin
// 본인인증 화면 완료 결과 (token = 본인인증 후 재발급된 ssoToken)
data class VerificationResult(
  val token: String,
)

// 권장 — SSO 부트스트랩 진입. accessToken만 넘기면 발급→세션 수립→진입까지 SDK가 처리
// (url 오버로드와 JVM 시그니처가 겹쳐 별도 함수명)
@Composable
fun EstIdentityVerificationWebViewWithAccessToken(
  accessToken: String,          // 만료 판단·갱신은 앱 책임. 발급 실패는 onResult 로 failure 전달
  callbackUrl: String? = null,  // 완료 시 리다이렉트될 앱 콜백 URL (생략하면 결과 수신 불가)
  extraUserAgent: String? = EstLoginManager.getConfig()?.extraUserAgent,
  inspectable: Boolean = EstLoginManager.getConfig()?.webViewInspectable ?: false,
  onBackPressed: () -> Unit = {},
  onResult: (Result<VerificationResult>) -> Unit,
)

// 세션 쿠키가 살아있을 때의 URL 직접 진입
@Composable
fun EstIdentityVerificationWebView(
  url: String? = null,          // 생략 시 verificationUrl(callbackUrl) 로 구성
  callbackUrl: String? = null,
  /* 이하 동일 */
  onResult: (Result<VerificationResult>) -> Unit,
)
```

accessToken 진입 시 SDK가 ssoToken을 발급해 부트스트랩 URL로 이동하므로 **쿠키가 없거나 만료돼도
동작**합니다. 목적지 URL은 `EstLoginManager.verificationUrl(callbackUrl)` 로 생성됩니다.

```
{baseUrl}/auth/verification?callbackURL=<앱 콜백 URL, URL인코딩>
```

인증 회원 승격과 CI 충돌 해소는 웹뷰가 자체 처리합니다.

**완료 통지는 `callbackUrl` 리다이렉트 한 경로로만 전달됩니다.** 호스트는 `onResult` 만 구현하면 되고,
웹이 리다이렉트를 재시도해도 결과는 **한 번만** 전달됩니다.

| 경로 | 형태 |
|------|------|
| callbackUrl | `<callbackUrl>?status=certified\|cancelled\|error&code=<ssoToken>` |

> ⚠️ **`onVerificationComplete` JS 브릿지는 제거됐습니다.**
> 웹이 브릿지 존재 여부로 "네이티브가 본인인증을 호스팅 중"인지 판단해 리다이렉트를 생략했기 때문에,
> **웹이 자체적으로 회원가입 → 본인인증까지 이어가는 흐름이 인증 완료 직후 멈추는 문제**가 있었습니다.
> 이제 통지 경로가 리다이렉트 하나뿐이라 인증이 중간 단계든 종착점이든 동일하게 동작합니다.
> 웹은 브릿지 유무와 무관하게 **항상 `callbackUrl` 로 리다이렉트**하면 됩니다.

| `status` | 의미 | `onResult` |
|----------|------|------------|
| `certified` | 승격 완료 (CI 충돌 시 계정 병합까지 완료) | `Result.success(VerificationResult)` |
| `cancelled` | 사용자가 본인인증 취소/중단 | `Result.failure(AuthError.Cancelled)` |
| `error` | 승격 실패, 병합 실패, cert 조회 실패 등 | `Result.failure(AuthError.VerificationFailed)` |

> ⚠️ **CI 충돌로 계정이 병합되면 웹뷰 안의 세션이 다른 계정으로 바뀌어 있을 수 있습니다.**
> `certified` 수신 시 호스트는 전달받은 `token`(ssoToken)으로 **세션을 재수립**해야 병합된 계정과 상태가 맞습니다.

### 3. 전체 흐름 — "필요한 시점에 본인인증"

본인인증이 필요한 시점(예: 결제 진입)에서 앱이 상태를 확인하고, 미인증이면 화면을 띄웁니다.

```kotlin
@Composable
fun CheckoutButton(myAccessToken: String) {
  var showVerification by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  Button(onClick = {
    scope.launch {
      // 정책 판단은 앱이 — 여기서는 "미인증이면 막는다"
      val verified = runCatching {
        EstLoginManager.verificationStatus(accessToken = myAccessToken).isVerified
      }.getOrDefault(false)
      if (verified) proceedCheckout(verificationToken = null) else showVerification = true
    }
  }) { Text("결제하기") }

  if (showVerification) {
    EstIdentityVerificationWebViewWithAccessToken(
      accessToken = myAccessToken,
      onBackPressed = { showVerification = false },
      onResult = { result ->
        showVerification = false
        result
          .onSuccess { proceedCheckout(verificationToken = it.token) }
          .onFailure { /* AuthError.Cancelled 등 처리 */ }
      },
    )
  }
}
```

### 4. 동작 정리

| 항목 | 동작 |
|------|------|
| 본인인증 트리거(언제 띄울지) | **호스트** (결제 직전 등 비즈니스 시점) |
| 인증 여부 조회 | `verificationStatus(accessToken)` (SDK) |
| 인증 화면 제공 | `EstIdentityVerificationWebView` (SDK) |
| 화면 띄우기/닫기 | **호스트** |
| 토큰 | **호스트가 주입** (SDK 미보관) |
| 완료 통지 수신 | **SDK** (`callbackUrl` 리다이렉트를 가로채 전달, 결과는 1회만) |
| 결과 | `Result<VerificationResult>` |
| `certified` 후속 처리 | **호스트** (재발급된 ssoToken 으로 세션 재수립) |

## 에러 처리

`EstLoginManager.login(...)` 은 성공 시 `AuthResult` 를 반환하고, 실패 시 아래 `AuthError` 를 던집니다.

```kotlin
sealed class AuthError : Exception() {
  data object UnsupportedPlatform   // 미지원 플랫폼 (카카오/네이버 외)
  data object Cancelled             // 사용자 취소
  data object NotInitialized        // initialize(...) 미호출
  data object VerificationFailed    // 본인인증 승격/병합 실패, 또는 완료 통지 해석 불가
  data class  Server(statusCode)    // 서버가 2xx 아닌 상태로 응답 (401 이면 갱신 후 재시도)
  data class  Unknown(error)        // 그 외 (네트워크 오류 포함, 원본 에러 wrapping)
}
```

- `startWebLogin(...)` 은 예외 대신 `Result<String?>` 을 반환하며, 취소 시 `Result.failure(AuthError.Cancelled)`.
- `EstLoginWebView` / `EstMyPageWebView` 의 콜백은 예외를 던지지 않습니다(`onLoginCompleted` 의 `ssoToken` null 여부로 판정).
- 네이티브 SDK(카카오/네이버) 자체 에러 상세 분류가 필요하면 `AuthError.Unknown(error)` 의 원본 에러나
  각 [레퍼런스](#레퍼런스) 공식 문서를 참고하세요.

## 레퍼런스

- [Kakao developers (Android)](https://developers.kakao.com/docs/latest/ko/android/getting-started)
- [Naver Login SDK (Android)](https://developers.naver.com/docs/login/android/android.md)

---

## 부록 — iOS 버전과의 API 매핑 (마이그레이션 참고용)

이 SDK 는 [iOS ESTLoginKit](https://github.com/ZUM-Internet/ESTLoginKit-iOS) 과 API·동작을 정합시킵니다.
양쪽을 함께 다루는 개발자를 위한 대조표이며, **Android 단독 사용에는 필요 없습니다.**

| iOS | Android |
|---|---|
| `ESTLoginManager.shared` | `EstLoginManager` (object) |
| `ESTLoginConfiguration.Builder` | `EstLoginConfiguration.Builder` |
| `ESTEnvironment` (`.production`/`.development`) | `EstEnvironment` (`PRODUCTION`/`DEVELOPMENT`) |
| `useEnvironment(_:)` | `useEnvironment(...)` |
| `login(with:)` | `login(activity, platform)` |
| `loginURL(redirectURL:state:silent:)` | `loginUrl(redirectUrl, state, silent)` |
| `LoginWebView` / `MyPageWebView` | `EstLoginWebView` / `EstMyPageWebView` |
| `verificationStatus(accessToken:)` | `verificationStatus(accessToken)` |
| `logout()` | `logout()` |
