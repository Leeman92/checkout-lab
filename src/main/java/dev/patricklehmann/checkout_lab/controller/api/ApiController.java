package dev.patricklehmann.checkout_lab.controller.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

public class ApiController {

    public ResponseEntity<Map<String, Object>> sendResponse(
            Map<String, Object> body, HttpStatusCode status) {
        body.put("timestamp", new java.util.Date());

        return new ResponseEntity<>(body, status);
    }
}

