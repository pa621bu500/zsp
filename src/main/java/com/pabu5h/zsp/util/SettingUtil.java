package com.pabu5h.zsp.util;

import java.util.HashMap;
import java.util.Map;

public class SettingUtil {
    public static Map<String,String> getRequest(String from_timestamp, String to_timestamp, String end_timestamp){
        Map<String, String> request = new HashMap<>();
        request.put("project_scope", "ems_zsp");
        request.put("is_monthly", "true");
        request.put("from_timestamp", from_timestamp);
        request.put("to_timestamp", to_timestamp);
        request.put("end_timestamp", end_timestamp);
        return request;
    }
}
