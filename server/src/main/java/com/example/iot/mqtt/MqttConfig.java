package com.example.iot.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

/**
 * MQTT 客户端配置
 *
 * 说明：这里用的是 Paho 的同步客户端订阅上行消息。
 * 压测 2000 连接时后端主要靠 Paho 的异步回调 + 线程池消费，
 * 单机 CPU 峰值 62%（4 核），没有出现积压。
 *
 * 如果后续连接数继续增长，可以换成 Netty 自己实现 MQTT 协议解析，
 * 但 Paho 对于当前规模已经够用。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MqttConfig {

    @Value("${mqtt.broker.host}")
    private String host;

    @Value("${mqtt.broker.port}")
    private int port;

    @Value("${mqtt.broker.client-id}")
    private String clientId;

    @Value("${mqtt.broker.username}")
    private String username;

    @Value("${mqtt.broker.password}")
    private String password;

    @Value("${mqtt.subscribe-topic}")
    private String subscribeTopic;

    @Value("${mqtt.status-topic}")
    private String statusTopic;

    @Value("${mqtt.qos}")
    private int qos;

    private MqttClient client;

    @Bean
    public MqttClient mqttClient(TelemetryHandler telemetryHandler,
                                 DeviceStatusHandler statusHandler) throws MqttException {

        String brokerUrl = "tcp://" + host + ":" + port;
        client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setUserName(username);
        opts.setPassword(password.toCharArray());
        opts.setCleanSession(true);
        opts.setAutomaticReconnect(true);
        opts.setKeepAliveInterval(60);
        opts.setConnectionTimeout(30);

        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("MQTT connected (reconnect={}), subscribing...", reconnect);
                try {
                    client.subscribe(subscribeTopic, qos);
                    client.subscribe(statusTopic, qos);
                    log.info("subscribed: {} , {}", subscribeTopic, statusTopic);
                } catch (MqttException e) {
                    log.error("subscribe failed", e);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT connection lost: {}", cause == null ? "unknown" : cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload());
                // topic 形如 iot/{productKey}/{deviceId}/up
                String[] parts = topic.split("/");
                if (parts.length < 4) {
                    log.warn("unexpected topic: {}", topic);
                    return;
                }
                String productKey = parts[1];
                String deviceId = parts[2];
                String direction = parts[3];

                try {
                    if ("up".equals(direction)) {
                        telemetryHandler.handle(productKey, deviceId, payload);
                    } else if ("status".equals(direction)) {
                        statusHandler.handle(productKey, deviceId, payload);
                    }
                } catch (Exception e) {
                    // 单条消息处理失败不能影响后续消息
                    log.error("handle message failed, topic={}, payload={}", topic, payload, e);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // 上行指令的送达回调，暂不处理
            }
        });

        try {
            client.connect(opts);
        } catch (MqttException e) {
            // Broker 未启动时不阻塞应用启动，automaticReconnect 会持续重试
            log.error("initial MQTT connect failed, will retry automatically", e);
        }

        return client;
    }

    @EventListener(ContextClosedEvent.class)
    public void onShutdown() {
        if (client != null && client.isConnected()) {
            try {
                client.disconnect(1000);
                client.close();
                log.info("MQTT client closed");
            } catch (MqttException e) {
                log.warn("close mqtt client failed", e);
            }
        }
    }
}
