package com.pabu5h.zsp;

import com.pabu5h.zsp.model.Meter;
import com.pabu5h.zsp.repository.MeterRepository;
import com.pabu5h.zsp.service.MeterService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.io.*;
import java.text.*;

@SpringBootApplication
public class ZspApplication implements CommandLineRunner {
	@Autowired
	public MeterService meterService;

	public static void main(String[] args) {
		SpringApplication.run(ZspApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
//		meterService.updateTenantTable();
		meterService.setDateTime(2024,5);
		meterService.getMeterData();
		meterService.getTariffRate();
		meterService.getMeterUsage();
		meterService.getPaymentInfoFromTenantInvoice(); //after manual reading inserted into db, proceed to generate invoice
		meterService.generateMissingDataReport();
		meterService.generateNormalInvoices();
		meterService.generateInvSummary();
//		meterService.generateVVCReport();
	}
}
