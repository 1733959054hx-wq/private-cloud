package front.workspace.documentspace.service;

import front.workspace.documentspace.entity.DocShareLink;
import front.workspace.documentspace.repository.DocShareLinkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DocShareLinkService {

    @Autowired
    private DocShareLinkRepository shareLinkRepository;

    @Transactional
    public DocShareLink createShareLink(Long fileId, Long creatorId, String password,
                                        LocalDateTime expireTime, Integer maxAccess,
                                        String permissionType) {
        DocShareLink link = new DocShareLink();
        link.setFileId(fileId);
        link.setCreatorId(creatorId);
        link.setToken(UUID.randomUUID().toString().replace("-", ""));
        link.setPassword(password);
        link.setExpireTime(expireTime);
        link.setMaxAccess(maxAccess != null ? maxAccess : 0);
        link.setPermissionType(permissionType != null ? permissionType : "view");
        link.setAccessCount(0);
        link.setStatus(1);
        return shareLinkRepository.save(link);
    }

    public DocShareLink getShareLinkByToken(String token) {
        DocShareLink link = shareLinkRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("分享链接不存在"));
        if (link.getStatus() != 1) {
            throw new RuntimeException("分享链接已失效");
        }
        if (link.getExpireTime() != null && link.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("分享链接已过期");
        }
        if (link.getMaxAccess() > 0 && link.getAccessCount() >= link.getMaxAccess()) {
            throw new RuntimeException("分享链接访问次数已达上限");
        }
        return link;
    }

    public List<DocShareLink> getShareLinksByFileId(Long fileId) {
        return shareLinkRepository.findByFileId(fileId);
    }

    public List<DocShareLink> getActiveShareLinksByCreator(Long creatorId) {
        return shareLinkRepository.findByCreatorIdAndStatus(creatorId, 1);
    }

    public List<DocShareLink> getAllShareLinksByCreator(Long creatorId) {
        return shareLinkRepository.findByCreatorIdOrderByCreateTimeDesc(creatorId);
    }

    @Transactional
    public void cancelShareLink(Long linkId, Long userId) {
        DocShareLink link = shareLinkRepository.findById(linkId)
                .orElseThrow(() -> new RuntimeException("分享链接不存在"));
        if (!link.getCreatorId().equals(userId)) {
            throw new RuntimeException("无权取消此分享链接");
        }
        link.setStatus(0);
        shareLinkRepository.save(link);
    }

    @Transactional
    public void deleteShareLinkPermanently(Long linkId, Long userId) {
        DocShareLink link = shareLinkRepository.findById(linkId)
                .orElseThrow(() -> new RuntimeException("分享链接不存在"));
        if (!link.getCreatorId().equals(userId)) {
            throw new RuntimeException("无权删除此分享链接");
        }
        shareLinkRepository.delete(link);
    }

    @Transactional
    public void incrementAccessCount(String token) {
        DocShareLink link = shareLinkRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("分享链接不存在"));
        link.setAccessCount(link.getAccessCount() + 1);
        shareLinkRepository.save(link);
    }
}
