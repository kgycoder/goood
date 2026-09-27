package com.lmarena.app;

/**
 * DOM 감지 및 조작 JavaScript 스크립트 모음.
 *
 * dom_selectors.py의 셀렉터를 JavaScript로 이식한 것.
 * WebView에 inject되어 LM Arena 페이지를 직접 제어한다.
 */
public class DomInjector {

    // ── 셀렉터 (dom_selectors.py 기반) ──────────────────────────────

    /** 모바일에서 사이드바 열기 버튼 (문제 기술의 aria-label 기반) */
    private static final String SIDEBAR_OPEN_BTN =
        "button[aria-label='Open sidebar']";

    /** 로그아웃 마커: Sign in / Log in 버튼 */
    private static final String SIGN_IN_MARKERS =
        "button[class*='whitespace-nowrap']";

    /** 프롬프트 입력창 (TipTap/ProseMirror contenteditable) */
    private static final String PROMPT_INPUT =
        "div.ProseMirror[contenteditable='true'], " +
        "div[contenteditable='true'][translate='no'], " +
        "div[contenteditable='true']:has(p[data-placeholder]), " +
        "[role='textbox'][contenteditable='true'], " +
        "div[contenteditable='true']";

    /** 전송 버튼 */
    private static final String SEND_BUTTON =
        "button[type='submit'], button[aria-label='Send message'], button[data-testid='send-button']";

    /** 생성 중 인디케이터 (Stop 버튼) */
    private static final String GENERATING_INDICATOR =
        "button:has(svg rect[x='6'][y='6'][width='12'][height='12']), " +
        "button[aria-label='Stop generation'], button[aria-label='Stop generating']";

    /** 답변 컨테이너 */
    private static final String RESPONSE_CONTAINER =
        "div.prose[class*='prose-base'], div.prose, [data-testid='message-content']";

    /** 코드 블록 컨테이너 */
    private static final String CODE_BLOCK_CONTAINER =
        "div[data-code-block='true'], div.not-prose:has(pre), pre";

    /** New Chat 버튼 */
    private static final String NEW_CHAT_BTN =
        "a[data-sidebar='menu-button'][href='/text'], a[href='/text']:has-text('New Chat'), a[href='/text']";

    // ── 초기화 스크립트 ──────────────────────────────────────────────

    /**
     * 페이지 로드 후 DOM 감지 루프를 시작하는 초기화 스크립트.
     * window.LMArena 네임스페이스를 설정하고 MutationObserver로
     * 로그인 상태 및 응답 생성을 모니터링한다.
     */
    public static String getInitScript() {
        return "(function() {\n" +
            "  if (window.__lmArenaInjected) return;\n" +
            "  window.__lmArenaInjected = true;\n" +
            "\n" +
            "  var lastResponseText = '';\n" +
            "  var isGenerating = false;\n" +
            "  var responseCheckInterval = null;\n" +
            "  var loginCheckInterval = null;\n" +
            "\n" +
            // ── 로그인 감지 ──
            "  function checkLoginState() {\n" +
            "    var hasSignIn = !!(document.querySelector(\"button[class*='whitespace-nowrap']\") && \n" +
            "      (document.body.innerText.indexOf('Log in') >= 0 || \n" +
            "       document.body.innerText.indexOf('Sign in') >= 0 ||\n" +
            "       document.body.innerText.indexOf('Sign up') >= 0));\n" +
            "    var hasInput = !!(document.querySelector(\"div.ProseMirror[contenteditable='true']\") ||\n" +
            "                      document.querySelector(\"div[contenteditable='true']\"));\n" +
            "    var hasFrontier = document.body.innerText.toLowerCase().indexOf('experience the frontier') >= 0;\n" +
            "    if (hasInput && !hasFrontier) {\n" +
            "      Android.notifyLoginDetected();\n" +
            "    } else if (hasSignIn || hasFrontier) {\n" +
            "      Android.notifyLogoutDetected();\n" +
            "    }\n" +
            "  }\n" +
            "\n" +
            // ── 응답 스트리밍 감지 ──
            "  function getResponseContainers() {\n" +
            "    return document.querySelectorAll('div.prose, div[class*=\"prose-base\"]');\n" +
            "  }\n" +
            "\n" +
            "  function getLastResponse() {\n" +
            "    var containers = getResponseContainers();\n" +
            "    if (!containers || containers.length === 0) return null;\n" +
            "    return containers[containers.length - 1];\n" +
            "  }\n" +
            "\n" +
            "  function isCurrentlyGenerating() {\n" +
            "    return !!(document.querySelector(\"button[aria-label='Stop generation']\") ||\n" +
            "              document.querySelector(\"button[aria-label='Stop generating']\") ||\n" +
            "              document.querySelector('button:has(svg rect[x=\\'6\\'][y=\\'6\\'][width=\\'12\\'][height=\\'12\\'])'));\n" +
            "  }\n" +
            "\n" +
            "  function pollResponse() {\n" +
            "    var generating = isCurrentlyGenerating();\n" +
            "    if (generating !== isGenerating) {\n" +
            "      isGenerating = generating;\n" +
            "      Android.notifyGeneratingState(isGenerating);\n" +
            "    }\n" +
            "    var lastEl = getLastResponse();\n" +
            "    if (!lastEl) return;\n" +
            "    var text = lastEl.innerText || '';\n" +
            "    if (text !== lastResponseText) {\n" +
            "      lastResponseText = text;\n" +
            "      var codeBlocks = extractCodeBlocks(lastEl);\n" +
            "      var payload = JSON.stringify({\n" +
            "        text: text,\n" +
            "        codeBlocks: codeBlocks,\n" +
            "        isDone: !generating\n" +
            "      });\n" +
            "      Android.notifyResponseChunk(payload, !generating);\n" +
            "    } else if (!generating && lastResponseText.length > 0) {\n" +
            "      // Stable + not generating = done\n" +
            "      Android.notifyResponseChunk(JSON.stringify({\n" +
            "        text: lastResponseText,\n" +
            "        codeBlocks: extractCodeBlocks(lastEl),\n" +
            "        isDone: true\n" +
            "      }), true);\n" +
            "    }\n" +
            "  }\n" +
            "\n" +
            // ── 코드 블록 추출 ──
            "  function extractCodeBlocks(container) {\n" +
            "    var blocks = [];\n" +
            "    // Direct mode code blocks\n" +
            "    var codeEls = container.querySelectorAll('pre code, pre');\n" +
            "    codeEls.forEach(function(el) {\n" +
            "      var lang = '';\n" +
            "      var parent = el.closest('[data-code-block]') || el.closest('div.not-prose');\n" +
            "      if (parent) {\n" +
            "        var langEl = parent.querySelector('span[class*=\"text-secondary\"], span.text-sm');\n" +
            "        if (langEl) lang = langEl.innerText.trim();\n" +
            "      }\n" +
            "      var code = el.nodeName === 'CODE' ? el.innerText : el.innerText;\n" +
            "      if (code && code.trim()) {\n" +
            "        blocks.push({ language: lang, code: code });\n" +
            "      }\n" +
            "    });\n" +
            "    return blocks;\n" +
            "  }\n" +
            "\n" +
            // ── 모바일 사이드바 열기 ──
            "  function tryOpenSidebar() {\n" +
            "    var btn = document.querySelector(\"button[aria-label='Open sidebar']\");\n" +
            "    if (btn) { btn.click(); return true; }\n" +
            "    return false;\n" +
            "  }\n" +
            "  window.__lmOpenSidebar = tryOpenSidebar;\n" +
            "\n" +
            // ── 인터벌 시작 ──
            "  loginCheckInterval = setInterval(checkLoginState, 1500);\n" +
            "  responseCheckInterval = setInterval(pollResponse, 500);\n" +
            "\n" +
            "  Android.notifyPageReady(window.location.href);\n" +
            "  checkLoginState();\n" +
            "})();\n";
    }

    /**
     * 프롬프트를 LM Arena 입력창에 입력하고 전송하는 스크립트.
     * @param prompt 전송할 텍스트 (JSON-escaped)
     */
    public static String getSendPromptScript(String escapedPrompt) {
        return "(function() {\n" +
            "  var selectors = [\n" +
            "    \"div.ProseMirror[contenteditable='true']\",\n" +
            "    \"div[contenteditable='true'][translate='no']\",\n" +
            "    \"div[contenteditable='true']\",\n" +
            "    \"textarea[name='message']\",\n" +
            "    \"textarea\"\n" +
            "  ];\n" +
            "  var input = null;\n" +
            "  for (var i = 0; i < selectors.length; i++) {\n" +
            "    input = document.querySelector(selectors[i]);\n" +
            "    if (input) break;\n" +
            "  }\n" +
            "  if (!input) { Android.notifyError('입력창을 찾을 수 없습니다.'); return; }\n" +
            "\n" +
            "  input.focus();\n" +
            "  var text = " + escapedPrompt + ";\n" +
            "\n" +
            // ProseMirror contenteditable
            "  if (input.getAttribute('contenteditable') === 'true') {\n" +
            "    input.innerHTML = '';\n" +
            "    var p = document.createElement('p');\n" +
            "    p.textContent = text;\n" +
            "    input.appendChild(p);\n" +
            "    input.dispatchEvent(new Event('input', {bubbles: true}));\n" +
            "    setTimeout(function() {\n" +
            "      var sendSelectors = [\n" +
            "        \"button[type='submit']\",\n" +
            "        \"button[aria-label='Send message']\",\n" +
            "        \"button[data-testid='send-button']\"\n" +
            "      ];\n" +
            "      var sendBtn = null;\n" +
            "      for (var j = 0; j < sendSelectors.length; j++) {\n" +
            "        sendBtn = document.querySelector(sendSelectors[j]);\n" +
            "        if (sendBtn) break;\n" +
            "      }\n" +
            "      if (sendBtn) {\n" +
            "        sendBtn.click();\n" +
            "      } else {\n" +
            // Enter key fallback
            "        var evt = new KeyboardEvent('keydown', {key: 'Enter', code: 'Enter', keyCode: 13, bubbles: true});\n" +
            "        input.dispatchEvent(evt);\n" +
            "      }\n" +
            "    }, 200);\n" +
            "  } else {\n" +
            // textarea fallback
            "    var nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set;\n" +
            "    nativeInputValueSetter.call(input, text);\n" +
            "    input.dispatchEvent(new Event('input', {bubbles: true}));\n" +
            "    setTimeout(function() {\n" +
            "      var evt = new KeyboardEvent('keydown', {key: 'Enter', code: 'Enter', keyCode: 13, bubbles: true});\n" +
            "      input.dispatchEvent(evt);\n" +
            "    }, 200);\n" +
            "  }\n" +
            "})();\n";
    }

    /**
     * 새 채팅 버튼 클릭 스크립트.
     */
    public static String getNewChatScript() {
        return "(function() {\n" +
            "  var selectors = [\n" +
            "    \"a[data-sidebar='menu-button'][href='/text']\",\n" +
            "    \"a[href='/text']\"\n" +
            "  ];\n" +
            "  for (var i = 0; i < selectors.length; i++) {\n" +
            "    var el = document.querySelector(selectors[i]);\n" +
            "    if (el) { el.click(); return; }\n" +
            "  }\n" +
            // Fallback: navigate directly
            "  window.location.href = 'https://arena.ai/text/direct';\n" +
            "})();\n";
    }

    /**
     * Agent 모드 새 채팅 스크립트.
     */
    public static String getNewChatAgentScript() {
        return "(function() {\n" +
            "  var selectors = [\n" +
            "    \"a[href^='/agent']\",\n" +
            "    \"a[data-sidebar='menu-button'][href^='/agent']\"\n" +
            "  ];\n" +
            "  for (var i = 0; i < selectors.length; i++) {\n" +
            "    var el = document.querySelector(selectors[i]);\n" +
            "    if (el) { el.click(); return; }\n" +
            "  }\n" +
            "  window.location.href = 'https://arena.ai/agent/';\n" +
            "})();\n";
    }

    /**
     * JSON.stringify 시 문자열을 안전하게 이스케이프.
     */
    public static String escapeForJson(String text) {
        if (text == null) return "\"\"";
        return "\"" + text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            + "\"";
    }
}
