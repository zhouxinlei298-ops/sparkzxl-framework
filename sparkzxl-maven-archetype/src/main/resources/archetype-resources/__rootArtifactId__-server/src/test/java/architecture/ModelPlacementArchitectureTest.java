#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * <p>
 * 模型落位守护
 * <p>固定各层模型的物理位置，防止新代码回到"DTO/VO 散落在 domain 层"的旧结构：
 * Controller 只在 interfaces，DTO/VO 只在 application/model，
 * MapStruct 转换器只在 application/convert，持久化实体与仓储实现只在 infrastructure。</p>
 *
 * @author zhouxinlei
 */
@AnalyzeClasses(packages = "${package}")
public class ModelPlacementArchitectureTest {

    @ArchTest
    public static final ArchRule controllers_should_reside_in_interfaces =
            classes().that().haveSimpleNameEndingWith("Controller")
                    .should().resideInAPackage("..system.interfaces..")
                    .because("interfaces 层只放 Controller");

    @ArchTest
    public static final ArchRule dtos_and_vos_should_reside_in_application_model =
            classes().that().haveSimpleNameEndingWith("DTO").or().haveSimpleNameEndingWith("VO")
                    .should().resideInAPackage("..system.application.model..")
                    .because("入参出参模型统一放在 application/model，不放 domain");

    @ArchTest
    public static final ArchRule converters_should_reside_in_application_convert =
            classes().that().haveSimpleNameEndingWith("Convert")
                    .should().resideInAPackage("..system.application.convert..")
                    .because("模型转换是应用层职责");

    @ArchTest
    public static final ArchRule repository_implementations_should_reside_in_infrastructure =
            classes().that().haveSimpleNameEndingWith("RepositoryImpl")
                    .should().resideInAPackage("..system.infrastructure.repository..")
                    .because("仓储实现属于 infrastructure 层，domain 只保留仓储接口");
}
