package com.example.iot.controller;

import com.example.iot.entity.Device;
import com.example.iot.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping("/list")
    public List<Device> list() {
        return deviceService.listAll();
    }

    @GetMapping("/{deviceId}")
    public Device detail(@PathVariable String deviceId) {
        return deviceService.get(deviceId);
    }

    @GetMapping("/stat")
    public Map<String, Object> stat() {
        Map<String, Object> m = new HashMap<>();
        m.put("total", deviceService.listAll().size());
        m.put("online", deviceService.countOnline());
        return m;
    }
}
