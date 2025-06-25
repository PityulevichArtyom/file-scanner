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
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FileScanService {

    private final ConcurrentLinkedQueue<String> foundFiles = new ConcurrentLinkedQueue<>();

    public List<String> scan(String directoryPath, String fileMask, String threadsInput) throws IOException, InterruptedException{
        foundFiles.clear();
        int numThreads;
        if (threadsInput.equalsIgnoreCase("auto")){
            numThreads = Runtime.getRuntime().availableProcessors();
        } else {
            try {
                numThreads = Integer.parseInt(threadsInput);
                if (numThreads <= 0 ){
                    System.err.println("Numbers of processors cant be 0 or -n");
                    numThreads = Runtime.getRuntime().availableProcessors();
                }
            } catch (NumberFormatException e){
                System.err.println("Uncorrect format");
                numThreads = Runtime.getRuntime().availableProcessors();
            }
        }
        System.out.println("Directory for scan: "+ directoryPath);
        System.out.println("File mask: " + fileMask);
        System.out.println("Threads: " + numThreads);

        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        try{
            Path startPath = Paths.get(directoryPath);
            FileSearchVisitor visitor = new FileSearchVisitor(fileMask, executorService);
            Files.walkFileTree(startPath, visitor);
            executorService.shutdown();
            executorService.awaitTermination(1, TimeUnit.MINUTES); // Против бесконечного ожидания
        } catch (InvalidPathException e){
            throw new IOException("Uncorrected filepath" + directoryPath, e);
        } finally {
            executorService.shutdownNow();
        }

        List<String> sortedFiles = new ArrayList<>(foundFiles);
        Collections.sort(sortedFiles);

        return sortedFiles;
    }

    private class FileSearchVisitor extends SimpleFileVisitor<Path> {
        private final Pattern pattern;
        private final ExecutorService executorService;

        public FileSearchVisitor (String fileMask, ExecutorService executorService){
            String regex = fileMask.replace(".", "\\.").replace("*", ".*").replace("?", ".");
            this.pattern = Pattern.compile(regex);
            this.executorService = executorService;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            String fileName = file.getFileName().toString();
            Matcher matcher = pattern.matcher(fileName);
            if (matcher.matches()) {
                foundFiles.add(file.toAbsolutePath().toString());
            }
            return FileVisitResult.CONTINUE;
        }
        @Override
        public FileVisitResult preVisitDirectory (Path dir, BasicFileAttributes attrs) {
            executorService.submit(() -> {
                try {
                    Files.walkFileTree(dir, this);
                } catch (IOException e) {
                    System.err.println("Ошибка доступа к директории " + dir + ": " + e.getMessage());
                }
            });
            return FileVisitResult.SKIP_SUBTREE;
        }
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            System.err.println("Ошибка доступа к файлу/директории " + file + ": " + exc.getMessage());
            return FileVisitResult.CONTINUE;
        }
    }

}
