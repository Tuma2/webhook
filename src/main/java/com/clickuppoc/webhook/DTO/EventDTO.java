package com.clickuppoc.webhook.DTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.AllArgsConstructor;

@Data
public class EventDTO {
    private int code;
    private String message;
}
