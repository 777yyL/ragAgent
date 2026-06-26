package com.company.knowledge.version.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.version.entity.VersionMeta;
import com.company.knowledge.version.mapper.VersionMetaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 版本切换服务。
 *
 * <p>核心规则：同一个 {@code datasetId} 在 ONLINE 环境下只能有一个版本。
 * 切换 {@link #switchToOnline(Long)} 时：旧 ONLINE → TEST，新版本 → ONLINE。
 *
 * <p>回滚 {@link #rollback(Long)}：本质是切回 parent 版本为 ONLINE（当前版本 → TEST）。
 *
 * <p>所有非法操作抛 {@link BizException}，业务码：
 * <ul>
 *   <li>{@code 4051} 版本不存在</li>
 *   <li>{@code 4052} 无可回滚的父版本</li>
 *   <li>{@code 4053} 同环境已存在版本（创建时冲突）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VersionSwitchService {

    public static final int CODE_VERSION_NOT_FOUND = 4051;
    public static final int CODE_NO_PARENT = 4052;
    public static final int CODE_ENV_CONFLICT = 4053;

    private final VersionMetaMapper mapper;

    /**
     * 版本列表。
     *
     * @param datasetId 可选，按 dataset 过滤；为 null 返回全部
     */
    public List<VersionMeta> list(String datasetId) {
        LambdaQueryWrapper<VersionMeta> w = new LambdaQueryWrapper<>();
        if (datasetId != null && !datasetId.isEmpty()) {
            w.eq(VersionMeta::getDatasetId, datasetId);
        }
        w.orderByDesc(VersionMeta::getId);
        return mapper.selectList(w);
    }

    /**
     * 当前线上版本（env=ONLINE）。同一个 dataset 期望只有一个 ONLINE。
     */
    public VersionMeta getCurrentOnline(String datasetId) {
        LambdaQueryWrapper<VersionMeta> w = new LambdaQueryWrapper<>();
        w.eq(VersionMeta::getDatasetId, datasetId)
                .eq(VersionMeta::getEnv, VersionMeta.Env.ONLINE)
                .last("LIMIT 1");
        return mapper.selectOne(w);
    }

    /**
     * 创建新版本。同 dataset + 同 env 不允许重复（避免双 ONLINE）。
     *
     * @return 已创建的版本（含回填主键）
     */
    @Transactional
    public VersionMeta create(String datasetId, String env, String label, String changeLog) {
        if (env == null) {
            throw BizException.of(400, "env is required");
        }
        // 同环境已存在则冲突（ONLINE 单例；TEST 仅允许一个，避免列表混乱）
        LambdaQueryWrapper<VersionMeta> exists = new LambdaQueryWrapper<>();
        exists.eq(VersionMeta::getDatasetId, datasetId)
                .eq(VersionMeta::getEnv, env);
        if (mapper.selectCount(exists) > 0) {
            throw BizException.of(CODE_ENV_CONFLICT,
                    "dataset " + datasetId + " already has env=" + env + " version");
        }

        // parentId 取当前 ONLINE（若创建的是 ONLINE，自动串链）
        Long parentId = null;
        if (VersionMeta.Env.ONLINE.equals(env)) {
            VersionMeta current = getCurrentOnline(datasetId);
            if (current != null) {
                parentId = current.getId();
            }
        }

        VersionMeta v = new VersionMeta();
        v.setDatasetId(datasetId);
        v.setEnv(env);
        v.setVersionLabel(label);
        v.setParentId(parentId);
        v.setChangeLog(changeLog);
        v.setPublishedBy(currentUser());
        v.setPublishedAt(LocalDateTime.now());
        mapper.insert(v);
        log.info("version created: id={}, dataset={}, env={}, label={}",
                v.getId(), datasetId, env, label);
        return v;
    }

    /**
     * 切到线上：旧 ONLINE → TEST，指定版本 → ONLINE。
     *
     * <p>调用方负责确认新版本已就绪（chunk/文档已同步到 RAGFlow）。
     *
     * @param versionId 即将上线的版本 id（env 可以是任意）
     */
    @Transactional
    public VersionMeta switchToOnline(Long versionId) {
        VersionMeta target = requireVersion(versionId);

        VersionMeta current = getCurrentOnline(target.getDatasetId());
        if (current != null && !current.getId().equals(target.getId())) {
            current.setEnv(VersionMeta.Env.TEST);
            mapper.updateById(current);
            log.info("version {} -> TEST (replaced by {})", current.getId(), target.getId());
        }
        // 串链：target 的 parent 设为旧 ONLINE（若尚未设置）
        if (target.getParentId() == null && current != null) {
            target.setParentId(current.getId());
        }
        target.setEnv(VersionMeta.Env.ONLINE);
        target.setPublishedAt(LocalDateTime.now());
        mapper.updateById(target);
        log.info("version {} -> ONLINE (dataset={})", target.getId(), target.getDatasetId());
        return target;
    }

    /**
     * 切到测试环境：指定版本 → TEST。若它当前是 ONLINE，则要求有 parent 可继承为 ONLINE。
     *
     * <p>简化策略：直接把 env 改成 TEST；若该版本是当前 ONLINE，则视为"下线"，
     * 由调用方后续切换其它版本为 ONLINE。
     */
    @Transactional
    public VersionMeta switchToTest(Long versionId) {
        VersionMeta target = requireVersion(versionId);
        target.setEnv(VersionMeta.Env.TEST);
        mapper.updateById(target);
        log.info("version {} -> TEST", target.getId());
        return target;
    }

    /**
     * 回滚到指定版本：将其变为 ONLINE（同时旧 ONLINE → TEST）。
     *
     * <p>语义：把 {@code versionId} 这个旧版本重新置为 ONLINE，等价于
     * {@link #switchToOnline(Long)}；独立方法便于业务语义清晰。
     *
     * @param versionId 要回滚到的目标版本（通常是历史 ONLINE）
     */
    @Transactional
    public VersionMeta rollback(Long versionId) {
        VersionMeta target = requireVersion(versionId);
        // 没有 parent 表示这是首个版本，没有可回滚的目标——但 rollback 到自身允许，
        // 仅当 parentId 为 null 且 caller 想回滚到更早的版本时报错。
        // 这里简化：rollback 等价于 switchToOnline(target)，自身是 ONLINE 则报错。
        if (VersionMeta.Env.ONLINE.equals(target.getEnv())) {
            throw BizException.of(CODE_NO_PARENT,
                    "version " + versionId + " is already ONLINE, cannot rollback to itself");
        }
        return switchToOnline(versionId);
    }

    // ===== 辅助 =====

    private VersionMeta requireVersion(Long id) {
        VersionMeta v = mapper.selectById(id);
        if (v == null) {
            throw BizException.of(CODE_VERSION_NOT_FOUND, "version not found: " + id);
        }
        return v;
    }

    private String currentUser() {
        // UserContext 可能在非 HTTP 线程为空（如 Job），宽容处理
        try {
            return com.company.knowledge.common.context.UserContext.get() == null
                    ? null
                    : com.company.knowledge.common.context.UserContext.require().getPersonId();
        } catch (Exception e) {
            return null;
        }
    }
}
