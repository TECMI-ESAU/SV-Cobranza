package mx.tecmilenio.svcobranza.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import mx.tecmilenio.svcobranza.AppException;

/**
 * Cifrado de los respaldos con una contrasena (SEG-03). Formato del archivo:
 * marca "SVB1" + sal (16 bytes) + vector (12 bytes) + datos cifrados con AES-GCM.
 * La clave se obtiene de la contrasena, asi que el respaldo se puede abrir en otra computadora.
 */
final class CifradoRespaldo {

    private static final byte[] MARCA = "SVB1".getBytes(StandardCharsets.US_ASCII);
    private static final int SAL = 16;
    private static final int VECTOR = 12;
    private static final int ITERACIONES = 120_000;

    private CifradoRespaldo() {
    }

    static byte[] cifrar(byte[] datos, char[] contrasena) {
        try {
            SecureRandom azar = new SecureRandom();
            byte[] sal = new byte[SAL];
            byte[] vector = new byte[VECTOR];
            azar.nextBytes(sal);
            azar.nextBytes(vector);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, clave(contrasena, sal), new GCMParameterSpec(128, vector));
            byte[] cifrado = cipher.doFinal(datos);
            return ByteBuffer.allocate(MARCA.length + SAL + VECTOR + cifrado.length)
                    .put(MARCA).put(sal).put(vector).put(cifrado).array();
        } catch (GeneralSecurityException e) {
            throw new AppException("No se pudo cifrar el respaldo", e);
        }
    }

    static byte[] descifrar(byte[] archivo, char[] contrasena) {
        if (archivo.length < MARCA.length + SAL + VECTOR + 16
                || !Arrays.equals(Arrays.copyOf(archivo, MARCA.length), MARCA)) {
            throw new AppException("El archivo no es un respaldo de SV-Cobranza.");
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(archivo, MARCA.length, archivo.length - MARCA.length);
            byte[] sal = new byte[SAL];
            byte[] vector = new byte[VECTOR];
            buffer.get(sal);
            buffer.get(vector);
            byte[] cifrado = new byte[buffer.remaining()];
            buffer.get(cifrado);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, clave(contrasena, sal), new GCMParameterSpec(128, vector));
            return cipher.doFinal(cifrado);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new AppException("La contraseña es incorrecta o el respaldo está dañado.", e);
        } catch (GeneralSecurityException e) {
            throw new AppException("No se pudo abrir el respaldo", e);
        }
    }

    private static SecretKey clave(char[] contrasena, byte[] sal) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(contrasena, sal, ITERACIONES, 256);
        byte[] bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return new SecretKeySpec(bytes, "AES");
    }
}
