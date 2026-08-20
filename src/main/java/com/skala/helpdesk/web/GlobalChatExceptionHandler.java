package com.skala.helpdesk.web;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** {@code /api/**}로만 한정 — 전역 핸들러로 두면 다른 패키지의 예외까지 가로챌 수 있다. */
@RestControllerAdvice(basePackages = "com.skala.helpdesk.web")
class GlobalChatExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalChatExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class) // 없는 티켓 등
    ResponseEntity<ErrorResponse> notFound(IllegalArgumentException e) {
        return ResponseEntity.status(404).body(new ErrorResponse(e.getMessage(), null));
    }

    @ExceptionHandler(IllegalStateException.class) // 이미 승인된 티켓 재승인 등
    ResponseEntity<ErrorResponse> conflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(new ErrorResponse(e.getMessage(), null));
    }

    @ExceptionHandler(Exception.class) // 모델 호출 실패 등 예기치 못한 오류의 최종 방어선
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.error("[{}] 상담 처리 실패", traceId, e);
        return ResponseEntity.status(503).body(new ErrorResponse(
                "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.", traceId));
    }

    record ErrorResponse(String message, String traceId) {
    }
}
