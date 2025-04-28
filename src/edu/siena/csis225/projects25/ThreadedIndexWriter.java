package edu.siena.csis225.projects25;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.util.concurrent.*;

/**
 * A drop-in replacement for IndexWriter that uses a thread-pool
 * to handle add/update calls in parallel
 */
public class ThreadedIndexWriter extends IndexWriter {
    private final ExecutorService pool;

    public ThreadedIndexWriter(
        Directory dir,
        IndexWriterConfig config,
        int numThreads,
        int maxQueueSize
    ) throws IOException {
        super(dir, config);
        // a bounded queue + caller-runs policy
        BlockingQueue<Runnable> queue =
            new ArrayBlockingQueue<>(maxQueueSize);
        this.pool = new ThreadPoolExecutor(
            numThreads,
            numThreads,
            0L, TimeUnit.MILLISECONDS,
            queue,
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

 
    public void addDocument(org.apache.lucene.document.Document doc) {
        pool.submit(() -> {
            try {
                super.addDocument(doc);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

 
    public void updateDocument(org.apache.lucene.index.Term term,
                               org.apache.lucene.document.Document doc) {
        pool.submit(() -> {
            try {
                super.updateDocument(term, doc);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void close() throws IOException {
        pool.shutdown();
        try {
            pool.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        super.close();
    }
}
