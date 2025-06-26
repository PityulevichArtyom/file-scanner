package com.azati.file_scanner;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class FileScanService {

    final ConcurrentLinkedQueue<String> foundFiles = new ConcurrentLinkedQueue<>();
    Pattern currentPattern;
    ExecutorService currentExecutorService;
    final Set<Future<?>> runningTasks = new CopyOnWriteArraySet<>();

    private Path scanStartPath;

    public List<String> scan(String directoryPath, String fileMask, String threadsInput) throws IOException, InterruptedException {
        foundFiles.clear();
        runningTasks.clear();

        int numThreads;
        if (threadsInput.equalsIgnoreCase("auto")) {
            numThreads = Runtime.getRuntime().availableProcessors();
        } else {
            try {
                numThreads = Integer.parseInt(threadsInput);
                if (numThreads <= 0) {
                    System.err.println("Numbers of threads can't be 0 or negative. Using available processors.");
                    numThreads = Runtime.getRuntime().availableProcessors();
                }
            } catch (NumberFormatException e) {
                System.err.println("Incorrect format for threads. Using available processors.");
                numThreads = Runtime.getRuntime().availableProcessors();
            }
        }

        System.out.println("Directory for scan: " + directoryPath);
        System.out.println("File mask: " + fileMask);
        System.out.println("Threads: " + numThreads);

        String regex = fileMask.replace(".", "\\.").replace("*", ".*").replace("?", ".");
        this.currentPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        this.currentExecutorService = Executors.newFixedThreadPool(numThreads);

        try {
            this.scanStartPath = Paths.get(directoryPath);

            if (!Files.exists(scanStartPath) || !Files.isDirectory(scanStartPath)) {
                return Collections.emptyList();
            }

            FileSearchVisitor initialVisitor = new FileSearchVisitor(this, true, scanStartPath);
            Files.walkFileTree(scanStartPath, initialVisitor);


            currentExecutorService.shutdown();

            while (!runningTasks.isEmpty()) {
                Set<Future<?>> tasksToComplete = new CopyOnWriteArraySet<>(runningTasks);
                for (Future<?> future : tasksToComplete) {
                    if (future.isDone() || future.isCancelled()) {
                        runningTasks.remove(future);
                    } else {
                        try {
                            future.get(100, TimeUnit.MILLISECONDS);
                        } catch (java.util.concurrent.TimeoutException e) {
                        } catch (Exception e) {
                            System.err.println("DEBUG ERROR: Error while waiting for task: " + e.getMessage());
                            runningTasks.remove(future);
                        }
                    }
                }
                if (!runningTasks.isEmpty()) {
                    Thread.sleep(50);
                }
            }

            boolean terminated = currentExecutorService.awaitTermination(1, TimeUnit.MINUTES);  // Против бесконечного ожидания

        } finally {
            if (currentExecutorService != null && !currentExecutorService.isShutdown()) {
                currentExecutorService.shutdownNow();
            }
        }

        List<String> sortedFiles = new ArrayList<>(foundFiles);
        Collections.sort(sortedFiles);

        return sortedFiles;
    }
}