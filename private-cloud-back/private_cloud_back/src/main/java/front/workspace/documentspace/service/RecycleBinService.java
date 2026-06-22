package front.workspace.documentspace.service;

import front.storage.service.StorageRouter;
import front.workspace.documentspace.dto.FileDTO;
import front.workspace.documentspace.entity.DocDirectory;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.entity.RecycleBin;
import front.workspace.documentspace.repository.DocDirectoryRepository;
import front.workspace.documentspace.repository.DocFileRepository;
import front.workspace.documentspace.repository.RecycleBinRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecycleBinService {

    @Value("${recycle-bin.retain-days:30}")
    private int retainDays;

    @Autowired
    private RecycleBinRepository recycleBinRepository;

    @Autowired
    private DocFileRepository fileRepository;

    @Autowired
    private DocDirectoryRepository directoryRepository;

    @Autowired
    private StorageRouter storageRouter;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    private RecycleBinService self;

    private RecycleBinService getSelf() {
        if (self == null) {
            self = applicationContext.getBean(RecycleBinService.class);
        }
        return self;
    }

    @Transactional
    public void moveToRecycleBin(String itemType, Long itemId, Long deletedBy) {
        if (recycleBinRepository.findByItemTypeAndItemId(itemType, itemId).isPresent()) {
            return;
        }

        String itemName;
        if ("doc".equals(itemType)) {
            DocFile file = fileRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("文件不存在"));
            file.setStatus(0);
            fileRepository.save(file);
            itemName = file.getFileName();
        } else if ("directory".equals(itemType)) {
            DocDirectory dir = directoryRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("目录不存在"));
            dir.setDeleted(1);
            directoryRepository.save(dir);
            itemName = dir.getDirName();
        } else {
            throw new RuntimeException("不支持的回收站项目类型: " + itemType);
        }

        RecycleBin binItem = new RecycleBin();
        binItem.setItemType(itemType);
        binItem.setItemId(itemId);
        binItem.setItemName(itemName);
        binItem.setDeletedBy(deletedBy);
        binItem.setExpireTime(LocalDateTime.now().plusDays(retainDays));
        recycleBinRepository.save(binItem);
    }

    public List<RecycleBin> getRecycleBinItems(Long userId) {
        return recycleBinRepository.findByDeletedByOrderByCreateTimeDesc(userId);
    }

    @Transactional
    public void restoreItem(String itemType, Long itemId) {
        RecycleBin binItem = recycleBinRepository.findByItemTypeAndItemId(itemType, itemId)
                .orElseThrow(() -> new RuntimeException("回收站中不存在该项目"));

        if ("doc".equals(itemType)) {
            DocFile file = fileRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("文件不存在"));
            file.setStatus(1);
            fileRepository.save(file);
        } else if ("directory".equals(itemType)) {
            DocDirectory dir = directoryRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("目录不存在"));
            dir.setDeleted(0);
            directoryRepository.save(dir);
        }

        recycleBinRepository.delete(binItem);
    }

    @Transactional
    public void emptyRecycleBin(Long userId) {
        List<RecycleBin> items = recycleBinRepository.findByDeletedByOrderByCreateTimeDesc(userId);
        if (items.isEmpty()) {
            return;
        }
        recycleBinRepository.deleteAll(items);
        getSelf().emptyRecycleBinAsync(items);
    }

    @Async("taskExecutor")
    public void emptyRecycleBinAsync(List<RecycleBin> items) {
        for (RecycleBin item : items) {
            try {
                getSelf().hardDeleteEntity(item.getItemType(), item.getItemId());
            } catch (Exception e) {
                System.err.println("[回收站] 异步删除失败, itemType=" + item.getItemType() + ", itemId=" + item.getItemId() + ", error=" + e.getMessage());
            }
        }
    }

    @Transactional
    public void hardDeleteEntity(String itemType, Long itemId) {
        if ("doc".equals(itemType)) {
            DocFile file = fileRepository.findById(itemId).orElse(null);
            if (file != null) {
                String filePath = file.getFilePath();
                file.setDeleted(1);
                fileRepository.save(file);
                deletePhysicalFile(file, filePath);
            }
        } else if ("directory".equals(itemType)) {
            DocDirectory dir = directoryRepository.findById(itemId).orElse(null);
            if (dir != null) {
                dir.setDeleted(1);
                directoryRepository.save(dir);
            }
        }
    }

    @Transactional
    public void permanentlyDelete(String itemType, Long itemId) {
        if ("doc".equals(itemType)) {
            DocFile file = fileRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("文件不存在"));
            String filePath = file.getFilePath();
            file.setDeleted(1);
            fileRepository.save(file);
            deletePhysicalFile(file, filePath);
        } else if ("directory".equals(itemType)) {
            DocDirectory dir = directoryRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("目录不存在"));
            dir.setDeleted(1);
            directoryRepository.save(dir);
        }

        recycleBinRepository.deleteByItemTypeAndItemId(itemType, itemId);
    }

    /**
     * 删除物理文件（适配 MinIO 和本地存储）
     */
    private void deletePhysicalFile(DocFile file, String filePath) {
        if (filePath == null || filePath.isEmpty()) return;
        String storageType = file.getStorageType() != null ? file.getStorageType() : "local";
        try {
            storageRouter.delete(storageType, filePath);
        } catch (Exception e) {
            // 删除失败仅记录日志，不影响业务
            System.err.println("[回收站] 物理文件删除失败: storageType=" + storageType + ", path=" + filePath + ", error=" + e.getMessage());
        }
    }

    @Transactional
    public void cleanExpiredItems() {
        List<RecycleBin> expired = recycleBinRepository.findByExpireTimeBefore(LocalDateTime.now());
        for (RecycleBin item : expired) {
            getSelf().permanentlyDelete(item.getItemType(), item.getItemId());
        }
    }
}
