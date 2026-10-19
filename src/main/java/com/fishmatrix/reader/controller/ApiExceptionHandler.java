package com.fishmatrix.reader.controller;

import com.fishmatrix.reader.model.NotFoundException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "参数不合法" : exception.getMessage();
        return Map.of("error", "BAD_REQUEST", "message", message);
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> notFound(NotFoundException exception) {
        return Map.of("error", "NOT_FOUND", "message", exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, Object> tooLarge(MaxUploadSizeExceededException exception) {
        return Map.of("error", "PAYLOAD_TOO_LARGE", "message", "文件大小超过限制");
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> unavailable(DataAccessException exception) {
        return Map.of("error", "READER_UNAVAILABLE", "message", "阅读服务暂不可用");
    }
}
