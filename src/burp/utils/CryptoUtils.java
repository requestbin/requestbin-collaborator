package burp.utils;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import com.github.shamil.Xid;

/**
 * Crypto utilities for RequestBin
 * Based on crypto functions from requestbin.saas libs
 */
public class CryptoUtils {
    
    /**
     * Generate registration parameters for server registration
     * Based on generateRegistrationParams from requestbin.saas
     */
    public static RegistrationParams generateRegistrationParams(String userId) {
        try {
            // Generate correlation ID using Xid (same as interactsh)
            String correlationId = Xid.get().toString();
            
            // Generate secret key
            String secretKey = UUID.randomUUID().toString();
            
            // Generate RSA key pair
            KeyPair keyPair = generateRSAKeyPair();
            
            String publicKey = encodePublicKey(keyPair.getPublic());
            String privateKey = encodePrivateKey(keyPair.getPrivate());
            
            return new RegistrationParams(correlationId, secretKey, publicKey, privateKey);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate registration parameters: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate RSA key pair for encryption
     */
    public static KeyPair generateRSAKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }
    
    /**
     * Encode public key to PEM format string
     */
    public static String encodePublicKey(PublicKey publicKey) {
        String pubKey = "-----BEGIN PUBLIC KEY-----\n";
        String[] chunks = splitStringEveryN(Base64.getEncoder().encodeToString(publicKey.getEncoded()), 64);
        for (String chunk : chunks) {
            pubKey += chunk + "\n";
        }
        pubKey += "-----END PUBLIC KEY-----\n";
        return pubKey;
    }
    
    /**
     * Encode private key to PEM format string
     */
    public static String encodePrivateKey(PrivateKey privateKey) {
        String privKey = "-----BEGIN PRIVATE KEY-----\n";
        String[] chunks = splitStringEveryN(Base64.getEncoder().encodeToString(privateKey.getEncoded()), 64);
        for (String chunk : chunks) {
            privKey += chunk + "\n";
        }
        privKey += "-----END PRIVATE KEY-----\n";
        return privKey;
    }
    
    /**
     * Decrypt AES key using RSA private key
     * Based on decryptAESKey from requestbin.saas
     */
    public static String decryptAESKey(String encryptedAESKey, PrivateKey privateKey) throws Exception {
        byte[] cipherTextArray = Base64.getDecoder().decode(encryptedAESKey);
        
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        OAEPParameterSpec oaepParams = new OAEPParameterSpec("SHA-256", "MGF1",
                new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT);
        cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams);
        byte[] decrypted = cipher.doFinal(cipherTextArray);
        
        return new String(decrypted, StandardCharsets.UTF_8);
    }
    
    /**
     * Decrypt interaction data using AES key
     * Based on processData from requestbin.saas
     */
    public static String decryptInteractionData(String encryptedData, String aesKey) throws Exception {
        byte[] cipherTextArray = Base64.getDecoder().decode(encryptedData);
        
        byte[] iv = Arrays.copyOfRange(cipherTextArray, 0, 16);
        byte[] cipherText = Arrays.copyOfRange(cipherTextArray, 16, cipherTextArray.length - 1);
        
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        SecretKeySpec skeySpec = new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES");
        
        Cipher cipher = Cipher.getInstance("AES/CFB/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec);
        byte[] decrypted = cipher.doFinal(cipherText);
        
        return new String(decrypted, StandardCharsets.UTF_8);
    }
    
    /**
     * Generate unique ID for bins
     */
    public static String generateUniqueId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 13);
    }
    
    /**
     * Split string into chunks of specified length
     */
    private static String[] splitStringEveryN(String s, int interval) {
        int arrayLength = (int) Math.ceil(((s.length() / (double) interval)));
        String[] result = new String[arrayLength];

        int j = 0;
        int lastIndex = result.length - 1;
        for (int i = 0; i < lastIndex; i++) {
            result[i] = s.substring(j, j + interval);
            j += interval;
        }
        result[lastIndex] = s.substring(j);

        return result;
    }
    
    /**
     * Registration parameters container
     */
    public static class RegistrationParams {
        private final String correlationId;
        private final String secretKey;
        private final String publicKey;
        private final String privateKey;
        
        public RegistrationParams(String correlationId, String secretKey, String publicKey, String privateKey) {
            this.correlationId = correlationId;
            this.secretKey = secretKey;
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }
        
        public String getCorrelationId() { return correlationId; }
        public String getSecretKey() { return secretKey; }
        public String getPublicKey() { return publicKey; }
        public String getPrivateKey() { return privateKey; }
    }
}