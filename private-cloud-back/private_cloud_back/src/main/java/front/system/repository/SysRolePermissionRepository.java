package front.system.repository;

import front.system.entity.SysRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SysRolePermissionRepository extends JpaRepository<SysRolePermission, Long> {

    List<SysRolePermission> findByRoleId(Long roleId);

    void deleteByRoleId(Long roleId);
}
