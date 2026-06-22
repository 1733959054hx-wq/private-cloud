package front.workspace.documentspace.service;

import front.workspace.documentspace.dto.DirectoryDTO;
import front.workspace.documentspace.entity.DocDirectory;
import front.workspace.documentspace.repository.DocDirectoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DirectoryService {

    @Autowired
    private DocDirectoryRepository directoryRepository;

    @Autowired
    private FileListCacheService fileListCacheService;

    @Autowired
    private DirectoryTreeCacheService directoryTreeCacheService;

    public List<DirectoryDTO> getDirectoryTree(Long departmentId) {
        List<DirectoryDTO> cached = directoryTreeCacheService.getDeptTree(departmentId);
        if (cached != null) {
            return cached;
        }

        List<DocDirectory> directories;
        if (departmentId != null) {
            directories = directoryRepository.findByDepartmentIdOrPublic(departmentId);
        } else {
            directories = directoryRepository.findByDeletedOrderBySortOrder(0);
        }
        List<DirectoryDTO> tree = directories.stream().map(this::toDTO).collect(Collectors.toList());
        directoryTreeCacheService.putDeptTree(departmentId, tree);
        return tree;
    }

    public List<DirectoryDTO> getDirectoryTreeBySpace(Integer spaceType, Long spaceId) {
        List<DirectoryDTO> cached = directoryTreeCacheService.getSpaceTree(spaceType, spaceId);
        if (cached != null) {
            return cached;
        }

        List<DocDirectory> directories = directoryRepository.findBySpaceTypeAndSpaceId(spaceType, spaceId);
        List<DirectoryDTO> tree = directories.stream().map(this::toDTO).collect(Collectors.toList());
        directoryTreeCacheService.putSpaceTree(spaceType, spaceId, tree);
        return tree;
    }

    @Transactional
    public DirectoryDTO createDirectory(DirectoryDTO dto, Long userId) {
        Long parentId = dto.getParentId() != null ? dto.getParentId() : 0L;

        if (dto.getSpaceType() != null && dto.getSpaceId() != null) {
            if (directoryRepository.existsByDirNameAndParentIdAndSpaceTypeAndSpaceIdAndDeleted(
                    dto.getDirName(), parentId, dto.getSpaceType(), dto.getSpaceId(), 0)) {
                throw new RuntimeException("同一目录下已存在同名文件夹");
            }
        } else {
            if (directoryRepository.existsByDirNameAndParentIdAndDeleted(dto.getDirName(), parentId, 0)) {
                throw new RuntimeException("同一目录下已存在同名文件夹");
            }
        }

        DocDirectory dir = new DocDirectory();
        dir.setDirName(dto.getDirName());
        dir.setParentId(parentId);
        dir.setDepartmentId(dto.getDepartmentId());
        dir.setSpaceType(dto.getSpaceType() != null ? dto.getSpaceType() : 0);
        dir.setSpaceId(dto.getSpaceId());
        dir.setOwnerId(userId);
        dir.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        dir.setDeleted(0);
        DocDirectory saved = directoryRepository.save(dir);
        directoryTreeCacheService.evictAll(saved.getDepartmentId(), saved.getSpaceType(), saved.getSpaceId());
        return toDTO(saved);
    }

    @Transactional
    public DirectoryDTO updateDirectory(Long id, DirectoryDTO dto) {
        DocDirectory dir = directoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("目录不存在"));
        if (dto.getDirName() != null) {
            Long parentId = dir.getParentId() != null ? dir.getParentId() : 0L;
            if (!dir.getDirName().equals(dto.getDirName())) {
                if (dir.getSpaceType() != null && dir.getSpaceId() != null) {
                    if (directoryRepository.existsByDirNameAndParentIdAndSpaceTypeAndSpaceIdAndDeleted(
                            dto.getDirName(), parentId, dir.getSpaceType(), dir.getSpaceId(), 0)) {
                        throw new RuntimeException("同一目录下已存在同名文件夹");
                    }
                } else {
                    if (directoryRepository.existsByDirNameAndParentIdAndDeleted(dto.getDirName(), parentId, 0)) {
                        throw new RuntimeException("同一目录下已存在同名文件夹");
                    }
                }
            }
            dir.setDirName(dto.getDirName());
        }
        if (dto.getParentId() != null) dir.setParentId(dto.getParentId());
        if (dto.getDepartmentId() != null) dir.setDepartmentId(dto.getDepartmentId());
        if (dto.getSortOrder() != null) dir.setSortOrder(dto.getSortOrder());
        DocDirectory saved = directoryRepository.save(dir);
        directoryTreeCacheService.evictAll(saved.getDepartmentId(), saved.getSpaceType(), saved.getSpaceId());
        return toDTO(saved);
    }

    @Transactional
    public DirectoryDTO renameDirectory(Long id, String newName) {
        DocDirectory dir = directoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("目录不存在"));

        if (newName == null || newName.trim().isEmpty()) {
            throw new RuntimeException("目录名不能为空");
        }
        if (newName.equals(dir.getDirName())) {
            return toDTO(dir);
        }

        Long parentId = dir.getParentId() != null ? dir.getParentId() : 0L;
        if (dir.getSpaceType() != null && dir.getSpaceId() != null) {
            if (directoryRepository.existsByDirNameAndParentIdAndSpaceTypeAndSpaceIdAndDeleted(
                    newName, parentId, dir.getSpaceType(), dir.getSpaceId(), 0)) {
                throw new RuntimeException("同一目录下已存在同名文件夹");
            }
        } else {
            if (directoryRepository.existsByDirNameAndParentIdAndDeleted(newName, parentId, 0)) {
                throw new RuntimeException("同一目录下已存在同名文件夹");
            }
        }

        dir.setDirName(newName);
        DocDirectory saved = directoryRepository.save(dir);
        directoryTreeCacheService.evictAll(saved.getDepartmentId(), saved.getSpaceType(), saved.getSpaceId());
        return toDTO(saved);
    }

    @Transactional
    public void deleteDirectory(Long id) {
        DocDirectory dir = directoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("目录不存在"));
        dir.setDeleted(1);
        directoryRepository.save(dir);
        fileListCacheService.evictSpace(dir.getSpaceType(), dir.getSpaceId(), dir.getId());
        directoryTreeCacheService.evictAll(dir.getDepartmentId(), dir.getSpaceType(), dir.getSpaceId());
    }

    @Transactional
    public DirectoryDTO moveDirectory(Long id, Long newParentId, Integer sortOrder) {
        DocDirectory dir = directoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("目录不存在"));
        dir.setParentId(newParentId);
        if (sortOrder != null) dir.setSortOrder(sortOrder);
        DocDirectory saved = directoryRepository.save(dir);
        directoryTreeCacheService.evictAll(saved.getDepartmentId(), saved.getSpaceType(), saved.getSpaceId());
        return toDTO(saved);
    }

    @Transactional
    public void batchSort(List<Map<String, Object>> sortItems) {
        Set<String> dirtyDeptIds = new HashSet<>();
        Set<String> dirtySpaceKeys = new HashSet<>();
        for (Map<String, Object> item : sortItems) {
            Long id = Long.valueOf(item.get("id").toString());
            DocDirectory dir = directoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("目录不存在, id=" + id));
            if (item.containsKey("sortOrder")) {
                dir.setSortOrder(Integer.valueOf(item.get("sortOrder").toString()));
            }
            if (item.containsKey("parentId")) {
                dir.setParentId(Long.valueOf(item.get("parentId").toString()));
            }
            directoryRepository.save(dir);
            if (dir.getDepartmentId() != null) {
                dirtyDeptIds.add(String.valueOf(dir.getDepartmentId()));
            }
            if (dir.getSpaceType() != null && dir.getSpaceId() != null) {
                dirtySpaceKeys.add(dir.getSpaceType() + ":" + dir.getSpaceId());
            }
        }
        for (String deptId : dirtyDeptIds) {
            directoryTreeCacheService.evictDeptTree(Long.valueOf(deptId));
        }
        for (String key : dirtySpaceKeys) {
            String[] parts = key.split(":");
            directoryTreeCacheService.evictSpaceTree(Integer.valueOf(parts[0]), Long.valueOf(parts[1]));
        }
    }

    private DirectoryDTO toDTO(DocDirectory dir) {
        DirectoryDTO dto = new DirectoryDTO();
        dto.setId(dir.getId());
        dto.setDirName(dir.getDirName());
        dto.setParentId(dir.getParentId());
        dto.setDepartmentId(dir.getDepartmentId());
        dto.setSpaceType(dir.getSpaceType());
        dto.setSpaceId(dir.getSpaceId());
        dto.setSortOrder(dir.getSortOrder());
        return dto;
    }
}
