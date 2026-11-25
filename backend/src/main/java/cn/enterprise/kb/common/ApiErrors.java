package cn.enterprise.kb.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);
    public record Body(String code, String message) {}

    @ExceptionHandler(ApiProblem.class)
    ResponseEntity<Body> handle(ApiProblem error) { return reply(error.status(), error.code(), error.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Body> invalid(MethodArgumentNotValidException error) {
        var field = error.getBindingResult().getFieldError();
        return reply(HttpStatus.BAD_REQUEST, "INVALID_INPUT", field == null ? "请检查输入。" : field.getDefaultMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            ServletRequestBindingException.class, MissingServletRequestPartException.class})
    ResponseEntity<Body> malformed() { return reply(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "请求格式或参数不正确。"); }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Body> tooLarge() { return reply(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "文件超过上传大小限制。"); }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Body> duplicate() {
        return reply(HttpStatus.CONFLICT, "CONFLICT", "数据已存在或关联状态已变化，请刷新后重试。");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Body> notFound() { return reply(HttpStatus.NOT_FOUND, "NOT_FOUND", "请求的地址不存在。"); }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Body> methodNotAllowed() {
        return reply(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "请求方式不受支持。");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Body> unexpected(Exception error) {
        // Provider messages may contain document text or credentials.
        log.error("Request failed ({})", error.getClass().getSimpleName());
        return reply(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "处理失败，请稍后重试。");
    }

    private ResponseEntity<Body> reply(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new Body(code, message));
    }
}
