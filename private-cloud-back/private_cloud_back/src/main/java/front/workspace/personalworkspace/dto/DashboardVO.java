package front.workspace.personalworkspace.dto;

import java.util.List;

public class DashboardVO {

    private long totalDocs;
    private long monthlyUploads;
    private long pendingApprovals;
    private long favoriteCount;
    private long unreadNotifications;
    private List<RecentFile> recentFiles;
    private List<PendingApproval> pendingApprovalList;
    private StorageStats storageStats;
    private List<ContributionDay> contributionHeatmap;

    public long getTotalDocs() {
        return totalDocs;
    }

    public void setTotalDocs(long totalDocs) {
        this.totalDocs = totalDocs;
    }

    public long getMonthlyUploads() {
        return monthlyUploads;
    }

    public void setMonthlyUploads(long monthlyUploads) {
        this.monthlyUploads = monthlyUploads;
    }

    public long getPendingApprovals() {
        return pendingApprovals;
    }

    public void setPendingApprovals(long pendingApprovals) {
        this.pendingApprovals = pendingApprovals;
    }

    public long getFavoriteCount() {
        return favoriteCount;
    }

    public void setFavoriteCount(long favoriteCount) {
        this.favoriteCount = favoriteCount;
    }

    public long getUnreadNotifications() {
        return unreadNotifications;
    }

    public void setUnreadNotifications(long unreadNotifications) {
        this.unreadNotifications = unreadNotifications;
    }

    public List<RecentFile> getRecentFiles() {
        return recentFiles;
    }

    public void setRecentFiles(List<RecentFile> recentFiles) {
        this.recentFiles = recentFiles;
    }

    public List<PendingApproval> getPendingApprovalList() {
        return pendingApprovalList;
    }

    public void setPendingApprovalList(List<PendingApproval> pendingApprovalList) {
        this.pendingApprovalList = pendingApprovalList;
    }

    public StorageStats getStorageStats() { return storageStats; }
    public void setStorageStats(StorageStats storageStats) { this.storageStats = storageStats; }
    public List<ContributionDay> getContributionHeatmap() { return contributionHeatmap; }
    public void setContributionHeatmap(List<ContributionDay> contributionHeatmap) { this.contributionHeatmap = contributionHeatmap; }

    public static class StorageStats {
        private long totalQuota;
        private long totalUsed;
        private List<StorageByType> byType;

        public StorageStats() {}

        public long getTotalQuota() { return totalQuota; }
        public void setTotalQuota(long totalQuota) { this.totalQuota = totalQuota; }
        public long getTotalUsed() { return totalUsed; }
        public void setTotalUsed(long totalUsed) { this.totalUsed = totalUsed; }
        public List<StorageByType> getByType() { return byType; }
        public void setByType(List<StorageByType> byType) { this.byType = byType; }
    }

    public static class StorageByType {
        private String type;
        private long size;

        public StorageByType() {}
        public StorageByType(String type, long size) { this.type = type; this.size = size; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
    }

    public static class ContributionDay {
        private String date;
        private int count;

        public ContributionDay() {}
        public ContributionDay(String date, int count) { this.date = date; this.count = count; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }

    public static class RecentFile {
        private Long fileId;
        private String fileName;
        private String fileType;
        private Long fileSize;
        private String accessTime;

        public RecentFile() {}

        public RecentFile(Long fileId, String fileName, String fileType, Long fileSize, String accessTime) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.fileType = fileType;
            this.fileSize = fileSize;
            this.accessTime = accessTime;
        }

        public Long getFileId() { return fileId; }
        public void setFileId(Long fileId) { this.fileId = fileId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
        public String getAccessTime() { return accessTime; }
        public void setAccessTime(String accessTime) { this.accessTime = accessTime; }
    }

    public static class PendingApproval {
        private Long approvalId;
        private String title;
        private String type;
        private String status;
        private String createTime;

        public PendingApproval() {}

        public PendingApproval(Long approvalId, String title, String type, String status, String createTime) {
            this.approvalId = approvalId;
            this.title = title;
            this.type = type;
            this.status = status;
            this.createTime = createTime;
        }

        public Long getApprovalId() { return approvalId; }
        public void setApprovalId(Long approvalId) { this.approvalId = approvalId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getCreateTime() { return createTime; }
        public void setCreateTime(String createTime) { this.createTime = createTime; }
    }
}
