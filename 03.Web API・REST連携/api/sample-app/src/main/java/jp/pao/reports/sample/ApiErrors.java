package jp.pao.reports.sample;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;

@RestControllerAdvice
class ApiErrors {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(ApiErrors.class);
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,String>> error(Exception e) {
        int status=500; String message="処理に失敗しました。時間をおいて再度お試しください。";
        if(e instanceof ResponseStatusException r) {status=r.getStatusCode().value();message=r.getReason();}
        else if(e instanceof IllegalArgumentException) {status=400;message=e.getMessage();}
        else if(e instanceof JsonProcessingException) {status=400;message="JSON形式を確認してください。";}
        if(status>=500) LOG.error("Sample request failed",e);
        return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("error",message==null?"処理に失敗しました。":message));
    }
}
