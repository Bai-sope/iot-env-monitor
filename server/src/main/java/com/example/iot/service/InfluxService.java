package com.example.iot.service;

import com.example.iot.mqtt.TelemetryMessage;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * InfluxDB 时序数据写入
 *
 * measurement: sensor
 * tags:        deviceId（基数低，适合做 tag）
 * fields:      temp / humi / pm25 / current（数值，做 field）
 *
 * 注意：不要把高基数字段（如 msgId）写成 tag，会导致 series 爆炸。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InfluxService {

    private final InfluxDBClient influxClient;

    public void write(String deviceId, TelemetryMessage msg) {
        Point point = Point.measurement("sensor")
                .addTag("deviceId", deviceId)
                .addField("temp", msg.getTemp() == null ? 0.0 : msg.getTemp())
                .addField("humi", msg.getHumi() == null ? 0.0 : msg.getHumi())
                .addField("pm25", msg.getPm25() == null ? 0.0 : msg.getPm25())
                .addField("current", msg.getCurrent() == null ? 0.0 : msg.getCurrent())
                .time(Instant.now(), WritePrecision.MS);

        try {
            WriteApiBlocking writeApi = influxClient.getWriteApiBlocking();
            writeApi.writePoint(point);
        } catch (Exception e) {
            // 写时序库失败不影响主流程，打日志后继续
            log.error("write influx failed, device={}", deviceId, e);
        }
    }
}
