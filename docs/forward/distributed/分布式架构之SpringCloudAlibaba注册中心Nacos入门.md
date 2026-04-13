# 分布式架构之Spring Cloud Alibaba 注册中心 Nacos 入门

摘要: 原创出处 http://www.iocoder.cn/Spring-Cloud-Alibaba/Nacos-Discovery/ 「芋道源码」

## 1. 概述

本文我们来学习 [Spring Cloud Alibaba](https://spring.io/projects/spring-cloud-alibaba)
提供的 [Spring Cloud Alibaba Nacos Discovery](https://github.com/alibaba/spring-cloud-alibaba/wiki/Nacos-discovery) 组件，基于 Spring Cloud 的编程模型，接入 Nacos
作为注册中心，实现服务的注册与发现。
> [服务注册/发现: Nacos Discovery](https://github.com/alibaba/spring-cloud-alibaba/blob/master/spring-cloud-alibaba-docs/src/main/asciidoc-zh/nacos-discovery.adoc)
> - 服务发现是微服务架构体系中最关键的组件之一。如果尝试着用手动的方式来给每一个客户端来配置所有服务提供者的服务列表是一件非常困难的事，而且也不利于服务的动态扩缩容。
> - Nacos Discovery 可以帮助您将服务自动注册到 Nacos 服务端并且能够动态感知和刷新某个服务实例的服务列表。
> - 除此之外，Nacos Discovery 也将服务实例自身的一些元数据信息-例如 host，port, 健康检查URL，主页等内容注册到 Nacos。

在开始本文之前，胖友需要对 Nacos 进行简单的学习。可以阅读[《Nacos注册&配置中心搭建》](分布式架构之Nacos注册&配置中心搭建.md)文章，在本机搭建一个 Nacos 服务。

## 2. 注册中心原理

在开始搭建 Nacos Discovery 的示例之前，我们先来简单了解下注册中心的原理。

在使用注册中心时，一共有三种角色：服务提供者（Service Provider）、服务消费者（Service Consumer）、注册中心（Registry）。

> 在一些文章中，服务提供者被称为 Server，服务消费者被称为 Client。胖友们知道即可。

三个角色交互如下图所示：
![nacos-discovery.png](https://oss.sparksys.top/sparkzxl-framework/nacos-discovery.png)

① Provider：

- 启动时，向 Registry 注册自己为一个服务（Service）的实例（Instance）。
- 同时，定期向 Registry 发送心跳，告诉自己还存活。
- 关闭时，向 Registry 取消注册。

② Consumer：

- 启动时，向 Registry 订阅使用到的服务，并缓存服务的实例列表在内存中。
- 后续，Consumer 向对应服务的 Provider 发起调用时，从内存中的该服务的实例列表选择一个，进行远程调用。
- 关闭时，向 Registry 取消订阅。

③ Registry：

- Provider 超过一定时间未**心跳**时，从服务的实例列表移除。
- 服务的实例列表发生变化（新增或者移除）时，通知订阅该服务的 Consumer，从而让 Consumer 能够刷新本地缓存。

当然，不同的注册中心可能在实现原理上会略有差异。例如说，Eureka 注册中心，并不提供通知功能，而是 Eureka Client 自己定期轮询，实现本地缓存的更新。

另外，Provider 和 Consumer 是角色上的定义，一个服务同时即可以是 Provider 也可以作为 Consumer。例如说，优惠劵服务可以给订单服务提供接口，同时又调用用户服务提供的接口。

## 3. 快速入门

> 示例代码对应仓库：
> - 服务提供者：[sparkzxl-nacos-discovery-provider](https://github.com/zhouxinlei828/sparkzxl-cloud-learning/tree/main/sparkzxl-nacos-learn/sparkzxl-nacos-discovery-provider)
> - 服务消费者：[sparkzxl-nacos-discovery-consumer](https://github.com/zhouxinlei828/sparkzxl-cloud-learning/tree/main/sparkzxl-nacos-learn/sparkzxl-nacos-discovery-consumer)

本小节，我们来搭建一个 Nacos Discovery 组件的快速入门示例。步骤如下：

首先，搭建一个服务提供者 sparkzxl-nacos-discovery-provider ，注册服务到 Nacos 中。

然后，搭建一个服务消费者 sparkzxl-nacos-discovery-consumer，从 Nacos 获取到 nacos-provider 服务的实例列表，选择其中一个示例，进行 HTTP 远程调用。

### 3.1 搭建服务提供者

创建 sparkzxl-nacos-discovery-provider 项目，作为服务提供者 nacos-provider。最终项目代码如下图所示：

![sparkzxl-nacos-discovery-provider](https://oss.sparksys.top/sparkzxl-framework/sparkzxl-nacos-discovery-provider.png)

#### 3.1.1 引入依赖

- 父pom

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.github.sparkzxl</groupId>
    <artifactId>sparkzxl-cloud-learning</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
        <java.version>8</java.version>
        <!-- maven -->
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>
        <maven-compiler-plugin.version>3.8.0</maven-compiler-plugin.version>
        <maven-resources-plugin.version>3.1.0</maven-resources-plugin.version>
        <maven-source-plugin.version>3.1.0</maven-source-plugin.version>
        <spring-boot-maven.version>2.3.5.RELEASE</spring-boot-maven.version>
        <sparkzxl-dependencies.version>1.0.1.RELEASE</sparkzxl-dependencies.version>
        <lombok.version>1.18.8</lombok.version>
        <javafaker.version>1.0.2</javafaker.version>
    </properties>

    <modules>
        <module>sparkzxl-wechat-admin</module>
        <module>sparkzxl-account</module>
        <module>sparkzxl-admin-server</module>
        <module>sparkzxl-code-generator</module>
        <module>sparkzxl-order</module>
        <module>sparkzxl-product</module>
        <module>sparkzxl-sharding-demo</module>
        <module>sparkzxl-test-demo</module>
        <module>sparkzxl-sentinel-learn</module>
        <module>sparkzxl-kafka-learn</module>
        <module>sparkzxl-elasticsearch-learn</module>
        <module>sparkzxl-nacos-learn</module>
    </modules>

    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
        </dependency>
    </dependencies>
    <dependencyManagement>
        <dependencies>
            <!-- sparkzxl-dependencies 依赖-->
            <dependency>
                <groupId>com.github.sparkzxl</groupId>
                <artifactId>sparkzxl-dependencies</artifactId>
                <version>${sparkzxl-dependencies.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <resources>
            <resource>
                <directory>src/main/resources</directory>
                <includes>
                    <include>**/*</include>
                </includes>
                <filtering>true</filtering>
            </resource>
            <resource>
                <directory>src/main/java</directory>
                <includes>
                    <include>**/*.xml</include>
                </includes>
                <filtering>true</filtering>
            </resource>
        </resources>

        <pluginManagement>
            <plugins>
                <!-- resources资源插件 -->
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-resources-plugin</artifactId>
                    <version>${maven-resources-plugin.version}</version>
                    <configuration>
                        <delimiters>
                            <delimiter>@</delimiter>
                        </delimiters>
                        <useDefaultDelimiters>false</useDefaultDelimiters>
                        <encoding>UTF-8</encoding>
                        <!-- 后缀为pem、pfx的证书文件 -->
                        <nonFilteredFileExtensions>
                            <nonFilteredFileExtension>pem</nonFilteredFileExtension>
                            <nonFilteredFileExtension>pfx</nonFilteredFileExtension>
                            <nonFilteredFileExtension>p12</nonFilteredFileExtension>
                            <nonFilteredFileExtension>key</nonFilteredFileExtension>
                            <nonFilteredFileExtension>jks</nonFilteredFileExtension>
                            <nonFilteredFileExtension>db</nonFilteredFileExtension>
                            <nonFilteredFileExtension>txt</nonFilteredFileExtension>
                        </nonFilteredFileExtensions>
                    </configuration>
                </plugin>
                <!--配置生成源码包 -->
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-source-plugin</artifactId>
                    <version>${maven-source-plugin.version}</version>
                    <executions>
                        <execution>
                            <id>attach-sources</id>
                            <goals>
                                <goal>jar</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
                <!-- 打包 -->
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot-maven.version}</version>
                    <executions>
                        <execution>
                            <goals>
                                <goal>repackage</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </pluginManagement>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
            </plugin>
            <!-- resources资源插件 -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-resources-plugin</artifactId>
            </plugin>
            <!--配置生成源码包 -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-source-plugin</artifactId>
            </plugin>
            <!-- 打包 -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>

```

- sparkzxl-nacos-discovery-provider pom

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>sparkzxl-nacos-learn</artifactId>
        <groupId>com.github.sparkzxl</groupId>
        <version>1.0-SNAPSHOT</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>sparkzxl-nacos-discovery-provider</artifactId>

    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.github.sparkzxl</groupId>
            <artifactId>sparkzxl-boot-starter</artifactId>
            <exclusions>
                <exclusion>
                    <groupId>com.github.sparkzxl</groupId>
                    <artifactId>sparkzxl-database-starter</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

> 友情提示：有点小长，不要慌~

在 <dependencyManagement/> 中，我们引入了 sparkzxl-dependencies BOM 文件，进行依赖版本的管理，防止不兼容。 sparkzxl-dependencies BOM 文件中定义了Spring Boot、Spring Cloud、Spring Cloud Alibaba 三者
BOM 文件，进行依赖版本的管理 在[《Spring Cloud 官方文档 —— 版本说明》](https://github.com/alibaba/spring-cloud-alibaba/wiki/版本说明)
文档中，推荐了三者的依赖关系。如下表格：

|Spring Cloud Version|Spring Cloud Alibaba Version|Spring Boot Version
|-------|-------|-------|
|Spring Cloud|2020.0|2021.1|
|Spring Cloud Hoxton.SR8|2.2.5.RELEASE|2.3.2.RELEASE|
|Spring Cloud Greenwich.SR6|2.1.4.RELEASE|2.1.13.RELEASE|
|Spring Cloud Hoxton.SR3|2.2.1.RELEASE|2.2.5.RELEASE|
|Spring Cloud Hoxton.RELEASE|2.2.0.RELEASE|2.2.X.RELEASE|
|Spring Cloud Greenwich|2.1.2.RELEASE|2.1.X.RELEASE|
|Spring Cloud Finchley|2.0.4.RELEASE(停止维护，建议升级)|2.0.X.RELEASE|
|Spring Cloud Edgware|1.5.1.RELEASE(停止维护，建议升级)|1.5.X.RELEASE|

- 这里，我们选择了 Spring Cloud Alibaba 版本为 2.2.5.RELEASE。
- 当前版版本下，我们使用的 Nacos 版本为 1.4.1。

引入 spring-cloud-starter-alibaba-nacos-discovery 依赖，将 Nacos 作为注册中心，并实现对它的自动配置。

#### 3.1.2 配置文件

创建 application.yaml 配置文件，添加 Nacos Discovery 配置项。配置如下：

```yaml
server:
  port: 8080
spring:
  application:
    name: nacos-provider # Spring 应用名
  cloud:
    nacos:
      # Nacos 作为注册中心的配置项，对应 NacosDiscoveryProperties 配置类
      discovery:
        server-addr: 127.0.0.1:8848 # Nacos 服务器地址
        service: ${spring.application.name} # 注册到 Nacos 的服务名。默认值为 ${spring.application.name}。
knife4j:
  enable: true
  description: sparkzxl nacos provider在线文档
  base-package: com.github.sparkzxl.nacos.controller
  group: nacos provider应用
  title: sparkzxl nacos provider在线文档
  terms-of-service-url: https://www.sparksys.top
  version: 1.0
  license: Powered By sparkzxl
  license-url: https://github.com/zhouxinlei828
  contact:
    name: zhouxinlei
    email: zhouxinlei298@163.com
    url: https://github.com/zhouxinlei828

```

重点看 spring.cloud.nacos.discovery 配置项，它是 Nacos Discovery 配置项的前缀，对应 NacosDiscoveryProperties 配置项。

![NacosDiscoveryProperties.png](https://oss.sparksys.top/sparkzxl-framework/NacosDiscoveryProperties.png)

#### 3.1.3 NacosProviderApplication

创建 DemoProviderApplication 类，创建应用启动类，并提供 HTTP 接口。代码如下

```java
package com.github.sparkzxl.nacos;

import com.github.sparkzxl.boot.SparkBootApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * description: nacos provider
 *
 * @author charles.zhou
 */
@SpringBootApplication(scanBasePackages = {"com.github.sparkzxl.nacos"})
public class NacosProviderApplication extends SparkBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(NacosProviderApplication.class, args);
    }

}
```

```java
package com.github.sparkzxl.nacos.controller;

import com.github.sparkzxl.annotation.echo.result.ResponseResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * description: test
 *
 * @author charles.zhou
 * @since 2021-05-11 10:47:54
 */
@RestController
@ResponseResult
@Api(tags = "测试")
public class TestController {

    @ApiOperation("echoField")
    @GetMapping("/echoField")
    public String echoField(String name) {
        return "provider:" + name;
    }

}
```

① @SpringBootApplication 注解，被添加在类上，声明这是一个 Spring Boot 应用。Spring Cloud 是构建在 Spring Boot 之上的，所以需要添加。 ② @EnableDiscoveryClient 注解，开启 Spring Cloud 的注册发现功能。不过从
Spring Cloud Edgware 版本开始，实际上已经不需要添加 @EnableDiscoveryClient 注解，只需要引入 Spring Cloud 注册发现组件，就会自动开启注册发现的功能。例如说，我们这里已经引入了
spring-cloud-starter-alibaba-nacos-discovery 依赖，就不用再添加 @EnableDiscoveryClient 注解了。
> - 拓展小知识：在 Spring Cloud Common 项目中，定义了 **DiscoveryClient** 接口，作为通用的发现客户端，提供读取服务和读取服务列表的 API 方法。而想要集成到 Spring Cloud 体系的注册中心的组件，需要提供对应的 DiscoveryClient 实现类。
> - 例如说，Spring Cloud Alibaba Nacos Discovery 提供了 **NacosDiscoveryClient** 实现，Spring Cloud Netflix Eureka 提供了 **EurekaDiscoveryClient** 实现。
> - 如此，所有需要使用到的地方，只需要获取到 DiscoveryClient 客户端，而无需关注具体实现，保证其通用性。

③ TestController 类，提供了 /echoField 接口，返回 provider:${name} 结果。

#### 3.1.4 简单测试

① 通过 DemoProviderApplication 启动服务提供者，IDEA 控制台输出日志如：

```text
2021-05-11 10:56:17.503 application: nacos-provider  INFO 3665 TID: N/A --- [           main] c.a.c.n.r.NacosServiceRegistry           : nacos registry, DEFAULT_GROUP nacos-provider 172.34.67.31:8080 register finished
```

- 服务 **nacos-provider** 注册到 Nacos 上的日志。

② 打开 Nacos 控制台，可以在服务列表看到服务 nacos-provider。如下图：

![nacos-provider-img.png](https://oss.sparksys.top/sparkzxl-framework/nacos-provider-img.png)

### 3.2 搭建服务消费者

创建 sparkzxl-nacos-discovery-consumer 项目，作为服务提供者 nacos-consumer。最终项目代码如下图所示：

![sparkzxl-nacos-discovery-consumer.png](https://oss.sparksys.top/sparkzxl-framework/sparkzxl-nacos-discovery-consumer.png)

整个项目的代码，和服务提供者是基本一致的，毕竟是示例代码 😜

#### 3.2.1 引入依赖

和「3.1.1 引入依赖」一样，只是修改 Maven <artifactId/> 为 sparkzxl-nacos-discovery-consumer，见 pom.xml 文件。

#### 3.2.2 配置文件

创建 application.yaml 配置文件，添加相应配置项。配置如下：

```yaml
server:
  port: 8081
spring:
  application:
    name: nacos-consumer
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848 # Nacos 服务器地址
        service: ${spring.application.name} # 注册到 Nacos 的服务名。默认值为 ${spring.application.name}。
knife4j:
  enable: true
  description: sparkzxl nacos consumer在线文档
  base-package: com.github.sparkzxl.nacos.controller
  group: nacos consumer应用
  title: sparkzxl nacos consumer在线文档
  terms-of-service-url: https://www.sparksys.top
  version: 1.0
  license: Powered By sparkzxl
  license-url: https://github.com/zhouxinlei828
  contact:
    name: zhouxinlei
    email: zhouxinlei298@163.com
    url: https://github.com/zhouxinlei828

```

和「3.1.2 配置文件」基本一致，主要是将配置项目 spring.application.name 修改为 nacos-consumer。

#### 3.2.3 NacosConsumerApplication

创建 **NacosConsumerApplication** 类，创建应用启动类，并提供一个调用服务提供者的 HTTP 接口。代码如下：

```java
package com.github.sparkzxl.nacos;

import com.github.sparkzxl.boot.SparkBootApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * description: nacos consumer
 *
 * @author charles.zhou
 * @since 2021-05-11 10:41:35
 */
@SpringBootApplication(scanBasePackages = {"com.github.sparkzxl.nacos"})
public class NacosConsumerApplication extends SparkBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(NacosConsumerApplication.class, args);
    }


    @Configuration
    public class RestTemplateConfiguration {
        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplate();
        }

    }

}
```

```java
package com.github.sparkzxl.nacos.controller;

import com.github.sparkzxl.annotation.echo.result.ResponseResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * description: test
 *
 * @author charles.zhou
 * @since 2021-05-11 10:47:54
 */
@RestController
@ResponseResult
@Api(tags = "测试")
public class TestController {

    @Autowired
    private DiscoveryClient discoveryClient;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private LoadBalancerClient loadBalancerClient;

    @ApiOperation("echoField")
    @GetMapping("/hello")
    public String hello(String name) {
        // <1> 获得服务 `nacos-provider` 的一个实例
        ServiceInstance instance;
        if (true) {
            // 获取服务 `nacos-provider` 对应的实例列表
            List<ServiceInstance> instances = discoveryClient.getInstances("nacos-provider");
            // 选择第一个
            instance = instances.size() > 0 ? instances.get(0) : null;
        } else {
            instance = loadBalancerClient.choose("nacos-provider");
        }
        // <2> 发起调用
        if (instance == null) {
            throw new IllegalStateException("获取不到实例");
        }
        String targetUrl = instance.getUri() + "/echoField?name=" + name;
        String r = restTemplate.getForObject(targetUrl, String.class);
        // 返回结果
        return "consumer:" + r;
    }
}
```

① @EnableDiscoveryClient 注解，因为已经无需添加，所以我们进行了注释，原因在上面已经解释过。

② RestTemplateConfiguration 配置类，创建 **RestTemplate** Bean。RestTemplate 是 Spring 提供的 HTTP 调用模板工具类，可以方便我们稍后调用服务提供者的 HTTP API。

③ TestController 提供了 /hello 接口，用于调用服务提供者的 /demo 接口。代码略微有几行，我们来稍微解释下哈。

discoveryClient 属性，DiscoveryClient 对象，服务发现客户端，上文我们已经介绍过。这里我们注入的不是 Nacos Discovery 提供的 NacosDiscoveryClient，保证通用性。未来如果我们不使用 Nacos 作为注册中心，而是使用 Eureka 或则 Zookeeper
时，则无需改动这里的代码。

loadBalancerClient 属性，**LoadBalancerClient** 对象，负载均衡客户端。稍后我们会使用它，从 Nacos 获取的服务 demo-provider 的实例列表中，选择一个进行 HTTP 调用。

> - 拓展小知识：在 Spring Cloud Common 项目中，定义了LoadBalancerClient 接口，作为通用的负载均衡客户端，提供从指定服务中选择一个实例、对指定服务发起请求等 API 方法。而想要集成到 Spring Cloud 体系的负载均衡的组件，需要提供对应的 LoadBalancerClient 实现类。
> - 例如说，Spring Cloud Netflix Ribbon 提供了 RibbonLoadBalancerClient 实现。
> - 如此，所有需要使用到的地方，只需要获取到 DiscoveryClient 客户端，而无需关注具体实现，保证其通用性。😈 不过貌似 Spring Cloud 体系中，暂时只有 Ribbon 一个负载均衡组件。
> - 当然，LoadBalancerClient 的服务的实例列表，是来自 DiscoveryClient 提供的。

/hello 接口，示例接口，对服务提供者发起一次 HTTP 调用。

- <1> 处，获得服务 nacos-provider 的一个实例。这里我们提供了两种方式的代码，分别基于 DiscoveryClient 和 LoadBalancerClient。
- <2> 处，通过获取到的服务实例 ServiceInstance 对象，拼接请求的目标 URL，之后使用 RestTemplate 发起 HTTP 调用。

#### 3.2.4 简单测试

① 通过 NacosConsumerApplication 启动服务消费者，IDEA 控制台输出日志如：

```text

2021-05-11 14:23:26.590 application: nacos-consumer  INFO 5629 TID: N/A --- [           main] c.a.c.n.r.NacosServiceRegistry           : nacos registry, DEFAULT_GROUP nacos-consumer 172.34.67.31:8081 register finished
```

- 服务 nacos-consumer 注册到 Nacos 上的日志。

> 注意，服务消费者和服务提供是一种角色的概念，本质都是一种服务，都是可以注册自己到注册中心的。

② 打开 Nacos 控制台，可以在服务列表看到服务 nacos-consumer。如下图：

![nacos-consumer-console.png](https://oss.sparksys.top/sparkzxl-framework/nacos-consumer-console.png)

③ 访问服务消费者的 http://127.0.0.1:8081/hello?name=helloWorld 接口。

![nacos-consumer-httpRequest.png](https://oss.sparksys.top/sparkzxl-framework/nacos-consumer-httpRequest.png)

④ 打开 Nacos 控制台，可以在订阅者列表看到订阅关系。如下图：

![nacos-consumer-sub.png](https://oss.sparksys.top/sparkzxl-framework/nacos-consumer-sub.png)

⑤ 关闭服务提供者后，再次访问 http://127.0.0.1:8081/hello?name=helloWorld 接口，返回结果为报错提示 "获取不到实例"，说明我们本地缓存的服务 demo-provider 的实例列表已刷新，没有任何实例。

![nacos-consumer-error.png](https://oss.sparksys.top/sparkzxl-framework/nacos-consumer-error.png)

😈 这里我们并没有演示启动多个服务提供者的测试，胖友可以自己尝试下哟。

## 4. Nacos 概念详解

> 友情提示：本小节的内容，基于如下两篇文档梳理，推荐胖友后续也看看：
> - [《Nacos 官方文档 —— 概念》](https://nacos.io/zh-cn/docs/what-is-nacos.html)
> - [《Nacos 官方文档 —— 架构》](https://nacos.io/zh-cn/docs/architecture.html)

### 4.1 数据模型

Nacos 数据模型 Key 由三元组唯一确认。如下图所示：

![nacos-data-model.png](https://oss.sparksys.top/sparkzxl-framework/nacos-data-model.png)

- 作为注册中心时，Namespace + Group + Service
- 作为配置中心时，Namespace + Group + DataId

我们来看看 Namespace、Group、Service 的概念。

### 4.1.1 Namespace 命名空间

用于进行租户粒度的配置隔离。默认为 public（公共命名空间）。

不同的命名空间下，可以存在相同的 Group 或 Data ID 的配置。Namespace 的常用场景之一是不同环境的配置的区分隔离，例如开发测试环境和生产环境的资源（如配置、服务）隔离等。

稍后在**6. 多环境配置**小节中，我们会通过 Namespace 隔离不同环境的服务。

#### 4.1.2 Group 服务分组

不同的服务可以归类到同一分组。默认为 DEFAULT_GROUP（默认分组）。

#### 4.1.3 Service 服务

例如说，用户服务、订单服务、商品服务等等。

### 4.2 服务领域模型

Service 可以进一步细拆服务领域模型，如下图：

![nacos-service-level.png](https://oss.sparksys.top/sparkzxl-framework/nacos-service-level.png)

我们来看看图中的每个**节点**的概念。

#### 4.2.1 Instance 实例

提供一个或多个服务的具有可访问网络地址（IP:Port）的进程。

我们以**3.1 搭建服务提供者**小节来举例子：

- 如果我们启动一个 JVM 进程，就是服务 demo-provider 下的一个实例。
- 如果我们启动多个 JVM 进程，就是服务 demo-provider 下的多个实例。

#### 4.2.2 Cluster 集群

同一个服务下的所有服务实例组成一个默认集群（Default）。集群可以被进一步按需求划分，划分的单位可以是虚拟集群。

例如说，我们将服务部署在多个机房之中，每个机房可以创建为一个虚拟集群。每个服务在注册到 Nacos 时，设置所在机房的虚拟集群。这样，服务在调用其它服务时，可以通过虚拟集群，优先调用本机房的服务。如此，在提升服务的可用性的同时，保证了性能。

#### 4.2.3 Metadata 元数据

Nacos 元数据（如配置和服务）描述信息，如服务版本、权重、容灾策略、负载均衡策略、鉴权配置、各种自定义标签 (label)。

从作用范围来看，分为服务级别的元信息、集群的元信息及实例的元信息。如下图：

![nacos-instance-example.png](https://oss.sparksys.top/sparkzxl-framework/nacos-instance-example.png)

![nacos-instance-example1.png](https://oss.sparksys.top/sparkzxl-framework/nacos-instance-example1.png)

以 Nacos 元数据的服务版本举例子。当一个接口实现，出现不兼容升级时，可以用版本号过渡，版本号不同的服务相互间不引用。

可以按照以下的步骤进行版本迁移：

- 在低压力时间段，先升级一半提供者为新版本
- 再将所有消费者升级为新版本
- 然后将剩下的一半提供者升级为新版本

再次 Nacos 元数据的鉴权配置举例子。通过令牌验证在注册中心控制权限，以决定要不要下发令牌给消费者，可以防止消费者绕过注册中心访问提供者。另外，通过注册中心可灵活改变授权方式，而不需修改或升级提供者。

![nacos-security-1.png](https://oss.sparksys.top/sparkzxl-framework/nacos-security-1.png)

#### 4.2.4 Health Check 健康检查

以指定方式检查服务下挂载的实例的健康度，从而确认该实例是否能提供服务。根据检查结果，实例会被判断为健康或不健康。

对服务发起解析请求时，不健康的实例不会返回给客户端。

健康保护阈值

为了防止因过多实例不健康导致流量全部流向健康实例，继而造成流量压力把健康实例实例压垮并形成雪崩效应，应将健康保护阈值定义为一个 0 到 1 之间的浮点数。

当域名健康实例占总服务实例的比例小于该值时，无论实例是否健康，都会将这个实例返回给客户端。这样做虽然损失了一部分流量，但是保证了集群的剩余健康实例能正常工作。

### 4.3 小结

为了让胖友更好理解，我们把数据模型和服务领域模型整理如下图所示：

![img.png](https://oss.sparksys.top/sparkzxl-framework/nacos-example-1.png)

## 5. 更多的配置项信息

在**3. 快速入门**小节中，我们为了快速入门，只使用了 Nacos Discovery Starter 两个配置项。实际上，Nacos Discovery Starter 提供的配置项挺多的，我们参考文档将配置项一起梳理下。

**Nacos 服务器相关**

|配置项|Key|说明
|-------|-------|-------|
|服务端地址|spring.cloud.nacos.discovery.server-addr|    Nacos Server 启动监听的ip地址和端口|
|AccessKey|spring.cloud.nacos.discovery.access-key|    当要上阿里云时，阿里云上面的一个云账号名|
|SecretKey|spring.cloud.nacos.discovery.secret-key|    当要上阿里云时，阿里云上面的一个云账号密码|

**服务相关**

|配置项|Key|说明
|-------|-------|-------|
|命名空间|spring.cloud.nacos.discovery.namespace|常用场景之一是不同环境的注册的区分隔离，例如开发测试环境和生产环境的资源（如配置、服务）隔离等|
|服务分组    |spring.cloud.nacos.discovery.group    |不同的服务可以归类到同一分组。默认为 DEFAULT_GROUP|
服务名    |spring.cloud.nacos.discovery.service    |注册的服务名。默认为 ${spring.application.name}|
集群    |spring.cloud.nacos.discovery.cluster-name    |Nacos 集群名称。默认为 DEFAULT|
权重    |spring.cloud.nacos.discovery.weight    |取值范围 1 到 100，数值越大，权重越大。默认为 1|
Metadata|    spring.cloud.nacos.discovery.metadata    |使用Map格式配置，用户可以根据自己的需要自定义一些和服务相关的元数据信息|
是否开启Nacos Watch    |spring.cloud.nacos.discovery.watch.enabled    |可以设置成 false 来关闭 watch。默认为 true|

**网络相关**

|配置项|Key|说明
|-------|-------|-------|
|网卡名    |spring.cloud.nacos.discovery.network-interface    |当IP未配置时，注册的 IP 为此网卡所对应的 IP 地址，如果此项也未配置，则默认取第一块网卡的地址|
|注册的IP地址    |spring.cloud.nacos.discovery.ip    |优先级最高|
|注册的端口    |spring.cloud.nacos.discovery.port    |默认情况下不用配置，会自动探测。默认为 -1|

**其它相关**

|配置项|Key|说明
|-------|-------|-------|
|是否集成 Ribbon    |ribbon.nacos.enabled    |一般都设置成true 即可。默认为 true|
|日志文件名    |spring.cloud.nacos.discovery.log-name||
|接入点    |spring.cloud.nacos.discovery.endpoint    |地域的某个服务的入口域名，通过此域名可以动态地拿到服务端地址|

## 6. 多环境配置

> 示例代码对应仓库：
> - 服务提供者：[sparkzxl-nacos-discovery-provider-env](https://github.com/zhouxinlei828/sparkzxl-cloud-learning/tree/main/sparkzxl-nacos-learn/sparkzxl-nacos-discovery-provider-env)
> - 服务消费者：[sparkzxl-nacos-discovery-consumer-env](https://github.com/zhouxinlei828/sparkzxl-cloud-learning/tree/main/sparkzxl-nacos-learn/sparkzxl-nacos-discovery-consumer-env)

同一个服务，我们会部署到开发、测试、预发布、生产等环境中，那么我们需要在项目中，添加不同环境的 Nacos 配置。一般情况下，开发和测试使用同一个 Nacos，预发布和生产使用另一个 Nacos。那么针对相同的 Nacos，我们怎么实现不同环境的隔离呢？

实际上，Nacos 开发者已经告诉我们如何实现了，通过 Nacos Namespace 命名空间。文档说明如下：

> [FROM 《Nacos 文档 —— Nacos 概念》](https://nacos.io/zh-cn/docs/concepts.html)
> 命名空间，用于进行租户粒度的配置隔离。不同的命名空间下，可以存在相同的 Group 或 Data ID 的配置。Namespace 的常用场景之一是不同环境的配置的区分隔离，例如开发测试环境和生产环境的资源（如配置、服务）隔离等。

下面，我们来搭建一个多环境配置的示例。步骤如下：

- 首先，我们会在 Nacos 中创建开发环境使用的 Namespace 为 dev，测试环境使用的 Namespace 为 uat。
- 然后，搭建一个服务提供者 nacos-provider，使用开发环境配置，注册服务到 Nacos 的 dev Namespace 下。
- 之后，搭建一个服务消费者 nacos-consumer，调用服务提供者 demo-provider 提供的 HTTP 接口。
    - 先使用开发环境配置，因为服务 nacos-provider 是在 Nacos dev Namespace 下注册，所以调用它成功。
    - 后使用测试环境配置，因为服务 nacos-provider 不在 Nacos uat Namespace 下注册，所以调用它失败，

> 友情提示：在 Spring Boot（Spring Cloud）项目中，可以使用 Profiles 机制，基于 spring.profiles.active 配置项，实现不同环境读取不同的配置文件。

## 6.1 创建 Nacos 命名空间

① 打开 Nacos UI 界面的「命名空间」菜单，进入「命名空间」功能。如下图所示：

![nacos-namespace-1.png](https://oss.sparksys.top/sparkzxl-framework/nacos-namespace-1.png)

② 点击列表右上角的「新建命名空间」按钮，弹出「新建命名空间」窗口，创建一个 **dev** 命名空间。输入如下内容，并点击「确定」按钮，完成创建。如下图所示：

![nacos-namespace-2.png](https://oss.sparksys.top/sparkzxl-framework/nacos-namespace-2.png)

③ 重复该操作，继续创建一个 uat 命名空间。最终 **dev** 和 **uat** 信息如下图：

![nacos-namespace-3.png](https://oss.sparksys.top/sparkzxl-framework/nacos-namespace-3.png)

### 6.2 搭建服务提供者

从**3.1 搭建服务提供者**小节的 sparkzxl-nacos-discovery-provider 项目，复制出 sparkzxl-nacos-discovery-provider-env 项目。然后在其上进行修改，方便搭建~

#### 6.2.1 配置文件

修改 application.yaml 配置文件，将 Nacos Discovery 配置项删除，稍后添加在不同环境的配置文件中。配置如下：

```yaml
spring:
  application:
    name: nacos-provider
server:
  port: 8080
```

创建开发环境使用的 application-dev.yaml 配置文件，增加 Namespace 为 dev 的 Nacos Discovery 配置项。配置如下：

```yaml
spring:
  cloud:
    nacos:
      # Nacos 作为注册中心的配置项，对应 NacosDiscoveryProperties 配置类
      discovery:
        server-addr: 47.114.40.129:8848 # Nacos 服务器地址
        service: ${spring.application.name} # 注册到 Nacos 的服务名。默认值为 ${spring.application.name}。
        namespace: 5acd2f93-cd2d-420a-afe4-15d7cf5b3b0b # Nacos 命名空间 uat 的编号
knife4j:
  enable: true
  description: sparkzxl nacos provider在线文档
  base-package: com.github.sparkzxl.nacos.controller
  group: nacos provider应用
  title: sparkzxl nacos provider在线文档
  terms-of-service-url: https://www.sparksys.top
  version: 1.0
  license: Powered By sparkzxl
  license-url: https://github.com/zhouxinlei828
  contact:
    name: zhouxinlei
    email: zhouxinlei298@163.com
    url: https://github.com/zhouxinlei828
```

创建测试环境使用的 application-uat.yaml 配置文件，增加 Namespace 为 uat 的 Nacos Discovery 配置项。配置如下：

```yaml
spring:
  cloud:
    nacos:
      # Nacos 作为注册中心的配置项，对应 NacosDiscoveryProperties 配置类
      discovery:
        server-addr: 47.114.40.129:8848 # Nacos 服务器地址
        service: ${spring.application.name} # 注册到 Nacos 的服务名。默认值为 ${spring.application.name}。
        namespace: 6657f6f1-6c27-48c0-8187-9f675237144a # Nacos 命名空间 uat 的编号
knife4j:
  enable: true
  description: sparkzxl nacos provider在线文档
  base-package: com.github.sparkzxl.nacos.controller
  group: nacos provider应用
  title: sparkzxl nacos provider在线文档
  terms-of-service-url: https://www.sparksys.top
  version: 1.0
  license: Powered By sparkzxl
  license-url: https://github.com/zhouxinlei828
  contact:
    name: zhouxinlei
    email: zhouxinlei298@163.com
    url: https://github.com/zhouxinlei828
```

6.2.2 简单测试 下面，我们使用命令行参数进行 --spring.profiles.active 配置项，实现不同环境，读取不同配置文件。

① 先配置 --spring.profiles.active 为 dev，设置 NacosProviderEnvApplication 读取 application-dev.yaml 配置文件。如下图所示：

![nacos-namespace-swtich-env.png](https://oss.sparksys.top/sparkzxl-framework/nacos-namespace-swtich-env.png)

之后通过 NacosProviderEnvApplication 启动服务提供者。

② 打开 Nacos 控制台，可以在服务列表看到服务 nacos-provider 注册在命名空间 dev 下。如下图：

![nacos-console-swtich-env.png](https://oss.sparksys.top/sparkzxl-framework/nacos-console-swtich-env.png)

### 6.3 搭建服务消费者

从**3.2 搭建服务消费者**小节的 labx-01-sca-nacos-discovery-demo01-consumer 项目，复制出 labx-01-sca-nacos-discovery-demo02-consumer 项目。然后在其上进行修改，方便搭建~

#### 6.3.1 配置文件

> 友情提示：和**6.2.1 配置文件**小节的内容是基本一致的，重复唠叨一遍。

修改 application.yaml 配置文件，将 Nacos Discovery 配置项删除，稍后添加在不同环境的配置文件中。配置如下：

```yaml
spring:
  application:
    name: nacos-consumer # Spring 应用名

server:
  port: 8081 # 服务器端口。默认为 8080
```

创建开发环境使用的 application-dev.yaml 配置文件，增加 Namespace 为 **dev** 的 Nacos Discovery 配置项。配置如下：

```yaml
spring:
  cloud:
    nacos:
      # Nacos 作为注册中心的配置项，对应 NacosDiscoveryProperties 配置类
      discovery:
        server-addr: 47.114.40.129:8848 # Nacos 服务器地址
        service: ${spring.application.name} # 注册到 Nacos 的服务名。默认值为 ${spring.application.name}。
        namespace: 5acd2f93-cd2d-420a-afe4-15d7cf5b3b0b # Nacos 命名空间 uat 的编号
knife4j:
  enable: true
  description: sparkzxl nacos consumer在线文档
  base-package: com.github.sparkzxl.nacos.controller
  group: nacos consumer应用
  title: sparkzxl nacos consumer在线文档
  terms-of-service-url: https://www.sparksys.top
  version: 1.0
  license: Powered By sparkzxl
  license-url: https://github.com/zhouxinlei828
  contact:
    name: zhouxinlei
    email: zhouxinlei298@163.com
    url: https://github.com/zhouxinlei828
```

创建测试环境使用的 application-uat.yaml 配置文件，增加 Namespace 为 uat 的 Nacos Discovery 配置项。配置如下：

```yaml
spring:
  cloud:
    nacos:
      # Nacos 作为注册中心的配置项，对应 NacosDiscoveryProperties 配置类
      discovery:
        server-addr: 47.114.40.129:8848 # Nacos 服务器地址
        service: ${spring.application.name} # 注册到 Nacos 的服务名。默认值为 ${spring.application.name}。
        namespace: 6657f6f1-6c27-48c0-8187-9f675237144a # Nacos 命名空间 uat 的编号
knife4j:
  enable: true
  description: sparkzxl nacos consumer在线文档
  base-package: com.github.sparkzxl.nacos.controller
  group: nacos consumer应用
  title: sparkzxl nacos consumer在线文档
  terms-of-service-url: https://www.sparksys.top
  version: 1.0
  license: Powered By sparkzxl
  license-url: https://github.com/zhouxinlei828
  contact:
    name: zhouxinlei
    email: zhouxinlei298@163.com
    url: https://github.com/zhouxinlei828
```

#### 6.2.3 简单测试

下面，我们使用命令行参数进行 --spring.profiles.active 配置项，实现不同环境，读取不同配置文件。

① 先配置 --spring.profiles.active 为 dev，设置 NacosConsumerEnvApplication 读取 application-dev.yaml 配置文件。如下图所示：

![nacos-console-swtich-env1.png](https://oss.sparksys.top/sparkzxl-framework/nacos-console-swtich-env1.png)

之后通过 NacosConsumerEnvApplication 启动服务消费者。

访问服务消费者的 http://127.0.0.1:8081/hello?name=helloWorld 接口，返回结果为 "consumer:provider:helloWorld"。说明，调用远程的服务提供者【成功】。

![nacos-consumer-httpRequest.png](https://oss.sparksys.top/sparkzxl-framework/nacos-consumer-httpRequest.png)

② 再配置 --spring.profiles.active 为 uat，设置 NacosConsumerEnvApplication 读取 application-uat.yaml 配置文件。如下图所示：

![nacos-console-swtich-env2.png](https://oss.sparksys.top/sparkzxl-framework/nacos-console-swtich-env2.png)

之后通过 NacosConsumerEnvApplication 启动服务消费者。

访问服务消费者的 http://127.0.0.1:8081/hello?name=helloWorld 接口，返回结果为 报错提示 "获取不到实例"。说明，调用远程的服务提供者【失败】。

原因是，虽然说服务 demo-provider 已经启动，因为其注册在 Nacos 的 Namespace 为 dev，这就导致第 ① 步启动的服务 demo-consumer 可以调用到该服务，而第② 步启动的服务 nacos-consumer 无法调用到该服务。

即，我们可以通过 Nacos 的 Namespace 实现不同环境下的服务隔离。未来，在开源版本 Nacos 权限完善之后，每个 Namespace 提供不同的 AccessKey、SecretKey，保证只有知道账号密码的服务，才能连到对应的 Namespace，进一步提升安全性。

## 7. 监控端点

> 示例代码对应仓库：
> - 服务提供者：[sparkzxl-nacos-discovery-provider](https://github.com/zhouxinlei828/sparkzxl-cloud-learning/tree/main/sparkzxl-nacos-learn/sparkzxl-nacos-discovery-provider)
> - 服务消费者：[sparkzxl-nacos-discovery-consumer](https://github.com/zhouxinlei828/sparkzxl-cloud-learning/tree/main/sparkzxl-nacos-learn/sparkzxl-nacos-discovery-consumer)

Nacos Discovery 基于 Spring Boot Actuator，提供了自定义监控端点 nacos-discovery，获取 Nacos Discovery 配置项，和订阅的服务信息。

同时，Nacos Discovery 拓展了 Spring Boot Actuator 内置的 health 端点，通过自定义的 **NacosDiscoveryHealthIndicator**，获取和 Nacos 服务器的连接状态。

> 友情提示：对 Spring Boot Actuator 不了解的胖友，可以后续阅读[《芋道 Spring Boot 监控端点 Actuator 入门》](https://www.iocoder.cn/Spring-Boot/Actuator/?self)文章。

下面，我们来搭建一个 Nacos Discovery 监控端点的示例。步骤如下：

- 首先，搭建一个服务提供者 nacos-provider ，注册服务到 Nacos 中。
- 然后，搭建一个服务消费者 nacos-consumer，调用服务提供者 demo-provider 提供的 HTTP 接口。同时，配置开启服务消费者的 Nacos Discovery 监控端点。
- 最后，访问服务消费者的 Nacos Discovery 监控端点，查看下返回的监控数据。 7.1 搭建服务提供者 直接复用**3.1 搭建服务提供者**
  小节的 [sparkzxl-nacos-discovery-provider](https://github.com/zhouxinlei828/sparkzxl-cloud-learning/tree/main/sparkzxl-nacos-learn/sparkzxl-nacos-discovery-provider)
  项目即可。

因为 **sparkzxl-nacos-discovery-provider** 项目没有从 Nacos 订阅任何服务，无法完整看到 nacos-discovery 端点的完整效果，所以我们暂时不配置该项目的 Nacos Discovery 监控端点。

不过实际项目中，配置下开启 Nacos Discovery 监控端点 还是可以的，至少可以看到 Nacos Discovery 配置项。

### 7.2 搭建服务消费者

从**3.2 搭建服务消费者**小节的 sparkzxl-nacos-discovery-consumer 项目，复用 sparkzxl-nacos-discovery-consumer 项目。然后在其上进行修改，方便搭建~

#### 7.2.1 引入依赖

在 pom.xml 文件中，额外引入 Spring Boot Actuator 相关依赖。代码如下：

```xml
<!-- 实现对 Actuator 的自动化配置 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

#### 7.2.2 配置文件

修改 **application.yaml** 配置文件，增加 Spring Boot Actuator 配置项。配置如下：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: '*' # 需要开放的端点。默认值只打开 health 和 info 两个端点。通过设置 * ，可以开放所有端点。
  endpoint:
    # Health 端点配置项，对应 HealthProperties 配置类
    health:
      enabled: true # 是否开启。默认为 true 开启。
      show-details: ALWAYS # 何时显示完整的健康信息。默认为 NEVER 都不展示。可选 WHEN_AUTHORIZED 当经过授权的用户；可选 ALWAYS 总是展示。
```

每个配置项的作用，胖友看下艿艿添加的注释。如果还不理解的话，后续看下[《芋道 Spring Boot 监控端点 Actuator 入门》](https://www.iocoder.cn/Spring-Boot/Actuator/?self)文章。

### 7.3 简单测试

① 通过 NacosProviderApplication 启动服务提供者，通过 NacosConsumerApplication 启动服务消费者。

之后，访问服务消费者的 http://127.0.0.1:8081/hello?name=helloWorld 接口，返回结果为 "consumer:provider:helloWorld"。a说明，调用远程的服务提供者成功。

② 访问服务消费者的 nacos-discovery 监控端点 http://127.0.0.1:8081/actuator/nacos-discovery，返回结果如下图：

![nacos-actuator.png](https://oss.sparksys.top/sparkzxl-framework/nacos-actuator.png)

理论来说，"subscribe" 字段应该返回订阅的服务 demo-provider 的信息，结果这里返回的是空。后来翻看了下源码，是需要主动向 Nacos EventDispatcher 注册 EventListener 才可以。咳咳咳，感觉这个设定有点神奇~

③ 访问服务消费者的 health 监控端点 http://127.0.0.1:8081/actuator/health，返回结果如下图：

![nacos-actuator-1.png](https://oss.sparksys.top/sparkzxl-framework/nacos-actuator-1.png)

#### 666. 彩蛋

至此，我们已经完成 Spring Cloud Alibaba Nacos Discovery 的学习。如下是 Nacos 相关的官方文档：

- [《Nacos 官方文档》](https://nacos.io/zh-cn/docs/what-is-nacos.html)
- [《Spring Cloud Alibaba 官方文档 —— Nacos Discovery》](https://github.com/alibaba/spring-cloud-alibaba/wiki/Nacos-discovery)
- [《Spring Cloud Alibaba 官方示例 —— Nacos Discovery》](https://github.com/alibaba/spring-cloud-alibaba/blob/master/spring-cloud-alibaba-examples/nacos-example/nacos-discovery-example/readme-zh.md)

另外，想要在 Spring Boot 项目中使用 Nacos 作为注册中心的胖友，可以阅读[《芋道 Spring Boot 注册中心 Nacos 入门》](https://www.iocoder.cn/Spring-Boot/registry-nacos/?self)文章。

# 公众号

学习不走弯路，关注公众号「凛冬王昭君」

![wechat-sparkzxl.jpg](https://oss.sparksys.top/sparkzxl-framework/wechat-sparkzxl.jpg)
