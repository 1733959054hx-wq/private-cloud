package front.system.repository;

import front.system.entity.SysPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SysPermissionRepository extends JpaRepository<SysPermission, Long> {

    List<SysPermission> findByParentIdOrderBySortOrder(Long parentId);

    List<SysPermission> findByType(Integer type);
}
