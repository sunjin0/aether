package com.aether.msg.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aether.msg.entity.Sms;
import com.aether.msg.vo.SmsVo;
import com.aether.exception.ServerException;
import com.aether.msg.mapper.SmsMessageMapper;
import com.aether.msg.service.SmsMessageService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 短信消息发送记录 服务实现类
 * </p>
 *
 * @author sun
 * @since 2024-09-11
 */
@Service
public class SmsMessageServiceImpl extends ServiceImpl<SmsMessageMapper, Sms> implements SmsMessageService {

    @Override
    public Boolean send(Sms message) throws ServerException {
        return null;
    }

    @Override
    public Sms getById(String id) throws ServerException {
        return super.getById(id);
    }

    @Override
    public Page<Sms> list(SmsVo message) throws ServerException {
        return super.page(new Page<>(message.getCurrent(), message.getPageSize()),
                Wrappers.lambdaQuery(Sms.class)
                        .eq(StringUtils.isNotBlank(message.getId()), Sms::getId, message.getId())
                        .eq(StringUtils.isNotBlank(message.getPhone()), Sms::getPhone, message.getPhone())
                        .eq(message.getType() != null, Sms::getType, message.getType())
                        .eq(message.getState() != null, Sms::getState, message.getState())
                        .eq(message.getUserId() != null, Sms::getUserId, message.getUserId())
                        .orderByDesc(Sms::getCreatedAt));
    }

    @Override
    public Boolean delete(String id) throws ServerException {
        return removeById(id);
    }

    @Override
    public boolean save(Sms message) throws ServerException {
        String id = message.getId();
        if (StringUtils.isNotBlank(id)) {
            return updateById(message);
        } else {
            return super.save(message);
        }
    }

    @Override
    public Sms getByUserId(String userId) throws ServerException {
        return getOne(Wrappers.lambdaQuery(Sms.class)
                .eq(Sms::getUserId, userId));
    }
}
