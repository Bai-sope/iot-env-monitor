package com.example.iot.mqtt;

import com.example.iot.service.DeviceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 设备状态消息处理器（遗嘱消息）
 *
 * 设备连上时发 {"online":true}
 * 设备异常掉线时，Broker 在 1.5 × KeepAlive 后自动代发 {"online":false}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceStatusHandler {

    private final ObjectMapper objectMapper;
    private final DeviceService deviceService;

    public void handle(String productKey, String deviceId, String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            boolean online = node.path("online").asBoolean(false);

            if (online) {
                deviceService.markOnline(productKey, deviceId);
                log.info("device online: {}", deviceId);
            } else {
                deviceService.markOffline(productKey, deviceId);
                log.warn("device offline (LWT): {}", deviceId);
            }
        } catch (Exception e) {
            log.warn("invalid status payload from {}: {}", deviceId, payload);
        }
    }
}
