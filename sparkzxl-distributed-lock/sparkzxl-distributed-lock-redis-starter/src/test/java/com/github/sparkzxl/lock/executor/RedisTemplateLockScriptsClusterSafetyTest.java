package com.github.sparkzxl.lock.executor;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * description: 分布式锁 Lua 脚本 Cluster 静态校验回归测试
 * <p>
 * 阿里云集群版 Proxy 对 Lua 做静态校验：键访问必须是 KEYS[字面量]，变量/表达式下标
 * 会被整体拒绝；脚本内禁止 pub/sub、UNPACK、跨节点命令。本地单机实例无法暴露该类
 * 拒绝，靠本测试正则锁死 {@link RedisTemplateLockExecutor} 两个脚本的形状。
 *
 * @author zhouxinlei
 * @since 2026-10-26
 */
class RedisTemplateLockScriptsClusterSafetyTest {

    private static final Pattern KEYS_INDEX = Pattern.compile("KEYS\\[([^\\]]*)\\]");

    @Test
    void allKeysSubscriptsAreLiteral() throws Exception {
        String lockScript = scriptText("SCRIPT_LOCK");
        String unlockScript = scriptText("SCRIPT_UNLOCK");

        assertThat(lockScript).contains("KEYS[1]");
        assertThat(unlockScript).contains("KEYS[1]");
        assertAllKeysLiteral(lockScript, "SCRIPT_LOCK");
        assertAllKeysLiteral(unlockScript, "SCRIPT_UNLOCK");
    }

    @Test
    void unlockGuardsMissingKeyBeforeComparison() throws Exception {
        String unlockScript = scriptText("SCRIPT_UNLOCK");

        // GET 对不存在的键返回 false，缺键路径必须先显式拦截，禁止与字符串裸比较后依赖隐式语义。
        assertThat(unlockScript)
                .contains("redis.call('get',KEYS[1])")
                .contains("==false")
                .contains("~=ARGV[1]");
        assertThat(unlockScript).doesNotContain("redis.call('get',KEYS[1])==ARGV[1]");
    }

    @Test
    void scriptsAvoidProxyForbiddenCommands() throws Exception {
        String lockScript = scriptText("SCRIPT_LOCK");
        String unlockScript = scriptText("SCRIPT_UNLOCK");

        String[] forbidden = {"publish", "subscribe", "psubscribe", "unsubscribe",
                "unpack", "multi", "exec", "'keys'", "'scan'", "randomkey"};
        for (String command : forbidden) {
            assertThat(lockScript).as("SCRIPT_LOCK 含禁止命令 " + command).doesNotContain(command);
            assertThat(unlockScript).as("SCRIPT_UNLOCK 含禁止命令 " + command).doesNotContain(command);
        }
    }

    @Test
    void scriptResultTypesFollowConvention() throws Exception {
        // 成功/失败用 Long 1/0，取值用 String；与 iam 侧脚本返回值约定一致。
        RedisScript<?> unlockScript = (RedisScript<?>) scriptField("SCRIPT_UNLOCK").get(null);
        assertThat(unlockScript.getResultType()).isEqualTo(Long.class);
        RedisScript<?> lockScript = (RedisScript<?>) scriptField("SCRIPT_LOCK").get(null);
        assertThat(lockScript.getResultType()).isEqualTo(String.class);
    }

    private static void assertAllKeysLiteral(String script, String name) {
        Matcher matcher = KEYS_INDEX.matcher(script);
        StringBuilder nonLiteral = new StringBuilder();
        while (matcher.find()) {
            if (!matcher.group(1).matches("\\d+")) {
                nonLiteral.append("KEYS[").append(matcher.group(1)).append("] ");
            }
        }
        assertThat(nonLiteral.length())
                .as(name + " 存在会被代理静态校验拒绝的非字面量下标: " + nonLiteral)
                .isZero();
    }

    private static String scriptText(String fieldName) throws Exception {
        RedisScript<?> script = (RedisScript<?>) scriptField(fieldName).get(null);
        assertThat(script).isInstanceOf(DefaultRedisScript.class);
        return ((DefaultRedisScript<?>) script).getScriptAsString();
    }

    private static Field scriptField(String fieldName) throws Exception {
        Field field = RedisTemplateLockExecutor.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }
}
