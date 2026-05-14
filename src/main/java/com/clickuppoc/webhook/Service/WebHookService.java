package com.clickuppoc.webhook.Service;


import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface WebHookService {
    ResponseEntity<Map<String, Object>> handleClickUpEvent(String signature, Map<String, Object> rawBody);
}
