package com.example.demo;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * ✅ FileEncryptionUtil — يشفر/يفك تشفير بيانات الملفات الطبية (الأشعة والتحاليل)
 *
 * الخوارزمية المستخدمة: AES-256 CBC
 * — يتم تشفير كل ملف بـ IV عشوائي منفصل لضمان أعلى مستوى أمان
 * — الـ IV (16 بايت) يُخزن في أول 16 بايت من الملف المشفر
 * — بدون مفتاح التشفير، الملف غير قابل للقراءة حتى لو اخترق أحد السيرفر
 */
@Component
public class FileEncryptionUtil {

    // نفس المفتاح المستخدم في AttributeEncryptor لاتساق النظام (AES-256)
    private static final byte[] ENCRYPTION_KEY = "MaweedSuperSecretKeyForAES256Bit".getBytes();
    private static final String AES_ALGORITHM = "AES/CBC/PKCS5Padding";

    /**
     * تشفير بيانات الملف (byte array)
     * يُضاف الـ IV (16 بايت) في بداية الملف المشفر
     *
     * @param fileBytes بيانات الملف الأصلية
     * @return بيانات مشفرة = [IV (16 bytes)] + [Encrypted Data]
     */
    public byte[] encrypt(byte[] fileBytes) {
        try {
            // توليد IV عشوائي لكل ملف — يجعل كل تشفير مختلفاً حتى لو نفس الملف
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(ENCRYPTION_KEY, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            byte[] encryptedData = cipher.doFinal(fileBytes);

            // دمج الـ IV مع البيانات المشفرة: [IV][EncryptedData]
            byte[] result = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encryptedData, 0, result, iv.length, encryptedData.length);
            return result;

        } catch (Exception e) {
            throw new IllegalStateException("فشل في تشفير الملف الطبي", e);
        }
    }

    /**
     * فك تشفير بيانات الملف
     * يستخرج الـ IV من أول 16 بايت ثم يفك تشفير باقي البيانات
     *
     * @param encryptedBytes البيانات المشفرة = [IV (16 bytes)] + [Encrypted Data]
     * @return بيانات الملف الأصلية
     */
    public byte[] decrypt(byte[] encryptedBytes) {
        try {
            // استخراج الـ IV من أول 16 بايت
            byte[] iv = Arrays.copyOfRange(encryptedBytes, 0, 16);
            byte[] encryptedData = Arrays.copyOfRange(encryptedBytes, 16, encryptedBytes.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(ENCRYPTION_KEY, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            return cipher.doFinal(encryptedData);

        } catch (Exception e) {
            throw new IllegalStateException("فشل في فك تشفير الملف الطبي", e);
        }
    }
}
