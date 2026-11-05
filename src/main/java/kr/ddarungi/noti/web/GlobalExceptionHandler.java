package kr.ddarungi.noti.web;

import kr.ddarungi.noti.web.dto.ApiDtos.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).findFirst().orElse("Invalid request");
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_FAILED", msg));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    ResponseEntity<ErrorResponse> unreadable(Exception e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", "Malformed request"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", "Invalid value for '" + e.getName() + "'"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> other(Exception e) {
        // Spring MVC's own client errors (missing @RequestParam -> 400, unknown route -> 404,
        // wrong method -> 405, ...) implement org.springframework.web.ErrorResponse: keep their status.
        if (e instanceof org.springframework.web.ErrorResponse spring) {
            HttpStatus status = HttpStatus.resolve(spring.getStatusCode().value());
            String code = status != null ? status.name() : "HTTP_" + spring.getStatusCode().value();
            return ResponseEntity.status(spring.getStatusCode())
                    .body(new ErrorResponse(code, spring.getBody().getDetail()));
        }
        log.error("Unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "Unexpected server error"));
    }
}
