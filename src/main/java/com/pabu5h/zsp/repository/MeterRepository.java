package com.pabu5h.zsp.repository;

import com.pabu5h.zsp.model.Meter;
import org.pabuff.evs2helper.locale.LocalHelper;

import org.pabuff.evs2helper.meter_usage.MeterUsageProcessor;
import org.pabuff.oqghelper.OqgHelper;
import org.pabuff.utils.DateTimeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.logging.Logger;

@Repository
public class MeterRepository {
    Logger logger = Logger.getLogger(MeterRepository.class.getName());
    @Autowired
    private OqgHelper oqgHelper;

    @Autowired
    private JdbcTemplate jdbcTemplatePub3;

    @Autowired
    private LocalHelper localHelper;

    //first query
    public List<Map<String,Object>> getMeterData() throws Exception {
        try {
            // retrieve all required information from the database (premise, recorder and tenant table)
            // recorderID (Meter id), tenant ID , billType (1 or 0), groupID, meterName, deposit, tenant, addr1, addr2, leftUnit, leftPostal, unitName, street, floor, rightUnit, rightPostal, powerfactor
            String sql = "select r.recid, p.tenant_id, t.bill_type, t.tenantgroup_id, r.recname, t.deposit, " +
                    "t.tenant_name, t.address1, t.address2, t.unit, t.postal, t.unit_name, p.street, p.floor, p.unit as rightunit, p.postal as rightpostal, " +
                    "r.powerfactor, t.giro " +
                    "from premise p, recorder r, tenant t " +
                    "where p.recid = r.recid and p.tenant_id = t.tenant_id " +
                    "order by p.tenant_id";
            return oqgHelper.OqgR2(sql,true);
//          return jdbcTemplatePub3.queryForList(sql);
        } catch (Exception e) {
            throw new Exception("Error: " + e.getMessage());
        }
    }

    public List<Map<String,Object>> getOverallKwhTotByStartOfMonth(String date) throws Exception{
        try{
            String formattedEndDate = date + "-01";
            // Convert formattedStartDate to Timestamp object
            Timestamp timestampDate = Timestamp.valueOf(formattedEndDate + " 00:00:00");
            String sql = "SELECT r.recname, e.timestamp, e.kwhtot " +
                    "FROM ( " +
                    "    SELECT recname, recid " +
                    "    FROM zsp.recorder " +
                    ") r " +
                    "INNER JOIN ( " +
                    "    SELECT " +
                    "        e.egyinstkey, " +
                    "        MAX(timestamp) AS timestamp, " +
                    "        MAX(kwhtot) AS kwhtot" +
                    "    FROM zsp.energy_etc e " +
                    "    WHERE e.timestamp >= '" + timestampDate + "' " +
                    "    GROUP BY e.egyinstkey " +
                    ") e ON r.recid = e.egyinstkey";

//            return jdbcTemplatePub3.queryForList(sql);
            return oqgHelper.OqgR2(sql,true);
        }catch(Exception e){
            throw new Exception("Error: " + e.getMessage());
        }
    }

    public List<Map<String,Object>> getOverallKwhTotByEndOfMonth(String date) throws Exception{
        try{
            // Assuming endDate is in "yyyy-MM" format
            YearMonth yearMonth = YearMonth.parse(date);
            int lastDay = yearMonth.lengthOfMonth(); // Get the last day of the month
            String formattedEndDate = date + "-" + lastDay;
            // Convert formattedStartDate to Timestamp object
            Timestamp timestampDate = Timestamp.valueOf(formattedEndDate + " 23:59:59");
            String sql = "SELECT r.recname, e.timestamp, e.kwhtot " +
                    "FROM zsp.recorder r " +
                    "INNER JOIN ( " +
                    "    SELECT " +
                    "        e.egyinstkey, " +
                    "        MAX(e.timestamp) AS timestamp, " +
                    "        MAX(e.kwhtot) AS kwhtot " +
                    "    FROM zsp.energy_etc e " +
                    "    WHERE e.timestamp <= '" + timestampDate + "' " +
                    "    GROUP BY e.egyinstkey " +
                    ") e ON r.recid = e.egyinstkey";
//            return jdbcTemplatePub3.queryForList(sql);
            return oqgHelper.OqgR2(sql,true);
        }catch(Exception e){
            throw new Exception("Error: " + e.getMessage());
        }
    }

    // retrieve all rates (tariff, peak or off peak charges, contracted or uncontracted capacity rate) from database
    public Map<String, Object> getTariffRate() throws Exception {
        try{
            //// retrieve all rates (tariff, peak or off peak charges, contracted or uncontracted capacity rate) from database
            String sql = "select rates from sprate where created_timestamp = (select max(created_timestamp) from sprate)";
            List<Map<String,Object>> response = oqgHelper.OqgR2(sql,true);
            return response.getFirst();
//            return jdbcTemplatePub3.queryForMap(sql);
        }catch(Exception e){
            throw new Exception("Error: " + e.getMessage());
        }
    }

    public Map<String, Object> findMonthlyReading(/*String monthStartDatetimeStr, */
            LocalDateTime commissionedDatetime,
            String monthEndDatetimeStr,
            String meterId,
            String targetReadingTableName,
            String itemIdColName,
            String idKey,
            String timeKey, String valKey) {
        logger.info("process findMonthlyReading");
        LocalDateTime searchingStart = localHelper.getLocalNow();

        //the month which you want to set or get the first and last reading
        LocalDateTime monthEndDatetimeDay = DateTimeUtil.getLocalDateTime(monthEndDatetimeStr);

        //set end datetime e.g 2024-02-29 23:59:59
        LocalDateTime monthEndDatetime =  monthEndDatetimeDay
                .withDayOfMonth(monthEndDatetimeDay.getMonth().maxLength())
                .withHour(23).withMinute(59).withSecond(59);

        //set start datetime e.g 2024-02-01 00:00:00
        LocalDateTime monthStartDatetime = monthEndDatetime.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        // find the 2 mbrs of the month
        Map<String, Object> result = new HashMap<>();
        String firstReadingTimestamp = "";
        String firstReadingVal = "";
        String lastReadingTimestamp = "";
        String lastReadingVal = "";

        // find the first reading of the month
        int theYear = monthEndDatetime.getYear();
        int theMonth = monthEndDatetime.getMonthValue();

        //commission datetime = date and time when a meter or equipment was officially put into service or operation
        //first check commissionedDatetime
        int commissionedYear = 0;
        int commissionedMonth = 0;
        if(commissionedDatetime != null){
            commissionedYear = commissionedDatetime.getYear();
            commissionedMonth = commissionedDatetime.getMonthValue();
        }
        boolean useCommissionedDatetime = false;
        if(commissionedYear > 0){
            //case 1: meter is not yet commissioned -> do nothing
            if(commissionedYear > theYear || (commissionedYear == theYear && commissionedMonth > theMonth)){
                // if commissionedDatetime is in the future, ignore the commissionedDatetime
                //return Collections.singletonMap("info", "commissionedDatetime is in the future");
            }

            //case 2: if the commissionedDatetime is in the same month as the monthEndDatetime -> use the commissionedDatetime as the first reading of the month
            if(commissionedYear==theYear && commissionedMonth==theMonth){
                //use the commissionedDatetime as the first reading of the month
                String firstReadingOfCurrentMonthSqlAsCommissionedMonth =
                        "SELECT " + valKey + ", " + timeKey + ", ref FROM " + targetReadingTableName
                                + " WHERE "
                                + itemIdColName + " = '" + meterId
                                + "' AND " + timeKey + " >= '" + commissionedDatetime
                                + "' AND " + timeKey + " < '" + monthEndDatetime + "' "
//                        + AND " + " ref = 'mbr' "
                                + " ORDER BY " + timeKey + " LIMIT 1";


                List<Map<String, Object>> respCommissionedMonth;
                try {
                    respCommissionedMonth = oqgHelper.OqgR2(firstReadingOfCurrentMonthSqlAsCommissionedMonth, true);
                } catch (Exception e) {
                    logger.info("oqgHelper error: " + e.getMessage());
                    return Collections.singletonMap("error", "oqgHelper error: " + e.getMessage());
                }
                if (respCommissionedMonth == null) {
                    logger.info("oqgHelper error: resp is null");
                    return Collections.singletonMap("error", "oqgHelper error: resp is null");
                }
                if (respCommissionedMonth.isEmpty()) {
                    logger.info("no commission month reading found for meter: " + meterId);
//                    return Collections.singletonMap("info", "no first reading of the month found for meter: " + meterId);
//                    result.put("first_reading_time", "-");
//                    result.put("first_reading_val", "-");
                }
                firstReadingTimestamp = (String) respCommissionedMonth.getFirst().get(timeKey);
                firstReadingVal = (String) respCommissionedMonth.getFirst().get(valKey);
                useCommissionedDatetime = true;
            }
        }

        //if commissionedDatetime is not used, find the first reading of the month
        if(firstReadingTimestamp.isEmpty()) {
            // search left 3 hours and right 3 hours of current month start
            // for the first reading with 'ref' as 'mbr',

            //find MBR between 3 hours before and 3 hours after the start of the month
            String firstReadingOfCurrentMonthSqlAsMbr =
                    "SELECT " + valKey + ", " + timeKey + ", ref FROM " + targetReadingTableName
                            + " WHERE "
                            + itemIdColName + " = '" + meterId
                            + "' AND " + timeKey + " >= '" + monthStartDatetime.minusHours(3)
                            + "' AND " + timeKey + " < '" + monthStartDatetime.plusHours(3)
                            + "' AND " + " ref = 'mbr' "
                            + " ORDER BY " + timeKey + " LIMIT 1";
            List<Map<String, Object>> respStartSearchRange;
            try {
                respStartSearchRange = oqgHelper.OqgR2(firstReadingOfCurrentMonthSqlAsMbr, true);
            } catch (Exception e) {
                logger.info("oqgHelper error: " + e.getMessage());
                return Collections.singletonMap("error", "oqgHelper error: " + e.getMessage());
            }
            if (respStartSearchRange == null) {
                logger.info("oqgHelper error: resp is null");
                return Collections.singletonMap("error", "oqgHelper error: resp is null");
            }
            if (respStartSearchRange.size() > 1) {
                logger.info("error: mbr count " + respStartSearchRange.size());
                return Collections.singletonMap("error", "mbr count " + respStartSearchRange.size() + " for meter: " + meterId);
            }
            // if mbr is found, use it
            if (respStartSearchRange.size() == 1) {
                firstReadingTimestamp = (String) respStartSearchRange.getFirst().get(timeKey);
                firstReadingVal = (String) respStartSearchRange.getFirst().get(valKey);
            }
            // if mbr near the beginning of the month is not found, use the first reading of the month
            if (respStartSearchRange.isEmpty()) {
                LocalDateTime beginOfMonth = monthStartDatetime.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
                System.out.println("beginOfMonth: " + beginOfMonth);

                LocalDateTime endOfMonth = monthStartDatetime.withDayOfMonth(monthStartDatetime.getMonth().maxLength()).withHour(23).withMinute(59).withSecond(59);
                System.out.println("endOfMonth: " + endOfMonth);
                //get egyid, kwhtot and timestamp of the first reading of the month
                String firstReadingOfCurrentMonthSql = "SELECT "+ idKey+", " + valKey + ", " + timeKey + ", ref FROM " + targetReadingTableName
                        + " WHERE " +
                        itemIdColName + " = '" + meterId + "' AND " +
                        timeKey + " >= '" + beginOfMonth + "' AND " +
                        timeKey + " < '" + /*beginOfMonth.plusHours(3)*/ endOfMonth + "' " +
                        " ORDER BY " + timeKey + " LIMIT 1";


                List<Map<String, Object>> respFirstReadingOfCurrentMonth;
                try {
                    respFirstReadingOfCurrentMonth = oqgHelper.OqgR2(firstReadingOfCurrentMonthSql, true);
                } catch (Exception e) {
                    logger.info("oqgHelper error: " + e.getMessage());
                    return Collections.singletonMap("error", "oqgHelper error: " + e.getMessage());
                }
                if (respFirstReadingOfCurrentMonth == null) {
                    logger.info("oqgHelper error: resp is null");
                    return Collections.singletonMap("error", "oqgHelper error: resp is null");
                }
                if (respFirstReadingOfCurrentMonth.isEmpty()) {
                    logger.info("no first reading of the month found for meter: " + meterId);
//                    return Collections.singletonMap("info", "no first reading of the month found for meter: " + meterId);
//                    result.put("first_reading_time", "-");
//                    result.put("first_reading_val", "-");
                } else {
                    // update the first reading of the month to mbr if it is not
                    String firstReadingOfCurrentMonthRef = (String) respFirstReadingOfCurrentMonth.getFirst().get("ref");
                    if (firstReadingOfCurrentMonthRef == null || !firstReadingOfCurrentMonthRef.equalsIgnoreCase("mbr")) {
                        String firstReadingOfCurrentMonthId = (String) respFirstReadingOfCurrentMonth.getFirst().get("egyid");
                        String updateFirstReadingOfCurrentMonthSql =
                                "UPDATE " + targetReadingTableName + " SET ref = 'mbr' WHERE " +
                                        idKey + "= '" + firstReadingOfCurrentMonthId + "'";
                        try {
                            oqgHelper.OqgIU(updateFirstReadingOfCurrentMonthSql);
                            logger.info("updateFirstReadingOfCurrentMonthSql: " + updateFirstReadingOfCurrentMonthSql
                                    +", timestamp:" +respFirstReadingOfCurrentMonth.getFirst().get("timestamp"));
                        } catch (Exception e) {
                            logger.info("oqgHelper error: " + e.getMessage());
                            return Collections.singletonMap("error", "oqgHelper error: " + e.getMessage());
                        }
                    }
                    firstReadingTimestamp = (String) respFirstReadingOfCurrentMonth.getFirst().get(timeKey);
                    firstReadingVal = (String) respFirstReadingOfCurrentMonth.getFirst().get(valKey);
                }
            }
        }

        // find the last reading of the month

        // search left 3 hours and right 3 hours of current month end
        String lastReadingOfCurrentMonthSqlAsMbr = "SELECT " + valKey + ", " + timeKey + ", ref FROM " + targetReadingTableName
                + " WHERE " +
                itemIdColName + " = '" + meterId + "' AND " +
                timeKey + " >= '" + monthEndDatetime.minusHours(3) + "' AND " +
                timeKey + " < '" + monthEndDatetime.plusHours(3) + "' AND " +
                " ref = 'mbr' " +
                " ORDER BY " + timeKey + " LIMIT 1";
        List<Map<String, Object>> respEndSearchRange;
        try {
            respEndSearchRange = oqgHelper.OqgR2(lastReadingOfCurrentMonthSqlAsMbr, true);
        } catch (Exception e) {
            logger.info("oqgHelper error: " + e.getMessage());
            return Collections.singletonMap("error", "oqgHelper error: " + e.getMessage());
        }
        if (respEndSearchRange == null) {
            logger.info("oqgHelper error: resp is null");
            return Collections.singletonMap("error", "oqgHelper error: resp is null");
        }
        if (respEndSearchRange.size() > 1) {
            logger.info("error: mbr count " + respEndSearchRange.size());
            return Collections.singletonMap("error", "mbr count " + respEndSearchRange.size());
        }
        // if mbr is found, use it
        if (respEndSearchRange.size() == 1) {
            lastReadingTimestamp = (String) respEndSearchRange.getFirst().get(timeKey);
            lastReadingVal = (String) respEndSearchRange.getFirst().get(valKey);
        }
        // if mbr is not found, use the first reading of the following month
        if(respEndSearchRange.isEmpty()) {
            LocalDateTime beginOfFollowingMonth = monthEndDatetime.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            String firstReadingOfFollowingMonthSql = "SELECT egyid, " + valKey + ", " + timeKey + ", ref FROM "
                    + targetReadingTableName + " WHERE " +
                    itemIdColName + " = '" + meterId + "' AND " +
                    timeKey + " >= '" + beginOfFollowingMonth + "' AND " +
                    timeKey + " < '" + beginOfFollowingMonth.plusHours(3) + "' " +
                    " ORDER BY " + timeKey + " LIMIT 1";
            List<Map<String, Object>> respFirstReadingOfFollowingMonth;
            try {
                respFirstReadingOfFollowingMonth = oqgHelper.OqgR2(firstReadingOfFollowingMonthSql, true);
            } catch (Exception e) {
                logger.info("oqgHelper error: " + e.getMessage());
                return Collections.singletonMap("error", "oqgHelper error: " + e.getMessage());
            }
            if (respFirstReadingOfFollowingMonth == null) {
                logger.info("oqgHelper error: resp is null");
                return Collections.singletonMap("error", "oqgHelper error: resp is null");
            }
            if (respFirstReadingOfFollowingMonth.isEmpty()) {
                logger.info("no first reading of the following month found");
//                return Collections.singletonMap("info", "no first reading of the following month found");
//                lastReadingTimestamp = "-";
//                lastReadingVal = "-";
            }else{
                String respFirstReadingOfFollowingMonthRef = (String) respFirstReadingOfFollowingMonth.getFirst().get("ref");
                if(respFirstReadingOfFollowingMonthRef == null || !respFirstReadingOfFollowingMonthRef.equalsIgnoreCase("mbr")) {
                    // update the first reading of the following month to mbr
                    String firstReadingOfFollowingMonthId = (String) respFirstReadingOfFollowingMonth.getFirst().get("egyid");
                    String updateFirstReadingOfFollowingMonthSql =
                            "UPDATE " + targetReadingTableName + " SET ref = 'mbr' WHERE " +
                                    "egyid = '" + firstReadingOfFollowingMonthId + "'";
                    try {
                        oqgHelper.OqgIU(updateFirstReadingOfFollowingMonthSql);
                        logger.info("updateFirstReadingOfFollowingMonthSql: " + updateFirstReadingOfFollowingMonthSql);
                    } catch (Exception e) {
                        logger.info("oqgHelper error: " + e.getMessage());
                        return Collections.singletonMap("error", "oqgHelper error: " + e.getMessage());
                    }
                }
                lastReadingTimestamp = (String) respFirstReadingOfFollowingMonth.getFirst().get(timeKey);
                lastReadingVal = (String) respFirstReadingOfFollowingMonth.getFirst().get(valKey);
            }
        }

        result.put("first_reading_time", firstReadingTimestamp);
        result.put("first_reading_val", firstReadingVal);
        result.put("last_reading_time", lastReadingTimestamp);
        result.put("last_reading_val", lastReadingVal);
        result.put("use_commissioned_datetime", useCommissionedDatetime);

        LocalDateTime searchingEnd = localHelper.getLocalNow();
        logger.info("findMonthlyReading duration: " + Duration.between(searchingStart, searchingEnd).toSeconds() + " seconds");

        return result;
    }

//    public Map<String,Object> getKwhTotByStartOfMonth(Integer recorderID,String date) throws Exception{
//        try{
//            // Assuming endDate is in "yyyy-MM" format
//            YearMonth yearMonth = YearMonth.parse(date);
//            int lastDay = yearMonth.lengthOfMonth(); // Get the last day of the month
//            String formattedEndDate = date + "-" + lastDay;
//            String sql = "SELECT MIN(kwhtot) as kwhtot, MIN(timestamp) as timestamp " +
//                    "FROM energy_etc " +
//                    "WHERE egyinstkey = ? " +
//                    "AND timestamp >= ?";
//            // Convert formattedStartDate to Timestamp object
//            Timestamp timestampDate = Timestamp.valueOf(formattedEndDate + " 00:00:00");
//            return jdbcTemplatePub3.queryForMap(sql, recorderID, timestampDate);
//        }catch(Exception e){
//            logger.info("Error: " + e.getMessage());
//            throw new Exception("Error: " + e.getMessage());
//        }
//    }
//
//    public Map<String,Object> getKwhTotByEndOfMonth(Integer recorderID,String date) throws Exception{
//        try{
//            // Assuming endDate is in "yyyy-MM" format
//            YearMonth yearMonth = YearMonth.parse(date);
//            int lastDay = yearMonth.lengthOfMonth(); // Get the last day of the month
//            String formattedEndDate = date + "-" + lastDay;
//            // Convert formattedStartDate to Timestamp object
//            Timestamp timestampDate = Timestamp.valueOf(formattedEndDate + " 23:59:59");
//            String sql = "SELECT MAX(kwhtot) as kwhtot, MAX(timestamp) as timestamp " +
//                    "FROM energy_etc " +
//                    "WHERE egyinstkey = ? " +
//                    "AND timestamp <= ?";
//
//
//            return jdbcTemplatePub3.queryForMap(sql, recorderID, timestampDate);
//        }catch(Exception e){
//            logger.info("Error: " + e.getMessage());
//            throw new Exception("Error: " + e.getMessage());
//        }
//    }


}

