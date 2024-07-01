package com.pabu5h.zsp.util;

import com.pabu5h.zsp.model.Meter;

import java.util.HashMap;
import java.util.Map;

public class MeterUtil {
    public static String findKeyContainingSubstring(Map<String, Object> map, String substring) {
        for (String key : map.keySet()) {
            if (key.contains(substring)) {
                return key;
            }
        }
        return null;
    }
}
