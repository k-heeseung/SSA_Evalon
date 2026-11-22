package com.example.evalhub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 서비스 계층이 의도적으로 던지는 예외는 모두 ResponseStatusException 계열이다. 상태 코드는 그대로 두고
 * 운영에서도 사유(message)가 화면에 보이도록 본문만 맞춘다. 그 외에는 그대로 두면 500으로 나가
 * 원인 파악이 어려운 예외만 골라 상태 코드를 바로잡는다.
 *
 * Exception 전체를 받는 핸들러는 두지 않는다. AccessDeniedException처럼 Spring Security가
 * 403으로 변환해야 할 예외까지 가로채 500으로 만들어 버리기 때문이다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * DB 제약 조건 위반(유니크·NOT NULL·FK)을 409로 변환한다.
     * 예: 같은 프로그램에 지원서를 두 번 만들려는 요청(uq_applications_program_applicant).
     *
     * 응답 본문의 키는 Spring Boot 기본 오류 응답과 맞춘다. 프론트가 전역적으로
     * err.response.data.message를 읽기 때문에 message 키가 없으면 안내 문구가 사라진다.
     */
    /**
     * 서비스 계층이 의도적으로 던진 ResponseStatusException의 사유(reason)를 message로 내려준다.
     *
     * 운영 설정(SERVER_ERROR_INCLUDE_MESSAGE=never)은 Spring Boot 기본 오류 응답에서 message를 비운다.
     * 그러면 "답변이 달린 항목은 삭제할 수 없습니다" 같은 안내가 화면에 'Conflict'로만 보인다.
     * 여기서는 서비스가 직접 쓴 사유만 노출하고, 예상하지 못한 예외(500)의 내부 정보는 계속 숨긴다.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException e) {
        HttpStatusCode status = e.getStatusCode();
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String error = resolved == null ? String.valueOf(status.value()) : resolved.getReasonPhrase();
        String message = e.getReason() == null || e.getReason().isBlank() ? error : e.getReason();
        return ResponseEntity.status(status).headers(e.getHeaders()).body(Map.of(
                "status", status.value(),
                "error", error,
                "message", message
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        // 어떤 제약이 걸렸는지는 스택 트레이스에만 남기고, 응답에는 내부 정보를 노출하지 않는다.
        log.error("데이터 제약 조건 위반", e);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status", HttpStatus.CONFLICT.value(),
                "error", HttpStatus.CONFLICT.getReasonPhrase(),
                "message", "요청을 처리하지 못했습니다. 이미 등록된 값이거나 다른 데이터와 충돌합니다."
        ));
    }
}
