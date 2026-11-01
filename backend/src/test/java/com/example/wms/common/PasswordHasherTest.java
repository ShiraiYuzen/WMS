package com.example.wms.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 口令散列单元测试。
 *
 * <p>覆盖三类断言：</p>
 * <ol>
 *   <li><b>正确性</b> —— 生成的口令能被自己校验通过，错口令不通过；</li>
 *   <li><b>安全属性</b> —— 同一口令两次散列必须不同（随机盐）、明文与 Base64 一律不准通过；</li>
 *   <li><b>边界</b> —— nullptr / 空串 / 损坏散列 / 历史 PBKDF2 格式都不能把异常抛到接口层。</li>
 * </ol>
 *
 * <p>其中 {@link #rejectsLegacyBase64()} 对应安全审计 SEC-02（"密码可逆存储 + 接口接受 Base64 密码"）
 * 的回归防线：修复后必须保证这条路永远走不通。</p>
 */
@DisplayName("PasswordHasher — 口令散列")
class PasswordHasherTest {

    private static final String RAW = "Wms@2026";

    // ------------------------------------------------------------------ 生成

    @Nested
    @DisplayName("hash() 生成")
    class Hash {

        @Test
        @DisplayName("产出的必须是 BCrypt 格式（$2a$10$…，60 字符）")
        void producesBcryptFormat() {
            String hashed = PasswordHasher.hash(RAW);

            assertThat(hashed).hasSize(60).startsWith("$2a$10$");
            assertThat(PasswordHasher.isBcrypt(hashed)).isTrue();
        }

        @Test
        @DisplayName("同一口令两次散列必须不同 —— 盐是随机的")
        void saltsAreRandom() {
            String first = PasswordHasher.hash(RAW);
            String second = PasswordHasher.hash(RAW);

            // 若相等，说明实现用了固定盐（或干脆没加盐），这是严重缺陷
            assertThat(first).isNotEqualTo(second);
            // 但两者都必须能校验通过
            assertThat(PasswordHasher.matches(RAW, first)).isTrue();
            assertThat(PasswordHasher.matches(RAW, second)).isTrue();
        }

        @Test
        @DisplayName("口令为 null 时抛 IllegalArgumentException")
        void rejectsNull() {
            assertThatThrownBy(() -> PasswordHasher.hash(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ------------------------------------------------------------------ 校验

    @Nested
    @DisplayName("matches() 校验")
    class Matches {

        private final String stored = PasswordHasher.hash(RAW);

        @Test
        @DisplayName("正确口令通过")
        void acceptsCorrectPassword() {
            assertThat(PasswordHasher.matches(RAW, stored)).isTrue();
        }

        @Test
        @DisplayName("错误口令不通过")
        void rejectsWrongPassword() {
            assertThat(PasswordHasher.matches("wms@2026", stored)).isFalse();  // 大小写不同
            assertThat(PasswordHasher.matches("Wms@2026 ", stored)).isFalse(); // 尾部多空格
            assertThat(PasswordHasher.matches("", stored)).isFalse();
        }

        @ParameterizedTest(name = "rawPassword = {0}")
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("口令为空 / 全空白 / null 时一律不通过，且不抛异常")
        void rejectsBlankRawPassword(String raw) {
            assertThat(PasswordHasher.matches(raw, stored)).isFalse();
        }

        @ParameterizedTest(name = "stored = {0}")
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("库中散列为空 / 全空白 / null 时一律不通过")
        void rejectsBlankStoredValue(String bad) {
            assertThat(PasswordHasher.matches(RAW, bad)).isFalse();
        }

        @Test
        @DisplayName("长度合法但内容损坏的 BCrypt 串按“不匹配”处理，不抛异常")
        void brokenBcryptDoesNotThrow() {
            // 60 字符、$2 开头，能骗过 isBcrypt()，但不是合法散列
            String corrupted = "$2a$10$" + "x".repeat(53);

            assertThat(PasswordHasher.matches(RAW, corrupted)).isFalse();
        }

        @Test
        @DisplayName("明文一律不准通过 —— 历史漏洞的直接回归")
        void rejectsPlaintext() {
            assertThat(PasswordHasher.matches(RAW, RAW)).isFalse();
            assertThat(PasswordHasher.matches("123456", "123456")).isFalse();
        }

        @Test
        @DisplayName("Base64 一律不准通过 —— SEC-02 回归防线")
        void rejectsLegacyBase64() {
            // "123456" 的 Base64；修复前把库里的值直接当密码提交能登录
            String base64 = Base64.getEncoder().encodeToString("123456".getBytes());

            assertThat(PasswordHasher.matches("123456", base64)).isFalse();
            assertThat(PasswordHasher.matches(base64, base64)).isFalse();
        }
    }

    // ------------------------------------------------------------------ 格式判断

    @Test
    @DisplayName("isBcrypt()：只认 60 字符且 $2 开头的串")
    void isBcryptOnlyAcceptsRealHashes() {
        assertThat(PasswordHasher.isBcrypt(PasswordHasher.hash(RAW))).isTrue();

        assertThat(PasswordHasher.isBcrypt(null)).isFalse();
        assertThat(PasswordHasher.isBcrypt("")).isFalse();
        assertThat(PasswordHasher.isBcrypt("123456")).isFalse();
        assertThat(PasswordHasher.isBcrypt("pbkdf2$210000$salt$hash")).isFalse();
        // 长度不对但前缀对
        assertThat(PasswordHasher.isBcrypt("$2a$10$short")).isFalse();
    }

    // ------------------------------------------------------------------ 升级判断

    @Test
    @DisplayName("needsUpgrade()：非 BCrypt 一律需要升级，当前强度不需要")
    void needsUpgrade() {
        assertThat(PasswordHasher.needsUpgrade(null)).isTrue();
        assertThat(PasswordHasher.needsUpgrade("123456")).isTrue();
        assertThat(PasswordHasher.needsUpgrade("pbkdf2$210000$salt$hash")).isTrue();
        assertThat(PasswordHasher.needsUpgrade(PasswordHasher.hash(RAW))).isFalse();
    }

    // ------------------------------------------------------------------ 历史口令还原

    @Nested
    @DisplayName("decodeLegacy() 历史口令还原")
    class DecodeLegacy {

        @Test
        @DisplayName("能被还原成原始明文")
        void decodesBase64() {
            String base64 = Base64.getEncoder().encodeToString("123456".getBytes());

            assertThat(PasswordHasher.decodeLegacy(base64)).isEqualTo("123456");
        }

        @Test
        @DisplayName("本身就是明文时原样返回")
        void returnsPlaintextAsIs() {
            assertThat(PasswordHasher.decodeLegacy("123456")).isEqualTo("123456");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("空值返回 null")
        void returnsNullForBlank(String blank) {
            assertThat(PasswordHasher.decodeLegacy(blank)).isNull();
        }

        @Test
        @DisplayName("已经是散列的口令不做还原")
        void ignoresHashedValues() {
            assertThat(PasswordHasher.decodeLegacy(PasswordHasher.hash(RAW))).isNull();
            assertThat(PasswordHasher.decodeLegacy("pbkdf2$210000$c2FsdA$aGFzaA")).isNull();
        }
    }
}
