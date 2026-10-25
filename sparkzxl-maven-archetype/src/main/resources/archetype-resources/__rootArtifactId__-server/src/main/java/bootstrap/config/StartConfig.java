#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.bootstrap.config;

import com.github.sparkzxl.log.event.HttpRequestLogListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * description: 启动配置
 * <p>横切的技术性 Bean（日志监听、拦截器装配等）统一放在 bootstrap/config；
 * 业务 @Configuration 只允许出现在各上下文的 infrastructure/config 下。</p>
 *
 * @author zhouxinlei
 */
@Slf4j
@Configuration
public class StartConfig {

    @Bean
    public HttpRequestLogListener httpRequestLogListener() {
        return new HttpRequestLogListener(requestInfoLog ->
                log.info("请求接口名【{}】,请求IP【{}】,请求接口：【{}】请求耗时：【{}】,请求入参【{}】",
                        requestInfoLog.getCategory(),
                        requestInfoLog.getIp(),
                        requestInfoLog.getRequestUrl(),
                        requestInfoLog.getConsumingTime(),
                        requestInfoLog.getRequestParams()));
    }
}
