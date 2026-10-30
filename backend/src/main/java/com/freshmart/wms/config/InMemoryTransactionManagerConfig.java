package com.freshmart.wms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * 无数据库模式下的空事务管理器。
 *
 * <p><b>为什么需要它</b>：Service 层方法上标了 {@code @Transactional}（这是接库后必需的），
 * 但当前没有 DataSource，Spring Boot 不会自动配置 {@code PlatformTransactionManager}，
 * 一旦调用带事务注解的方法就会抛 {@code NoSuchBeanDefinitionException}。
 *
 * <p>这里提供一个「什么都不做」的事务管理器：进入即开始、结束即提交，不做任何回滚。
 * 内存仓储本身是线程安全的，且种子数据不需要事务语义，因此这是安全的。
 *
 * <p><b>接库后如何让位</b>：本 Bean 标了 {@link ConditionalOnMissingBean}，
 * 一旦 Spring Boot 因存在 DataSource 而自动配置了 {@code DataSourceTransactionManager}，
 * 本 Bean 便不会再注册，{@code @Transactional} 自动切换为真实事务。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "wms.mock.enabled", havingValue = "true", matchIfMissing = true)
public class InMemoryTransactionManagerConfig {

    @Bean
    @ConditionalOnMissingBean(PlatformTransactionManager.class)
    public PlatformTransactionManager inMemoryTransactionManager() {
        log.info("当前为无数据库模式，已启用空实现事务管理器（@Transactional 不会真正回滚）");
        return new AbstractPlatformTransactionManager() {

            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
                // 无库模式：不开启真实事务
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
                // 无库模式：不做提交
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
                // 无库模式：不做回滚
            }
        };
    }
}
