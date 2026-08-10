package com.gateway.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/example-resource")
public class DemoResourceController {

    @GetMapping
    public ResponseEntity<String> getExample() {
        return ResponseEntity.ok("Success");
    }
}