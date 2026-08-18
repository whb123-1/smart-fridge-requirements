package com.xianzhi.fridge.recipe.application;

import com.xianzhi.fridge.recipe.infrastructure.RecipeStore;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RecipeImportProcessor {
    private static final Logger log = LoggerFactory.getLogger(RecipeImportProcessor.class);
    private final RecipeStore store;
    private final RecipeImportParser parser;
    private final RecipeVectorIndex vectorIndex;
    private final TransactionTemplate transactions;
    private final TransactionTemplate requiresNew;

    public RecipeImportProcessor(RecipeStore store, RecipeImportParser parser, RecipeVectorIndex vectorIndex,
                                 PlatformTransactionManager transactionManager) {
        this.store = store;
        this.parser = parser;
        this.vectorIndex = vectorIndex;
        this.transactions = new TransactionTemplate(transactionManager);
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public int processBatch() {
        List<RecipeStore.ImportWork> jobs = transactions.execute(status -> store.claimImports(5));
        if (jobs == null) return 0;
        for (RecipeStore.ImportWork job : jobs) {
            try { requiresNew.executeWithoutResult(status -> process(job)); }
            catch (RuntimeException exception) {
                log.warn("Recipe import job {} failed", job.id(), exception);
                requiresNew.executeWithoutResult(status -> store.failImport(job.id(), safeMessage(exception)));
            }
        }
        return jobs.size();
    }

    private void process(RecipeStore.ImportWork job) {
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        List<RecipeImportParser.RecipeDocument> documents = parser.parse(job.payload());
        for (RecipeImportParser.RecipeDocument document : documents) {
            if (store.fingerprintExists(document.fingerprint())) {
                skipped++;
                continue;
            }
            var recipeId = store.insertImportedRecipe(job, document);
            boolean indexed = vectorIndex.index(recipeId, document.title(), document.title() + "\n" + document.snapshot());
            store.markVector(recipeId, indexed, indexed ? null : "Vector indexing disabled or unavailable");
            imported++;
        }
        store.completeImport(job.id(), imported, skipped, errors);
    }

    private static String safeMessage(Throwable exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) value = exception.getClass().getSimpleName();
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
