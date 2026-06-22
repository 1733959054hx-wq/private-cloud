package front.system.repository;

import front.system.entity.SysUserCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SysUserCredentialRepository extends JpaRepository<SysUserCredential, String> {
}
