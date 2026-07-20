package com.aether.utils;


import com.aether.i18n.I18nUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class AesUtil {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String DEFAULT_KEY = "1k_)(+*/@!abc.ef"; // 16位默认密钥
    private static final String DEFAULT_IV = "0123456789abcdef"; // 16位初始化向量

    /**
     * 加密
     *
     * @param content 内容
     * @return {@link String }
     */
    public static String encrypt(String content) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(DEFAULT_KEY.getBytes(), KEY_ALGORITHM);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(DEFAULT_IV.getBytes());
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
            byte[] encryptedBytes = cipher.doFinal(content.getBytes());
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException(I18nUtils.getMessage("aes.encrypt.fail"), e);
        }
    }

    /**
     * 解密
     *
     * @param content 内容
     * @return {@link String }
     */
    public static String decrypt(String content) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(DEFAULT_KEY.getBytes(), KEY_ALGORITHM);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(DEFAULT_IV.getBytes());
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
            byte[] encryptedBytes = Base64.getDecoder().decode(content);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException(I18nUtils.getMessage("aes.decrypt.fail"), e);
        }
    }
}
