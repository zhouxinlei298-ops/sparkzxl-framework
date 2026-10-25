#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * description: 应用启动类
 * <p>装配层（bootstrap）只存放启动类、全局装配配置与横切 advice，
 * 业务代码按限界上下文组织在 ${package} 下的各上下文包中。</p>
 *
 * @author zhouxinlei
 */
@SpringBootApplication(scanBasePackages = "${package}")
@EnableFeignClients(basePackages = "${package}")
public class StartApplication {

    public static void main(String[] args) {
        SpringApplication.run(StartApplication.class, args);
    }

}
