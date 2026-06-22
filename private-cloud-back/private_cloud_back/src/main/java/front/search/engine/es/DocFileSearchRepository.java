package front.search.engine.es;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import java.util.List;

/**
 * ES 文档搜索仓库
 */
public interface DocFileSearchRepository extends ElasticsearchRepository<DocFileDocument, Long> {

    /** 按上传者精确查找 */
    List<DocFileDocument> findByUploaderName(String uploaderName);

    /** 按部门ID查找 */
    List<DocFileDocument> findByDepartmentId(Long departmentId);
}
