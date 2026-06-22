package front.system.repository;

import front.system.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsernameAndDeleted(String username, Integer deleted);

    Optional<SysUser> findByUsername(String username);

    boolean existsByUsername(String username);
}
