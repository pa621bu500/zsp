//package com.pabu5h.zsp.controller;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.logging.Logger;
//
//@RestController
//public class ZspController {
//    Logger logger = Logger.getLogger(ZspController.class.getName());
//
//    @Autowired
//    public JdbcTemplate jdbcTemplatePub3;
//
//    @GetMapping("/zsp")
//    public String zsp() {
//        return "ZSP";
//    }
//
//    @GetMapping("/retrieveData")
//    public String retrieveData() {
//        String sql = "SELECT * FROM ZSP";
//        return jdbcTemplatePub3.queryForObject(sql, String.class);
//    }
//
//}
