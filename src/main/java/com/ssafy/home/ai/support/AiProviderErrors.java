package com.ssafy.home.ai.support;

import org.springframework.web.client.HttpClientErrorException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * AI 공급자(GMS/OpenAI) 호출 예외를 분류하는 공통 유틸.
 * <p>
 * 질문/에이전트/어시스턴트 컨트롤러가 동일 규칙으로 timeout·인증 실패를 판별하도록 한 곳에 모았다
 * (기존 두 컨트롤러에 복사돼 있던 로직을 추출). 메시지 원문은 응답·로그에 노출하지 않고 분류 용도로만 쓴다.
 */
public final class AiProviderErrors {

    private AiProviderErrors() {
    }

    /** 예외 체인에 타임아웃 계열이 있으면 true. */
    public static boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof TimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 공급자 인증 실패(키 무효/만료)로 보이는지 판별한다 — HTTP 401/403 또는 인증 관련 표식.
     * 메시지 원문을 노출하지 않고 분류 용도로만 사용한다.
     */
    public static boolean isAuthFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof HttpClientErrorException httpError) {
                int code = httpError.getStatusCode().value();
                if (code == 401 || code == 403) {
                    return true;
                }
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("401") || lower.contains("403")
                        || lower.contains("unauthorized")
                        || lower.contains("invalid_api_key")
                        || lower.contains("incorrect api key")
                        || lower.contains("expired")
                        || lower.contains("authentication")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
