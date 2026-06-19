package com.example.iot.controller;

import com.example.iot.mqtt.TelemetryMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TelemetryController {

    private final InfluxDBClient influxClient;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${influx.bucket}")
    private String bucket;

    /** 最新值：优先读 Redis 缓存，未命中返回空 */
    @GetMapping("/latest/{deviceId}")
    public Object latest(@PathVariable String deviceId) throws Exception {
        String json = redis.opsForValue().get("iot:latest:" + deviceId);
        if (json == null) {
            return Collections.emptyMap();
        }
        return objectMapper.readValue(json, TelemetryMessage.class);
    }

    /**
     * 历史曲线
     * 用 Flux 的 aggregateWindow 做降采样
     * 注意：InfluxDB 2.x 不再支持 InfluxQL 的 GROUP BY time()
     */
    @GetMapping("/history")
    public Map<String, Object> history(@RequestParam String deviceId,
                                       @RequestParam(defaultValue = "1h") String range,
                                       @RequestParam(defaultValue = "1m") String window) {

        String flux = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: -%s) " +
                "|> filter(fn: (r) => r._measurement == \"sensor\" and r.deviceId == \"%s\") " +
                "|> filter(fn: (r) => r._field == \"temp\" or r._field == \"humi\" " +
                "                     or r._field == \"pm25\" or r._field == \"current\") " +
                "|> aggregateWindow(every: %s, fn: mean, createEmpty: false)",
                bucket, range, deviceId, window);

        Map<String, List<Map<String, Object>>> series = new LinkedHashMap<>();
        List<FluxTable> tables = influxClient.getQueryApi().query(flux, "iot");

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                String field = record.getField();
                series.computeIfAbsent(field, k -> new ArrayList<>())
                        .add(Map.of(
                                "time", record.getTime() == null ? "" : record.getTime().toString(),
                                "value", record.getValue() == null ? 0 : record.getValue()
                        ));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", deviceId);
        result.put("range", range);
        result.put("window", window);
        result.put("series", series);
        return result;
    }
}
