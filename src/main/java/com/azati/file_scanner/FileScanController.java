package com.azati.file_scanner;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/scan")
public class FileScanController {
    private final FileScanService fileScanService;

    public FileScanController (FileScanService fileScanService){
        this.fileScanService = fileScanService;
    }
    @GetMapping
    public ResponseEntity<List<String>> scanFiles(
            @RequestParam String path,
            @RequestParam String mask,
            @RequestParam(defaultValue = "auto") String threads
    ){
        try {
            List<String> foundFiles = fileScanService.scan(path, mask, threads);

            if (foundFiles.isEmpty()){
                return new ResponseEntity<>(
                        Collections.singletonList("Not found with mask: "+ mask),
                        HttpStatus.NOT_FOUND
                );
            }
            return new ResponseEntity<>(foundFiles, HttpStatus.OK);
        }
        catch (IOException | InterruptedException e){
            System.err.println("Error processing scan request: " + e.getMessage());
            return new ResponseEntity<>(
                    Collections.singletonList("Server error while scanning: "+ e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
