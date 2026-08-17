package com.aether.user.service.impl;

import com.aether.user.entity.Member;
import com.aether.user.mapper.MemberMapper;
import com.aether.user.service.MemberService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 实现Member业务服务。
 */
@Service
public class MemberServiceImpl extends ServiceImpl<MemberMapper, Member> implements MemberService {
}