-- ============================================================
-- IoT 环境监测平台 —— 数据库初始化脚本
-- MySQL 8.0
-- ============================================================

CREATE DATABASE IF NOT EXISTS iot_monitor
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE iot_monitor;

-- ------------------------------------------------------------
-- 设备表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    product_key   VARCHAR(64)  NOT NULL COMMENT '产品标识',
    device_id     VARCHAR(64)  NOT NULL COMMENT '设备标识',
    device_name   VARCHAR(128) DEFAULT NULL COMMENT '设备名称',
    location      VARCHAR(256) DEFAULT NULL COMMENT '安装位置',
    online        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否在线',
    last_online_at DATETIME    DEFAULT NULL COMMENT '最后上线时间',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_device (product_key, device_id),
    KEY idx_online (online)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备表';

-- ------------------------------------------------------------
-- 告警规则表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alert_rule (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    device_id     VARCHAR(64)  NOT NULL COMMENT '设备标识',
    metric        VARCHAR(32)  NOT NULL COMMENT '指标名: temp/humi/pm25/current',
    operator      VARCHAR(8)   NOT NULL COMMENT '比较符: gt/lt/ge/le',
    threshold     DECIMAL(10,3) NOT NULL COMMENT '阈值',
    duration_sec  INT          NOT NULL DEFAULT 0 COMMENT '持续秒数(0=立即触发)',
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则';

-- ------------------------------------------------------------
-- 告警记录表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alert_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    device_id     VARCHAR(64)  NOT NULL,
    rule_id       BIGINT       DEFAULT NULL,
    metric        VARCHAR(32)  NOT NULL,
    metric_value  DECIMAL(10,3) NOT NULL COMMENT '触发时的实际值',
    threshold     DECIMAL(10,3) NOT NULL,
    message       VARCHAR(512) DEFAULT NULL,
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0=未处理 1=已处理 2=已忽略',
    triggered_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handled_at    DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_device_time (device_id, triggered_at),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警记录';

-- ------------------------------------------------------------
-- 初始化测试数据
-- ------------------------------------------------------------
INSERT INTO device (product_key, device_id, device_name, location, online)
VALUES ('pk1', 'esp32-node-001', '车间一号监测点', 'A 栋 1 层东侧', 0)
ON DUPLICATE KEY UPDATE device_name = VALUES(device_name);

INSERT INTO alert_rule (device_id, metric, operator, threshold, duration_sec)
VALUES
    ('esp32-node-001', 'temp', 'gt', 35.000, 300),
    ('esp32-node-001', 'pm25', 'gt', 150.000, 60),
    ('esp32-node-001', 'humi', 'gt', 85.000, 300)
ON DUPLICATE KEY UPDATE threshold = VALUES(threshold);
