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

	private static String startError = "";		// error message for start reading when retrieving the data
	private static String endError = "";		// error message for end reading when retrieving the data
	private static String paymentError = "";	// error message for payment when retrieving the data
	private static String invoiceError = "";	// error message for invoice when retrieving the data

	public static void main(String[] args) {
		SpringApplication.run(ZspApplication.class, args);
	}
	@Override
	public void run(String... args) throws Exception {
		meterService.setDateTime();
		meterService.getMeterData();
		meterService.getTariffRate();
		meterService.getStartAndEndMeterReading();
		meterService.getPaymentChargeFromExcel();
		meterService.generateHotelInvoices();
		meterService.generateNormalInvoices();
	}

	// messages shown in command prompt
	private static void printInfo(){
		if(startError.equals("")){
			System.out.println("All start reading have successfully been retrieved!\n");
		}
		else{
			System.out.println(startError + "\n");
		}

		if(endError.equals("")){
			System.out.println("All end reading have successfully been retrieved!\n");
		}
		else{
			System.out.println(endError + "\n");
		}

		if(paymentError.equals("")){
			System.out.println("All payment have successfully been retrieved!\n");
		}
		else{
			System.out.println(paymentError + "\n");
		}

		if(invoiceError.equals("")){
			System.out.println("All invoice have successfully been retrieved!\n");
		}
		else{
			System.out.println(invoiceError + "\n");
		}
	}
}
