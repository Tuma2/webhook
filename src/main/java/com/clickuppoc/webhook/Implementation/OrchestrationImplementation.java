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
import java.util.*;

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

        LOG.info("THis is the rawbody as a string: {}",rawBody);

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
            LOG.info("### Raw webhook payload parsed successfully ==> {}", payload.toString());
        } catch (Exception e) {
            LOG.warn("Failed to parse webhook payload: {}", e.getMessage());
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "Invalid JSON");
            return ResponseEntity.<Map<String, Object>>badRequest().body(errorBody);
        }
        String spaceId = payload.path("space_id").asText("");
        String event = payload.path("event").asText("");
        String teamId    = payload.path("team_id").asText("");
        String webhookId = payload.path("webhook_id").asText("");
        LOG.info("#### ==> SpaceId: {}\nEvent: {}\nTeamId: {}\nWebhookId: {}\nsecret: {}", spaceId, event, teamId, webhookId, webhookSecret);

        if("spaceCreated".equals(event)){
            triggerSpaceEvent(spaceId, teamId, webhookId);
        }else{
            LOG.info("### Event ignored (not spaceCreated): {}", event);
        }
//        JsonNode payload;
//        try {
//            payload = objectMapper.readTree(rawBody);
//            LOG.info("###  123 Raw webhook payload: {}", rawBody);
//            LOG.info("### Webhook payload parsed successfully ==> {}", payload.toString());
//        } catch (Exception e) {
//            LOG.warn("Failed to parse webhook payload: {}", e.getMessage());
//            Map<String, Object> errorBody = new HashMap<>();
//            errorBody.put("error", "Invalid JSON");
//            return ResponseEntity.<Map<String, Object>>badRequest().body(errorBody);
//        }

//        String event     = payload.path("event").asText("");
//        String listId    = payload.path("list_id").asText("");
////        String spaceId   = payload.path("space_id").asText("");
//        String teamId    = payload.path("team_id").asText("");
//        String webhookId = payload.path("webhook_id").asText("");
//        testTriggerViaApi(teamId);
//        String spaceId = payload.path("space_id").asText("");
//        if (spaceId.isBlank()) {
//            spaceId = payload.path("list").path("space").path("id").asText("");
//        }
//        if (spaceId.isBlank()) {
//            // some events put it directly under "space"
//            spaceId = payload.path("space").path("id").asText("");
//        }

//        LOG.info("###@@@ Payload details - spaceId: {}, teamId: {}, webhookId: {}", spaceId, teamId, webhookId);
//
//        LOG.info("### Received ClickUp event: {} | list: {}", event, listId);
//        LOG.info("### Payload details - spaceId: {}, teamId: {}, webhookId: {}", spaceId, teamId, webhookId);
//
//        if ("listCreated".equals(event)) {
////            LOG.info("### listCreated event received for space: {}", spaceId);testTriggerViaApi(teamId);
//            triggerSuperagentAsync(spaceId, teamId, webhookId);
//        } else {
//            LOG.info("### Event ignored (not listCreated): {}", event);
//        }

        //  Acknowledge ClickUp immediately (must respond within 3 seconds)
        Map<String, Object> ack = new HashMap<>();
        ack.put("received", true);
        return ResponseEntity.<Map<String, Object>>ok(ack);
    }

    private void triggerSpaceEvent(String spaceId, String teamId, String webhookId) {
        LOG.info("### Triggering spaceCreated event for spaceId: {}, teamId: {}, webhookId: {}", spaceId, teamId, webhookId);

        List<String> listNames = java.util.List.of(
                "Consultation & Onboarding",
                "ISO Implementation (4-Phase)",
                "Gap Analysis",
                "Internal Audit & Closing"
        );

        new Thread(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", superagentApiKey);

                // ── Step 1: Create folder ──────────────────────────────────────
                Map<String, Object> folderBody = new HashMap<>();
                folderBody.put("name", "Test Folder"); // ✅ name only

                HttpEntity<Map<String, Object>> folderRequest = new HttpEntity<>(folderBody, headers);
                ResponseEntity<Map> folderResponse = restTemplate.postForEntity(
                        "https://api.clickup.com/api/v2/space/" + spaceId + "/folder",
                        folderRequest, Map.class
                );
                LOG.info("### Folder Response body: {}", folderResponse.getBody());

                Map<?, ?> folderResponseBody = folderResponse.getBody();
                String folderId = null;
                String spaceName = null;

                if (folderResponseBody != null) {
                    if (folderResponseBody.get("id") != null) {
                        folderId = folderResponseBody.get("id").toString();
                    } else if (folderResponseBody.get("folder_id") != null) {
                        folderId = folderResponseBody.get("folder_id").toString();
                    } else if (folderResponseBody.get("data") != null) {
                        folderId = ((Map<?, ?>) folderResponseBody.get("data")).get("id").toString();
                    }
                }
                if (folderId == null) {
                    LOG.error("### Could not extract folderId — aborting");
                    return;
                }
                LOG.info("### Created folder ID: {}", folderId);

                if (folderResponseBody.get("space") instanceof Map<?, ?> spaceObj) {
                    spaceName = spaceObj.get("name") != null ? spaceObj.get("name").toString() : null;
                }

                LOG.info("### Folder ID: {}, Space Name: {}", folderId, spaceName);

                List<String> createdListIds = new ArrayList<>();
                for (String listName : listNames) {
                    Map<String, Object> listBody = new HashMap<>();
                    listBody.put("name", listName);

                    HttpEntity<Map<String, Object>> listRequest = new HttpEntity<>(listBody, headers);
                    ResponseEntity<Map> listResponse = restTemplate.postForEntity(
                            "https://api.clickup.com/api/v2/folder/" + folderId + "/list",
                            listRequest, Map.class
                    );

                    Map<?, ?> listResponseBody = listResponse.getBody();
                    if (listResponseBody != null && listResponseBody.get("id") != null) {
                        createdListIds.add(listResponseBody.get("id").toString());
                    }
                    LOG.info("### Created list [{}]", listName);
                }
                LOG.info("### All lists created: {}", createdListIds);

                // ── Step 2: Create DM channel ──────────────────────────────────
                Map<String, Object> dmChannelBody = new HashMap<>();
                dmChannelBody.put("member_ids", java.util.List.of(superagentUserId));

                HttpEntity<Map<String, Object>> dmChannelRequest = new HttpEntity<>(dmChannelBody, headers);
                ResponseEntity<Map> dmChannelResponse = restTemplate.postForEntity(
                        "https://api.clickup.com/api/v3/workspaces/" + teamId + "/chat/channels/direct_message",
                        dmChannelRequest, Map.class
                );
                LOG.info("### DM Channel Response body: {}", dmChannelResponse.getBody());

                String channelId = null;
                Map<?, ?> dmChannelResponseBody = dmChannelResponse.getBody();
                if (dmChannelResponseBody != null) {
                    if (dmChannelResponseBody.get("id") != null) {
                        channelId = dmChannelResponseBody.get("id").toString();
                    } else if (dmChannelResponseBody.get("channel_id") != null) {
                        channelId = dmChannelResponseBody.get("channel_id").toString();
                    } else if (dmChannelResponseBody.get("data") instanceof Map) {
                        channelId = ((Map<?, ?>) dmChannelResponseBody.get("data")).get("id").toString();
                    }
                }
                if (channelId == null) {
                    LOG.error("### Could not extract channelId — aborting");
                    return;
                }
                LOG.info("### DM channel ID: {}", channelId);

                // ── Step 3: Send message ───────────────────────────────────────
                if (dmChannelResponse.getStatusCode().is2xxSuccessful()) {
                    String message = "A new client space has been created in the WWISE PMO. " +
                            "Space ID: " + spaceId + ". " +
                            "Folder ID: " + folderId + ". " +
                            "Please scaffold the full 4-list structure for this client now: " +
                            "1) Consultation & Onboarding, 2) ISO Implementation (4-Phase), " +
                            "3) Gap Analysis, 4) Internal Audit & Closing. " +
                            "Use the standard WWISE PMO configuration for statuses, custom fields, and views.";

                    Map<String, Object> msgBody = new HashMap<>();
                    msgBody.put("content", message);
                    msgBody.put("content_format", "text/md");

                    HttpEntity<Map<String, Object>> msgRequest = new HttpEntity<>(msgBody, headers);
                    ResponseEntity<String> msgResponse = restTemplate.postForEntity(
                            "https://api.clickup.com/api/v3/workspaces/" + teamId + "/chat/channels/" + channelId + "/messages",
                            msgRequest, String.class
                    );

                    LOG.info("### Superagent DM sent: HTTP {}", msgResponse.getStatusCode());
                    LOG.info("### Superagent response body: {}", msgResponse.getBody());
                } else {
                    LOG.warn("### DM channel creation failed. HTTP Status: {}", dmChannelResponse.getStatusCode());
                }

            } catch (Exception e) {
                LOG.error("### triggerSpaceEvent failed: {}", e.getMessage(), e); // ✅ no RuntimeException
            }
        }).start();
    }

    private boolean isValidSignature(String rawBody, String receivedSignature) {
        LOG.info("### Verifying signature for incoming webhook event receivedSignature {}:",receivedSignature);
        if (receivedSignature == null || receivedSignature.isBlank()) {
            LOG.warn("### No x-signature header present");
            return false;
        }
        try {
            LOG.info("Test test test");
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);

            byte[] hash = mac.doFinal(rawBody.toString().getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);

            return computed.equals(receivedSignature);
        } catch (Exception e) {
            LOG.error("### Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private void testTriggerViaApi(String teamID){
        new Thread(() ->{
            LOG.info("### Test Trigger for teamID: {}",teamID);


            try{
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", superagentApiKey);

                Map<String, Object> dmBody = new HashMap<>();
                dmBody.put("member_ids", java.util.List.of(superagentUserId));

                HttpEntity<Map<String, Object>> dmRequest = new HttpEntity<>(dmBody, headers);

                ResponseEntity<Map> dmResponse = restTemplate.postForEntity(
                        "https://api.clickup.com/api/v2/team/"+teamID+"/space",
                        dmRequest,
                        Map.class
                );

                LOG.info("### 123 DM Response body: {}", dmResponse.getBody());

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
