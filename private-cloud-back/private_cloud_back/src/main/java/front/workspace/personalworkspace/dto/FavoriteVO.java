package front.workspace.personalworkspace.dto;

import java.time.LocalDateTime;

public class FavoriteVO {

    private Long id;
    private Long targetId;
    private Integer targetType;
    private String targetName;
    private String fileType;
    private LocalDateTime createTime;

    public FavoriteVO() {}

    public FavoriteVO(Long id, Long targetId, Integer targetType, String targetName, String fileType, LocalDateTime createTime) {
        this.id = id;
        this.targetId = targetId;
        this.targetType = targetType;
        this.targetName = targetName;
        this.fileType = fileType;
        this.createTime = createTime;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public Integer getTargetType() { return targetType; }
    public void setTargetType(Integer targetType) { this.targetType = targetType; }
    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
