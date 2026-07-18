package dev.patricklehmann.checkout_lab.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomErrorController implements ErrorController {
    @GetMapping("/error")
    public ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", new java.util.Date());

        // Extract status code
        Integer statusCode = (Integer) request.getAttribute("javax.servlet.error.status_code");
        body.put("status", statusCode != null ? statusCode : 500);
        body.put(
                "error",
                HttpStatus.valueOf(statusCode != null ? statusCode : 500).getReasonPhrase());

        return new ResponseEntity<>(
                body, HttpStatus.valueOf(statusCode != null ? statusCode : 500));
    }
}
