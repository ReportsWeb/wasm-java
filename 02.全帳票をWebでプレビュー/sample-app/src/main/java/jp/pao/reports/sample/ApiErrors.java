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
        // Spring's own client errors (e.g. NoResourceFoundException for a missing static
        // file such as the optional reports-web.license) keep their 4xx status. Answering
        // 500 made the previewer show the license error dialog in trial mode (B17).
        else if(e instanceof org.springframework.web.ErrorResponse r && r.getStatusCode().is4xxClientError()) {status=r.getStatusCode().value();message=status==404?"ファイルが見つかりません。":"要求を処理できません。";}
        else if(e instanceof IllegalArgumentException) {status=400;message=e.getMessage();}
        else if(e instanceof JsonProcessingException) {status=400;message="JSON形式を確認してください。";}
        if(status>=500) LOG.error("Sample request failed",e);
        return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("error",message==null?"処理に失敗しました。":message));
    }
}
