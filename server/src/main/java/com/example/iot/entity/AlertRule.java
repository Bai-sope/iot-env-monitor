package com.example.iot.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AlertRule {

    private Long id;

    private String deviceId;

    /** temp / humi / pm25 / current */
    private String metric;

    /** gt / lt / ge / le */
    private String operator;

    private BigDecimal threshold;

    /** 持续多少秒才触发，0 表示立即 */
    private Integer durationSec;

    private Boolean enabled;

    private LocalDateTime createdAt;
}
