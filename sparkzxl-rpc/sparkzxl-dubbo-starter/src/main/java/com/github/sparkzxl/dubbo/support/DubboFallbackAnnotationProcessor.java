package com.github.sparkzxl.dubbo.support;

import com.github.sparkzxl.dubbo.annotation.DubboFallback;
import com.github.sparkzxl.dubbo.fallback.DubboFallbackHandler;
import com.github.sparkzxl.dubbo.fallback.DubboFallbackRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dubbo服务降级注解处理器
 * <p>
 * 在 Spring 容器启动时，自动扫描并注册标注了 {@link DubboFallback} 注解的降级处理器。
 * </p>
 * <p>
 * 支持两种注解方式：
 * <ul>
 *   <li><b>类级别</b>：标注在实现了 {@link DubboFallbackHandler} 接口的类上</li>
 *   <li><b>方法级别</b>：标注在返回 {@link DubboFallbackHandler} 的 @Bean 方法上（支持 Lambda 表达式）</li>
 * </ul>
 * </p>
 * <p>
 * 扫描范围：所有标注了 {@link Component} 及其衍生注解（@Service、@Repository、@Configuration 等）的类。
 * </p>
 *
 * @author zhouxinlei
 * @since 2026-03-20
 */
@Slf4j
@Component
public class DubboFallbackAnnotationProcessor implements ApplicationContextAware, InitializingBean {

    /**
     * 无注解类的缓存集合
     * <p>
     * 用于缓存已经扫描过但没有任何 @DubboFallback 注解的类，避免重复扫描，提升性能。
     * 使用 ConcurrentHashMap 保证线程安全。
     * </p>
     */
    private final Set<Class<?>> nonAnnotatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));

    /**
     * Spring 应用上下文
     */
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() {
        // 获取所有候选 beans：扫描 @Component 注解的类（包括 @Configuration, @Service, @Repository 等）
        String[] candidateBeanNames = applicationContext.getBeanNamesForAnnotation(Component.class);
        int classLevelCount = 0;
        int methodLevelCount = 0;

        for (String beanName : candidateBeanNames) {
            Class<?> targetType = applicationContext.getType(beanName);
            // 跳过已缓存的无注解类
            if (targetType == null || this.nonAnnotatedClasses.contains(targetType)) {
                continue;
            }

            boolean processed = false;

            // 1. 处理类级别注解：检查类上是否有 @DubboFallback 注解
            DubboFallback classAnnotation = AnnotatedElementUtils.findMergedAnnotation(targetType, DubboFallback.class);
            if (classAnnotation != null) {
                processClassLevelAnnotation(beanName, targetType, classAnnotation);
                classLevelCount++;
                processed = true;
            }

            // 2. 处理方法级别注解：扫描类中所有方法，查找带有 @DubboFallback 注解的方法
            Map<Method, DubboFallback> annotatedMethods = MethodIntrospector.selectMethods(targetType,
                    (MethodIntrospector.MetadataLookup<DubboFallback>) method ->
                            AnnotatedElementUtils.findMergedAnnotation(method, DubboFallback.class));

            if (!CollectionUtils.isEmpty(annotatedMethods)) {
                for (Map.Entry<Method, DubboFallback> entry : annotatedMethods.entrySet()) {
                    if (processAnnotatedMethod(beanName, entry.getKey(), entry.getValue())) {
                        methodLevelCount++;
                    }
                }
                processed = true;
            }

            // 如果该类既没有类级别注解，也没有方法级别注解，加入缓存避免下次重复扫描
            if (!processed) {
                this.nonAnnotatedClasses.add(targetType);
            }
        }

        log.info("[Dubbo降级] 扫描完成 - 类级别: {} 个, 方法级别: {} 个", classLevelCount, methodLevelCount);
    }

    /**
     * 处理类级别的 @DubboFallback 注解
     * <p>
     * 验证 Bean 是否实现了 {@link DubboFallbackHandler} 接口，
     * 如果是，则注册服务级降级处理器。
     * </p>
     *
     * @param beanName    Bean 名称
     * @param targetType  Bean 的目标类型
     * @param annotation  @DubboFallback 注解实例
     */
    private void processClassLevelAnnotation(String beanName, Class<?> targetType, DubboFallback annotation) {
        Object bean = applicationContext.getBean(beanName);

        // 验证 Bean 必须实现 DubboFallbackHandler 接口
        if (!(bean instanceof DubboFallbackHandler)) {
            log.warn("[Dubbo降级] Bean {} 标注了 @DubboFallback 注解但未实现 DubboFallbackHandler 接口，已忽略", beanName);
            return;
        }

        // 验证 interfaceClass 必须配置且不为 void.class
        Class<?> interfaceClass = annotation.interfaceClass();
        if (interfaceClass == void.class) {
            log.warn("[Dubbo降级] Bean {} 的 @DubboFallback 注解缺少 interfaceClass 属性，已忽略", beanName);
            return;
        }

        // 验证 interfaceClass 必须是接口
        if (!interfaceClass.isInterface()) {
            log.warn("[Dubbo降级] Bean {} 的 interfaceClass {} 不是接口类型，已忽略", beanName, interfaceClass.getName());
            return;
        }

        // 验证 value 不能为空
        String clientName = annotation.value();
        if (clientName.isEmpty()) {
            log.warn("[Dubbo降级] Bean {} 的 @DubboFallback 注解缺少 value 属性，已忽略", beanName);
            return;
        }

        String interfaceName = interfaceClass.getName();
        DubboFallbackRegistry.registerService(interfaceName, clientName, (DubboFallbackHandler) bean);

        log.info("[Dubbo降级] 已注册服务级降级处理器: bean={}, client={}, interface={}",
                beanName, clientName, interfaceName);
    }

    /**
     * 处理标注了 @DubboFallback 的方法
     * <p>
     * 验证方法返回类型是否为 {@link DubboFallbackHandler}，
     * 然后调用方法获取降级处理器实例，并注册服务级降级处理器。
     * </p>
     * <p>
     * 此方法主要用于支持函数式编程风格，允许使用 Lambda 表达式简化降级逻辑的编写。
     * </p>
     *
     * @param beanName    Bean 名称
     * @param method      标注了 @DubboFallback 注解的方法
     * @param annotation  @DubboFallback 注解实例
     * @return 是否成功注册降级处理器
     */
    private boolean processAnnotatedMethod(String beanName, Method method, DubboFallback annotation) {
        // 验证返回类型必须是 DubboFallbackHandler
        if (!DubboFallbackHandler.class.isAssignableFrom(method.getReturnType())) {
            log.warn("[Dubbo降级] 方法返回类型不是 DubboFallbackHandler: bean={}, method={}",
                    beanName, method.getName());
            return false;
        }

        // 验证 interfaceClass 必须配置且不为 void.class
        Class<?> interfaceClass = annotation.interfaceClass();
        if (interfaceClass == void.class) {
            log.warn("[Dubbo降级] 方法 {} 的 @DubboFallback 注解缺少 interfaceClass 属性，已忽略",
                    method.getName());
            return false;
        }

        // 验证 interfaceClass 必须是接口
        if (!interfaceClass.isInterface()) {
            log.warn("[Dubbo降级] 方法 {} 的 interfaceClass {} 不是接口类型，已忽略",
                    method.getName(), interfaceClass.getName());
            return false;
        }

        // 验证 value 不能为空
        String clientName = annotation.value();
        if (clientName.isEmpty()) {
            log.warn("[Dubbo降级] 方法 {} 的 @DubboFallback 注解缺少 value 属性，已忽略",
                    method.getName());
            return false;
        }

        // 调用方法获取 DubboFallbackHandler 实例
        DubboFallbackHandler handler = invokeFallbackMethod(beanName, method);
        if (handler == null) {
            return false;
        }

        // 注册服务级降级处理器
        String interfaceName = interfaceClass.getName();
        DubboFallbackRegistry.registerService(interfaceName, clientName, handler);

        log.info("[Dubbo降级] 已注册服务级降级处理器: bean={}, method={}, client={}, interface={}",
                beanName, method.getName(), clientName, interfaceName);

        return true;
    }

    /**
     * 调用标注了 @DubboFallback 的方法，获取降级处理器实例
     * <p>
     * 使用 {@link AopUtils#selectInvocableMethod} 处理 AOP 代理场景，
     * 确保在代理情况下也能正确调用目标方法。
     * </p>
     *
     * @param beanName Bean 名称
     * @param method   目标方法
     * @return DubboFallbackHandler 实例，调用失败返回 null
     */
    private DubboFallbackHandler invokeFallbackMethod(String beanName, Method method) {
        try {
            Object bean = applicationContext.getBean(beanName);
            // 处理 AOP 代理场景：获取可调用的方法对象
            Method methodToInvoke = AopUtils.selectInvocableMethod(method, applicationContext.getType(beanName));
            Object result = methodToInvoke.invoke(bean);
            return (DubboFallbackHandler) result;
        } catch (Exception e) {
            log.error("[Dubbo降级] 调用降级方法异常: bean={}, method={}", beanName, method.getName(), e);
            return null;
        }
    }
}
