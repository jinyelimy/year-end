package com.example.yearend.common.exception;

import com.example.yearend.common.api.ApiResponse;
import com.example.yearend.common.api.ErrorResponse;
import com.example.yearend.common.api.FieldErrorDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
            .body(ApiResponse.failure(ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(exception.getMessage())
                .build()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        List<FieldErrorDetail> fieldErrors = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::toFieldError)
            .toList();

        return ResponseEntity.badRequest()
            .body(ApiResponse.failure(ErrorResponse.builder()
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .message(ErrorCode.VALIDATION_ERROR.getMessage())
                .fieldErrors(fieldErrors)
                .build()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getStatus())
            .body(ApiResponse.failure(ErrorResponse.builder()
                .code(ErrorCode.FORBIDDEN.getCode())
                .message(ErrorCode.FORBIDDEN.getMessage())
                .build()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException exception) {
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getStatus())
            .body(ApiResponse.failure(ErrorResponse.builder()
                .code(ErrorCode.UNAUTHORIZED.getCode())
                .message(ErrorCode.INVALID_CREDENTIALS.getMessage())
                .build()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        return ResponseEntity.internalServerError()
            .body(ApiResponse.failure(ErrorResponse.builder()
                .code("COMMON_500")
                .message(exception.getMessage())
                .build()));
    }

    private FieldErrorDetail toFieldError(FieldError fieldError) {
        return FieldErrorDetail.builder()
            .field(fieldError.getField())
            .reason(fieldError.getDefaultMessage())
            .build();
    }
}
