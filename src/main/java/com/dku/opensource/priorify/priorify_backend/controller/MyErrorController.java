package com.dku.opensource.priorify.priorify_backend.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;

@RestController
public class MyErrorController implements ErrorController {
    private static final String ERROR_MAPPING = "/error";

    @RequestMapping(value = ERROR_MAPPING)
    public ResponseEntity<String> error() {
        return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
    }

    // @Override
    // public String getErrorPath() {
    //     return ERROR_MAPPING;
    // }
}
