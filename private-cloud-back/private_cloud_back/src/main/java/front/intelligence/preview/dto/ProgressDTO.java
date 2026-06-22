package front.intelligence.preview.dto;

public class ProgressDTO {
    private Long fileId;
    private Integer progressType;
    private Double progressValue;

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public Integer getProgressType() { return progressType; }
    public void setProgressType(Integer progressType) { this.progressType = progressType; }
    public Double getProgressValue() { return progressValue; }
    public void setProgressValue(Double progressValue) { this.progressValue = progressValue; }
}
