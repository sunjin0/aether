package com.aether.msg.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aether.msg.entity.Sms;
import com.aether.msg.vo.SmsVo;
import com.aether.exception.ServerException;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;

/**
 * <p>
 * 消息表 服务类
 * </p>
 *
 * @author sun
 * @since 2024-08-19
 */
public interface SmsMessageService extends IService<Sms> {

    /**
     * 发送
     *
     * @param message 消息
     * @return {@link Boolean }
     * @throws ServerException 服务器异常
     */
    @ApiOperation("发送短信")
    @ApiImplicitParams(
            {
                    @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
            }
    )
    Boolean send(Sms message) throws ServerException;

    /**
     * 按 ID 获取
     *
      * @param id ID
     * @return {@link Sms }
     * @throws ServerException 服务器异常
     */
    @ApiOperation("获取短信记录")
    @ApiImplicitParams(
            {
                    @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
            }
    )
    Sms getById(String id) throws ServerException;

    /**
     * 列表
     *
     * @param message 消息
     * @return {@link Page }<{@link Sms }>
     * @throws ServerException 服务器异常
     */
    @ApiOperation("获取短信记录列表")
    @ApiImplicitParams(
            {
                    @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
            }
    )
    Page<Sms> list(SmsVo message) throws ServerException;

    /**
     * 删除
     *
      * @param id ID
     * @return {@link Boolean }
     * @throws ServerException 服务器异常
     */
    @ApiOperation("删除短信记录")
    @ApiImplicitParams(
            {
                    @ApiImplicitParam(name = "id", required = true),
                    @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
            }
    )
    Boolean delete(String id) throws ServerException;

    /**
     * 救
     *
     * @param message 消息
     * @return boolean
     * @throws ServerException 服务器异常
     */
    @ApiOperation("修改短信记录")
    @ApiImplicitParams(
            {
                    @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
            }
    )
    boolean save(Sms message) throws ServerException;

    /**
     * 按用户 ID 获取
     *
     * @param userId 用户 ID
     * @return {@link Sms }
     * @throws ServerException 服务器异常
     */
    @ApiOperation("根据用户Id获取信息")
    @ApiImplicitParams(
            {
                    @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header"),
                    @ApiImplicitParam(name = "userId", required = true)
            }
    )
    Sms getByUserId(String userId) throws ServerException;

}
