package com.pabu5h.zsp.util;

import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExcelReader {

    public List<Map<String, Object>> readXlsFile(String filePath, int startRow, int startColumn) throws IOException {
        List<Map<String, Object>> dataList = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             HSSFWorkbook workbook = new HSSFWorkbook(fis)) {

            HSSFSheet sheet = workbook.getSheetAt(0);
            HSSFRow headerRow = sheet.getRow(startRow - 1); // Adjust for 0-based index

            if (headerRow == null) {
                throw new IllegalArgumentException("Header row is null. Please check the startRow parameter.");
            }

            List<String> headers = new ArrayList<>();
            for (int j = startColumn; j < headerRow.getLastCellNum(); j++) {
                headers.add(getCellValueAsString(headerRow.getCell(j)));
            }

            for (int i = startRow; i <= sheet.getLastRowNum()-2; i++) {
                HSSFRow row = sheet.getRow(i);

                if (row == null) {
                    continue; // Skip null rows
                }

                Map<String, Object> dataMap = new HashMap<>();

                for (int j = startColumn; j <= 13; j++) {
                    String header = headers.get(j - startColumn);

                    Object value = getCellValue(row.getCell(j));
                    dataMap.put(header, value);
                }
                dataList.add(dataMap);
            }
        }

        return dataList;
    }



    private Object getCellValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue();
                } else {
                    return cell.getNumericCellValue();
                }
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case FORMULA:
                switch (cell.getCachedFormulaResultType()) {
                    case STRING:
                        return cell.getRichStringCellValue().toString();
                    case NUMERIC:
                        return cell.getNumericCellValue();
                    case BOOLEAN:
                        return cell.getBooleanCellValue();
                    default:
                        return "";
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    private String getCellValueAsString(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                switch (cell.getCachedFormulaResultType()) {
                    case STRING:
                        return cell.getRichStringCellValue().toString();
                    case NUMERIC:
                        return String.valueOf(cell.getNumericCellValue());
                    case BOOLEAN:
                        return String.valueOf(cell.getBooleanCellValue());
                    default:
                        return "";
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }
}



