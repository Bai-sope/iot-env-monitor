package com.example.iot.service;

import com.example.iot.entity.Device;
import com.example.iot.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceMapper deviceMapper;

    public List<Device> listAll() {
        return deviceMapper.findAll();
    }

    public Device get(String deviceId) {
        return deviceMapper.findByDeviceId(deviceId);
    }

    /**
     * 标记设备在线。
     * 收到上行消息就认为在线，这是最可靠的判据（比心跳更直接）。
     */
    public void markOnline(String productKey, String deviceId) {
        int rows = deviceMapper.updateOnline(productKey, deviceId, true);
        if (rows > 0) {
            log.info("device {} marked online", deviceId);
        }
    }

    public void markOffline(String productKey, String deviceId) {
        int rows = deviceMapper.updateOnline(productKey, deviceId, false);
        if (rows > 0) {
            log.warn("device {} marked offline", deviceId);
        }
    }

    public int countOnline() {
        return deviceMapper.countOnline();
    }
}
