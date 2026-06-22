package com.aether.sys.service;

import com.aether.entity.Option;
import com.aether.sys.entity.Dict;
import com.aether.sys.vo.DictVo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 系统字典表 服务类
 * </p>
 *
 * @author sun
 * @since 2024-11-27
 */
public interface DictService extends IService<Dict> {

    /**
     * 按代码获取字典
     *
     * @param code 法典
     * @param lng  液化天然气
     * @return {@link Dict }
     */
    Dict getByCode(String code, String lng);

    /**
     * 列表
     *
     * @param dict dict （字典）
     * @return {@link ArrayList }<{@link Dict }>
     */
    Page<DictVo> list(DictVo dict);

    /**
     * 信息
     *
     * @param id 身份证
     * @return {@link Dict }
     */
    Dict info(String id);

    /**
     * 删除
     *
     * @param id 身份证
     * @return boolean
     */
    boolean delete(String id);

    /**
     * 获取选项
     *
     * @param parentCode 父代码
     * @return {@link List }<{@link Option }>
     */
    List<Option> getOptions(String parentCode);

    /**
     * 选择
     *
     * @return {@link List }<{@link Dict }>
     */
    List<Dict> select();

    /**
     * 获取值
     *
     * @param code 法典
     * @return {@link String }
     */
    String getValue(String code);
}
