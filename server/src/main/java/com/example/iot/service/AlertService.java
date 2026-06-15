package com.example.iot.service;

import com.example.iot.entity.AlertRecord;
import com.example.iot.entity.AlertRule;
import com.example.iot.mapper.AlertMapper;
import com.example.iot.mqtt.TelemetryMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 告警检查
 *
 * 支持两种触发方式：
 *   1. durationSec = 0 —— 立即触发
 *   2. durationSec > 0 —— 需要持续超限 N 秒才触发（用 Redis 记录首次超限时间）
 *
 * 防抖：同一条规则在 5 分钟内只触发一次，避免刷屏。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private static final String DURATION_KEY_PREFIX = "iot:alert:first:";
    private static final String DEBOUNCE_KEY_PREFIX = "iot:alert:debounce:";
    private static final Duration DEBOUNCE_WINDOW = Duration.ofMinutes(5);

    private final AlertMapper alertMapper;
    private final StringRedisTemplate redis;

    public void check(String deviceId, TelemetryMessage msg) {
        List<AlertRule> rules = alertMapper.findEnabledRules(deviceId);
        if (rules.isEmpty()) return;

        Map<String, Double> metrics = Map.of(
                "temp", msg.getTemp() == null ? 0.0 : msg.getTemp(),
                "humi", msg.getHumi() == null ? 0.0 : msg.getHumi(),
                "pm25", msg.getPm25() == null ? 0.0 : msg.getPm25(),
                "current", msg.getCurrent() == null ? 0.0 : msg.getCurrent()
        );

        for (AlertRule rule : rules) {
            Double value = metrics.get(rule.getMetric());
            if (value == null) continue;

            boolean exceeded = compare(value, rule.getOperator(), rule.getThreshold().doubleValue());
            String durationKey = DURATION_KEY_PREFIX + rule.getId() + ":" + deviceId;

            if (!exceeded) {
                redis.delete(durationKey);
                continue;
            }

            // 需要持续时间的规则：记录首次超限时间，未满时长不触发
            if (rule.getDurationSec() != null && rule.getDurationSec() > 0) {
                long now = System.currentTimeMillis();
                Boolean first = redis.opsForValue()
                        .setIfAbsent(durationKey, String.valueOf(now), Duration.ofHours(1));
                if (Boolean.TRUE.equals(first)) {
                    log.debug("rule {} started exceeding, waiting {}s",
                            rule.getId(), rule.getDurationSec());
                    continue;
                }
                long firstAt = Long.parseLong(redis.opsForValue().get(durationKey));
                if ((now - firstAt) / 1000 < rule.getDurationSec()) {
                    continue;
                }
            }

            // 防抖
            String debounceKey = DEBOUNCE_KEY_PREFIX + rule.getId() + ":" + deviceId;
            Boolean fresh = redis.opsForValue().setIfAbsent(debounceKey, "1", DEBOUNCE_WINDOW);
            if (Boolean.FALSE.equals(fresh)) {
                continue;
            }

            trigger(deviceId, rule, value);
        }
    }

    private void trigger(String deviceId, AlertRule rule, double value) {
        AlertRecord record = new AlertRecord();
        record.setDeviceId(deviceId);
        record.setRuleId(rule.getId());
        record.setMetric(rule.getMetric());
        record.setMetricValue(BigDecimal.valueOf(value));
        record.setThreshold(rule.getThreshold());
        record.setMessage(String.format("%s %s %s，当前值 %.2f",
                rule.getMetric(), operatorText(rule.getOperator()),
                rule.getThreshold().toPlainString(), value));

        alertMapper.insertRecord(record);
        log.warn("ALERT [{}] {} -> {}", deviceId, record.getMessage(), rule.getThreshold());
    }

    private boolean compare(double value, String op, double threshold) {
        switch (op) {
            case "gt": return value > threshold;
            case "ge": return value >= threshold;
            case "lt": return value < threshold;
            case "le": return value <= threshold;
            default: return false;
        }
    }

    private String operatorText(String op) {
        switch (op) {
            case "gt": return ">";
            case "ge": return ">=";
            case "lt": return "<";
            case "le": return "<=";
            default: return op;
        }
    }

    public List<AlertRecord> recent(String deviceId, int limit) {
        return alertMapper.findRecent(deviceId, limit);
    }

    public int handle(Long id, int status) {
        return alertMapper.markHandled(id, status);
    }
}
