package com.aether.msg.service;

import com.aether.exception.ServerException;
import com.aether.msg.entity.Email;
import com.aether.msg.vo.EmailVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * 电子邮件服务
 *
 * @author sun
 * @date 2024/09/11
 */
public interface EmailMessageService extends IService<Email> {
    /**
     * 按 ID 获取
     *
     * @param id 身份证
     * @return {@link Email }
     * @throws ServerException 服务器异常
     */
    @ApiOperation("根据消息id获取消息")
    @ApiImplicitParam(name = "id", value = "消息id", required = true)
    Email getById(Long id) throws ServerException;


    /**
     * 按用户 ID 获取
     *
     * @return {@link Email }
     * @throws ServerException 服务器异常
     */
    @ApiOperation("根据用户id获取消息")
    @ApiImplicitParam(name = "userId", value = "用户id", required = true)
    Email getByUserId(String userId) throws ServerException;


    /**
     * 列表
     *
     * @param message 消息
     * @return {@link List }<{@link Email }>
     * @throws ServerException 服务器异常
     */
    @ApiOperation("获取消息列表")
    @ApiImplicitParam(name = "message", value = "消息对象", required = true)
    Page<Email> list(EmailVo message) throws ServerException;

    /**
     * 发送
     *
     * @param message 消息
     * @return {@link Boolean }
     * @throws ServerException 服务器异常
     */
    @ApiOperation("发送消息")
    @ApiImplicitParam(name = "message", value = "消息对象", required = true)
    Boolean send(Email message) throws ServerException;

    /**
     * 异步发送
     *
     * @param message 消息
     * @throws ServerException 服务器异常
     */
    @Async
    @ApiOperation("异步发送消息")
    @ApiImplicitParam(name = "message", value = "消息对象", required = true)
    void AsyncSend(Email message) throws ServerException;

    /**
     * 更新
     *
     * @param message 消息
     * @return {@link Boolean }
     * @throws ServerException 服务器异常
     */
    @ApiOperation("更新消息")
    @ApiImplicitParam(name = "message", value = "消息对象", required = true)
    Boolean update(Email message) throws ServerException;
}
