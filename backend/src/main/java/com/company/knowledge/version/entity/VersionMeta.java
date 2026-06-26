package com.company.knowledge.version.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 版本元数据，对应 {@code version_meta} 表。
 *
 * <p>一个 {@code datasetId} 可有多个版本，分别标记为 ONLINE 或 TEST 环境。
 * 通过 {@link Env} 区分当前线上版本与测试版本，{@link #parentId} 串起回滚链。
 */
@Data
@TableName("version_meta")
public class VersionMeta {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的 RAGFlow dataset id */
    private String datasetId;

    /** 环境标记：{@link Env#ONLINE} / {@link Env#TEST} */
    private String env;

    /** 版本标签，如 v1.0、v2.3-rc1 */
    private String versionLabel;

    /** 上一版本 id（用于回滚链） */
    private Long parentId;

    /** 变更说明 */
    private String changeLog;

    /** 发布人 personId */
    private String publishedBy;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    /** 环境枚举常量，与数据库列值保持一致 */
    public static final class Env {
        public static final String ONLINE = "ONLINE";
        public static final String TEST = "TEST";
        private Env() {}
    }
}
