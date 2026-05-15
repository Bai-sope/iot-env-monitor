/**
 * 引脚与参数配置
 * 修改 WiFi / MQTT 配置后重新烧录
 */
#ifndef CONFIG_H
#define CONFIG_H

// ===== WiFi =====
#define WIFI_SSID       "your-wifi-ssid"
#define WIFI_PASSWORD   "your-wifi-password"

// ===== MQTT =====
#define MQTT_HOST       "192.168.1.100"
#define MQTT_PORT       1883
#define MQTT_USER       "device"
#define MQTT_PASSWORD   "device123"

#define PRODUCT_KEY     "pk1"
#define DEVICE_ID       "esp32-node-001"

// 主题定义：iot/{productKey}/{deviceId}/{up|down|status}
#define TOPIC_UP        "iot/" PRODUCT_KEY "/" DEVICE_ID "/up"
#define TOPIC_DOWN      "iot/" PRODUCT_KEY "/" DEVICE_ID "/down"
#define TOPIC_STATUS    "iot/" PRODUCT_KEY "/" DEVICE_ID "/status"

// ===== 引脚定义 =====
#define PIN_DHT22       4       // DHT22 数据引脚
#define PIN_PM25_ADC    34      // GP-2Y PM2.5 模拟输出（ADC1_CH6）
#define PIN_CT_ADC      35      // SCT-013 电流互感器（ADC1_CH7）
#define PIN_LED         2       // 板载 LED，用于状态指示

// ===== 采样参数 =====
#define SAMPLE_INTERVAL_MS      10000   // 上报间隔 10s
#define DHT_INTERVAL_MS         2500    // DHT22 最小间隔 2s，留余量
#define FILTER_WINDOW           5       // 滑动平均窗口

// ===== MQTT 参数 =====
#define MQTT_KEEPALIVE          60      // 秒，Broker 1.5 倍后判离线
#define MQTT_RECONNECT_MIN_MS   1000    // 重连退避起点
#define MQTT_RECONNECT_MAX_MS   30000   // 重连退避上限

// ===== ADC 校准 =====
#define ADC_VREF            3.3f
#define ADC_MAX             4095.0f
#define PM25_VOLTAGE_OFFSET 0.6f    // GP-2Y 干净空气输出电压
#define PM25_SLOPE          1000.0f // 电压到浓度的换算系数
#define CT_RATIO            100.0f  // SCT-013-000 变比 100A/50mA

#endif
