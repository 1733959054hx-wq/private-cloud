package front.system.repository;

import front.system.entity.SysDepartment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SysDepartmentRepository extends JpaRepository<SysDepartment, Long> {

    List<SysDepartment> findByParentIdOrderBySortOrder(Long parentId);
}
