package com.company.knowledge.permission.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.company.knowledge.permission.entity.PermissionPolicy;
import com.company.knowledge.permission.mapper.PermissionPolicyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 权限策略 Service（四维：subject + object + actions + inherit）。
 *
 * <p>典型用法：
 *
 * <ul>
 *   <li>{@link #listBySubject} - 查「某角色/部门/用户」所有策略</li>
 *   <li>{@link #listByObject} - 查「某 dataset/category/tag/doc」被授予了哪些主体</li>
 *   <li>{@link #create} - 授权（一次一行策略）</li>
 *   <li>{@link #delete} - 撤权</li>
 * </ul>
 *
 * <p>不维护缓存；权限索引计算由 {@code PermissionIndexService} 负责。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionPolicyService {

    private final PermissionPolicyMapper policyMapper;

    /**
     * 创建一条策略。
     *
     * @param policy 待创建策略；{@code id/createdAt} 由 DB 生成
     * @return 带 DB 主键的策略对象
     */
    @Transactional(rollbackFor = Exception.class)
    public PermissionPolicy create(PermissionPolicy policy) {
        policyMapper.insert(policy);
        log.info("policy created: subject={}/{} object={}/{} actions={}",
                policy.getSubjectType(), policy.getSubjectId(),
                policy.getObjectType(), policy.getObjectValue(),
                policy.getActions());
        return policy;
    }

    /**
     * 列出全部策略。
     *
     * @return 策略列表，按 id 升序
     */
    public List<PermissionPolicy> listAll() {
        return policyMapper.selectList(Wrappers.<PermissionPolicy>lambdaQuery()
                .orderByAsc(PermissionPolicy::getId));
    }

    /**
     * 按「主体类型 + 主体 ID」查询策略。
     *
     * @param subjectType ROLE / DEPT / USER
     * @param subjectId   角色 code / orgIndexCode / personId
     * @return 策略列表
     */
    public List<PermissionPolicy> listBySubject(String subjectType, String subjectId) {
        return policyMapper.selectList(Wrappers.<PermissionPolicy>lambdaQuery()
                .eq(PermissionPolicy::getSubjectType, subjectType)
                .eq(PermissionPolicy::getSubjectId, subjectId)
                .orderByAsc(PermissionPolicy::getId));
    }

    /**
     * 按「客体类型 + 客体值」查询策略。
     *
     * @param objectType  DATASET / CATEGORY / TAG / DOC
     * @param objectValue dataset_id / category_code / tag / doc_id
     * @return 策略列表
     */
    public List<PermissionPolicy> listByObject(String objectType, String objectValue) {
        return policyMapper.selectList(Wrappers.<PermissionPolicy>lambdaQuery()
                .eq(PermissionPolicy::getObjectType, objectType)
                .eq(PermissionPolicy::getObjectValue, objectValue)
                .orderByAsc(PermissionPolicy::getId));
    }

    /**
     * 按 id 删除策略。
     *
     * @param id 策略主键
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        policyMapper.deleteById(id);
        log.info("policy deleted: id={}", id);
    }
}
