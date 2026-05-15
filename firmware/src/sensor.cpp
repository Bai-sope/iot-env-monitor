/**
 * 传感器读取与数据处理
 *
 * 三个传感器：
 *   DHT22   —— 温湿度，数字接口，读取间隔 >= 2s
 *   GP-2Y   —— PM2.5，模拟输出，需要 ADC 采样 + 电压换算
 *   SCT-013 —— 电流互感器，模拟输出，需要算 RMS
 *
 * 数据经过滑动平均滤波后再上报，抑制现场噪声。
 */
#include "sensor.h"
#include "config.h"
#include <Arduino.h>
#include <DHT.h>
#include <math.h>

static DHT dht(PIN_DHT22, DHT22);

// 滑动平均的环形缓冲
struct RingBuffer {
    float buf[FILTER_WINDOW];
    uint8_t idx = 0;
    uint8_t count = 0;

    void push(float v) {
        buf[idx] = v;
        idx = (idx + 1) % FILTER_WINDOW;
        if (count < FILTER_WINDOW) count++;
    }

    float avg() const {
        if (count == 0) return NAN;
        float sum = 0;
        for (uint8_t i = 0; i < count; i++) sum += buf[i];
        return sum / count;
    }
};

static RingBuffer tempBuf, humiBuf, pmBuf, currBuf;
static unsigned long lastDhtRead = 0;

void sensor_init() {
    dht.begin();
    analogReadResolution(12);
    analogSetAttenuation(ADC_11db);   // 量程 0~3.3V
    Serial.println("[sensor] init done");
}

/**
 * 读 DHT22，带间隔保护。
 * DHT22 采样过快会返回 NaN 或跳变到极值，必须 >= 2s。
 */
static bool readDht(float *temp, float *humi) {
    unsigned long now = millis();
    if (now - lastDhtRead < DHT_INTERVAL_MS) {
        return false;
    }
    lastDhtRead = now;

    float t = dht.readTemperature();
    float h = dht.readHumidity();

    // DHT22 异常值：NaN、-40、80、65535 之类
    if (isnan(t) || isnan(h)) return false;
    if (t < -40.0f || t > 80.0f) return false;
    if (h < 0.0f || h > 100.0f) return false;

    tempBuf.push(t);
    humiBuf.push(h);
    *temp = tempBuf.avg();
    *humi = humiBuf.avg();
    return true;
}

/**
 * 读 ADC 并转电压。多次采样取平均，降低 ADC 噪声。
 */
static float readAdcVoltage(uint8_t pin, uint8_t samples = 16) {
    uint32_t sum = 0;
    for (uint8_t i = 0; i < samples; i++) {
        sum += analogRead(pin);
        delayMicroseconds(200);
    }
    float raw = (float)sum / samples;
    return raw / ADC_MAX * ADC_VREF;
}

/**
 * GP-2Y PM2.5 传感器
 * 输出 0.6V 对应干净空气，浓度与电压近似线性
 */
static float readPm25() {
    float v = readAdcVoltage(PIN_PM25_ADC);
    float dust = (v - PM25_VOLTAGE_OFFSET) * PM25_SLOPE;
    if (dust < 0) dust = 0;
    pmBuf.push(dust);
    return pmBuf.avg();
}

/**
 * SCT-013 电流互感器
 * 输出是交流波形，需要采样一个周期算 RMS
 * 采样 200 个点覆盖 50Hz 的 4 个周期（20ms/周期）
 */
static float readCurrent() {
    const int N = 200;
    float sumSq = 0;
    int offset = 2048;   // 1.65V 中点对应的 ADC 值

    for (int i = 0; i < N; i++) {
        int raw = analogRead(PIN_CT_ADC);
        float centered = (float)(raw - offset) / ADC_MAX * ADC_VREF;
        sumSq += centered * centered;
        delayMicroseconds(100);   // 100us * 200 = 20ms，覆盖一个完整周期
    }

    float rms = sqrt(sumSq / N);
    // 互感器输出经 33Ω 采样电阻，再乘变比
    float current = rms * CT_RATIO * 10.0f;
    if (current < 0.05f) current = 0;   // 死区，避免零点漂移读数

    currBuf.push(current);
    return currBuf.avg();
}

bool sensor_readAll(SensorData *out) {
    float t, h;
    bool dhtOk = readDht(&t, &h);

    if (dhtOk) {
        out->temperature = t;
        out->humidity = h;
    } else {
        // 保留上一次的值，标记无效
        out->temperature = tempBuf.avg();
        out->humidity = humiBuf.avg();
    }

    out->pm25 = readPm25();
    out->current = readCurrent();
    out->dhtValid = dhtOk;
    return true;
}
