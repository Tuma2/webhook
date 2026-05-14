package com.clickuppoc.webhook.Controller;

import com.clickuppoc.webhook.Implementation.OrchestrationImplementation;
import com.clickuppoc.webhook.Service.WebHookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/automation")
public class WebHookController {

    private static final Logger LOG = LoggerFactory.getLogger(WebHookController.class);

    @Autowired
    private WebHookService webHookService;


    @PostMapping("/listCreated")
    public ResponseEntity<Map<String, Object>> handleClickUpEvent(
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestBody Map<String, Object> rawBody) {
            LOG.info("### Received webhook event with signature: {}", signature);
            try{
                LOG.info("### RawBody received in controller: {}", rawBody.toString());
                String spaceId = String.valueOf(rawBody.get("space_id"));
                LOG.info("### 123 Received webhook event for space ID: {}", spaceId);
                return webHookService.handleClickUpEvent(signature, rawBody);
            } catch (Exception e) {
//                LOG.error("Error processing webhook event: " + e.getMessage());
                throw new RuntimeException(e);
            }
    }
}
