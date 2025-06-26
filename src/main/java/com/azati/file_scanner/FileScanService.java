package com.azati.file_scanner;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
import java.nio.charset.StandardCharsets;

@Service
public class FileScanService {

    final ConcurrentLinkedQueue<String> foundFiles = new ConcurrentLinkedQueue<>();
    Pattern currentPattern;
    ExecutorService currentExecutorService;
    final Set<Future<?>> runningTasks = new CopyOnWriteArraySet<>();

    private Path scanStartPath;

    Long minFileSize;
    Long maxFileSize;
    Date modifiedAfterDate;
    Date modifiedBeforeDate;
    String containsText;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final long KB_TO_BYTES = 1024L;


    public List<String> scan(String directoryPath, String fileMask, String threadsInput,
                             Long minSizeKB, Long maxSizeKB,
                             String modifiedAfter, String modifiedBefore,
                             String containsText) throws IOException, InterruptedException {
        foundFiles.clear();
        runningTasks.clear();

        this.minFileSize = (minSizeKB != null) ? minSizeKB * KB_TO_BYTES : null;
        this.maxFileSize = (maxSizeKB != null) ? maxSizeKB * KB_TO_BYTES : null;
        this.containsText = containsText;

        try {
            this.modifiedAfterDate = (modifiedAfter != null && !modifiedAfter.isEmpty()) ? DATE_FORMAT.parse(modifiedAfter) : null;
            this.modifiedBeforeDate = (modifiedBefore != null && !modifiedBefore.isEmpty()) ? DATE_FORMAT.parse(modifiedBefore) : null;
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid date format. Please use YYYY-MM-DD for modifiedAfter and modifiedBefore.", e);
        }


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
        System.out.println("Min Size: " + (minSizeKB != null ? minSizeKB + " KB" : "N/A"));
        System.out.println("Max Size: " + (maxSizeKB != null ? maxSizeKB + " KB" : "N/A"));
        System.out.println("Modified After: " + (modifiedAfterDate != null ? DATE_FORMAT.format(modifiedAfterDate) : "N/A"));
        System.out.println("Modified Before: " + (modifiedBeforeDate != null ? DATE_FORMAT.format(modifiedBeforeDate) : "N/A"));
        System.out.println("Contains Text: " + (containsText != null && !containsText.isEmpty() ? "'" + containsText + "'" : "N/A"));


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