package com.example.iot.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Device {

    private Long id;

    private String productKey;

    private String deviceId;

    private String deviceName;

    private String location;

    private Boolean online;

    private LocalDateTime lastOnlineAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
