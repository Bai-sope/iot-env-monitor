package com.example.iot.controller;

import com.example.iot.entity.AlertRecord;
import com.example.iot.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alert")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AlertController {

    private final AlertService alertService;

    @GetMapping("/recent")
    public List<AlertRecord> recent(@RequestParam(required = false) String deviceId,
                                    @RequestParam(defaultValue = "50") int limit) {
        return alertService.recent(deviceId, limit);
    }

    @PostMapping("/handle/{id}")
    public int handle(@PathVariable Long id, @RequestParam(defaultValue = "1") int status) {
        return alertService.handle(id, status);
    }
}
