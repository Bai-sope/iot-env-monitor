package com.example.iot.mapper;

import com.example.iot.entity.AlertRecord;
import com.example.iot.entity.AlertRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AlertMapper {

    List<AlertRule> findEnabledRules(@Param("deviceId") String deviceId);

    int insertRecord(AlertRecord record);

    List<AlertRecord> findRecent(@Param("deviceId") String deviceId,
                                 @Param("limit") int limit);

    int markHandled(@Param("id") Long id, @Param("status") int status);
}
