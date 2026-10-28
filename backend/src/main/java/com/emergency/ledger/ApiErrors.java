package com.emergency.ledger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiErrors {
    private final ObjectMapper json;
    public ApiErrors(ObjectMapper json) { this.json=json; }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<byte[]> uploadTooLarge(MaxUploadSizeExceededException e) throws JsonProcessingException {
        return payloadTooLarge("UPLOAD_TOO_LARGE", "Excel 文件不能超过 2 MB，上传请求不能超过 3 MB");
    }
    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiResponse<Void>> missingFile(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", "请选择要导入的 Excel 文件"));
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> api(ApiException e) throws JsonProcessingException {
        if(e.status()==HttpStatus.PAYLOAD_TOO_LARGE) return payloadTooLarge(e.code(),e.getMessage());
        return ResponseEntity.status(e.status()).body(ApiResponse.error(e.code(), e.getMessage()));
    }
    private ResponseEntity<byte[]> payloadTooLarge(String code,String message) throws JsonProcessingException {
        // 代理转发拒绝上传的关闭连接响应时，使用字节长度明确界定 JSON，避免依赖 chunked 结束帧。
        byte[] payload=json.writeValueAsBytes(ApiResponse.error(code,message));
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).contentType(MediaType.APPLICATION_JSON)
            .contentLength(payload.length).body(payload);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream().map(x -> x.getField() + "：" + x.getDefaultMessage()).distinct().collect(Collectors.joining("；"));
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", msg));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<ApiResponse<Void>> badRequest(Exception e) { return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", "请求格式或字段值无效")); }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<ApiResponse<Void>> badParameter(Exception e) { return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", "路径或查询参数格式无效")); }
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
