package front.intelligence.ai.repository;

import front.intelligence.ai.entity.GeneratedDoc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GeneratedDocRepository extends JpaRepository<GeneratedDoc, Long> {

    List<GeneratedDoc> findByDepartmentIdAndStatusOrderByCreateTimeDesc(Long departmentId, Integer status);

    List<GeneratedDoc> findByDepartmentIdIsNullAndStatusOrderByCreateTimeDesc(Integer status);

    List<GeneratedDoc> findByCreatorIdAndStatusOrderByCreateTimeDesc(Long creatorId, Integer status);

    List<GeneratedDoc> findByStatusOrderByCreateTimeDesc(Integer status);

    List<GeneratedDoc> findByDepartmentIdAndStatusInOrderByCreateTimeDesc(Long departmentId, List<Integer> statuses);

    List<GeneratedDoc> findByStatusInOrderByCreateTimeDesc(List<Integer> statuses);

    List<GeneratedDoc> findByCreatorIdAndStatusInOrderByCreateTimeDesc(Long creatorId, List<Integer> statuses);
}
