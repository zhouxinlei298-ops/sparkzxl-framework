#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.generator;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.util.NumberUtil;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.IFill;
import com.baomidou.mybatisplus.generator.config.*;
import com.baomidou.mybatisplus.generator.config.builder.CustomFile;
import com.baomidou.mybatisplus.generator.config.po.TableInfo;
import com.baomidou.mybatisplus.generator.config.rules.DateType;
import com.baomidou.mybatisplus.generator.config.rules.DbColumnType;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import com.baomidou.mybatisplus.generator.fill.Property;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.type.JdbcType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * description: 代码生成
 * <p>在项目根目录运行 main 方法（user.dir 需为工程根目录），
 * 按限界上下文 × DDD 四层生成代码：
 * <pre>
 * ${package}.{module}.interfaces            Controller
 * ${package}.{module}.application.model     DTO / VO
 * ${package}.{module}.application.convert   MapStruct 转换器
 * ${package}.{module}.application.service   应用服务（含查询服务）
 * ${package}.{module}.domain.repository     仓储接口（继承 SuperCacheService）
 * ${package}.{module}.infrastructure.entity / mapper / repository  实体、Mapper、仓储实现
 * </pre>
 *
 * @author zhouxinlei
 */
public class MybatisPlusGenerator {

    public static Map<String, String> getModuleMap() {
        Map<String, String> moduleMap = Maps.newHashMap();
        moduleMap.put("1", "system");
        moduleMap.put("2", "ext");
        return moduleMap;
    }

    public static void main(String[] args) {
        extractCodeGenerator("${rootArtifactId}-server", "${package}");
    }

    private static void extractCodeGenerator(String serverModule, String parentPackage) {
        String projectPath = System.getProperty("user.dir");
        AtomicReference<String> moduleName = new AtomicReference<>("");

        DataSourceConfig.Builder dataSourceBuilder = new DataSourceConfig
                .Builder("jdbc:mysql://${databaseAddress}/${databaseName}?serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&useUnicode=true&useSSL=false&tinyInt1isBit=true",
                "${databaseUsername}",
                "${databasePassword}").typeConvertHandler((globalConfig, typeRegistry, metaInfo) -> {
            JdbcType jdbcType = metaInfo.getJdbcType();
            String remarks = metaInfo.getRemarks();
            if (remarks != null && (remarks.contains("状态") || remarks.contains("删除"))) {
                return DbColumnType.BOOLEAN;
            }
            if (JdbcType.TINYINT == jdbcType) {
                return DbColumnType.INTEGER;
            } else if (JdbcType.SMALLINT == jdbcType) {
                return DbColumnType.BOOLEAN;
            }
            return typeRegistry.getColumnType(metaInfo);
        });

        BiConsumer<Function<String, String>, GlobalConfig.Builder> globalConfigBiConsumer = (scanner, builder) -> builder
                .outputDir(serverModule.concat("/src/main/java"))
                .author(scanner.apply("请输入作者名称？"))
                .enableSwagger()
                .dateType(DateType.TIME_PACK)
                .commentDate(DatePattern.NORM_DATETIME_PATTERN);

        BiConsumer<Function<String, String>, PackageConfig.Builder> packageConfigBiConsumer = (scanner, builder) -> {
            String apply = scanner.apply("请输入模块序号？" +
                    "1.system 2.ext");
            boolean number = NumberUtil.isNumber(apply);
            if (!number) {
                throw new RuntimeException("输入内容不合法");
            }
            moduleName.set(getModuleMap().get(apply));
            String mapperPath = projectPath.concat("/").concat(serverModule).concat("/src/main/resources/mapper");
            Map<OutputFile, String> outputFileStringMap = Maps.newHashMap();
            outputFileStringMap.put(OutputFile.xml, mapperPath);
            builder.parent(parentPackage.concat(".").concat(moduleName.get()))
                    .entity("infrastructure.entity")
                    .service("domain.repository")
                    .serviceImpl("infrastructure.repository")
                    .mapper("infrastructure.mapper")
                    .xml("mapper.xml")
                    .controller("interfaces")
                    .pathInfo(outputFileStringMap).build();
        };

        Consumer<TemplateConfig.Builder> templateConfigConsumer = (builder) -> builder
                .controller("/templates/ctx_controller.java")
                .service("/templates/ctx_service.java")
                .serviceImpl("/templates/ctx_serviceImpl.java");

        FastAutoGenerator.create(dataSourceBuilder)
                .globalConfig(globalConfigBiConsumer)
                .packageConfig(packageConfigBiConsumer)
                .strategyConfig(MybatisPlusGenerator::getStrategyConfig)
                .injectionConfig((scanner, builder) -> getInjectionConfig(parentPackage, moduleName.get(), serverModule, builder))
                .templateEngine(getTemplateEngine())
                .templateConfig(templateConfigConsumer)
                .execute();
    }

    private static FreemarkerTemplateEngine getTemplateEngine() {
        return new FreemarkerTemplateEngine() {
            @Override
            protected void outputCustomFile(@org.jetbrains.annotations.NotNull List<CustomFile> customFiles,
                                            @org.jetbrains.annotations.NotNull TableInfo tableInfo,
                                            @org.jetbrains.annotations.NotNull Map<String, Object> objectMap) {
                String entityName = tableInfo.getEntityName();
                String parentPath = getPathInfo(OutputFile.parent);
                customFiles.forEach(file -> {
                    String filePath = StringUtils.isNotBlank(file.getFilePath()) ? file.getFilePath() : parentPath;
                    String fileName = filePath + File.separator + entityName + file.getFileName();
                    outputFile(new File(fileName), objectMap, file.getTemplatePath(), file.isFileOverride());
                });
            }
        };
    }

    public static CustomFile getFileConfig(String fileName, String packageName, String filePath, String templatePath) {
        CustomFile.Builder fileConfigBuilder = new CustomFile.Builder();
        fileConfigBuilder.fileName(fileName);
        fileConfigBuilder.packageName(packageName);
        fileConfigBuilder.filePath(filePath);
        fileConfigBuilder.templatePath(templatePath);
        fileConfigBuilder.enableFileOverride();
        return fileConfigBuilder.build();
    }

    private static void getInjectionConfig(String parentPackage, String moduleName, String serverModule,
                                           InjectionConfig.Builder builder) {
        List<CustomFile> customFileList = Lists.newArrayList();
        Map<String, CustomFile> filePathMap = Maps.newHashMap();
        String projectPath = System.getProperty("user.dir");
        String parentPackagePath = parentPackage.replaceAll("\\.", "/");
        String modulePackagePath = parentPackagePath.concat("/").concat(moduleName);
        String serverPath = projectPath.concat("/").concat(serverModule).concat("/src/main/java/").concat(parentPackagePath);

        String contextPackage = parentPackage.concat(".").concat(moduleName);
        String modelPackage = contextPackage.concat(".application.model");
        String modelDir = serverPath.concat("/").concat(modulePackagePath).concat("/application/model");
        CustomFile dtoFileConfig = getFileConfig("DTO.java",
                modelPackage,
                modelDir,
                "/templates/ctx_dto.java.ftl");
        customFileList.add(dtoFileConfig);
        filePathMap.put("dtoConfig", dtoFileConfig);

        CustomFile saveDtoConfig = getFileConfig("SaveDTO.java",
                modelPackage,
                modelDir,
                "/templates/ctx_save_dto.java.ftl");
        customFileList.add(saveDtoConfig);
        filePathMap.put("saveDtoConfig", saveDtoConfig);

        CustomFile updateDtoConfig = getFileConfig("UpdateDTO.java",
                modelPackage,
                modelDir,
                "/templates/ctx_update_dto.java.ftl");
        customFileList.add(updateDtoConfig);
        filePathMap.put("updateDtoConfig", updateDtoConfig);

        CustomFile voFileConfig = getFileConfig("VO.java",
                modelPackage,
                modelDir,
                "/templates/ctx_vo.java.ftl");
        customFileList.add(voFileConfig);
        filePathMap.put("voConfig", voFileConfig);

        CustomFile convertFileConfig = getFileConfig("Convert.java",
                contextPackage.concat(".application.convert"),
                serverPath.concat("/").concat(modulePackagePath).concat("/application/convert"),
                "/templates/ctx_convert.java.ftl");
        customFileList.add(convertFileConfig);
        filePathMap.put("convertConfig", convertFileConfig);

        CustomFile insideServiceConfig = getFileConfig("Service.java",
                contextPackage.concat(".application.service"),
                serverPath.concat("/").concat(modulePackagePath).concat("/application/service"),
                "/templates/ctx_inside_service.java.ftl");
        customFileList.add(insideServiceConfig);
        filePathMap.put("insideServiceConfig", insideServiceConfig);

        CustomFile insideServiceImplConfig = getFileConfig("ServiceImpl.java",
                contextPackage.concat(".application.service"),
                serverPath.concat("/").concat(modulePackagePath).concat("/application/service"),
                "/templates/ctx_inside_serviceImpl.java.ftl");
        customFileList.add(insideServiceImplConfig);
        filePathMap.put("insideServiceImplConfig", insideServiceImplConfig);

        InjectionConfig injectionConfig = builder.beforeOutputFile((tableInfo, objectMap) -> {
                    objectMap.put("customizeConfig", filePathMap);
                })
                .customFile(customFileList)
                .build();
        System.out.println(injectionConfig);
    }

    private static void getStrategyConfig(Function<String, String> scanner, StrategyConfig.Builder builder) {
        builder.addInclude(getTables(scanner.apply("请输入表名，多个英文逗号分隔？所有请输入【all】")))
                .addTablePrefix("sys_", "ext_")
                .entityBuilder()
                .enableLombok()
                .enableChainModel()
                .enableTableFieldAnnotation()
                .logicDeleteColumnName("is_delete")
                .logicDeletePropertyName("isDelete")
                .addTableFills(getTableFillList())
                .naming(NamingStrategy.underline_to_camel)
                .columnNaming(NamingStrategy.underline_to_camel)
                .idType(IdType.ASSIGN_ID)
                .enableFileOverride()
                .build()
                .mapperBuilder()
                .superClass("com.github.sparkzxl.mybatis.base.mapper.SuperMapper")
                .mapperAnnotation(Mapper.class)
                .enableFileOverride()
                .serviceBuilder()
                .formatServiceFileName("%sRepository")
                .formatServiceImplFileName("%sRepositoryImpl")
                .enableFileOverride()
                .build()
                .controllerBuilder()
                .enableRestStyle()
                .enableHyphenStyle()
                .enableFileOverride()
                .build();
    }

    private static @NotNull List<IFill> getTableFillList() {
        List<IFill> propertyList = Lists.newArrayList();
        Property createDateTimeProperty = new Property("createdAt", FieldFill.INSERT);
        propertyList.add(createDateTimeProperty);
        Property createNameProperty = new Property("createdBy", FieldFill.INSERT);
        propertyList.add(createNameProperty);
        Property modifyDateTimeProperty = new Property("updatedAt", FieldFill.INSERT_UPDATE);
        propertyList.add(modifyDateTimeProperty);
        Property modifyNameProperty = new Property("updatedBy", FieldFill.INSERT_UPDATE);
        propertyList.add(modifyNameProperty);
        return propertyList;
    }

    protected static List<String> getTables(String tables) {
        return "all".equals(tables) ? Collections.emptyList() : Arrays.asList(tables.split(","));
    }
}
