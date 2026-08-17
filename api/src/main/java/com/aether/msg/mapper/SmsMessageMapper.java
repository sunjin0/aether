package com.aether.msg.mapper;

import com.aether.msg.entity.Sms;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 消息表 Mapper 接口
 * </p>
 *
 * @author sun
 * @since 2024-08-19
 */
@Mapper
public interface SmsMessageMapper extends BaseMapper<Sms> {

}
