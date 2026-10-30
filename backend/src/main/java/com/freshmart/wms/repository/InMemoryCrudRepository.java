package com.freshmart.wms.repository;

import com.freshmart.wms.domain.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 内存仓储实现。
 *
 * <p>用 {@link ConcurrentHashMap} 承载数据、{@link AtomicLong} 发号，
 * 保证在 Web 容器多线程环境下不会出现并发写坏。
 *
 * <p><b>注意</b>：数据仅存活于 JVM 内存，应用重启即回到种子状态。
 * 这是「未接数据库」阶段的既定取舍，不是缺陷。
 *
 * @param <T> 实体类型
 */
public class InMemoryCrudRepository<T extends BaseEntity> implements CrudRepository<T> {

    private final Class<T> entityClass;
    private final Map<Long, T> store = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(0);

    public InMemoryCrudRepository(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    @Override
    public T save(T entity) {
        if (entity == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (entity.getId() == null) {
            entity.setId(idSeq.incrementAndGet());
            if (entity.getCreateTime() == null) {
                entity.setCreateTime(now);
            }
        }
        entity.setUpdateTime(now);
        if (entity.getDeleted() == null) {
            entity.setDeleted(0);
        }
        store.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public List<T> saveAll(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<>();
        }
        return entities.stream().map(this::save).collect(Collectors.toList());
    }

    @Override
    public Optional<T> findById(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(store.get(id));
    }

    @Override
    public boolean deleteById(Long id) {
        return id != null && store.remove(id) != null;
    }

    @Override
    public List<T> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(BaseEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Override
    public List<T> find(Predicate<T> predicate) {
        return store.values().stream()
                .filter(predicate)
                .sorted(Comparator.comparing(BaseEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<T> findOne(Predicate<T> predicate) {
        return store.values().stream().filter(predicate).findFirst();
    }

    @Override
    public long count(Predicate<T> predicate) {
        return store.values().stream().filter(predicate).count();
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public boolean exists(Predicate<T> predicate) {
        return store.values().stream().anyMatch(predicate);
    }

    @Override
    public void clear() {
        store.clear();
        idSeq.set(0);
    }

    @Override
    public Class<T> getEntityClass() {
        return entityClass;
    }
}
