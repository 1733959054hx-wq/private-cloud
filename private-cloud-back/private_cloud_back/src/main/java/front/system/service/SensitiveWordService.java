package front.system.service;

import front.system.entity.SysSensitiveWord;
import front.system.repository.SysSensitiveWordRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SensitiveWordService {

    private static final String REDIS_KEY = "sensitive:word:dfa";
    private static final String REDIS_WORD_LIST_KEY = "sensitive:word:list";

    @Autowired
    private SysSensitiveWordRepository sensitiveWordRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final Map<String, Object> localDfaMap = new ConcurrentHashMap<>();

    private static final String IS_END = "isEnd";

    @PostConstruct
    public void init() {
        try {
            loadFromRedis();
        } catch (Exception e) {
            System.err.println("[敏感词] Redis加载失败，从数据库加载: " + e.getMessage());
            rebuildDfaMap();
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFromRedis() {
        Object cached = redisTemplate.opsForValue().get(REDIS_KEY);
        if (cached != null) {
            String json = com.alibaba.fastjson.JSON.toJSONString(cached);
            Map<String, Object> map = com.alibaba.fastjson.JSON.parseObject(json, Map.class);
            localDfaMap.clear();
            localDfaMap.putAll(map);
            System.out.println("[敏感词] 从Redis加载DFA字典成功，词条数: " + countWords(localDfaMap));
        } else {
            rebuildDfaMap();
        }
    }

    public void rebuildDfaMap() {
        List<SysSensitiveWord> words = sensitiveWordRepository.findAllEnabled();
        Map<String, Object> newMap = new HashMap<>();
        for (SysSensitiveWord word : words) {
            addWordToMap(newMap, word.getWord());
        }
        localDfaMap.clear();
        localDfaMap.putAll(newMap);
        try {
            redisTemplate.opsForValue().set(REDIS_KEY, newMap);
        } catch (Exception e) {
            System.err.println("[敏感词] DFA字典写入Redis失败: " + e.getMessage());
        }
        System.out.println("[敏感词] DFA字典重建完成，词条数: " + words.size());
    }

    private void addWordToMap(Map<String, Object> map, String word) {
        Map<String, Object> current = map;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            String key = String.valueOf(c);
            @SuppressWarnings("unchecked")
            Map<String, Object> next = (Map<String, Object>) current.get(key);
            if (next == null) {
                next = new HashMap<>();
                current.put(key, next);
            }
            current = next;
            if (i == word.length() - 1) {
                current.put(IS_END, 1);
            }
        }
    }

    private int countWords(Map<String, Object> map) {
        int count = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (IS_END.equals(entry.getKey()) && Integer.valueOf(1).equals(entry.getValue())) {
                count++;
            }
            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> sub = (Map<String, Object>) entry.getValue();
                count += countWords(sub);
            }
        }
        return count;
    }

    public boolean containsSensitiveWord(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) {
            int len = checkWord(text, i);
            if (len > 0) return true;
        }
        return false;
    }

    public List<String> findSensitiveWords(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        for (int i = 0; i < text.length(); i++) {
            int len = checkWord(text, i);
            if (len > 0) {
                result.add(text.substring(i, i + len));
                i += len - 1;
            }
        }
        return result;
    }

    public String filterSensitiveWord(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text);
        for (int i = 0; i < text.length(); i++) {
            int len = checkWord(text, i);
            if (len > 0) {
                for (int j = i; j < i + len; j++) {
                    sb.setCharAt(j, '*');
                }
                i += len - 1;
            }
        }
        return sb.toString();
    }

    private int checkWord(String text, int startIndex) {
        Map<String, Object> current = localDfaMap;
        int len = 0;
        int maxLen = 0;
        for (int i = startIndex; i < text.length(); i++) {
            String key = String.valueOf(text.charAt(i));
            @SuppressWarnings("unchecked")
            Map<String, Object> next = (Map<String, Object>) current.get(key);
            if (next == null) break;
            len++;
            if (Integer.valueOf(1).equals(next.get(IS_END))) {
                maxLen = len;
            }
            current = next;
        }
        return maxLen;
    }

    @Transactional
    public SysSensitiveWord addWord(String word, String category, Integer level, Long createBy) {
        if (word == null || word.trim().isEmpty()) {
            throw new RuntimeException("敏感词不能为空");
        }
        word = word.trim();
        if (sensitiveWordRepository.existsByWord(word)) {
            throw new RuntimeException("敏感词已存在: " + word);
        }
        SysSensitiveWord entity = new SysSensitiveWord();
        entity.setWord(word);
        entity.setCategory(category);
        entity.setLevel(level);
        entity.setCreateBy(createBy);
        SysSensitiveWord saved = sensitiveWordRepository.save(entity);
        rebuildDfaMap();
        return saved;
    }

    @Transactional
    public void deleteWord(Long id) {
        sensitiveWordRepository.deleteById(id);
        rebuildDfaMap();
    }

    @Transactional
    public SysSensitiveWord updateWord(Long id, String word, String category, Integer level, Integer enabled) {
        SysSensitiveWord entity = sensitiveWordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("敏感词不存在"));
        if (word != null && !word.equals(entity.getWord())) {
            if (sensitiveWordRepository.existsByWord(word)) {
                throw new RuntimeException("敏感词已存在: " + word);
            }
            entity.setWord(word);
        }
        if (category != null) entity.setCategory(category);
        if (level != null) entity.setLevel(level);
        if (enabled != null) entity.setEnabled(enabled);
        SysSensitiveWord saved = sensitiveWordRepository.save(entity);
        rebuildDfaMap();
        return saved;
    }

    @Transactional
    public void batchAddWords(List<String> words, String category, Integer level, Long createBy) {
        for (String word : words) {
            word = word.trim();
            if (word.isEmpty()) continue;
            if (!sensitiveWordRepository.existsByWord(word)) {
                SysSensitiveWord entity = new SysSensitiveWord();
                entity.setWord(word);
                entity.setCategory(category);
                entity.setLevel(level);
                entity.setCreateBy(createBy);
                sensitiveWordRepository.save(entity);
            }
        }
        rebuildDfaMap();
    }

    public List<SysSensitiveWord> getAllWords() {
        return sensitiveWordRepository.findAllEnabled();
    }

    public List<SysSensitiveWord> searchWords(String keyword) {
        return sensitiveWordRepository.searchByKeyword(keyword);
    }

    public List<SysSensitiveWord> getWordsByCategory(String category) {
        return sensitiveWordRepository.findByCategoryAndEnabledOrderByWord(category, 1);
    }
}
