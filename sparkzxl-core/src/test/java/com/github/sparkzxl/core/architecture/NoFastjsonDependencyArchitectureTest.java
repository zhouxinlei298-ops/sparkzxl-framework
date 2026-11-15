package com.github.sparkzxl.core.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * fastjson 回流守卫（057）。
 *
 * <p>sparkzxl 第一方代码禁止依赖 com.alibaba.fastjson：fastjson 终态定位是「仅供 JustAuth 1.16.7 的
 * 被隔离传递依赖 + BOM 钉版」，JSON 一律走 Jackson（{@code JsonUtils.getJson()} 默认 JacksonImpl）。</p>
 *
 * <p>过渡豁免（M3 后移除）：{@code com.github.sparkzxl.core.json.impl.fastjson} 整包待删，
 * 删除完成前先豁免该包内部；扫描范围为本模块 classpath 上的 {@code com.github.sparkzxl..}，
 * gateway-plugin / log / feign / sms 等兄弟模块不在本守卫覆盖面（各自模块无测试基础设施，
 * 以 057 §7 全仓 grep 作为验收门）。</p>
 *
 * @author zhouxinlei
 * @since 2026-11-15
 */
@AnalyzeClasses(packages = "com.github.sparkzxl", importOptions = ImportOption.DoNotIncludeTests.class)
public class NoFastjsonDependencyArchitectureTest {

    @ArchTest
    static final ArchRule productionCodeMustNotDependOnFastjson =
            noClasses().that().resideInAPackage("com.github.sparkzxl..")
                    .and().resideOutsideOfPackage("com.github.sparkzxl.core.json.impl.fastjson..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.alibaba.fastjson..")
                    .because("fastjson 仅允许经 JustAuth 传递依赖存在（057 依赖隔离）；"
                            + "json/impl/fastjson 整包随 057 M3 删除，删除后移除本豁免");
}
