package com.aether.user.mapper;

import com.aether.user.entity.Member;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供Member映的数据访问能力。
 */
@Mapper
public interface MemberMapper extends BaseMapper<Member> {
}