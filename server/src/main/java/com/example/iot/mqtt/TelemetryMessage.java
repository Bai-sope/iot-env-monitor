package com.example.iot.mqtt;

import lombok.Data;

/**
 * 设备上行消息体
 * 与固件 mqtt_publish() 里的 JSON 结构一一对应
 */
@Data
public class TelemetryMessage {

    /** 消息唯一 ID，用于 QoS1 重复投递时的幂等去重 */
    private String msgId;

    /** 设备侧时间戳（秒），仅作参考，入库用服务端时间 */
    private Long ts;

    private Double temp;

    private Double humi;

    private Double pm25;

    private Double current;

    /** DHT22 本次是否读到有效值，false 时 temp/humi 是上一次的缓存值 */
    private Boolean dhtValid;
}
