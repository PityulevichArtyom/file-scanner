package com.azati.file_scanner;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.Future;
import java.util.regex.Matcher;

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
        String fileName = file.getFileName().toString();
        Matcher matcher = parentService.currentPattern.matcher(fileName);
        if (matcher.matches()) {
            parentService.foundFiles.add(file.toAbsolutePath().toString());
            System.out.println("File found and matches mask: " + file.toAbsolutePath());
        } else {
            System.out.println("File '" + fileName + "' does not match the mask '" + parentService.currentPattern.pattern() + "'");
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {

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
