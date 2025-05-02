package edu.siena.csis225.projects25;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.util.concurrent.*;

/**
 * A replacement for IndexWriter that uses a thread-pool
 * to handle add/update calls in parallel
 * 
 * @author Julien, Zi'Aire, Riley Pierson
 * @version 4/24/2025
 */
public class ThreadedIndexWriter extends IndexWriter {
    //Executor service to manage parallel indexing tasks
    private final ExecutorService pool;

    /**
     * Constructs a ThreadedIndexWriter with a fixed-size thread pool and bounded queue
     *
     * @param dir, Lucene Directory where the index is stored
     * @param config, configuration for the underlying IndexWriter
     * @param numThreads, number of threads in the pool
     * @param maxQueueSize, maximum number of pending tasks allowed in the queue
     * @throws IOException, if the base IndexWriter fails to initialize
     */
    public ThreadedIndexWriter(
        Directory dir,
        IndexWriterConfig config,
        int numThreads,
        int maxQueueSize
    ) throws IOException {
        super(dir, config); //initialize parent IndexWriter
        //create a queue to hold indexing tasks before execution
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(maxQueueSize);
        //configure thread pool: semi-fixed number of threads, no keep-alive time, caller-runs policy when queue is full
        this.pool = new ThreadPoolExecutor(
            numThreads,                           //core pool size
            numThreads,                           //maximum pool size matches core
            0L, TimeUnit.MILLISECONDS,            //idle threads terminated immediately 
            queue,                                //work queue for tasks
            new ThreadPoolExecutor.CallerRunsPolicy() //if queue full, run task in calling thread
        );
    }

    /**
     * Adds a document to the index asynchronously
     *
     * @param doc the Lucene Document to add to the index dir
     */
    
    public void addDocument(org.apache.lucene.document.Document doc) {
        //submit a task to add the document in a separate thread
        pool.submit(() -> {
            try {
                super.addDocument(doc); //delegate to parent addDocument
            } catch (IOException e) {
                throw new RuntimeException(e); 
            }
        });
    }

    /**
     * Updates a document in the index asynchronously
     *
     * @param term. the term identifying which documents to update
     * @param doc, new Lucene Document to index
     */

    public void updateDocument(org.apache.lucene.index.Term term,
                               org.apache.lucene.document.Document doc) {
        //submit a task to update the document in a separate thread
        pool.submit(() -> {
            try {
                super.updateDocument(term, doc); //delegate to parent updateDocument
            } catch (IOException e) {
                throw new RuntimeException(e);   
            }
        });
    }

    /**
     * Closes the thread pool and waits for all tasks to finish before closing the IndexWriter
     *
     * @throws IOException if closing the IndexWriter fails
     */
    @Override
    public void close() throws IOException {
        pool.shutdown(); //stop accepting tasks
        try {
            //wait up to an hour for running tasks to complete
            pool.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); //restore interrupt status if interrupted
        }
        super.close(); //close the IndexWriter
    }
}
