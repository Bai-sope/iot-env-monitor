/**
 * IoT 环境监测节点 —— 主程序
 *
 * FreeRTOS 双任务：
 *   vSampleTask —— 每 10s 采一次数据，放入队列
 *   vReportTask —— 从队列取数据，通过 MQTT 上报
 *
 * 为什么拆两个任务：采样可能因为 DHT22 的 2s 间隔而阻塞，
 * 不能让它拖慢 MQTT 的心跳（否则会被 Broker 判定离线）。
 */
#include <Arduino.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#include <freertos/queue.h>

#include "config.h"
#include "sensor.h"
#include "mqtt_client.h"

static QueueHandle_t dataQueue;
static unsigned long msgCounter = 0;

// 下行指令处理
static void onDownlink(const char *payload) {
    // 目前只支持 {"cmd":"reboot"} 和 {"cmd":"setInterval","value":5000}
    if (strstr(payload, "\"reboot\"")) {
        Serial.println("[cmd] rebooting in 1s...");
        delay(1000);
        ESP.restart();
    } else if (strstr(payload, "setInterval")) {
        Serial.println("[cmd] interval change requested (not implemented)");
    } else {
        Serial.printf("[cmd] unknown: %s\n", payload);
    }
}

/**
 * 采样任务：固定间隔读传感器，结果入队
 */
static void vSampleTask(void *pv) {
    SensorData d;
    TickType_t lastWake = xTaskGetTickCount();

    for (;;) {
        sensor_readAll(&d);

        Serial.printf("[sample] T=%.2f H=%.2f PM=%.1f I=%.3f\n",
                      d.temperature, d.humidity, d.pm25, d.current);

        // 队列满就丢弃最旧的，避免内存堆积
        if (xQueueSend(dataQueue, &d, 0) != pdTRUE) {
            SensorData discard;
            xQueueReceive(dataQueue, &discard, 0);
            xQueueSend(dataQueue, &d, 0);
            Serial.println("[sample] queue full, dropped oldest");
        }

        vTaskDelayUntil(&lastWake, pdMS_TO_TICKS(SAMPLE_INTERVAL_MS));
    }
}

/**
 * 上报任务：从队列取数据发 MQTT
 */
static void vReportTask(void *pv) {
    SensorData d;

    for (;;) {
        if (xQueueReceive(dataQueue, &d, portMAX_DELAY) == pdTRUE) {
            if (mqtt_connected()) {
                char msgId[32];
                snprintf(msgId, sizeof(msgId), "%s-%lu",
                         DEVICE_ID, msgCounter++);
                if (mqtt_publish(d, msgId)) {
                    digitalWrite(PIN_LED, HIGH);
                    vTaskDelay(pdMS_TO_TICKS(50));
                    digitalWrite(PIN_LED, LOW);
                } else {
                    Serial.println("[report] publish failed");
                }
            } else {
                Serial.println("[report] mqtt offline, skip");
            }
        }
    }
}

void setup() {
    Serial.begin(115200);
    delay(500);
    Serial.println("\n===== IoT Env Monitor =====");
    Serial.printf("device: %s, product: %s\n", DEVICE_ID, PRODUCT_KEY);

    pinMode(PIN_LED, OUTPUT);
    digitalWrite(PIN_LED, LOW);

    sensor_init();
    mqtt_init(onDownlink);

    dataQueue = xQueueCreate(10, sizeof(SensorData));
    if (dataQueue == nullptr) {
        Serial.println("[fatal] queue create failed");
        ESP.restart();
    }

    // 采样任务放核心 1，上报任务放核心 0，避免抢占
    xTaskCreatePinnedToCore(vSampleTask, "sample", 4096, nullptr, 2, nullptr, 1);
    xTaskCreatePinnedToCore(vReportTask, "report", 4096, nullptr, 3, nullptr, 0);

    Serial.println("[boot] tasks started");
}

void loop() {
    // MQTT 心跳和重连都在这里驱动
    mqtt_loop();
    vTaskDelay(pdMS_TO_TICKS(100));
}
