#ifndef SENSOR_H
#define SENSOR_H

#include <Arduino.h>

struct SensorData {
    float temperature;   // 摄氏度
    float humidity;      // %RH
    float pm25;          // ug/m3
    float current;       // 安培
    bool  dhtValid;      // DHT 本次是否读到有效值
};

void sensor_init();
bool sensor_readAll(SensorData *out);

#endif
