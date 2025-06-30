package com.azati.file_scanner;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class FileSearchVisitor extends SimpleFileVisitor<Path> {
    private final FileScanService parentService;
    private final boolean isRootForParallelization;
    private final Path visitorRootPath;

    public FileSearchVisitor(FileScanService parentService, boolean isRootForParallelization, Path visitorRootPath) {
        this.parentService = parentService;
        this.isRootForParallelization = isRootForParallelization;
        this.visitorRootPath = visitorRootPath;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (parentService.scanInterrupted) {
            System.out.println("Scan interrupted for file: " + file.toAbsolutePath());
            return FileVisitResult.TERMINATE; // Прерываем обход
        }

        String fileName = file.getFileName().toString();
        Matcher matcher = parentService.currentPattern.matcher(fileName);

        if (!matcher.matches()) {
            System.out.println("File '" + fileName + "' does not match the mask '" + parentService.currentPattern.pattern() + "'");
            return FileVisitResult.CONTINUE;
        }

        try {
            long fileSize = attrs.size(); // размер файла в байтах
            if (parentService.minFileSize != null && fileSize < parentService.minFileSize) {
                System.out.println("File '" + fileName + "' ignored: size " + fileSize + " bytes is less than minSize " + parentService.minFileSize + " bytes");
                return FileVisitResult.CONTINUE;
            }
            if (parentService.maxFileSize != null && fileSize > parentService.maxFileSize) {
                System.out.println("File '" + fileName + "' ignored: size " + fileSize + " bytes is greater than maxSize " + parentService.maxFileSize + " bytes");
                return FileVisitResult.CONTINUE;
            }
        } catch (Exception e) {
            System.err.println("Error getting file size for " + file.toAbsolutePath() + ": " + e.getMessage());
        }


        try {
            Date lastModified = Date.from(attrs.lastModifiedTime().toInstant());

            if (parentService.modifiedAfterDate != null && lastModified.before(parentService.modifiedAfterDate)) {
                System.out.println("File '" + fileName + "' ignored: modified " + lastModified + " is before " + parentService.modifiedAfterDate);
                return FileVisitResult.CONTINUE;
            }
            if (parentService.modifiedBeforeDate != null && lastModified.after(parentService.modifiedBeforeDate)) {
                System.out.println("File '" + fileName + "' ignored: modified " + lastModified + " is after " + parentService.modifiedBeforeDate);
                return FileVisitResult.CONTINUE;
            }
        } catch (Exception e) {
            System.err.println("Error getting last modified time for " + file.toAbsolutePath() + ": " + e.getMessage());
        }


        if (parentService.containsText != null && !parentService.containsText.isEmpty()) {
            boolean isTextFile = fileName.toLowerCase().endsWith(".txt") ||
                    fileName.toLowerCase().endsWith(".log") ||
                    fileName.toLowerCase().endsWith(".csv") ||
                    fileName.toLowerCase().endsWith(".json") ||
                    fileName.toLowerCase().endsWith(".xml") ||
                    fileName.toLowerCase().endsWith(".html") ||
                    fileName.toLowerCase().endsWith(".java") ||
                    fileName.toLowerCase().endsWith(".py") ||
                    fileName.toLowerCase().endsWith(".md") ;
            if (isTextFile) {
                try {
                    String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                    if (!content.contains(parentService.containsText)) {
                        System.out.println("File '" + fileName + "'does not contain text '" + parentService.containsText + "'");
                        return FileVisitResult.CONTINUE;
                    }
                } catch (IOException e) {
                    System.err.println("Error reading content of file " + file.toAbsolutePath() + ": " + e.getMessage());
                    return FileVisitResult.CONTINUE;
                } catch (OutOfMemoryError e) {
                    System.err.println("Memory error reading large file " + file.toAbsolutePath() + ". Skipping content search.");
                    return FileVisitResult.CONTINUE;
                }
            } else {
                return FileVisitResult.CONTINUE;
            }
        }

        parentService.foundFiles.add(file.toAbsolutePath().toString());
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        if (parentService.scanInterrupted) { //Прерывание потоков
            System.out.println("Scan interrupted for directory: " + dir.toAbsolutePath());
            return FileVisitResult.TERMINATE;
        }

        if (isRootForParallelization) {
            if (!dir.equals(visitorRootPath)) {
                Future<?> future = parentService.currentExecutorService.submit(() -> {
                    try {
                        Files.walkFileTree(dir, new FileSearchVisitor(parentService, false, dir));
                    } catch (IOException e) {
                        System.err.println("Threaded walk error: " + dir.toAbsolutePath() + " - " + e.getMessage());
                    }
                });
                parentService.runningTasks.add(future);
                return FileVisitResult.SKIP_SUBTREE;
            }
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
        System.err.println("Error accessing file/directories: " + file.toAbsolutePath() + " - " + exc.getMessage());
        return FileVisitResult.CONTINUE;
    }
}