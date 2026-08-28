package com.aether.sys.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aether.auth.UserTokenVerifier;
import com.aether.sys.mapper.TokenMapper;
import com.aether.sys.service.TokenService;
import com.aether.entity.Token;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 令牌表 服务实现类
 * </p>
 *
 * @author sun
 * @since 2024-11-27
 */
@Service
public class TokenServiceImpl extends ServiceImpl<TokenMapper, Token> implements TokenService, UserTokenVerifier {

    @Override
    public boolean isActive(String userId, String encryptedAccessToken) {
        return count(Wrappers.<Token>lambdaQuery()
                .eq(Token::getUserId, userId)
                .eq(Token::getToken, encryptedAccessToken)
                .eq(Token::getState, 1)) > 0;
    }

}
