package com.aether.organization.service.impl;
import com.aether.organization.entity.Invitation; import com.aether.organization.mapper.InvitationMapper; import com.aether.organization.service.InvitationService; import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl; import org.springframework.stereotype.Service;
@Service public class InvitationServiceImpl extends ServiceImpl<InvitationMapper, Invitation> implements InvitationService {}
