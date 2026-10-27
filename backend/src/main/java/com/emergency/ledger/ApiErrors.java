package com.emergency.ledger;

import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> api(ApiException e) { return ResponseEntity.status(e.status()).body(ApiResponse.error(e.code(), e.getMessage())); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream().map(x -> x.getField() + "：" + x.getDefaultMessage()).distinct().collect(Collectors.joining("；"));
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", msg));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<ApiResponse<Void>> badRequest(Exception e) { return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", "请求格式或字段值无效")); }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<Void>> badParameter(MethodArgumentTypeMismatchException e) { return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", "路径或查询参数格式无效")); }
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ApiResponse<Void>> duplicate(DuplicateKeyException e) {
        String detail = e.getMostSpecificCause().getMessage();
        if(detail!=null && (detail.contains("uk_ledger_org_code") || detail.contains("UK_LEDGER_ORG_CODE")))
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("DUPLICATE_LEDGER_CODE", "台账编号已存在"));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("DATA_CONFLICT", "数据与现有记录冲突"));
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException e) { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("FORBIDDEN", "无权执行此操作")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("INTERNAL_ERROR", "服务暂不可用，请稍后重试")); }
}
