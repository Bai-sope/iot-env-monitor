package com.example.iot.mqtt;

import com.example.iot.service.DeviceService;
import com.example.iot.service.InfluxService;
import com.example.iot.service.AlertService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 上行遥测消息处理器
 *
 * 处理流程：
 *   1. 解析 JSON
 *   2. msgId 幂等去重（Redis SETNX，TTL 60s）
 *   3. 写 InfluxDB（时序点位）
 *   4. 更新 Redis 最新值缓存
 *   5. 检查告警规则
 *
 * 为什么要幂等：QoS 1 是「至少一次」，网络重传会产生重复消息。
 * 压测时发现 InfluxDB 里出现重复点位，加 Redis SETNX 后解决。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryHandler {

    private static final String IDEMPOTENT_KEY_PREFIX = "iot:msg:";
    private static final Duration IDEMPOTENT_TTL = Duration.ofSeconds(60);
    private static final String LATEST_KEY_PREFIX = "iot:latest:";

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final InfluxService influxService;
    private final DeviceService deviceService;
    private final AlertService alertService;

    public void handle(String productKey, String deviceId, String payload) {
        TelemetryMessage msg;
        try {
            msg = objectMapper.readValue(payload, TelemetryMessage.class);
        } catch (Exception e) {
            log.warn("invalid payload from {}: {}", deviceId, payload);
            return;
        }

        // 1. 幂等去重
        if (msg.getMsgId() != null && !msg.getMsgId().isEmpty()) {
            Boolean first = redis.opsForValue()
                    .setIfAbsent(IDEMPOTENT_KEY_PREFIX + msg.getMsgId(), "1", IDEMPOTENT_TTL);
            if (Boolean.FALSE.equals(first)) {
                log.debug("duplicate message ignored: {}", msg.getMsgId());
                return;
            }
        } else {
            log.warn("message without msgId from {}, skip idempotent check", deviceId);
        }

        // 2. 设备在线状态（收到上行即视为在线）
        deviceService.markOnline(productKey, deviceId);

        // 3. 写时序库
        influxService.write(deviceId, msg);

        // 4. 更新最新值缓存（供前端首屏快速加载）
        try {
            redis.opsForValue().set(LATEST_KEY_PREFIX + deviceId,
                    objectMapper.writeValueAsString(msg), Duration.ofMinutes(5));
        } catch (Exception e) {
            log.warn("cache latest value failed", e);
        }

        // 5. 告警检查
        alertService.check(deviceId, msg);

        log.debug("handled {} {} temp={} pm25={}", productKey, deviceId,
                msg.getTemp(), msg.getPm25());
    }
}
