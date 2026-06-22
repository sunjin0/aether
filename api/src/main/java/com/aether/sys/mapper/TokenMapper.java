package com.aether.sys.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import  com.aether.entity.Token;
import org.apache.ibatis.annotations.Mapper;
/**
 * <p>
 * 令牌表 Mapper 接口
 * </p>
 *
 * @author sun
 * @since 2024-11-27
 */
@Mapper
public interface TokenMapper extends BaseMapper<Token> {

}
