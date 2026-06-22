package front.workspace.documentspace.dto;

public class ChunkUploadDTO {

    private String fileId;
    private String fileName;
    private Integer totalChunks;
    private Integer chunkIndex;
    private Long fileSize;
    private Long directoryId;
    private Long departmentId;

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Integer getTotalChunks() { return totalChunks; }
    public void setTotalChunks(Integer totalChunks) { this.totalChunks = totalChunks; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Long getDirectoryId() { return directoryId; }
    public void setDirectoryId(Long directoryId) { this.directoryId = directoryId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
}
