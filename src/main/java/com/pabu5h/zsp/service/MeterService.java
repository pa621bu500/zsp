package com.pabu5h.zsp.service;

import com.pabu5h.zsp.model.Meter;
import com.pabu5h.zsp.repository.MeterRepository;
import com.pabu5h.zsp.util.ExcelUtil;
import com.pabu5h.zsp.util.MeterUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.pabuff.evs2helper.meter_usage.MeterUsageProcessor;
import org.pabuff.evs2helper.report.ReportHelper;
import org.pabuff.evs2helper.scope.ScopeHelper;
import org.pabuff.oqghelper.OqgHelper;
import org.pabuff.utils.MathUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import java.io.*;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

@Service
public class MeterService {

    @Autowired
    private MeterRepository meterRepository;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private MeterUsageProcessor meterUsageProcessor;

    @Autowired
    private ReportHelper reportHelper;

    @Autowired
    private ScopeHelper scopeHelper;

    @Autowired
    private OqgHelper oqgHelper;

//    @Autowired
//    private SystemNotifier systemNotifier;

    private ArrayList<Meter> meterList = new ArrayList<Meter>();
    private List<Map<String, Object>> excelData;

    @Autowired
    public MeterService(ScopeHelper scopeHelper) {
        this.itemConfig = scopeHelper.getItemTypeConfig2("ems_zsp", null);
    }

    //--- for invoice summary---
    private List<LinkedHashMap<String, Object>> electricalInvExcelData = new ArrayList<>();

    @Value("${normal.invoice.config.path}")
    private String normalInvFilePath;
    @Value("${type0.invoice.config.path}")
    private String hotelInvFilePath;
    @Value("${normal.invoice.template.path}")
    private String normalInvTemplatePath;            // location of the template used for normal inv
    @Value("${hotel.invoice.template.path}")
    private String hotelInvTemplatePath;             // location of the template used for hotel inv
    @Value("${summary.template.path}")
    private String summaryTemplatePath;              // location of the summary template
    @Value("${output.path}")
    private String outputPath;                       // output path for generated file
    @Value("${due.date}")
    private String dueDate;                          // the due date for the billing
    @Value("${latest.payment.date}")
    private String latestPaymentDate;                // payment will be excluded after this date
    private String billingDueDate;

    Map<String, Object> itemConfig;

    //----- variable for tariff and gst rate ----
    private double tariffRate;
    private double gstRate;

    //-------- variable for dates ----------
    private String currDate;        // current year and month for generated excel file
    private String firstDay;            // first day of the month of the billing month
    private String lastDay;                // last day of the month of the billing month
    private String genDate;                // generated billing date
    private String billingEndDate;            // late payment starting date
    private String billingStartDate;        // late payment ending date & starting date for bill 0
    private String startDateTime;
    private String startDateTimeForInvSummary;
    private String invSummaryDate;
    private String prevMonth;
    private String invDate;                // invoice date
    private String excelDate;
    Logger logger = Logger.getLogger(MeterService.class.getName());

    //functions description
    //----------- getMeterData(), getTariffRate(),getStartAndEndMeterReading() -> functions to get readings from DB -----------------
    //----------- getPaymentInfoFromTenantInvoice() -> function to get payment info from tenant invoice excel sheet --------------
    //----------- generateMissingDataReport(), generateVVCReport(), generateNormalInvoices(), generateInvSummary() -> functions to generate desired reports -----------------

    //this function is to retrieve meter info, tenant info from database
    public void getMeterData() throws Exception {
        logger.info("retrieving meter data....");
        List<Map<String, Object>> metersData = meterRepository.getMeterData();

        if (metersData.isEmpty()) {
            logger.info("No record found");
        } else {
            logger.info("meter data retrieved successfully");
        }

        //--- outdated meter list ---
        Map<String, Object> outdatedMeterList = Map.ofEntries(
                Map.entry("53085706", Map.of("new_meter_id", "04-46-96-00-00-33", "new_recid", "345", "new_power_factor", 1)), //345
                Map.entry("17430167", Map.of("new_meter_id", "04-46-96-00-00-31", "new_recid", "1541", "new_power_factor", 1)), //310
                Map.entry("53085698", Map.of("new_meter_id", "44-69-00-00-00-84", "new_recid", "310", "new_power_factor", 1)), //not found , 328
                Map.entry("17944170", Map.of("new_meter_id", "04-63-67-00-01-14", "new_recid", "1617", "new_power_factor", 1)), //322
                Map.entry("17944169", Map.of("new_meter_id", "04-46-96-00-00-34", "new_recid", "1540", "new_power_factor", 1)),
                Map.entry("27079568", Map.of("new_meter_id", "12-05-25-70-00-36", "new_recid", "1665", "new_power_factor", 30)),
                Map.entry("18238738", Map.of("new_meter_id", "04-63-67-00-00-06", "new_recid", "1543", "new_power_factor", 1)),
                Map.entry("27079569", Map.of("new_meter_id", "12-05-25-70-00-31", "new_recid", "1666", "new_power_factor", 30))
        );

        //--- replace the outdated meters with the new meters ---
        for (Map<String, Object> row : metersData) {
            String meterId = row.get("recname").toString();
            if (meterId != null && outdatedMeterList.containsKey(meterId)) {
                Map<String, Object> outdatedMeterInfo = (Map<String, Object>) outdatedMeterList.get(meterId);
                System.out.println("outdated meter found:" + row.get("recname"));
                System.out.println("replaced with new meter id:" + outdatedMeterInfo.get("new_meter_id"));
                row.put("recname", outdatedMeterInfo.get("new_meter_id").toString());
                row.put("recid", outdatedMeterInfo.get("new_recid"));
                row.put("powerfactor", outdatedMeterInfo.get("new_power_factor"));
            }
        }

        //--- create a list of meter objects ---
        try{
            for (Map<String, Object> row : metersData) {
                Meter meterEntry = new Meter(
                        MathUtil.ObjToInteger(row.get("recorder_id")),
                        MathUtil.ObjToInteger(row.get("tenant_id")),
                        MathUtil.ObjToInteger(row.get("type")),
                        MathUtil.ObjToInteger(row.get("lc_status")),
                        (String) row.get("rec_name"),
                        MathUtil.ObjToDouble(row.get("deposit")) != null ? MathUtil.ObjToDouble(row.get("deposit")) : 0,
                        (String) row.get("address_tenant_name"),
                        (String) row.get("address_line1"),
                        (String) row.get("address_line2"),
                        (String) row.get("unit"),
                        row.get("postal").toString(),
                        (String) row.get("alt_tenant_name"),
                        (String) row.get("alt_address_line1"),
                        (String) row.get("alt_address_line2"),
                        row.get("alt_postal") != null ? row.get("alt_postal").toString() : "",
                        MathUtil.ObjToInteger(row.get("ct_multiplier")),
                        MathUtil.ObjToInteger(row.get("giro"))
                );
                meterList.add(meterEntry);
            }
        } catch(Exception e){
            throw new RuntimeException("Error creating meter object: ", e);
        }
    }

    // this function is to retrieve tariff rate from the db
    public void getTariffRate() {
        try {
            Map<String, Object> resp = meterRepository.getTariffRate();
            logger.info("retrieving tariff rate....");
            if (resp.isEmpty()) {
                logger.info("No record found! Please check SPRATE Table in database!");
            } else {
                tariffRate = Double.parseDouble((String)resp.get("tariff_rate"));
                gstRate = Double.parseDouble((String)resp.get("gst_rate"));
                logger.info("tariff rate retrieved successfully");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //this function is to get start,end reading, timestamp & month usage from the db
    public void getMeterUsage() throws Exception {
        String itemIdColName = (String) itemConfig.get("itemIdColName");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        //get first day and last time of the month
        LocalDateTime startDate = LocalDateTime.parse(startDateTime, formatter);
        LocalDateTime endDateTime =  startDate.with(TemporalAdjusters.lastDayOfMonth())
                .with(LocalTime.MAX);

        //convert List of Meter to List of Map
        List<Map<String, Object>> meterListUsageSummary = new ArrayList<>();
        for(Meter row:meterList){
            Map<String, Object> meterListUsageSummaryMap = new HashMap<>();
            meterListUsageSummaryMap.put(itemIdColName, String.valueOf(row.getRecordID()));
            meterListUsageSummary.add(meterListUsageSummaryMap);
        }

        //get meter usage
        Map<String,Object> resultMap = meterRepository.getMeterUsage(startDateTime,endDateTime.toString(),meterListUsageSummary);

        List<Map<String,Object>> resultList = (List<Map<String, Object>>) resultMap.get("meter_list_usage_summary");
        for (Meter row : meterList) {
            for (Map<String, Object> meter : resultList) {
                if (Objects.equals(row.getRecordID(), MathUtil.ObjToInteger(meter.get(itemIdColName)))) {

                    if(meter.get("first_reading_val").toString()!=null){
                            row.setStartReading(meter.get("first_reading_val").toString());
                    }else{
                        row.setStartReading(null);
                    }
                    if(meter.get("first_reading_time").toString()!=null){
                            row.setStartTimeStamp(meter.get("first_reading_time").toString());
                    }else{
                        row.setStartTimeStamp(null);
                    }

                    if(meter.get("last_reading_val").toString()!=null){
                            row.setEndReading(meter.get("last_reading_val").toString());
                    }else{
                        row.setEndReading(null);
                    }

                    if(meter.get("last_reading_time").toString()!=null){
                            row.setEndTimeStamp(meter.get("last_reading_time").toString());
                    }else{
                        row.setEndTimeStamp(null);
                    }

                    if(meter.get("usage").toString()!=null){
                            row.setUsage(meter.get("usage").toString());
                    }else{
                        row.setUsage(null);
                    }
                }
            }
        }
    }

//    public void updateTenantTable(){
//        try{
//            excelData = ExcelUtil.readXlsFile("src/main/resources/templates/updated_tenant_list.xlsx", 1, 0);
//            System.out.println(excelData);
//            for(Map<String, Object> row : excelData){
//                // Extract values from the row map
//                Integer tenantId = getIntegerValue(row.get("tenant_id"));
//                String addressTenantName = escapeSingleQuote((String) row.get("address_tenant_name"));
//                String addressLine2 = escapeSingleQuote((String) row.get("address line2"));
//                String altAddressLine1 = escapeSingleQuote((String) row.get("alt_address_line1"));
//                String addressLine1 = escapeSingleQuote((String) row.get("address_line1"));
//                String altAddressLine2 = escapeSingleQuote((String) row.get("alt_address_line 2"));
//                Integer postal = getIntegerValue(row.get("postal"));
//                String altTenantName = escapeSingleQuote((String) row.get("alt_tenant_name"));
//                String unit = escapeSingleQuote((String) row.get("Unit"));
//                Integer altPostal = getIntegerValue(row.get("alt_postal"));
//                String sql =
//                        "UPDATE tenant_zsp " +
//                                "SET " +
//                                "address_tenant_name = '"+addressTenantName+"' ," +
//                                "address_line1 = '"+addressLine1+"' ," +
//                                "address_line2 = '"+addressLine2+"' ," +
//                                "postal = '"+postal+"' ," +
//                                "unit = '"+unit+"' ," +
//                                "alt_tenant_name = '"+altTenantName+"', " +
//                                "alt_address_line1 = '"+altAddressLine1+"', " +
//                                "alt_address_line2 = '"+altAddressLine2+"', " +
//                                " alt_postal = '"+altPostal+"' " +
//                                "WHERE tenant_id = '"+tenantId+"'";
//                oqgHelper.OqgIU(sql);
//            }
//        }catch(Exception e){
//            throw new RuntimeException(e);
//        }
//    }

    private String escapeSingleQuote(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("'", "''");
    }

    private Integer getIntegerValue(Object value) {
        if (value instanceof Double) {
            return ((Double) value).intValue();
        } else if (value instanceof String) {
            return Integer.parseInt((String) value);
        } else if (value instanceof Integer) {
            return (Integer) value;
        } else {
            return null; // or throw an exception if necessary
        }
    }

    //this function is to read tenant invoice excel file to get the payment info such as prev bal, outstanding bal (Financial Data Import)
    public void getPaymentInfoFromTenantInvoice() {
        try {
            excelData = ExcelUtil.readXlsFile("src/main/resources/excel_from_zsp/" + excelDate + " Tenants Electrical Invoices Summary " + lastDay.substring(3) + ".xls", 6, 2);
        } catch (IOException e) {
            throw new RuntimeException("Error reading excel file : ", e);
        }
        //get the balanceBF and total paid up to date for each meter, balance from previous month  = balance bf + new charge
        //get the total paid up to date for each meter if any
        //get the late charge, late charge period for each meter if any
        for (Map<String, Object> row : excelData) {
            System.out.println(row);
            String accountNo = row.get("Account No").toString();
            if (accountNo != null && !accountNo.isEmpty()) {
                //get last 4 digits of account number (tenant ID)
                String tenantID = accountNo.substring(accountNo.length() - 4);
                for (int i = 0; i < meterList.size(); i++) {
                    //if tenant ID matches, set the balance from previous month and amount received
                    if (Objects.equals(tenantID, String.format("%04d", meterList.get(i).getTenantId()))) {
                        //get the balanceBF and new charges for each meter
                        double newCharge = 0;
                        double balanceBf = 0;
                        String keyWithSubstring = MeterUtil.findKeyContainingSubstring(row, "New Charges");
                        if (row.get(keyWithSubstring) != null && !row.get(keyWithSubstring).toString().isEmpty()) {
                            newCharge = Double.parseDouble(row.get(keyWithSubstring).toString());
                        }
                        if (row.get("Balance B/F") != null && !row.get("Balance B/F").toString().isEmpty()) {
                            balanceBf = Double.parseDouble(row.get("Balance B/F").toString());
                        }
                        //balance from previous month  = balance bf + new charge
                        meterList.get(i).setBalanceFromPrevMonth(balanceBf + newCharge);

                        //get the total paid up to date for each meter if any
                        if (row.get("Total Paid Up To date") != null && !row.get("Total Paid Up To date").toString().isEmpty()) {
                            double totalPaid = Double.parseDouble(row.get("Total Paid Up To date").toString());
                            meterList.get(i).setAmtReceived(totalPaid);
                        }

                        //get the late charge, late charge period for each meter if any
                        //note*** THERE IS A SPACE BEHIND for Period of Late Payment Interest
                        keyWithSubstring = MeterUtil.findKeyContainingSubstring(row, "Late Payment Interest for");
                        if (row.get(keyWithSubstring) != null && !row.get(keyWithSubstring).toString().isEmpty()) {
                            System.out.println(row.get(keyWithSubstring).toString());
                            meterList.get(i).setLateCharges(Double.parseDouble(row.get(keyWithSubstring).toString()));
                            meterList.get(i).setPeriodOfLatePayment(row.get("Period of Late Payment Interest ").toString());
                        }

                        if (row.get("Outstanding") != null && !row.get("Outstanding").toString().isEmpty()) {
                            if((MathUtil.ObjToDouble(row.get("Outstanding")) == null)) {
                                meterList.get(i).setOutstandingBalance(null);
                            }else if(MathUtil.ObjToDouble(row.get("Outstanding")) == 0.0) {
                                meterList.get(i).setOutstandingBalance("0.00");
                            }else{
                                meterList.get(i).setOutstandingBalance(row.get("Outstanding").toString());
                            }
                        }

                        //-----for consolidated invoice (eletrical invoice summary)-----
                        LinkedHashMap<String, Object> excelRow = new LinkedHashMap<>();
                        excelRow.put("ACCT_NO", "HH 0000-" + String.format("%04d", meterList.get(i).getTenantId()));
                        electricalInvExcelData.add(excelRow);
                    }
                }
            }
        }
    }

    //this function is to generate the missing data report for the personnel to go to the site and get the manual reading for the missing data
    public void generateMissingDataReport() {
        LinkedHashMap<String, Integer> errorReportHeaderRow = new LinkedHashMap<>();
        errorReportHeaderRow.put("Acc no", 2000);  // Set width in characters, e.g., 20 characters wide
        errorReportHeaderRow.put("Tenant name", 3000);
        errorReportHeaderRow.put("Unit number", 3500);
        errorReportHeaderRow.put("S/N", 3000);
        errorReportHeaderRow.put("KWHTOT_START", 3500);
        errorReportHeaderRow.put("START_TIMESTAMP", 3500);
        errorReportHeaderRow.put("KWHTOT_END", 3500);
        errorReportHeaderRow.put("END_TIMESTAMP", 3500);
        errorReportHeaderRow.put("Result", 2500);

        List<LinkedHashMap<String, Object>> missDataExcelData = new ArrayList<>();
        for (Meter meter : meterList) {
            LinkedHashMap<String, Object> excelRow = new LinkedHashMap<>();
            excelRow.put("Acc no", "HH 0000-" + String.format("%04d", meter.getTenantId()));
            excelRow.put("Tenant name", meter.getTenantName());
            excelRow.put("Unit number", "#" + meter.getFloor() + "-" + meter.getRightUnit());
            excelRow.put("S/N", meter.getMeterID());
            excelRow.put("KWHTOT_START", meter.getStartReading());
            excelRow.put("START_TIMESTAMP", meter.getStartTimeStamp());
            excelRow.put("KWHTOT_END", meter.getEndReading());
            excelRow.put("END_TIMESTAMP", meter.getEndTimeStamp());
            //case 2. no end reading
            //case 3. no start reading
            //case 4. no start and end reading
            if(!"-".equals(meter.getEndReading()) && !"-".equals(meter.getStartReading())){
                excelRow.put("Result", "Success");
            }
            else if ("-".equals(meter.getEndReading()) && !"-".equals(meter.getStartReading() )) {
                excelRow.put("Result", "Error : no end reading found");

            }else if (!"-".equals(meter.getEndReading()) && "-".equals(meter.getStartReading() )) {
                excelRow.put("Result", "Error : no start reading found");
            } else{
                excelRow.put("Result", "Error : no start and end reading found");
            }
            missDataExcelData.add(excelRow);
        }
        Map<String, Object> result = reportHelper.genReportExcel("MissingData", missDataExcelData, errorReportHeaderRow, "Miss data list");
        System.out.println(result);
//        systemNotifier.sendEmailWithAttachment("yujing1314276@gmail.com","pa621bu500@gmail.com","Missing Data Report","Please find the attached missing data report",(File)result.get("file"),false);
    }

    //generate the hotel invoices in excel file
    public void generateNormalInvoices() throws IOException {
        logger.info("generating normal invoice....");
        ExcelUtil normalExcelUtil = new ExcelUtil(normalInvFilePath);

        try {
            Resource template1Resource = resourceLoader.getResource(normalInvTemplatePath);
            InputStream template1InputStream = template1Resource.getInputStream();
            XSSFWorkbook workbook = new XSSFWorkbook(template1InputStream);
            Sheet sheet = null;

            int typeZero = 0;

            for (int i = 0; i < meterList.size(); i++) {
                if (meterList.get(i).getBillType() == 1) {
                    if (i - typeZero != 0) {
                        //clone sheet base on the first sheet (not from template anymore)
                        sheet = workbook.cloneSheet(0);
                        workbook.setSheetName(workbook.getNumberOfSheets() - 1, "000" + meterList.get(i).getTenantId().toString());
                    } else {
                        //get first sheet from the template
                        sheet = workbook.getSheetAt(0);
                        workbook.setSheetName(workbook.getNumberOfSheets() - 1, "000" + meterList.get(i).getTenantId().toString());
                    }
                    normalExcelUtil.setCellValue(sheet, "billMonth", lastDay.substring(3) + " Bill");
                    normalExcelUtil.setCellValue(sheet, "genDate", "Dated " + genDate);
                    normalExcelUtil.setCellValue(sheet, "accNo", "HH 0000-" + String.format("%04d", meterList.get(i).getTenantId()));
                    normalExcelUtil.setCellValue(sheet, "depositAmt", "SGD " + String.format("%.0f", meterList.get(i).getDeposit()));
                    normalExcelUtil.setCellValue(sheet, "invoiceNumberFr", "IN" + invDate);
                    normalExcelUtil.setCellValue(sheet, "invoiceNumberBk", "-" + String.format("1%04d", meterList.get(i).getTenantId()));
                    normalExcelUtil.setCellValue(sheet, "tenantName", meterList.get(i).getTenantName());
                    normalExcelUtil.setCellValue(sheet, "addr1", meterList.get(i).getAddr1());
                    normalExcelUtil.setCellValue(sheet, "unitName", meterList.get(i).getTenantName());
                    String addr2LeftUnit;
                    if ((meterList.get(i).getAddr2() == null || meterList.get(i).getAddr2().isEmpty()) && meterList.get(i).getLeftUnit().equals("-")) {
                        addr2LeftUnit = "";
                    } else if (meterList.get(i).getAddr2() == null || meterList.get(i).getAddr2().isEmpty()) {
                        addr2LeftUnit = meterList.get(i).getLeftUnit();
                    } else if (meterList.get(i).getLeftUnit().equals("-")) {
                        addr2LeftUnit = meterList.get(i).getAddr2();
                    } else {
                        addr2LeftUnit = meterList.get(i).getAddr2() + ", " + meterList.get(i).getLeftUnit();
                    }
                    normalExcelUtil.setCellValue(sheet, "addr2LeftUnit", addr2LeftUnit);
                    normalExcelUtil.setCellValue(sheet, "street", meterList.get(i).getStreet());
                    normalExcelUtil.setCellValue(sheet, "leftPostal", "Singapore(" + meterList.get(i).getLeftPostal() + ")");
                    normalExcelUtil.setCellValue(sheet, "rightUnitRightPostal", "#" + meterList.get(i).getFloor() + "-" + meterList.get(i).getRightUnit() + " Singapore(" + meterList.get(i).getRightPostal() + ")");
                    normalExcelUtil.setCellValue(sheet, "summaryOfCharges", "SUMMARY OF CHARGES " + firstDay + " to " + lastDay);
                    normalExcelUtil.setCellValue(sheet, "balanceFromPrevMonth",meterList.get(i).getBalanceFromPrevMonth());
                    if (meterList.get(i).getAmtReceived() != null) {
                        if (meterList.get(i).getAmtReceived() == -0) {
                            meterList.get(i).setAmtReceived(0.0);
                        }
                    }

                    if(meterList.get(i).getAmtReceived()!=null){
                        normalExcelUtil.setCellValue(sheet, "amtReceived", -Math.abs(meterList.get(i).getAmtReceived()) );
                    }


                    normalExcelUtil.setCellValue(sheet, "dueDate", billingDueDate);
                    if (meterList.get(i).getGiro() == 0) {
                        normalExcelUtil.setCellValue(sheet, "totalAmountPayable", "Total Amount Payable");
                    } else if (meterList.get(i).getGiro() == 1) {
                        normalExcelUtil.setCellValue(sheet, "totalAmountPayable", "Total Amount Payable will be charges to your Giro Acct on due Date");
                    }
                    normalExcelUtil.setCellValue(sheet, "paymentReceivedNote", "Payment received on or after " + latestPaymentDate + " may not be included into this bill");
                    normalExcelUtil.setCellValue(sheet, "readingMeterRatio", "Reading (kWh) taken from Meter with ratio " + meterList.get(i).getPowerfactor());
                    if(meterList.get(i).getPowerfactor() != null && !("-").equals(meterList.get(i).getUsage())){
                        double accumulatedUsage = (Double.parseDouble(meterList.get(i).getUsage()) * meterList.get(i).getPowerfactor()*1000)/1000;
                        double totalBeforeLateCharge = MathUtil.setDecimalPlaces(accumulatedUsage*tariffRate, 2, RoundingMode.HALF_UP);
                        double totalAfterLateCharge;
                        if(meterList.get(i).getLateCharges()!=null ){
                            if(meterList.get(i).getLateCharges() != 0) {
                                totalAfterLateCharge = totalBeforeLateCharge + meterList.get(i).getLateCharges();
                            }else{
                                totalAfterLateCharge = totalBeforeLateCharge;
                            }
                        }else{
                            totalAfterLateCharge = totalBeforeLateCharge;
                        }

                        double gstChargeAmount = MathUtil.setDecimalPlaces( gstRate*totalBeforeLateCharge, 2, RoundingMode.CEILING) ;
                        double finalChargeOfTheMonth = totalAfterLateCharge + gstChargeAmount;
                        double totalAmtPayale = meterList.get(i).getBalanceFromPrevMonth() - meterList.get(i).getAmtReceived() + finalChargeOfTheMonth;
                        normalExcelUtil.setCellValue(sheet, "usage", accumulatedUsage);
                        normalExcelUtil.setCellValue(sheet, "amount", totalBeforeLateCharge) ;
                        normalExcelUtil.setCellValue(sheet, "totalBeforeLateCharge", totalBeforeLateCharge) ;
                        normalExcelUtil.setCellValue(sheet, "totalAfterLateCharge", totalAfterLateCharge) ;
                        normalExcelUtil.setCellValue(sheet, "totalBeforeLateCharge2", totalBeforeLateCharge) ;
                        normalExcelUtil.setCellValue(sheet, "gstChargeAmount", gstChargeAmount) ;
                        normalExcelUtil.setCellValue(sheet, "finalCharge", finalChargeOfTheMonth) ;
                        normalExcelUtil.setCellValue(sheet, "newCharges", finalChargeOfTheMonth) ;
                        normalExcelUtil.setCellValue(sheet, "newCharges2", finalChargeOfTheMonth) ;
                        normalExcelUtil.setCellValue(sheet, "totalCharges", finalChargeOfTheMonth) ;
                        normalExcelUtil.setCellValue(sheet, "finalTotalCharges", totalAmtPayale) ;
                    }else{
                        normalExcelUtil.setCellValue(sheet, "usage", meterList.get(i).getUsage());
                    }
                    normalExcelUtil.setCellValue(sheet, "tariff",  tariffRate);

                    normalExcelUtil.setCellValue(sheet, "startRead", !("-").equals(meterList.get(i).getStartReading())? Double.parseDouble(meterList.get(i).getStartReading()):meterList.get(i).getStartReading());
                    normalExcelUtil.setCellValue(sheet, "endRead", !("-").equals(meterList.get(i).getEndReading())?Double.parseDouble(meterList.get(i).getEndReading()):meterList.get(i).getEndReading());

                    if (meterList.get(i).getLateCharges() == null || meterList.get(i).getLateCharges() == 0) {
                        normalExcelUtil.setCellValue(sheet, "lateChargesDescription", null);
                        normalExcelUtil.setCellValue(sheet, "lateCharges", null);
                    } else {
                        normalExcelUtil.setCellValue(sheet, "lateChargesDescription", "1% Interest Charge on Late Payment for " + meterList.get(i).getPeriodOfLatePayment());
                        normalExcelUtil.setCellValue(sheet, "lateCharges", meterList.get(i).getLateCharges());
                    }
                    normalExcelUtil.setCellValue(sheet, "gstRate", (gstRate*100)+"%") ;

                } else if (meterList.get(i).getBillType() == 0) {
                    typeZero++;
                }
            }

            if (sheet != null) {
                sheet.setForceFormulaRecalculation(true);
                workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            }
            String normalInvoiceOutputPath = outputPath + "Invoice_" + currDate;
            String normalInvoiceXlsx = normalInvoiceOutputPath + ".xlsx";
            FileOutputStream output_file = new FileOutputStream(new File(normalInvoiceXlsx));
            workbook.write(output_file);
            output_file.close();
            logger.info("normal invoice generated successfully");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void generateInvSummary() throws IOException {
        logger.info("generating invoice summary....");
        try {
            Resource template1Resource = resourceLoader.getResource(summaryTemplatePath);
            InputStream template1InputStream = template1Resource.getInputStream();
            XSSFWorkbook workbook = new XSSFWorkbook(template1InputStream);
            XSSFCell cell = null;
            XSSFSheet sheet = null;
            sheet = workbook.getSheetAt(0);
            ExcelUtil.writeNextCell(cell, sheet, 0, 0, "Tenants Electrical Invoices outstanding as at 1 " + invSummaryDate + " 2024", workbook, false);
            workbook.setSheetName(workbook.getNumberOfSheets() - 1, "invoice summary");

            for (int i = 0; i < meterList.size(); i++) {
                int j = i + 4;
                int k = 0;
                Double newChargeWGst=null;
                Double newCharge=null;
                k = ExcelUtil.writeNextCell(cell, sheet, k, j, "IN" + invDate + "-" + String.format("1%04d", meterList.get(i).getTenantId()), workbook, false);
                k = ExcelUtil.writeNextCell(cell, sheet, k, j, "HH 0000-" + String.format("%04d", meterList.get(i).getTenantId()), workbook, false);
                k = ExcelUtil.writeNextCell(cell, sheet, k, j, meterList.get(i).getTenantName(), workbook, false);
                k = ExcelUtil.writeNextCell(cell, sheet, k, j, "#" + meterList.get(i).getFloor() + "-" + meterList.get(i).getRightUnit(), workbook, false);
                if (meterList.get(i).getGiro() == 1) {
                    k = ExcelUtil.writeNextCell(cell, sheet, k, j, "GIRO", workbook, false);
                } else {
                    k = ExcelUtil.writeNextCell(cell, sheet, k, j, "", workbook, false);
                }

                if("-".equals(meterList.get(i).getUsage())){
                    k = ExcelUtil.writeNextCell(cell, sheet, k, j, "", workbook, false);
                }else{
                    double accumulatedUsage = (Double.parseDouble(meterList.get(i).getUsage()) * meterList.get(i).getPowerfactor()*1000)/1000;
                    k = ExcelUtil.writeNextCell(cell, sheet, k, j, accumulatedUsage, workbook, false);
                }

                if (meterList.get(i).getBalanceFromPrevMonth() == null || meterList.get(i).getBalanceFromPrevMonth() == 0 || meterList.get(i).getBalanceFromPrevMonth() == 0.0) {
                    k = ExcelUtil.writeNextCell(cell, sheet, k, j, "", workbook, true);
                } else {
                    k = ExcelUtil.writeNextCell(cell, sheet, k, j, meterList.get(i).getBalanceFromPrevMonth(), workbook, true);
                }

                if (meterList.get(i).getAmtReceived() == null || meterList.get(i).getAmtReceived() == 0 || meterList.get(i).getAmtReceived() == 0.0) {
                    k = ExcelUtil.writeNextCell(cell, sheet, k, j, "", workbook, true);
                } else {
                    k = ExcelUtil.writeNextCell(cell, sheet, k, j, meterList.get(i).getAmtReceived(), workbook, true);
                }


                if (meterList.get(i).getOutstandingBalance() != null) {
                    k = ExcelUtil.writeNextCell(cell, sheet, k, j, Double.parseDouble(meterList.get(i).getOutstandingBalance()), workbook, true);
                } else {
                    k = ExcelUtil.writeNextCell(cell, sheet, k, j, "", workbook, true);
                }

                k = ExcelUtil.writeNextCell(cell, sheet, k, j, "-", workbook, false);

                if (!"-".equals(meterList.get(i).getUsage())) {
                    double accumulatedUsage =  (Double.parseDouble(meterList.get(i).getUsage()) * meterList.get(i).getPowerfactor()*1000)/1000;
                    accumulatedUsage = MathUtil.setDecimalPlaces(accumulatedUsage, 2, RoundingMode.HALF_UP);
                    newCharge = MathUtil.setDecimalPlaces(accumulatedUsage*tariffRate, 2, RoundingMode.HALF_UP);
                    newChargeWGst = newCharge + MathUtil.setDecimalPlaces(newCharge * gstRate,2, RoundingMode.CEILING);
                    k = ExcelUtil.writeNextCell(cell, sheet, k, j, newChargeWGst, workbook, true);
                    k = ExcelUtil.writeNextCell(cell, sheet, k, j, newCharge, workbook, true);
                } else {
                    k = ExcelUtil.writeNextCell(cell, sheet, k, j, "", workbook, true);
                    k = ExcelUtil.writeNextCell(cell, sheet, k, j, "", workbook, true);
                }

                if (meterList.get(i).getLateCharges() != null) {
                    if (meterList.get(i).getLateCharges() == 0 || meterList.get(i).getLateCharges() == 0.0) {
                        k = ExcelUtil.writeNextCell(cell, sheet, k, j, 0, workbook, true);
                    } else {
                        k = ExcelUtil.writeNextCell(cell, sheet, k, j, meterList.get(i).getLateCharges(), workbook, true);
                    }
                } else {
                    k = ExcelUtil.writeNextCell(cell, sheet, k, j, "", workbook, true);
                }

                var balanceFromPrevMonth = meterList.get(i).getBalanceFromPrevMonth() == null ? 0.0 : meterList.get(i).getBalanceFromPrevMonth();
                var amtReceived = meterList.get(i).getAmtReceived() == null ? 0.0 : meterList.get(i).getAmtReceived();
                var lateCharge = meterList.get(i).getLateCharges() == null ? 0.0 : meterList.get(i).getLateCharges();

                if (!"-".equals(meterList.get(i).getUsage())) {
                    k = ExcelUtil.writeNextCell(cell, sheet, k, j,
                            MathUtil.setDecimalPlaces ((balanceFromPrevMonth -
                                    amtReceived + lateCharge +
                                    newChargeWGst),2, RoundingMode.HALF_UP), workbook, true);

                } else {
                    k = ExcelUtil.writeNextCell(cell, sheet, k, j, "-", workbook, true);
                }

                XSSFFormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
                sheet.setForceFormulaRecalculation(true);
                formulaEvaluator.evaluateAll();
            }
            // file name of the generated file
            FileOutputStream output_file = new FileOutputStream(new File(outputPath + "Electrical_Invoice_Summary_" + startDateTimeForInvSummary + "_ZSP" + ".xlsx"));
            workbook.write(output_file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //this function is to generate the VVC report
    public void generateVVCReport() throws Exception {
        logger.info("generating VVC report....");
        String itemIdColName = (String) itemConfig.get("itemIdColName");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime startDate = LocalDateTime.parse(startDateTime, formatter);
        LocalDateTime monthMinus1Date = startDate.minusMonths(1);
        LocalDateTime monthMinus2Date = startDate.minusMonths(2);
        LocalDateTime monthMinus3Date = startDate.minusMonths(3);
        DateTimeFormatter monthYearFormatter  = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
        String monthMinus1 = monthMinus1Date.format(monthYearFormatter);
        String monthMinus2 = monthMinus2Date.format(monthYearFormatter);
        String monthMinus3 = monthMinus3Date.format(monthYearFormatter);
        String currentMth = startDate.format(monthYearFormatter);
        List<String> months = new ArrayList<>();
        months.add(monthMinus3);
        months.add(monthMinus2);
        months.add(monthMinus1);

        System.out.println("prevMonth: "+prevMonth);

        LinkedHashMap<String, Integer> vvcHeader = new LinkedHashMap<>();
        vvcHeader.put("Acc no", 2000);  // Set width in characters, e.g., 20 characters wide
        vvcHeader.put("Tenant name", 3000);
        vvcHeader.put("S/N", 3000);
        vvcHeader.put(monthMinus3+ " Usage",3500);
        vvcHeader.put(monthMinus2+ " Usage",3500);
        vvcHeader.put(monthMinus1+ " Usage",3500);
        vvcHeader.put("past months average", 2500);
        vvcHeader.put(currentMth + " Usage", 3500);
        vvcHeader.put(currentMth + " start reading", 3500);
        vvcHeader.put(currentMth + " End reading", 3500);
        vvcHeader.put(currentMth + " start time", 3500);
        vvcHeader.put(currentMth + " end time", 3500);
        vvcHeader.put("Result", 2500);
        vvcHeader.put("VVC", 2500);
        Double pastMonthsAverage;
        List<LinkedHashMap<String, Object>> vvcData = new ArrayList<>();

        List<Map<String, Object>> meterListUsageSummary = new ArrayList<>();
        for(Meter meter:meterList){
            Map<String, Object> meterListUsageSummaryMap = new HashMap<>();
            meterListUsageSummaryMap.put(itemIdColName, String.valueOf(meter.getRecordID()));
            meterListUsageSummary.add(meterListUsageSummaryMap);
        }


        List<List<Map<String,Object>>> ListOFListSummary = new ArrayList<>();


        for (int i = 1; i <= 3; i++) {
            LocalDateTime prevEndDates = startDate.minusMonths(i).with(TemporalAdjusters.lastDayOfMonth())
                    .with(LocalTime.MAX); // Set to 23:59:59; // Subtract i months from the start date
            LocalDateTime prevStartDates = prevEndDates.with(TemporalAdjusters.firstDayOfMonth())
                    .with(LocalTime.MIN); // Set to 00:00:00; // Subtract i months from the start date

            Map<String, Object> resp =  meterRepository.getMeterUsage(prevStartDates.toString(),prevEndDates.toString(),meterListUsageSummary);
            ListOFListSummary.add((List<Map<String, Object>>) resp.get("meter_list_usage_summary"));
        }

        System.out.println("ListOFListSummary: "+ListOFListSummary);


        for (Meter meter : meterList) {
            LinkedHashMap<String, Object> excelRow = new LinkedHashMap<>();
            excelRow.put("Acc no", "HH 0000-" + String.format("%04d", meter.getTenantId()));
            excelRow.put("Tenant name", meter.getTenantName() + "123");
            excelRow.put("S/N", meter.getMeterID());

            List<String> pastMonthsUsage = new ArrayList<>();

            for (int i = 0; i < ListOFListSummary.size(); i++) {
                List<Map<String,Object>> meterListUsageSummaryResult = ListOFListSummary.get(i);
                for(Map<String, Object> meterUsageSummary : meterListUsageSummaryResult){
                    if (Objects.equals(meter.getRecordID(), MathUtil.ObjToInteger(meterUsageSummary.get(itemIdColName)))) {
                        excelRow.put(months.get(i)+ " Usage", meterUsageSummary.get("usage"));
                        pastMonthsUsage.add(meterUsageSummary.get("usage").toString());
                    }
                }
            }

            if (!"-".equals(pastMonthsUsage.get(0)) && !"-".equals(pastMonthsUsage.get(1)) && !"-".equals(pastMonthsUsage.get(2))) {

                pastMonthsAverage = MathUtil.setDecimalPlaces ((
                        Double.parseDouble(pastMonthsUsage.get(0)) +
                                Double.parseDouble(pastMonthsUsage.get(1)) +
                                Double.parseDouble(pastMonthsUsage.get(2))) / 3, 0, RoundingMode.HALF_UP);
                excelRow.put("past months average", pastMonthsAverage);
            } else {
                pastMonthsAverage = null;
                excelRow.put("past months average", "-");
            }

            excelRow.put(currentMth + " usage", meter.getUsage());
            excelRow.put(currentMth + " start reading", meter.getStartReading());
            excelRow.put(currentMth + " End reading", meter.getEndReading());
            excelRow.put(currentMth + " start time", meter.getStartTimeStamp());
            excelRow.put(currentMth + " end time", meter.getEndTimeStamp());

            if ("-".equals(pastMonthsUsage.get(0))) {
                excelRow.put("Result", "Error : unable to find " + months.get(0)+ " usage");
            } else if ("-".equals(pastMonthsUsage.get(1))) {
                excelRow.put("Result", "Error : unable to find " + months.get(1)+ " usage");
            } else if ("-".equals(pastMonthsUsage.get(2))) {
                excelRow.put("Result", "Error : unable to find " + months.get(2)+ " usage");
            } else if ("-".equals(meter.getUsage())) {
                excelRow.put("Result", "Error : unable to find " + currentMth + " usage");
            } else {
                excelRow.put("Result", "Success");
            }

            if (!"-".equals(meter.getUsage()) && pastMonthsAverage!=null) {
                String vccResult = MathUtil.setDecimalPlaces(((Double.parseDouble(meter.getUsage()) / pastMonthsAverage) * 100), 0, RoundingMode.HALF_UP) + "%";
                excelRow.put("VVC", vccResult);
            }
            pastMonthsUsage.clear();

            vvcData.add(excelRow);
        }
        reportHelper.genReportExcel("vvcData", vvcData, vvcHeader, "vvc result");

    }

    public void generateHotelInvoices() throws IOException {
        logger.info("generating hotel invoice....");
        ExcelUtil hotelExcelUtil = new ExcelUtil(hotelInvFilePath);

        try {
            Resource template2Resource = resourceLoader.getResource(normalInvTemplatePath);
            InputStream template2InputStream = template2Resource.getInputStream();
            XSSFWorkbook workbook = new XSSFWorkbook(template2InputStream);
            Sheet sheet = null;
            int typeOne = 0;

            for (int i = 0; i < meterList.size(); i++) {
                if (meterList.get(i).getBillType() == 0) {
                    if (i - typeOne != 0) {
                        sheet = workbook.cloneSheet(0);
                    } else {
                        sheet = workbook.getSheetAt(0);
                    }

                    hotelExcelUtil.setCellValue(sheet, "billMonth", lastDay.substring(3) + " Bill");
                    hotelExcelUtil.setCellValue(sheet, "genDate", "Dated " + genDate);
                    hotelExcelUtil.setCellValue(sheet, "accNo", "HH 0000-" + String.format("%04d", meterList.get(i).getTenantId()));
                    hotelExcelUtil.setCellValue(sheet, "depositAmt", "SGD " + meterList.get(i).getDeposit());
                    hotelExcelUtil.setCellValue(sheet, "invoiceNumber", "IN" + invDate + String.format("1%04d", i + 1));
                    hotelExcelUtil.setCellValue(sheet, "tenantName", meterList.get(i).getTenantName());
                    hotelExcelUtil.setCellValue(sheet, "addr1", meterList.get(i).getAddr1());
                    hotelExcelUtil.setCellValue(sheet, "unitName", meterList.get(i).getUnitName());

                    String addr2LeftUnit;
                    if ((meterList.get(i).getAddr2() == null || meterList.get(i).getAddr2().isEmpty()) && meterList.get(i).getLeftUnit().equals("-")) {
                        addr2LeftUnit = "";
                    } else if (meterList.get(i).getAddr2() == null || meterList.get(i).getAddr2().isEmpty()) {
                        addr2LeftUnit = meterList.get(i).getLeftUnit();
                    } else if (meterList.get(i).getLeftUnit().equals("-")) {
                        addr2LeftUnit = meterList.get(i).getAddr2();
                    } else {
                        addr2LeftUnit = meterList.get(i).getAddr2() + ", " + meterList.get(i).getLeftUnit();
                    }
                    hotelExcelUtil.setCellValue(sheet, "addr2LeftUnit", addr2LeftUnit);

                    hotelExcelUtil.setCellValue(sheet, "street", meterList.get(i).getStreet());
                    hotelExcelUtil.setCellValue(sheet, "leftPostal", "Singapore(" + meterList.get(i).getLeftPostal() + ")");
                    hotelExcelUtil.setCellValue(sheet, "rightUnitRightPostal", "#" + meterList.get(i).getFloor() + "-" + meterList.get(i).getRightUnit() + " Singapore(" + meterList.get(i).getRightPostal() + ")");
                    hotelExcelUtil.setCellValue(sheet, "summaryOfCharges", "SUMMARY OF CHARGES " + firstDay + " to " + lastDay);
                    hotelExcelUtil.setCellValue(sheet, "dueDate", billingDueDate);
                    hotelExcelUtil.setCellValue(sheet, "paymentReceivedNote", "Payment received on or after " + latestPaymentDate + " may not be included into this bill");
                    hotelExcelUtil.setCellValue(sheet, "powerFactor", "Reading (kWh) taken from Meter on " + meterList.get(i).getPowerfactor());
                    hotelExcelUtil.setCellValue(sheet, "m1startReading", meterList.get(i).getStartReading());
                    hotelExcelUtil.setCellValue(sheet, "m1endReading", meterList.get(i).getEndReading());
                    hotelExcelUtil.setCellValue(sheet, "m2startReading", meterList.get(i).getM2startReading());
                    hotelExcelUtil.setCellValue(sheet, "m2endReading", meterList.get(i).getM2endReading());
                    hotelExcelUtil.setCellValue(sheet, "m1ID", meterList.get(i).getMeterID());
                    hotelExcelUtil.setCellValue(sheet, "m2ID", meterList.get(i).getMeterID2());
                } else if (meterList.get(i).getBillType() == 1) {
                    typeOne++;
                }
            }

            if (sheet != null) {
                sheet.setForceFormulaRecalculation(true);
                workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            } else {
                throw new RuntimeException("error getting sheet data");
            }

            String hotelInvoiceOutputPath = outputPath + "Invoice-2_" + currDate;
            String hotelInvoiceXlsx = hotelInvoiceOutputPath + ".xlsx";
            try (FileOutputStream output_file = new FileOutputStream(new File(hotelInvoiceXlsx))) {
                workbook.write(output_file);
            }
            logger.info("hotel invoice generated successfully");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //this function sets all the required dates for the reports
    public void setDateTime(Integer year, Integer month) {
        SimpleDateFormat displayDate = new SimpleDateFormat("dd MMM yyyy");
        SimpleDateFormat longMonthYear = new SimpleDateFormat(" MMM yyyy");
        SimpleDateFormat shortMonthYear = new SimpleDateFormat("-MMM-yy");
        SimpleDateFormat searchDate = new SimpleDateFormat("yyyy-MM");
        SimpleDateFormat excelNamingDate = new SimpleDateFormat("yyyy MM");
        SimpleDateFormat invoice = new SimpleDateFormat("yyMM");
        SimpleDateFormat startMonthDateTime = new SimpleDateFormat("yyyy-MM-dd 00:00:00");
        SimpleDateFormat startDateTimeInvSummary = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat invSummaryDateFormat = new SimpleDateFormat("MMM");

        Calendar setDate = Calendar.getInstance();                                // current month
        if(year!=null && month!=null){
            setDate.set(year, month-1, 1);
        }else{
            setDate.set(Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), 1);

        }

        currDate = searchDate.format(setDate.getTime());                        // to display year month for the generated files (e.g. 2018-02)
        billingDueDate = dueDate + shortMonthYear.format(setDate.getTime());            // to set the due date of the billing (e.g. 15-Feb-2018)
        genDate = displayDate.format(setDate.getTime());                        // to set the billing date (e.g. 01 Feb 2018)
        invDate = invoice.format(setDate.getTime());                            // to set the invoice date number (e.g. 1802)
        latestPaymentDate = latestPaymentDate + longMonthYear.format(setDate.getTime()); //date in which payment may not be included after this date (e.g. 24 july 2024)
        firstDay = displayDate.format(setDate.getTime());                        // to set the first billing date (e.g. 01 Jan 2017)
        invSummaryDate = invSummaryDateFormat.format(setDate.getTime());        // to set the date for naming the excel file (e.g. Jan)
        setDate.add(Calendar.MONTH, -1);                            //*** setting it toprevious month
        startDateTime = startMonthDateTime.format(setDate.getTime());
        startDateTimeForInvSummary = startDateTimeInvSummary.format(setDate.getTime());
        prevMonth = invSummaryDateFormat.format(setDate.getTime());
        billingEndDate = searchDate.format(setDate.getTime());                        // to set the date for searching the start read in energy_etc table (e.g. 2017-12)
        lastDay = displayDate.format(setDate.getTime());                        // to set the last billing date (e.g. 31 Jan 2018)
        excelDate = excelNamingDate.format(setDate.getTime());            // to set the date for naming the excel file (e.g. 2018 01)
        setDate.add(Calendar.MONTH, -1);
        billingStartDate = searchDate.format(setDate.getTime());                    // to set the date for searching the end read in energy_etc table (e.g. 2018-01) or start date
        logger.info("billingStartDate:" + billingStartDate);
        logger.info("billingEndDate:" + billingEndDate);
    }
}
