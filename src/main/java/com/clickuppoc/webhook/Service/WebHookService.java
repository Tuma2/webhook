package com.clickuppoc.webhook.Service;

import com.clickuppoc.webhook.DTO.EventDTO;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface WebHookService {
    ResponseEntity<Map<String, Object>> handleClickUpEvent(String signature, String rawBody);
}
