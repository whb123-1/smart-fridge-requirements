package com.xianzhi.fridge.recipe.application;

import com.xianzhi.fridge.recipe.infrastructure.RecipeStore;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RecipeIndexRebuildProcessor {
    private static final Logger log = LoggerFactory.getLogger(RecipeIndexRebuildProcessor.class);
    private static final int BATCH_SIZE = 100;
    private final RecipeStore store;
    private final RecipeVectorIndex index;
    private final TransactionTemplate transactions;

    public RecipeIndexRebuildProcessor(RecipeStore store, RecipeVectorIndex index,
                                       PlatformTransactionManager transactionManager) {
        this.store = store;
        this.index = index;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public int processNext() {
        RecipeStore.RebuildWork work = transactions.execute(status -> store.claimRebuild());
        if (work == null) return 0;
        int processed = work.processedCount();
        int failures = work.failureCount();
        String lastError = null;
        try {
            while (true) {
                List<RecipeStore.IndexDocument> documents = store.indexDocuments(processed, BATCH_SIZE);
                if (documents.isEmpty()) break;
                for (RecipeStore.IndexDocument document : documents) {
                    boolean indexed = false;
                    for (int attempt = 0; attempt < 3 && !indexed; attempt++) {
                        indexed = index.indexInto(document.id(), document.title(), document.content(), work.collectionName());
                    }
                    if (!indexed) {
                        failures++;
                        lastError = "Could not index recipe " + document.id();
                    }
                    processed++;
                }
                int progress = processed;
                int failureCount = failures;
                String error = lastError;
                transactions.executeWithoutResult(status -> store.rebuildProgress(work.id(), progress, failureCount, error));
            }
            if (failures > 0) {
                String error = lastError == null ? "One or more recipes could not be indexed" : lastError;
                transactions.executeWithoutResult(status -> store.failRebuild(work.id(), error));
            } else {
                transactions.executeWithoutResult(status -> store.completeRebuild(
                        work.id(), work.collectionName(), work.embeddingModelVersion()));
            }
        } catch (RuntimeException exception) {
            String error = safeMessage(exception);
            log.warn("Recipe index rebuild {} failed", work.id(), exception);
            transactions.executeWithoutResult(status -> store.failRebuild(work.id(), error));
        }
        return 1;
    }

    private static String safeMessage(Throwable exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) value = exception.getClass().getSimpleName();
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
