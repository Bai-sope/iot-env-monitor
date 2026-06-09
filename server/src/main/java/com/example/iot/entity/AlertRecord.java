package com.example.iot.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AlertRecord {

    private Long id;

    private String deviceId;

    private Long ruleId;

    private String metric;

    private BigDecimal metricValue;

    private BigDecimal threshold;

    private String message;

    /** 0=未处理 1=已处理 2=已忽略 */
    private Integer status;

    private LocalDateTime triggeredAt;

    private LocalDateTime handledAt;
}
