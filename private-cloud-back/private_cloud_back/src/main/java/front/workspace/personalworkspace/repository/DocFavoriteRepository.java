package front.workspace.personalworkspace.repository;

import front.workspace.personalworkspace.entity.DocFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocFavoriteRepository extends JpaRepository<DocFavorite, Long> {

    List<DocFavorite> findByUserIdOrderByCreateTimeDesc(Long userId);

    Optional<DocFavorite> findByUserIdAndTargetIdAndTargetType(Long userId, Long targetId, Integer targetType);

    void deleteByUserIdAndTargetIdAndTargetType(Long userId, Long targetId, Integer targetType);

    long countByUserId(Long userId);

    @Query("SELECT f.targetId FROM DocFavorite f WHERE f.userId = :userId AND f.targetId IN :targetIds AND f.targetType = :targetType")
    List<Long> findFavoriteTargetIdsByUserAndTargets(@Param("userId") Long userId, @Param("targetIds") List<Long> targetIds, @Param("targetType") Integer targetType);
}
