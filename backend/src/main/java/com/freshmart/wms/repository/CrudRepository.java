package com.freshmart.wms.repository;

import com.freshmart.wms.domain.entity.BaseEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 通用仓储接口。
 *
 * <p><b>设计意图</b>：本接口的方法签名刻意与 MyBatis-Plus 的 {@code BaseMapper} 保持语义一致。
 * 当前由 {@link InMemoryCrudRepository} 提供内存实现；后续接入数据库时，
 * 只需新增 {@code XxxMapper extends BaseMapper<Xxx>}，让仓储实现类改为委托 Mapper，
 * Service 层代码无需任何改动。
 *
 * @param <T> 实体类型
 */
public interface CrudRepository<T extends BaseEntity> {

    /** 新增或更新（按 id 是否为 null 判定）。 */
    T save(T entity);

    /** 批量新增。 */
    List<T> saveAll(Collection<T> entities);

    /** 按主键查询。 */
    Optional<T> findById(Long id);

    /** 按主键删除。 */
    boolean deleteById(Long id);

    /** 全量查询。 */
    List<T> findAll();

    /** 条件查询。 */
    List<T> find(Predicate<T> predicate);

    /** 条件查询（返回首个匹配项）。 */
    Optional<T> findOne(Predicate<T> predicate);

    /** 条件统计。 */
    long count(Predicate<T> predicate);

    /** 全量条数。 */
    long count();

    /** 是否存在匹配项。 */
    boolean exists(Predicate<T> predicate);

    /** 清空（仅内存实现有意义，接库后可移除）。 */
    void clear();

    /** 实体类型，便于日志与调试。 */
    Class<T> getEntityClass();
}
