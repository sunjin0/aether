package com.aether.sys.mapper;

import com.aether.sys.entity.AdminPreferenceEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供管理员偏好事件映的数据访问能力。
 */
@Mapper
public interface AdminPreferenceEventMapper extends BaseMapper<AdminPreferenceEvent> {
}
