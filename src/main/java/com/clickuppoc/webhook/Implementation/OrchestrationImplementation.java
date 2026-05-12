package com.clickuppoc.webhook.Implementation;

import com.clickuppoc.webhook.Service.WebHookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

@Service
public class OrchestrationImplementation implements WebHookService {

    private static final Logger LOG = LoggerFactory.getLogger(OrchestrationImplementation.class);

    @Value("${webhook.secret}")
    private String webhookSecret;

    @Value("${superagent.endpoint}")
    private String superagentEndpoint;

    @Value("${superagent.api.key}")
    private String superagentApiKey;

    @Value("${superagent.user.id}")
    private String superagentUserId;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();


    @Override
    public ResponseEntity<Map<String, Object>> handleClickUpEvent(String signature, String rawBody) {
        if (!isValidSignature(rawBody, signature)) {
            LOG.warn("### Invalid signature received — request rejected");
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "Invalid signature");
            return ResponseEntity.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED).body(errorBody);
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            LOG.warn("Failed to parse webhook payload: {}", e.getMessage());
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "Invalid JSON");
            return ResponseEntity.<Map<String, Object>>badRequest().body(errorBody);
        }

        String event     = payload.path("event").asText("");
        String listId    = payload.path("list_id").asText("");
        String spaceId   = payload.path("space_id").asText("");
        String teamId    = payload.path("team_id").asText("");
        String webhookId = payload.path("webhook_id").asText("");

        LOG.info("### Received ClickUp event: {} | list: {}", event, listId);

        // 3. ── Filter: only act on listCreated
        if ("listCreated".equals(event)) {
            triggerSuperagentAsync(listId, spaceId, teamId, webhookId);
        } else {
            LOG.info("### Event ignored (not listCreated): {}", event);
        }

        // 4. ── Acknowledge ClickUp immediately (must respond within 3 seconds)
        Map<String, Object> ack = new HashMap<>();
        ack.put("received", true);
        return ResponseEntity.<Map<String, Object>>ok(ack);
    }

    private boolean isValidSignature(String rawBody, String receivedSignature) {
        if (receivedSignature == null || receivedSignature.isBlank()) {
            LOG.warn("### No x-signature header present");
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);

            byte[] hash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);

            return computed.equals(receivedSignature);
        } catch (Exception e) {
            LOG.error("### Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private void triggerSuperagentAsync(
            String listId, String spaceId, String teamId, String webhookId) {

        new Thread(() -> {
            try {
                LOG.info("### Triggering superagent for list: {}", listId);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", superagentApiKey);

                // Step 1: Create or retrieve DM channel with the super agent
                Map<String, Object> dmBody = new HashMap<>();
                dmBody.put("member_ids", java.util.List.of(superagentUserId));

                HttpEntity<Map<String, Object>> dmRequest = new HttpEntity<>(dmBody, headers);

                ResponseEntity<Map> dmResponse = restTemplate.postForEntity(
                        "https://api.clickup.com/api/v3/workspaces/" + teamId + "/chat/direct-messages",
                        dmRequest,
                        Map.class
                );

                String channelId = (String) ((Map<?, ?>) dmResponse.getBody()).get("id");
                LOG.info("### DM channel ID: {}", channelId);

                // Step 2: Send a message to the super agent
                String message = "A new list has been created with ID: " + listId +
                        " in space: " + spaceId +
                        ". Please create the predefined folders for this list now.";

                Map<String, Object> msgBody = new HashMap<>();
                msgBody.put("content", message);
                msgBody.put("content_format", "text/md");

                HttpEntity<Map<String, Object>> msgRequest = new HttpEntity<>(msgBody, headers);

                ResponseEntity<String> msgResponse = restTemplate.postForEntity(
                        "https://api.clickup.com/api/v3/workspaces/" + teamId + "/chat/channels/" + channelId + "/messages",
                        msgRequest,
                        String.class
                );

                LOG.info("### Superagent DM sent: HTTP {}", msgResponse.getStatusCode());

            } catch (Exception e) {
                LOG.error("Failed to trigger superagent: {}", e.getMessage());
            }
        }).start();
    }
}
