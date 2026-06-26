package com.filemanager.storage.service;

import cn.hutool.core.util.HexUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

@Slf4j
@Service
public class AesEncryptionService {

    private static final String AES = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int KEY_LENGTH = 32; // 256 bits

    /**
     * 生成随机 AES-256 密钥，返回 Hex 编码字符串
     */
    public String generateKey() {
        byte[] keyBytes = new byte[KEY_LENGTH];
        new SecureRandom().nextBytes(keyBytes);
        return HexUtil.encodeHexStr(keyBytes);
    }

    /**
     * 加密数据（AES-256-GCM），返回 IV + 密文拼接
     */
    public byte[] encrypt(byte[] data, String keyHex) {
        try {
            SecretKey secretKey = new SecretKeySpec(HexUtil.decodeHex(keyHex), "AES");
            Cipher cipher = Cipher.getInstance(AES);

            // 随机 IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(data);

            // 拼接: IV(12字节) + 密文
            byte[] result = new byte[GCM_IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, result, 0, GCM_IV_LENGTH);
            System.arraycopy(encrypted, 0, result, GCM_IV_LENGTH, encrypted.length);
            return result;
        } catch (Exception e) {
            log.error("AES加密失败", e);
            throw new RuntimeException("AES加密失败", e);
        }
    }

    /**
     * 解密数据，输入为 IV + 密文拼接
     */
    public byte[] decrypt(byte[] data, String keyHex) {
        try {
            SecretKey secretKey = new SecretKeySpec(HexUtil.decodeHex(keyHex), "AES");
            Cipher cipher = Cipher.getInstance(AES);

            // 提取 IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(data, 0, iv, 0, GCM_IV_LENGTH);

            // 提取密文
            byte[] ciphertext = new byte[data.length - GCM_IV_LENGTH];
            System.arraycopy(data, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            log.error("AES解密失败", e);
            throw new RuntimeException("AES解密失败", e);
        }
    }

    /**
     * 用主密钥加密文件AES密钥后存储到数据库
     */
    public String encryptFileKey(String fileAesKeyHex, String masterKeyHex) {
        return HexUtil.encodeHexStr(encrypt(fileAesKeyHex.getBytes(StandardCharsets.UTF_8), masterKeyHex));
    }

    /**
     * 解密存储的文件AES密钥
     */
    public String decryptFileKey(String encryptedFileKeyHex, String masterKeyHex) {
        return new String(decrypt(HexUtil.decodeHex(encryptedFileKeyHex), masterKeyHex), StandardCharsets.UTF_8);
    }
}
