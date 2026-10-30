package com.example.wms.common;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 口令散列工具（SEC-02）：BCrypt 为主，兼容校验历史的 PBKDF2。
 *
 * <p>存储形态与演进：</p>
 * <table border="1">
 *   <caption>口令存储格式</caption>
 *   <tr><th>格式</th><th>示例</th><th>状态</th></tr>
 *   <tr><td>BCrypt（当前标准）</td><td>{@code $2a$10$N9qo8uLO...}</td><td>新口令一律用它</td></tr>
 *   <tr><td>PBKDF2（v1 过渡版）</td><td>{@code pbkdf2$210000$salt$hash}</td><td>只校验，登录成功后自动升级为 BCrypt</td></tr>
 *   <tr><td>明文 / Base64（历史遗留）</td><td>{@code MTIzNDU2}</td><td>登录不接受，仅由启动任务一次性迁移</td></tr>
 * </table>
 *
 * <p>为什么用 BCrypt 而不是继续用 PBKDF2：BCrypt 自带盐、内存访问模式抗 GPU/ASIC，
 * 且 {@code spring-security-crypto} 只依赖一个 40KB 的 jar，不引入整个 Security 框架。</p>
 */
public final class PasswordHasher {

    /** 强度 10 ≈ 每次校验 50~100ms，够挡离线爆破又不拖慢登录 */
    private static final int BCRYPT_STRENGTH = 10;

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

    /* ---- 以下是历史 PBKDF2 的校验参数，只用于兼容老数据 ---- */
    private static final String PBKDF2_PREFIX = "pbkdf2";
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_KEY_BITS = 256;
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private PasswordHasher() {
    }

    // ------------------------------------------------------------------ 生成

    /** 生成 BCrypt 散列（自带随机盐，同一口令每次结果都不同） */
    public static String hash(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("口令不能为空");
        }
        return BCRYPT.encode(rawPassword);
    }

    // ------------------------------------------------------------------ 校验

    /**
     * 校验口令。BCrypt 与历史 PBKDF2 都能通过，明文/Base64 一律不通过。
     *
     * <p>BCrypt 内部用定长比较，PBKDF2 分支用 {@link MessageDigest#isEqual}，均不按字节短路。</p>
     */
    public static boolean matches(String rawPassword, String stored) {
        if (rawPassword == null || stored == null || stored.isBlank()) {
            return false;
        }
        if (isBcrypt(stored)) {
            try {
                return BCRYPT.matches(rawPassword, stored);
            } catch (IllegalArgumentException e) {
                return false; // 格式损坏的散列按"不匹配"处理，不抛到接口层
            }
        }
        if (stored.startsWith(PBKDF2_PREFIX + "$")) {
            return matchesPbkdf2(rawPassword, stored);
        }
        return false;
    }

    /** 是否是 BCrypt 散列（$2a$ / $2b$ / $2y$ 开头，cost 两位） */
    public static boolean isBcrypt(String stored) {
        return stored != null && stored.length() == 60 && stored.charAt(0) == '$' && stored.charAt(1) == '2';
    }

    /**
     * 是否需要重新散列。
     *
     * <p>登录成功后调用：老 PBKDF2 数据在这里被无感升级成 BCrypt，用户不用改密。</p>
     */
    public static boolean needsUpgrade(String stored) {
        if (!isBcrypt(stored)) {
            return true;
        }
        try {
            // cost 被调低（例如从 12 降到 10）也视为需要升级
            return BCRYPT.upgradeEncoding(stored);
        } catch (Exception e) {
            return true;
        }
    }

    // ------------------------------------------------------------------ 历史口令还原

    /**
     * 历史遗留口令还原：老库里的 password 是明文或明文做了一次 Base64。
     *
     * <p>仅用于启动时一次性迁移，登录流程不接受这两种形式。</p>
     */
    public static String decodeLegacy(String stored) {
        if (stored == null || stored.isBlank()) return null;
        if (isBcrypt(stored) || stored.startsWith(PBKDF2_PREFIX + "$")) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(stored);
            String text = new String(decoded, StandardCharsets.UTF_8);
            // 只有解出来全是可打印字符才认定"这是 Base64 过的明文"
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < 0x20 || c > 0x7e) return stored;
            }
            return text;
        } catch (IllegalArgumentException e) {
            return stored; // 不是合法 Base64，那就是明文
        }
    }

    // ------------------------------------------------------------------ PBKDF2 兼容校验

    private static boolean matchesPbkdf2(String rawPassword, String stored) {
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 4) return false;
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = B64D.decode(parts[2]);
            byte[] expected = B64D.decode(parts[3]);
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, PBKDF2_KEY_BITS);
            byte[] actual = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }
}
