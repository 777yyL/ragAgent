package com.company.knowledge.version.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.knowledge.version.entity.VersionMeta;
import org.apache.ibatis.annotations.Mapper;

/**
 * 版本元数据 Mapper。
 *
 * <p>统计/复杂查询走 XML；CRUD 由 {@link BaseMapper} 提供。
 */
@Mapper
public interface VersionMetaMapper extends BaseMapper<VersionMeta> {
}
