package com.aether.knowledge.service.impl;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.mapper.KnowledgeDocumentMapper;
import com.aether.knowledge.mapper.KnowledgeDocumentVersionMapper;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实现知识库文档Version业务服务。
 */
@Service
public class KnowledgeDocumentVersionServiceImpl
        extends ServiceImpl<KnowledgeDocumentVersionMapper, KnowledgeDocumentVersion>
        implements KnowledgeDocumentVersionService {

    private final KnowledgeDocumentMapper documentMapper;

    /**
     * 创建 {@code KnowledgeDocumentVersionServiceImpl} 实例。
     */
    public KnowledgeDocumentVersionServiceImpl(KnowledgeDocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    /**
     * 创建下一个Version。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVersion createNextVersion(KnowledgeDocument snapshot) {
        if (snapshot == null || snapshot.getId() == null) {
            throw new ServerException(400, I18nUtils.getMessage("knowledge.document.required"));
        }
        // Serialize version allocation per document. The supplied snapshot intentionally
        // remains the content being versioned; the lock is only used for allocation.
        KnowledgeDocument locked = documentMapper.selectActiveForUpdate(snapshot.getId());
        if (locked == null) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.not-found"));
        }
        KnowledgeDocumentVersion latest = getOne(Wrappers.lambdaQuery(KnowledgeDocumentVersion.class)
                .eq(KnowledgeDocumentVersion::getKnowledgeDocumentId, snapshot.getId())
                .eq(KnowledgeDocumentVersion::getDeleted, false)
                .orderByDesc(KnowledgeDocumentVersion::getVersionNo)
                .last("LIMIT 1"), false);
        int versionNo = latest == null ? 1 : latest.getVersionNo() + 1;
        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion();
        version.setKnowledgeDocumentId(snapshot.getId());
        version.setVersionNo(versionNo);
        version.setContent(snapshot.getContent());
        version.setStorageBucket(snapshot.getStorageBucket());
        version.setStorageObjectKey(snapshot.getStorageObjectKey());
        version.setFileChecksum(snapshot.getFileChecksum());
        version.setIndexStatus(0);
        save(version);
        return version;
    }
}
