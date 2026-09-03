package com.aether.sys.service;

import com.aether.entity.Token;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import com.aether.sys.entity.User;
import com.aether.sys.service.impl.UserServiceImpl;
import com.aether.sys.vo.UserVo;
import com.aether.utils.AesUtil;
import com.aether.utils.TokenUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/** Verifies the server-side half of the access-token refresh flow. */
class UserRefreshTokenFlowTest {

    @Test
    void refreshRotatesTokenPairAndRejectsReplay() throws Exception {
        UserServiceImpl service = spy(new UserServiceImpl());
        TokenService tokenService = mock(TokenService.class);
        I18nService i18nService = mock(I18nService.class);
        ReflectionTestUtils.setField(service, "tokenService", tokenService);
        ReflectionTestUtils.setField(I18nUtils.class, "i18nService", i18nService);
        when(i18nService.getMessage("error.token.expired")).thenReturn("令牌已失效");

        User user = new User();
        user.setId("user-1");
        user.setType("ADMIN");
        user.setState(0);
        doReturn(user).when(service).getById("user-1");
        when(tokenService.update(any(Token.class), any())).thenReturn(true).thenReturn(false);

        Token original = TokenUtils.createToken(java.util.Collections.singletonMap("userId", "user-1"));
        UserVo refreshed = service.refreshToken(original.getRefreshToken());

        assertNotEquals(original.getToken(), refreshed.getToken());
        assertNotEquals(original.getRefreshToken(), refreshed.getRefreshToken());
        assertTrue(TokenUtils.hasTokenType(AesUtil.decrypt(refreshed.getToken()), TokenUtils.ACCESS_TOKEN_TYPE));
        assertTrue(TokenUtils.hasTokenType(AesUtil.decrypt(refreshed.getRefreshToken()), TokenUtils.REFRESH_TOKEN_TYPE));
        assertThrows(ServerException.class, () -> service.refreshToken(original.getRefreshToken()));
    }
}
