package com.pabu5h.zsp.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.IOException;
import java.util.Map;


public class ExcelUtil {
    private Map<String, Map<String, Integer>> config;

    public ExcelUtil(String configFilePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        this.config = mapper.readValue(new File(configFilePath), Map.class);
    }

    public void setCellValue(Sheet sheet, String key, Object value) {
        Map<String, Integer> cellConfig = config.get(key);
        if (cellConfig != null) {
            Row row = sheet.getRow(cellConfig.get("row"));
            if (row == null) {
                row = sheet.createRow(cellConfig.get("row"));
            }
            Cell cell = row.getCell(cellConfig.get("cell"));
            if (cell == null) {
                cell = row.createCell(cellConfig.get("cell"));
            }
            switch (value) {
                case null -> cell.setBlank();  // Clears the cell content
                case Double v -> cell.setCellValue(v);
                case String s -> cell.setCellValue(s);
                default -> {
                }
            }

        }
    }

}
