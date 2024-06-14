package com.pabu5h.zsp.service;
import com.pabu5h.zsp.model.Meter;
import com.pabu5h.zsp.repository.MeterRepository;
import com.pabu5h.zsp.util.ExcelReader;
import com.pabu5h.zsp.util.ExcelUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.pabuff.dto.ItemIdTypeEnum;
import org.pabuff.dto.ItemTypeEnum;
import org.pabuff.evs2helper.meter_usage.MeterUsageProcessor;
import org.pabuff.evs2helper.scope.ScopeHelper;
import org.pabuff.utils.MathUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import java.io.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

@Service
public class MeterService {


    @Autowired
    private MeterRepository meterRepository;

    @Autowired
    private ExcelReader excelReader;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private MeterUsageProcessor meterUsageProcessor;

    @Autowired
    private ScopeHelper scopeHelper;

    @Value("${normal.invoice.config.path}")
    private String normalInvFilePath;

    @Value("${type0.invoice.config.path}")
    private String type0FilePath;

    private ArrayList<Meter> meterList = new ArrayList<Meter>();
    private List<Map<String,Object>> excelData;

    @Value("${template1.dir}")
    private String template1Dir;			// location of the template used for most of the meters
    @Value("${template2.dir}")
    private String template2Dir;			// location of the template used for selected meters
    @Value("${output.dir}")
    private String outputPath;			// output path for generated file
    @Value("${due.date}")
    private String dueDate;				// the due date for the billing
    @Value("${latest.payment.date}")
    private String latestPaymentDate;			// payment will be excluded after this date
    private double tariff;				// tariff rate
    private double gstRate;				// gst rate
    private String startError = "";		// error message for start reading when retrieving the data
    private String endError = "";		// error message for end reading when retrieving the data
    private String paymentError = "";	// error message for payment when retrieving the data
    private String invoiceError = "";	// error message for invoice when retrieving the data
    private String currDate = "";		// current year and month for generated excel file
    private String firstDay;			// first day of the month of the billing month
    private String lastDay;				// last day of the month of the billing month
    private String genDate;				// generated billing date
    private String billingEndDate;			// late payment starting date
    private String billingStartDate;		// late payment ending date & starting date for bill 0
    private String endDate;				// end date for bill 0
    private String payDate;				// database payment table date format
    private String invDate;				// invoice date
    private String searchInv = "IN";		// previous invoice ref code
    private String excelDate;

    Logger logger = Logger.getLogger(MeterService.class.getName());

    //set all required dates
    public void setDateTime(){
        SimpleDateFormat displayDate = new SimpleDateFormat("dd MMM yyyy");
        SimpleDateFormat longMonthYear = new SimpleDateFormat(" MMM yyyy");
        SimpleDateFormat shortMonthYear = new SimpleDateFormat("-MMM-yy");
        SimpleDateFormat searchDate = new SimpleDateFormat("yyyy-MM");
        SimpleDateFormat excelNamingDate = new SimpleDateFormat("yyyy MM");
        SimpleDateFormat searchPay = new SimpleDateFormat("yyyy/MM");
        SimpleDateFormat invoice = new SimpleDateFormat("yyMM-");

        Calendar setDate = Calendar.getInstance();								// example when the date when running this program is 2018-02-01 (or any other date on february 2018)
        setDate.set(2024, Calendar.MAY, 1);
        currDate = searchDate.format(setDate.getTime());						// to display year month for the generated files (e.g. 2018-02)
        dueDate = dueDate + shortMonthYear.format(setDate.getTime());			// to set the due date of the billing (e.g. 15-Feb-2018)
        genDate = displayDate.format(setDate.getTime());						// to set the billing date (e.g. 01 Feb 2018)
        invDate = invoice.format(setDate.getTime());							// to set the invoice date number (e.g. 1802-)
        firstDay = displayDate.format(setDate.getTime());						// to set the first billing date (e.g. 01 Jan 2017)
        setDate.add(Calendar.MONTH, -1);							//*** setting it to last day of the previous month
        billingEndDate = searchDate.format(setDate.getTime());						// to set the date for searching the start read in energy_etc table (e.g. 2017-12)
        searchInv += invoice.format(setDate.getTime());							// to set the date for searching invoice ref from invoice table
        payDate = searchPay.format(setDate.getTime());							// to set the date for searching timestamp in the payment table (e.g. 2018/01)
        latestPaymentDate = latestPaymentDate + longMonthYear.format(setDate.getTime());	// to set the date in which payment may not be included after this date (e.g. 24 Jan 2018)
        lastDay = displayDate.format(setDate.getTime());						// to set the last billing date (e.g. 31 Jan 2018)
        excelDate = excelNamingDate.format(setDate.getTime());			// to set the date for naming the excel file (e.g. 2018 01)
        setDate.add(Calendar.MONTH, -1);
        billingStartDate = searchDate.format(setDate.getTime());					// to set the date for searching the end read in energy_etc table (e.g. 2018-01) or start date
        logger.info("billingStartDate:"+billingStartDate);
        logger.info("billingEndDate:"+billingEndDate);
    }
    public Map<String,Object> getMbr(List<Map<String,Object>> meterList){
        Map<String, String> request = new HashMap<>();
        request.put("project_scope","ems_zsp");
        request.put("is_monthly", "true");
        request.put("start_datetime", "2018-04-01 00:00:00");
        request.put("end_datetime", "2018-04-30 23:59:59");
        request.put("item_type", ItemTypeEnum.TENANT.name());
        request.put("meter_type",ItemTypeEnum.METER_IWOW.name());
        request.put("item_id_type", ItemIdTypeEnum.NAME.name());
        request.put("project_scope", "ALL");
        Map<String,Object> result =  scopeHelper.getItemTypeConfig2("ems_zsp",null);
        System.out.println(result);
        return meterUsageProcessor.getMeterListUsageSummary(request,meterList);
    }

    //retrieve all required data from db, such as meter list, tariff rate, start and end usage etc..
    public Map<String,Object> getMeterData() throws Exception {
        logger.info("retrieving meter data....");
        List<Map<String,Object>> metersData = meterRepository.getMeterData();

        if(metersData.isEmpty()){
            return Collections.singletonMap("info","No record found, Please check PREMISE, RECORDER or TENANT Table in database!") ;
        }else{
            logger.info("meter data retrieved successfully");
            System.out.println("metersData:"+metersData);
        }

        //--- outdated meter list ---
        Map<String, Object> outdatedMeterList = Map.ofEntries(
                Map.entry("53085706", Map.of("new_meter_id","04-46-96-00-00-33","new_recid","345","new_power_factor",1)), //345
                Map.entry("17430167", Map.of("new_meter_id","04-46-96-00-00-31","new_recid","1541","new_power_factor",1)), //310
                Map.entry("53085698", Map.of("new_meter_id","44-69-00-00-00-84","new_recid","310","new_power_factor",1)), //not found , 328
                Map.entry("17944170", Map.of("new_meter_id","04-63-67-00-01-14","new_recid","1617","new_power_factor",1)), //322
                Map.entry("17944169", Map.of("new_meter_id","04-46-96-00-00-34","new_recid","1540","new_power_factor",1)),
                Map.entry("27079568", Map.of("new_meter_id","12-05-25-70-00-36","new_recid","1665","new_power_factor",30)),
                Map.entry("18238738", Map.of("new_meter_id","04-63-67-00-00-06","new_recid","1543","new_power_factor",1)),
                Map.entry("27079569", Map.of("new_meter_id","12-05-25-70-00-31","new_recid","1666","new_power_factor",30))
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
        for(Map<String,Object> row:metersData){
            Meter meterEntry = new Meter(
                    MathUtil.ObjToInteger(row.get("recid")),
                    MathUtil.ObjToInteger(row.get("tenant_id")),
                    MathUtil.ObjToInteger(row.get("bill_type")),
                    MathUtil.ObjToInteger(row.get("tenantgroup_id")),
                    (String) row.get("recname"),
                    MathUtil.ObjToDouble(row.get("deposit"))!=null?MathUtil.ObjToDouble(row.get("deposit")):0,
                    (String) row.get("tenant_name"),
                    (String) row.get("address1"),
                    (String) row.get("address2"),
                    (String) row.get("unit"),
                    row.get("postal").toString(),
                    (String) row.get("unit_name"),
                    (String) row.get("street"),
                    (String) row.get("floor"),
                    (String) row.get("rightunit"),
                    row.get("rightpostal")!=null?row.get("rightpostal").toString():"",
                    MathUtil.ObjToInteger(row.get("powerfactor")),
                    MathUtil.ObjToInteger(row.get("giro"))
            );
            meterList.add(meterEntry);
        }
//        // find mbr(monthly bill reference)-> first reading of the month

        Map<String,Object> itemConfig =  scopeHelper.getItemTypeConfig2("ems_zsp",null);
        String itemReadingTableName = (String) itemConfig.get("itemReadingTableName");
        String itemReadingIdColName = (String) itemConfig.get("itemReadingIdColName");
        String itemReadingIndexColName = (String) itemConfig.get("itemReadingIndexColName");
        String timeKey =(String) itemConfig.get("timeKey");
        String valKey = (String) itemConfig.get("valKey");


        Map<String, String> request = new HashMap<>();
        request.put("project_scope", "ems_zsp");
        request.put("itemReadingTableName", itemReadingTableName);
        request.put("itemReadingIdColName", itemReadingIdColName);
        request.put("itemReadingIndexColName", itemReadingIndexColName);
        request.put("timeKey", timeKey);
        request.put("valKey", valKey);
        request.put("from_timestamp", "2024-04-01 00:00:00");
        request.put("to_timestamp", "2024-04-30 23:59:59");



        meterUsageProcessor.getMeterListUsageSummary(request,metersData);

//        final DateTimeFormatter formatterWithSpace = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//        for(Meter row:meterList){
//            LocalDateTime commissionedDatetime = LocalDateTime.parse("2020-02-01 00:00:00", formatterWithSpace);
//            Map<String,Object> result = meterRepository.findMonthlyReading(null,"2024-04-01 13:59:59", String.valueOf(row.getRecordID()),"energy_etc", "egyinstkey","egyid","timestamp","kwhtot");
//        }
        return null;
    }

    public void getTariffRate () {
        // retrieve tariff rate
        try{
            Map<String, Object> tariffRate = meterRepository.getTariffRate();
            logger.info("retrieving tariff rate....");
            if (tariffRate.isEmpty()) {
                logger.info("No record found! Please check SPRATE Table in database!");
            }else{
                tariff = MathUtil.ObjToDouble(tariffRate.get("rates"));
                logger.info("tariff rate retrieved successfully");
            }
        }catch (Exception e){
            throw new RuntimeException("No record found! Please check SPRATE Table in database!");
        }
    }

    //get kwhtot, timestamp, meterID for the billing month e.g 2024-05-31 23:59:59 to 2024-06-31 23:59:59
    public void getStartAndEndMeterReading() throws Exception {
        List<Map<String,Object>> startKwhResult =  meterRepository.getOverallKwhTotByEndOfMonth(billingStartDate);
        List<Map<String,Object>> endKwhResult = meterRepository.getOverallKwhTotByEndOfMonth(billingEndDate);
        logger.info("retrieving start and end kwh reading....");
        if(startKwhResult.isEmpty()) {
           logger.info("No start reading found! Please check ENERGY_ETC Table in database!");
        }else{
            logger.info("start kwh reading retrieved successfully");

        }
        if(endKwhResult.isEmpty()) {
           logger.info("No end reading found! Please check ENERGY_ETC Table in database!");
        }else{
            logger.info("end kwh reading retrieved successfully");
        }

        //set the start and end reading for each meter
        for(int i=0;i<meterList.size();i++){
            for(Map<String,Object> startKwh: startKwhResult){
                if(startKwh.get("recname").equals(meterList.get(i).getMeterID())){
                    meterList.get(i).setStartRead(MathUtil.ObjToDouble(startKwh.get("kwhtot")));
                }
            }
            for(Map<String,Object> endKwh: endKwhResult){
                if(endKwh.get("recname").equals(meterList.get(i).getMeterID())){
                    meterList.get(i).setEndRead(MathUtil.ObjToDouble(endKwh.get("kwhtot")));
                }
            }
        }

        //calculate meter usage base on the end and start reading
        for (Meter meter : meterList) {
            meter.setUsage(Math.round(((meter.getEndRead() - meter.getStartRead()) * meter.getPowerfactor()) * 1000.0) / 1000.0);
        }
    }


    public void getPaymentChargeFromExcel(){
        try{
            //read the financial data excel sheet from zsp to get the previous balance and outstanding payment
            excelData =  excelReader.readXlsFile("src/main/resources/excel_from_zsp/"+excelDate+" Tenants Electrical Invoices Summary "+lastDay.substring(3)+".xls",6,2);
        } catch (IOException e) {
            throw new RuntimeException("Error reading excel file : ", e);
        }

        //get the balanceBF and total paid up to date for each meter, balance from previous month  = balance bf + new charge
        //get the total paid up to date for each meter if any
        //get the late charge, late charge period for each meter if any
        for(Map<String,Object> row : excelData){
            String accountNo = row.get("Account No").toString();
            if(accountNo!=null && !accountNo.isEmpty()){
                //get last 4 digits of account number (tenant ID)
                String tenantID = accountNo.substring(accountNo.length() - 4);
                for(int i = 0; i < meterList.size(); i++){
                    //if tenant ID matches, set the balance from previous month and amount received
                    if( Objects.equals(tenantID, String.format("%04d", meterList.get(i).getTenantId()))){
                        //get the balanceBF and new charges for each meter
                        double newCharge =0;
                        double balanceBf = 0;
                        String keyWithSubstring = findKeyContainingSubstring(row, "New Charges");
                        if (row.get(keyWithSubstring) != null && !row.get(keyWithSubstring).toString().isEmpty()) {
                            newCharge = Double.parseDouble(row.get(keyWithSubstring).toString());
                        }
                        if (row.get("Balance B/F") != null && !row.get("Balance B/F").toString().isEmpty()) {
                            balanceBf = Double.parseDouble(row.get("Balance B/F").toString());
                        }
                        //balance from previous month  = balance bf + new charge
                        meterList.get(i).setBalanceFromPrevMonth(balanceBf+newCharge);

                        //get the total paid up to date for each meter if any
                        if(row.get("Total Paid Up To date")!=null && !row.get("Total Paid Up To date").toString().isEmpty()){
                            double totalPaid = Double.parseDouble(row.get("Total Paid Up To date").toString());
                            meterList.get(i).setAmtReceived(-Math.abs(totalPaid));
                        }

                        //get the late charge, late charge period for each meter if any
                        //note*** THERE IS A SPACE BEHIND for Period of Late Payment Interest
                        keyWithSubstring = findKeyContainingSubstring(row, "Late Payment Interest for");
                        if (row.get(keyWithSubstring) != null && !row.get(keyWithSubstring).toString().isEmpty()) {
                            meterList.get(i).setLateCharges(Double.parseDouble(row.get(keyWithSubstring).toString()));
                            meterList.get(i).setPeriodOfLatePayment(row.get("Period of Late Payment Interest ").toString());
                        }
                    }
                }
            }
        }
        

    }

    public static String findKeyContainingSubstring(Map<String, Object> map, String substring) {
        for (String key : map.keySet()) {
            if (key.contains(substring)) {
                return key;
            }
        }
        return null;
    }

    //generate the hotel invoices in excel file
    public void generateNormalInvoices() throws IOException {
        logger.info("generating normal invoice....");
        ExcelUtil normalExcelUtil = new ExcelUtil(normalInvFilePath);

        try {
            Resource template1Resource = resourceLoader.getResource(template1Dir);
            InputStream template1InputStream = template1Resource.getInputStream();
            XSSFWorkbook workbook = new XSSFWorkbook(template1InputStream);
            Sheet sheet = null;

            int typeZero = 0;

            for (int i = 0; i < meterList.size(); i++) {
                if (meterList.get(i).getBillType() == 1) {
                    if (i - typeZero != 0) {
                        //clone sheet base on the first sheet (not from template anymore)
                        sheet = workbook.cloneSheet(0);
                        workbook.setSheetName(workbook.getNumberOfSheets() - 1, "000"+meterList.get(i).getTenantId().toString());
                    } else {
                        //get first sheet from the template
                        sheet = workbook.getSheetAt(0);
                        workbook.setSheetName(workbook.getNumberOfSheets() - 1, "000"+meterList.get(i).getTenantId().toString());
                    }
                    normalExcelUtil.setCellValue(sheet, "billMonth", lastDay.substring(3) + " Bill");
                    normalExcelUtil.setCellValue(sheet, "genDate", "Dated " + genDate);
                    normalExcelUtil.setCellValue(sheet, "accNo", "HH 0000-" + String.format("%04d", meterList.get(i).getTenantId()));
                    normalExcelUtil.setCellValue(sheet, "depositAmt", "SGD " + String.format("%.0f",meterList.get(i).getDeposit()));
                    normalExcelUtil.setCellValue(sheet, "invoiceNumber", "IN" + invDate + String.format("1%04d", i + 1 - typeZero));
                    normalExcelUtil.setCellValue(sheet, "tenantName", meterList.get(i).getTenant());
                    normalExcelUtil.setCellValue(sheet, "addr1", meterList.get(i).getAddr1());
                    normalExcelUtil.setCellValue(sheet, "unitName", meterList.get(i).getUnitName());
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
                    normalExcelUtil.setCellValue(sheet, "balanceFromPrevMonth", String.format("%.2f", meterList.get(i).getBalanceFromPrevMonth()));
                    if (meterList.get(i).getAmtReceived() == -0) {
                        meterList.get(i).setAmtReceived(0);
                    }
                    normalExcelUtil.setCellValue(sheet, "amtReceived", String.format("%.2f", meterList.get(i).getAmtReceived()));
                    normalExcelUtil.setCellValue(sheet, "dueDate", dueDate);
                    if (meterList.get(i).getGiro() == 0) {
                        normalExcelUtil.setCellValue(sheet, "totalAmountPayable", "Total Amount Payable");
                    } else if (meterList.get(i).getGiro() == 1) {
                        normalExcelUtil.setCellValue(sheet, "totalAmountPayable", "Total Amount Payable will be charges to your Giro Acct on due Date");
                    }
                    normalExcelUtil.setCellValue(sheet, "paymentReceivedNote", "Payment received on or after " + latestPaymentDate + " may not be included into this bill");
                    normalExcelUtil.setCellValue(sheet, "readingMeterRatio", "Reading (kWh) taken from Meter with ratio " + meterList.get(i).getPowerfactor());
                    normalExcelUtil.setCellValue(sheet, "usage", String.format("%.2f", meterList.get(i).getUsage()));
                    normalExcelUtil.setCellValue(sheet, "tariff", String.format("%.4f", tariff));
                    normalExcelUtil.setCellValue(sheet, "startRead", String.format("%.2f", meterList.get(i).getStartRead()));
                    normalExcelUtil.setCellValue(sheet, "endRead", String.format("%.2f", meterList.get(i).getEndRead()));
                    if (meterList.get(i).getLateCharges() != 0) {
                        normalExcelUtil.setCellValue(sheet, "lateChargesDescription", "1% Interest Charge on Late Payment for " + meterList.get(i).getPeriodOfLatePayment());
                        normalExcelUtil.setCellValue(sheet, "lateCharges", String.format("%.2f", meterList.get(i).getLateCharges()));
                    }else{
                        normalExcelUtil.setCellValue(sheet, "lateChargesDescription", "");
                        normalExcelUtil.setCellValue(sheet, "lateCharges", null);
                    }
                } else if (meterList.get(i).getBillType() == 0) {
                    typeZero++;
                }
            }

            if(sheet!=null){
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


    public void generateHotelInvoices() throws IOException {
        logger.info("generating hotel invoice....");
        ExcelUtil hotelExcelUtil = new ExcelUtil(type0FilePath);

        try {
            Resource template2Resource = resourceLoader.getResource(template1Dir);
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
                    hotelExcelUtil.setCellValue(sheet, "invoiceNumber", "IN" + invDate + String.format("1%04d", i + 1 ));
                    hotelExcelUtil.setCellValue(sheet, "tenantName", meterList.get(i).getTenant());
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
                    hotelExcelUtil.setCellValue(sheet, "dueDate", dueDate);
                    hotelExcelUtil.setCellValue(sheet, "paymentReceivedNote", "Payment received on or after " + latestPaymentDate + " may not be included into this bill");
                    hotelExcelUtil.setCellValue(sheet, "powerFactor", "Reading (kWh) taken from Meter on " + meterList.get(i).getPowerfactor());
                    hotelExcelUtil.setCellValue(sheet, "m1startReading", meterList.get(i).getStartRead());
                    hotelExcelUtil.setCellValue(sheet, "m1endReading", meterList.get(i).getEndRead());
                    hotelExcelUtil.setCellValue(sheet, "m2startReading", meterList.get(i).getM2startReading());
                    hotelExcelUtil.setCellValue(sheet, "m2endReading", meterList.get(i).getM2endReading());
                    hotelExcelUtil.setCellValue(sheet, "m1ID", meterList.get(i).getMeterID());
                    hotelExcelUtil.setCellValue(sheet, "m2ID", meterList.get(i).getMeterID2());
                } else if (meterList.get(i).getBillType() == 1) {
                    typeOne++;
                }
            }

            if(sheet!=null){
                sheet.setForceFormulaRecalculation(true);
                workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            }else{
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



}
