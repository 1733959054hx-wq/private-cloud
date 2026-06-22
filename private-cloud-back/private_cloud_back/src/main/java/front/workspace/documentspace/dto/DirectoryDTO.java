package front.workspace.documentspace.dto;

public class DirectoryDTO {

    private Long id;
    private String dirName;
    private Long parentId;
    private Long departmentId;
    private Integer spaceType;
    private Long spaceId;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDirName() { return dirName; }
    public void setDirName(String dirName) { this.dirName = dirName; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Integer getSpaceType() { return spaceType; }
    public void setSpaceType(Integer spaceType) { this.spaceType = spaceType; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
