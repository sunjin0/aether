package com.aether.agent.mapper;

import com.aether.agent.entity.ModelCatalog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供模型Catalog映的数据访问能力。
 */
@Mapper
public interface ModelCatalogMapper extends BaseMapper<ModelCatalog> {
}
