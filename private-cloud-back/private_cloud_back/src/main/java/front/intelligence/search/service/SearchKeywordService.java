package front.intelligence.search.service;

import front.intelligence.search.entity.SearchKeyword;
import front.intelligence.search.repository.SearchKeywordRepository;
import front.workspace.documentspace.dto.FileDTO;
import front.workspace.documentspace.service.DocFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SearchKeywordService {

    @Autowired
    private SearchKeywordRepository keywordRepository;

    @Autowired
    private DocFileService docFileService;

    @Transactional
    public void recordSearch(String keyword, Long userId) {
        SearchKeyword sk = keywordRepository.findByKeywordAndDeleted(keyword, 0)
                .orElseGet(() -> {
                    SearchKeyword newSk = new SearchKeyword();
                    newSk.setKeyword(keyword);
                    newSk.setSearchCount(0);
                    newSk.setDeleted(0);
                    return newSk;
                });
        sk.setSearchCount(sk.getSearchCount() + 1);
        sk.setUserId(userId);
        keywordRepository.save(sk);
    }

    public List<SearchKeyword> getHotKeywords() {
        return keywordRepository.findTop10ByDeletedOrderBySearchCountDesc(0);
    }

    public List<SearchKeyword> getUserSearchHistory(Long userId) {
        return keywordRepository.findByUserIdOrderByCreateTimeDesc(userId);
    }

    @Transactional
    public void markAsHot(Long keywordId) {
        SearchKeyword sk = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new RuntimeException("关键词不存在"));
        sk.setIsHot(1);
        keywordRepository.save(sk);
    }

    public List<FileDTO> search(String keyword, Long userId) {
        recordSearch(keyword, userId);
        return docFileService.searchFiles(keyword);
    }

    public List<FileDTO> fulltextSearch(String keyword, Long userId) {
        recordSearch(keyword, userId);
        return docFileService.fulltextSearchFiles(keyword);
    }
}
