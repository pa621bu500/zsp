package com.pabu5h.zsp.repository;

import com.pabu5h.zsp.util.SettingUtil;
import org.pabuff.evs2helper.meter_usage.MeterUsageProcessor;
import org.pabuff.oqghelper.OqgHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.util.logging.Logger;

@Repository
public class MeterRepository {
    Logger logger = Logger.getLogger(MeterRepository.class.getName());

    @Autowired
    private OqgHelper oqgHelper;

    @Autowired
    private MeterUsageProcessor meterUsageProcessor;

    // retrieve all required information from the database (premise, recorder and tenant table)
    public List<Map<String,Object>> getMeterData() throws Exception {
        try {
//            String sql = "select r.recid, p.tenant_id, t.bill_type, t.tenantgroup_id, r.recname, t.deposit, " +
//                    "t.tenant_name, t.address1, t.address2, t.unit, t.postal, " +
//                    "t.unit_name, p.street, p.floor, p.unit as rightunit, p.postal as rightpostal, " +
//                    "r.powerfactor, t.giro " +
//                    "from premise p, recorder r, tenant t " +
//                    "where p.recid = r.recid and p.tenant_id = t.tenant_id " +
//                    "order by p.tenant_id";
                String sql = "select r.recorder_id, t.tenant_id, t.type, t.lc_status, r.rec_name, t.deposit, " +
                    "t.address_tenant_name, t.address_line1, t.address_line2, t.unit, t.postal, " +
                    "t.alt_tenant_name, t.alt_address_line1,t.alt_address_line2, t.alt_postal, " +
                    "r.ct_multiplier, t.giro " +
                    "from premise p, meter_zsp r, tenant_zsp t " +
                    "where p.recid = r.recorder_id and p.tenant_id = t.tenant_id " +
                    "order by t.tenant_id";
            return oqgHelper.OqgR2(sql,true);
        } catch (Exception e) {
            logger.severe("Error: " + e.getMessage());
            throw new Exception("Error: " + e.getMessage());
        }
    }

    // retrieve all rates (tariff, peak or off peak charges, contracted or uncontracted capacity rate) from database
    public Map<String, Object> getTariffRate() throws Exception {
        try{
            String sql = "select tariff_rate,gst_rate from zsp.rates_zsp where created_timestamp = (select max(created_timestamp) from zsp.rates_zsp)";
            List<Map<String,Object>> response = oqgHelper.OqgR2(sql,true);
            return response.getFirst();
        }catch(Exception e){
            logger.severe("Error: " + e.getMessage());
            throw new Exception("Error: " + e.getMessage());
        }
    }

    //retrieve meter start reading,end reading,monthly usage
    public Map<String,Object> getMeterUsage(String startDateTime,String endDateTime,List<Map<String,Object>> meterList) throws Exception {
        try{
            return meterUsageProcessor.getMeterListUsageSummary(SettingUtil.getRequest(startDateTime,endDateTime,endDateTime),meterList);
        }catch (Exception e){
            logger.severe("Error: " + e.getMessage());
            throw new Exception("Error: " + e.getMessage());
        }
    }
}

