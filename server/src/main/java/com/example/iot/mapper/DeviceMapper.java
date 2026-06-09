package com.example.iot.mapper;

import com.example.iot.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceMapper {

    List<Device> findAll();

    Device findByDeviceId(@Param("deviceId") String deviceId);

    int insert(Device device);

    int updateOnline(@Param("productKey") String productKey,
                     @Param("deviceId") String deviceId,
                     @Param("online") boolean online);

    int countOnline();
}
