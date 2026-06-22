package com.aether.msg.service.impl;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aether.sys.service.DictService;
import com.aether.msg.entity.Email;
import com.aether.msg.vo.EmailVo;
import com.aether.sys.entity.Dict;
import com.aether.enums.State;
import com.aether.exception.ServerException;
import com.aether.msg.mapper.EmailMessageMapper;
import com.aether.msg.service.EmailMessageService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 邮箱消息发送记录 服务实现类
 * </p>
 *
 * @author sun
 * @since 2024-09-11
 */
@Service
public class EmailMessageServiceImpl extends ServiceImpl<EmailMessageMapper, Email> implements EmailMessageService {
    @Autowired
    private JavaMailSender javaEmailService;

    @Autowired
    private DictService dictService;

    @Override
    public Email getById(Long id) throws ServerException {
        return super.getById(id);
    }

    @Override
    public Email getByUserId(String userId) throws ServerException {
        this.list(Wrappers.lambdaQuery(Email.class)
                .eq(Email::getUserId, userId)
                .eq(Email::getState, State.Success.getCode())
                .orderByDesc(Email::getCreatedAt));
        return null;
    }

    @Override
    public Page<Email> list(EmailVo message) throws ServerException {
        return super.page(new Page<>(message.getCurrent(), message.getPageSize()),
                Wrappers.lambdaQuery(Email.class)
                        .eq(StringUtils.isNotBlank(message.getId()), Email::getId, message.getId())
                        .like(StringUtils.isNotBlank(message.getEmail()), Email::getEmail, message.getEmail())
                        .like(StringUtils.isNotBlank(message.getSubject()), Email::getSubject, message.getSubject())
                        .like(StringUtils.isNotBlank(message.getBody()), Email::getBody, message.getBody())
                        .eq(message.getType() != null, Email::getType, message.getType())
                        .eq(message.getState() != null, Email::getState, message.getState())
                        .eq(message.getUserId() != null, Email::getUserId, message.getUserId())
                        .orderByDesc(Email::getCreatedAt));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean send(Email message) throws ServerException {
        boolean save = this.save(message);
        String captcha = dictService.getValue("Captcha");
        if (!Boolean.parseBoolean(captcha)) {
            Dict email_from = dictService.getByCode("Message_Email_From", null);
            // 发送邮件
            SimpleMailMessage messages = new SimpleMailMessage();
            messages.setFrom(email_from.getVal());
            messages.setTo(message.getEmail());
            messages.setSubject(message.getSubject());
            messages.setText(message.getBody());
            // 保存到数据库
            if (save) {
                javaEmailService.send(messages);
                return true;
            }
            return false;
        }
        return true;
    }

    @Async
    @Override
    public void AsyncSend(Email message) throws ServerException {
        send(message);
    }

    @Override
    public Boolean update(Email message) throws ServerException {
        return this.updateById(message);
    }
}
