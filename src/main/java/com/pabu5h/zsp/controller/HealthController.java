package com.pabu5h.zsp.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private volatile boolean maintenance;

    @Value("${service.version}")
    private String version;

    @GetMapping("/hello")
    public ResponseEntity<String> hello(/*HttpServletResponse httpServletResponse*/) {
//        httpServletResponse.addHeader("Access-Control-Allow-Origin", "*");
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("Access-Control-Allow-Origin", "*");
//        headers.set("Access-Control-Allow-Methods", "GET,PUT,PATCH,POST,DELETE");
//        headers.set("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");

        return ResponseEntity.ok()./*headers(headers).*/body("ZSP:" + version);
    }

    @GetMapping("/health")
    public String checkHealth() {
//        System.out.println("Health check called at " + LocalDateTime.now() + " and maintenance is " + maintenance);
        return maintenance ? "DOWN" : "UP";
    }
}
