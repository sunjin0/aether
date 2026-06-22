package com.aether.sys.service;

import com.aether.sys.entity.Config;
import com.aether.sys.vo.ConfigVo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.ArrayList;

/**
 * <p>
 * 系统字典表 服务类
 * </p>
 *
 * @author sun
 * @since 2024-11-27
 */
public interface ConfigService extends IService<Config> {

    /**
     * 列表
     *
     * @param config config （字典）
     * @return {@link ArrayList }<{@link Config }>
     */
    Page<ConfigVo> list(ConfigVo config);

    /**
     * 信息
     *
     * @param id 身份证
     * @return {@link Config }
     */
    Config info(String id);
    /**
     * 删除
     *
     * @param id 身份证
     * @return boolean
     */
    boolean delete(String id);
    /**
     * 获取值
     *
     * @param code 法典
     * @return {@link String }
     */
    String getValue(String code);
}
