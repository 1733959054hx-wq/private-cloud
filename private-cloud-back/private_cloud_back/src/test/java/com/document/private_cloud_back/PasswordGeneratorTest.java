package com.document.private_cloud_back;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordGeneratorTest {

    @Test
    void generatePasswordHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "123456";
        String hash = encoder.encode(rawPassword);
        System.out.println("========================================");
        System.out.println("明文密码: " + rawPassword);
        System.out.println("BCrypt哈希: " + hash);
        System.out.println("验证结果: " + encoder.matches(rawPassword, hash));
        System.out.println("========================================");
    }

    @Test
    void verifyExistingHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String existingHash = "$2a$10$BrB80CeidzvI29IWfvnI.uHVxnEUKE8YVjDfXSz38SZATpqOHf50m";
        String[] testPasswords = {"123456", "hx123456", "password", "hx", "admin123"};
        System.out.println("========================================");
        System.out.println("验证已有哈希: " + existingHash);
        for (String pwd : testPasswords) {
            boolean match = encoder.matches(pwd, existingHash);
            System.out.println("  密码=" + pwd + " => " + match);
        }
        System.out.println("========================================");
    }

}
