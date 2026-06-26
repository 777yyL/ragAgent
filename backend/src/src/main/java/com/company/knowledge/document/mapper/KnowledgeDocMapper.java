package com.company.knowledge.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.knowledge.document.entity.KnowledgeDoc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface KnowledgeDocMapper extends BaseMapper<KnowledgeDoc> {

    /** 按 ragflowDocId 批量查审核状态，返回 docId → status 映射 */
    @Select("<script>" +
            "SELECT ragflow_doc_id, audit_status FROM knowledge_doc WHERE ragflow_doc_id IN " +
            "<foreach item='id' collection='docIds' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<Map<String, Object>> selectStatusByDocIds(@Param("docIds") List<String> docIds);
}
