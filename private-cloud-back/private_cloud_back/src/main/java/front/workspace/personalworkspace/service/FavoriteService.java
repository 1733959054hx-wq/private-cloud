package front.workspace.personalworkspace.service;

import front.workspace.documentspace.repository.DocDirectoryRepository;
import front.workspace.documentspace.repository.DocFileRepository;
import front.workspace.personalworkspace.dto.FavoriteVO;
import front.workspace.personalworkspace.entity.DocFavorite;
import front.workspace.personalworkspace.repository.DocFavoriteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FavoriteService {

    @Autowired
    private DocFavoriteRepository favoriteRepository;

    @Autowired
    private DocFileRepository fileRepository;

    @Autowired
    private DocDirectoryRepository directoryRepository;

    @Autowired
    private FavoriteCacheService favoriteCacheService;

    public List<FavoriteVO> getUserFavorites(Long userId) {
        List<DocFavorite> favorites = favoriteRepository.findByUserIdOrderByCreateTimeDesc(userId);
        return favorites.stream().map(fav -> {
            FavoriteVO vo = new FavoriteVO();
            vo.setId(fav.getId());
            vo.setTargetId(fav.getTargetId());
            vo.setTargetType(fav.getTargetType());
            if (fav.getTargetType() == 0) {
                boolean existsAndNotDeleted = fileRepository.findById(fav.getTargetId())
                        .map(f -> f.getDeleted() == 0).orElse(false);
                if (!existsAndNotDeleted) return null;
            } else if (fav.getTargetType() == 1) {
                boolean exists = directoryRepository.findById(fav.getTargetId()).isPresent();
                if (!exists) return null;
            }
            vo.setTargetName(resolveTargetName(fav.getTargetId(), fav.getTargetType()));
            vo.setFileType(resolveFileType(fav.getTargetId(), fav.getTargetType()));
            vo.setCreateTime(fav.getCreateTime());
            return vo;
        }).filter(vo -> vo != null).collect(Collectors.toList());
    }

    private String resolveTargetName(Long targetId, Integer targetType) {
        if (targetType == 0) {
            return fileRepository.findById(targetId)
                    .map(f -> f.getFileName())
                    .orElse("未知文件");
        } else if (targetType == 1) {
            return directoryRepository.findById(targetId)
                    .map(d -> d.getDirName())
                    .orElse("未知文件夹");
        }
        return "未知项目";
    }

    private String resolveFileType(Long targetId, Integer targetType) {
        if (targetType == 0) {
            return fileRepository.findById(targetId)
                    .map(f -> f.getFileType())
                    .orElse(null);
        }
        return null;
    }

    public FavoriteVO addFavorite(Long userId, Long targetId, Integer targetType) {
        if (targetType == 0) {
            boolean valid = fileRepository.findById(targetId)
                    .map(f -> f.getDeleted() == 0).orElse(false);
            if (!valid) throw new RuntimeException("目标文件不存在或已被删除");
        } else if (targetType == 1) {
            if (!directoryRepository.findById(targetId).isPresent()) {
                throw new RuntimeException("目标文件夹不存在");
            }
        }
        DocFavorite favorite = new DocFavorite();
        favorite.setUserId(userId);
        favorite.setTargetId(targetId);
        favorite.setTargetType(targetType);
        DocFavorite saved = favoriteRepository.save(favorite);

        favoriteCacheService.putFavorited(userId, targetId, targetType);

        FavoriteVO vo = new FavoriteVO();
        vo.setId(saved.getId());
        vo.setTargetId(saved.getTargetId());
        vo.setTargetType(saved.getTargetType());
        vo.setTargetName(resolveTargetName(saved.getTargetId(), saved.getTargetType()));
        vo.setFileType(resolveFileType(saved.getTargetId(), saved.getTargetType()));
        vo.setCreateTime(saved.getCreateTime());
        return vo;
    }

    @Transactional
    public void removeFavorite(Long userId, Long targetId, Integer targetType) {
        favoriteRepository.deleteByUserIdAndTargetIdAndTargetType(userId, targetId, targetType);
        favoriteCacheService.evictStatus(userId, targetId, targetType);
    }

    public boolean isFavorited(Long userId, Long targetId, Integer targetType) {
        Boolean cached = favoriteCacheService.getStatus(userId, targetId, targetType);
        if (cached != null) {
            return cached;
        }
        boolean exists = favoriteRepository.findByUserIdAndTargetIdAndTargetType(userId, targetId, targetType).isPresent();
        favoriteCacheService.applyStatus(userId, targetId, targetType, exists);
        return exists;
    }
}