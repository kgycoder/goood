# LM Arena Android

LM Arena (arena.ai) 안드로이드 앱.

## 기술 구조

기존 Windows 버전(Playwright 자동화)을 Android WebView 기반으로 재구현.

### 핵심 아키텍처

```
네이티브 UI (RecyclerView, DrawerLayout)
        ↕ Java Bridge (LMArenaWebBridge)
숨겨진 WebView (arena.ai 실제 페이지 로드)
        ↕ JavaScript Injection (DomInjector)
LM Arena DOM (로그인 감지, 답변 스트리밍, 입력 전송)
```

- **숨겨진 WebView**: 실제 LM Arena 페이지를 1x1px 크기로 백그라운드에서 로드
- **DomInjector**: `dom_selectors.py`의 셀렉터를 JavaScript로 이식 → 500ms 폴링
- **LMArenaWebBridge**: JS → Java 이벤트 브리지 (`@JavascriptInterface`)
- **MessageAdapter**: 네이티브 채팅 UI 렌더링

## 지원 기능

| 기능 | 지원 | 비고 |
|------|------|------|
| LM Arena 로그인 | ✅ | WebView 쿠키 기반 |
| Direct 채팅 | ✅ | JS injection으로 전송 |
| Agent 모드 | ✅ | arena.ai/agent/ 로드 |
| 코드 블록 표시 | ✅ | 복사 버튼 포함 |
| 답변 스트리밍 감지 | ✅ | DOM 폴링 방식 |
| 새 채팅 | ✅ | |

## 미지원 기능 (Android 불가)

| 기능 | 이유 |
|------|------|
| 파일 자동 저장 | `file_writer.py` → Android 파일시스템 권한 복잡성 |
| Workspace 공유 | 로컬 폴더 읽기 → Android Storage API 제한 |
| Work 에이전트 | 마우스/키보드 PC 조작 불가 |
| reCAPTCHA 자동 처리 | 사용자가 WebView에서 직접 해결 |
| Window 숨기기 | `window_hider.py` → Android에 해당 없음 |
| 모델 선택 UI | LM Arena DOM 의존 → 추후 추가 가능 |

## 빌드

### GitHub Actions (자동)

`main` 또는 `master` 브랜치에 push하면 자동으로 APK 빌드.
Actions 탭 → Artifacts에서 다운로드.

### 로컬 빌드

```bash
# JDK 17 필요
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## 최소 요구사항

- Android 8.0 (API 26) 이상
- 인터넷 연결
