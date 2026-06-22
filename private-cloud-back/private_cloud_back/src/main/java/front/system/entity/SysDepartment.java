package front.system.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "sys_department")
public class SysDepartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dept_name", nullable = false, length = 100)
    private String deptName;

    @Column(name = "parent_id")
    private Long parentId = 0L;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "company_id")
    private Long companyId = 0L;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
}
