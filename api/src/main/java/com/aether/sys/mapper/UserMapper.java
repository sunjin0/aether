package com.aether.sys.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import  com.aether.sys.entity.User;
import org.apache.ibatis.annotations.Mapper;
/**
 * <p>
 * 系统用户表 Mapper 接口
 * </p>
 *
 * @author sun
 * @since 2024-09-10
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}
