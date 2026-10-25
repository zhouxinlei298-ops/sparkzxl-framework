#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

/**
 * <p>
 * DDD 分层依赖方向守护
 * <p>分层规范（借鉴 authorization-server 的 ArchUnit 实践）：
 * <pre>
 * bootstrap      -&gt; interfaces / application / domain / infrastructure
 * interfaces     -&gt; application
 * application    -&gt; domain / infrastructure
 * domain         -&gt; infrastructure（仅限仓储接口引用持久化实体的既有妥协）
 * infrastructure -&gt; domain / application（端口实现需要引用 application 的 port）
 * </pre>
 * 新增限界上下文时，请同步把包名加入对应 layer 的 definedBy。
 *
 * @author zhouxinlei
 */
@AnalyzeClasses(packages = "${package}")
public class DDDLayerArchitectureTest {

    @ArchTest
    public static final ArchRule ddd_layers_should_follow_dependencies = Architectures.layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Bootstrap").definedBy("..bootstrap..")
            .layer("Interfaces").definedBy("..system.interfaces..")
            .layer("Application").definedBy("..system.application..")
            .layer("Domain").definedBy("..system.domain..")
            .layer("Infrastructure").definedBy("..system.infrastructure..")
            .whereLayer("Bootstrap").mayOnlyAccessLayers("Interfaces", "Application", "Domain", "Infrastructure")
            .whereLayer("Interfaces").mayOnlyAccessLayers("Application")
            .whereLayer("Application").mayOnlyAccessLayers("Domain", "Infrastructure")
            .whereLayer("Domain").mayOnlyAccessLayers("Infrastructure")
            .whereLayer("Infrastructure").mayOnlyAccessLayers("Domain", "Application");
}
