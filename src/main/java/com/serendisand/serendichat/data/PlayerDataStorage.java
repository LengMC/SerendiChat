package com.serendisand.serendichat.data;

import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.db.DataManager;
import cn.arkmillion.core.db.RelationalDB;
import cn.arkmillion.core.enums.SyncMode;
import cn.arkmillion.core.factory.DataManagerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static cn.arkmillion.core.condition.Condition.where;

/**
 * 基于 Arks DataManager（SQLite 适配器）的玩家数据存储后端。
 * 线程约定：除构造器与 close 外，全部操作由 PlayerDataManager 的单线程保存池串行调用，
 * 因此 SQLite 单连接池（poolSize=1）不会产生锁竞争。
 */
final class PlayerDataStorage implements AutoCloseable {

    private static final String ALIAS = "serendichat";

    private final DataManager manager;
    private final RelationalDB db;

    PlayerDataStorage(Path dbFile) {
        // 显式传入本类的 ClassLoader：Fabric 的线程上下文类加载器不保证能看到模组嵌套 jar，
        // 而 DataManagerFactory 通过 ServiceLoader 发现各数据库适配器
        DataManagerConfig cfg = DataManagerConfig.builder()
                .sqlite(dbFile.toString())
                    .alias(ALIAS)
                    .poolSize(1)
                    .busyTimeout(5000)
                    .build()
                .build();
        this.manager = DataManagerFactory.create(cfg, PlayerDataStorage.class.getClassLoader());
        this.db = manager.getRelationalDB(ALIAS);
        // UPDATE 模式：表不存在则建表，已存在则只增量补列/补索引，幂等且不破坏数据
        this.db.syncSchema(PlayerDataEntity.class, SyncMode.UPDATE);
    }

    /** 按主键读取单个玩家行；不存在返回 null。 */
    PlayerDataEntity findById(String uuid) {
        return db.selectById(PlayerDataEntity.class, uuid);
    }

    /** 全量读取所有玩家行；表为空时返回空列表（仅迁移与离线批量查询使用）。 */
    List<PlayerDataEntity> loadAll() {
        return db.select(PlayerDataEntity.class, null);
    }

    /**
     * 按 UUID 逐行 upsert（先 update，未命中再 insert），整批包在一个事务中，
     * 任一行失败整体回滚，避免半写状态。
     */
    void upsertAll(Collection<PlayerDataEntity> rows) {
        if (rows == null || rows.isEmpty()) return;
        boolean ownTx = !db.isInTransaction();
        if (ownTx) {
            db.beginTransaction();
        }
        try {
            for (PlayerDataEntity row : rows) {
                if (db.update(row) == 0) {
                    db.insert(row);
                }
            }
            if (ownTx) {
                db.commit();
            }
        } catch (RuntimeException e) {
            if (ownTx && db.isInTransaction()) {
                try {
                    db.rollback();
                } catch (RuntimeException re) {
                    e.addSuppressed(re);
                }
            }
            throw e;
        }
    }

    /**
     * 将指定玩家的手动星数置为 NULL（对应旧版 resetStars 直接删除键的行为）。
     * update(entity) 会跳过 null 字段，因此星数重置必须走条件更新显式写 NULL。
     */
    void clearManualStars(String uuid) {
        db.update(PlayerDataEntity.class,
                where("uuid").eq(uuid).build(),
                Collections.<String, Object>singletonMap("manual_stars", null));
    }

    @Override
    public void close() {
        manager.close();
    }
}
