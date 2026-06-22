package front.intelligence.comment.repository;

import front.intelligence.comment.entity.DocComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocCommentRepository extends JpaRepository<DocComment, Long> {

    List<DocComment> findByFileIdAndDeletedOrderByCreateTimeDesc(Long fileId, Integer deleted);

    List<DocComment> findByParentIdAndDeletedOrderByCreateTimeAsc(Long parentId, Integer deleted);
}
