/**
 * MQTT 连接管理
 *
 * 关键点：
 *   1. KeepAlive = 60s，Broker 在 1.5 倍超时后判离线并代发遗嘱
 *   2. 遗嘱消息注册在 status 主题，payload 为 {"online":false}
 *   3. 断线用指数退避重连（1s → 2s → 4s ... 上限 30s），避免网络抖动时疯狂重连
 *   4. msgId 用于服务端幂等去重（QoS1 会重复投递）
 */
#include "mqtt_client.h"
#include "config.h"
#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>

static WiFiClient espClient;
static PubSubClient mqtt(espClient);
static unsigned long reconnectDelay = MQTT_RECONNECT_MIN_MS;
static unsigned long lastReconnectAttempt = 0;

// 下行指令回调
static void (*downlinkHandler)(const char *payload) = nullptr;

static void onMessage(char *topic, byte *payload, unsigned int len) {
    if (strcmp(topic, TOPIC_DOWN) != 0) return;

    // payload 不保证以 \0 结尾，需要自己拼
    char buf[512];
    unsigned int n = len < sizeof(buf) - 1 ? len : sizeof(buf) - 1;
    memcpy(buf, payload, n);
    buf[n] = '\0';

    Serial.printf("[mqtt] downlink: %s\n", buf);
    if (downlinkHandler) downlinkHandler(buf);
}

static void connectWiFi() {
    if (WiFi.status() == WL_CONNECTED) return;

    Serial.printf("[wifi] connecting to %s", WIFI_SSID);
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    unsigned long start = millis();
    while (WiFi.status() != WL_CONNECTED && millis() - start < 20000) {
        delay(500);
        Serial.print(".");
    }

    if (WiFi.status() == WL_CONNECTED) {
        Serial.printf("\n[wifi] connected, ip=%s\n", WiFi.localIP().toString().c_str());
    } else {
        Serial.println("\n[wifi] timeout, will retry");
    }
}

static bool mqttConnect() {
    Serial.printf("[mqtt] connecting to %s:%d ... ", MQTT_HOST, MQTT_PORT);

    // 遗嘱消息：设备异常掉线时由 Broker 代发
    bool ok = mqtt.connect(
        DEVICE_ID,
        MQTT_USER,
        MQTT_PASSWORD,
        TOPIC_STATUS,      // will topic
        1,                 // will qos
        false,             // will retain
        "{\"online\":false,\"deviceId\":\"" DEVICE_ID "\"}"
    );

    if (ok) {
        Serial.println("ok");
        mqtt.subscribe(TOPIC_DOWN, 1);
        // 上线通知
        mqtt.publish(TOPIC_STATUS,
            "{\"online\":true,\"deviceId\":\"" DEVICE_ID "\"}", false);
        reconnectDelay = MQTT_RECONNECT_MIN_MS;   // 重连成功后重置退避
        return true;
    }

    Serial.printf("failed rc=%d\n", mqtt.state());
    return false;
}

void mqtt_init(void (*handler)(const char *)) {
    downlinkHandler = handler;
    mqtt.setServer(MQTT_HOST, MQTT_PORT);
    mqtt.setCallback(onMessage);
    mqtt.setKeepAlive(MQTT_KEEPALIVE);
    mqtt.setBufferSize(1024);   // 默认 256 不够，payload 会超
    connectWiFi();
}

void mqtt_loop() {
    if (WiFi.status() != WL_CONNECTED) {
        connectWiFi();
        return;
    }

    if (!mqtt.connected()) {
        unsigned long now = millis();
        if (now - lastReconnectAttempt >= reconnectDelay) {
            lastReconnectAttempt = now;
            if (!mqttConnect()) {
                // 指数退避
                reconnectDelay = reconnectDelay * 2;
                if (reconnectDelay > MQTT_RECONNECT_MAX_MS) {
                    reconnectDelay = MQTT_RECONNECT_MAX_MS;
                }
                Serial.printf("[mqtt] retry in %lu ms\n", reconnectDelay);
            }
        }
        return;
    }

    mqtt.loop();
}

bool mqtt_connected() {
    return mqtt.connected();
}

/**
 * 上报一次数据
 * payload 带 msgId，服务端用 Redis SETNX 做幂等去重
 */
bool mqtt_publish(const SensorData &d, const char *msgId) {
    if (!mqtt.connected()) return false;

    char payload[320];
    snprintf(payload, sizeof(payload),
        "{\"msgId\":\"%s\",\"ts\":%lu,"
        "\"temp\":%.2f,\"humi\":%.2f,\"pm25\":%.1f,\"current\":%.3f,"
        "\"dhtValid\":%s}",
        msgId,
        (unsigned long)(millis() / 1000),
        d.temperature, d.humidity, d.pm25, d.current,
        d.dhtValid ? "true" : "false"
    );

    // QoS 1，不 retain（时序数据不需要保留最后一条）
    return mqtt.publish(TOPIC_UP, payload, false);
}
