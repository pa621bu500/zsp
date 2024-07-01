package com.pabu5h.zsp.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    public static int writeNextCell(XSSFCell cell, XSSFSheet sheet, int k, int j, Object val, XSSFWorkbook workbook, boolean isCurrency) {
        // Ensure the row exists
        if (sheet.getRow(j) == null) {
            sheet.createRow(j);
        }

        // Ensure the cell exists
        if (sheet.getRow(j).getCell(k) == null) {
            sheet.getRow(j).createCell(k);
        }

        cell = sheet.getRow(j).getCell(k);

        // Preserve the original cell style if it exists
        CellStyle originalStyle = cell.getCellStyle();

        // Set the cell value based on the type of 'val'
        if (val instanceof String) {
            cell.setCellValue((String) val);
        } else if (val instanceof Number) {
            cell.setCellValue(((Number) val).doubleValue());

            CellStyle newStyle = workbook.createCellStyle();
            newStyle.cloneStyleFrom(originalStyle); // Preserve the original style

            DataFormat format = workbook.createDataFormat();
            if (isCurrency) {
                newStyle.setDataFormat(format.getFormat("$#,##0.00"));
            } else {
                newStyle.setDataFormat(format.getFormat("#,##0.00"));
            }
            cell.setCellStyle(newStyle);
        } else {
            // Handle other data types if needed
        }

        return k + 1;
    }

    public static String[] getMonthHeaders(String currentMonthAbbreviation) {
        String[] months = new String[3];
        String currentMonth = currentMonthAbbreviation;

        switch (currentMonth) {
            case "Jan":
                months[0] = "October Usage";
                months[1] = "November Usage";
                months[2] = "December Usage";
                break;
            case "Feb":
                months[0] = "November Usage";
                months[1] = "December Usage";
                months[2] = "January Usage";
                break;
            case "Mar":
                months[0] = "December Usage";
                months[1] = "January Usage";
                months[2] = "February Usage";
                break;
            case "Apr":
                months[0] = "January Usage";
                months[1] = "February Usage";
                months[2] = "March Usage";
                break;
            case "May":
                months[0] = "February Usage";
                months[1] = "March Usage";
                months[2] = "April Usage";
                break;
            case "Jun":
                months[0] = "March Usage";
                months[1] = "April Usage";
                months[2] = "May Usage";
                break;
            case "Jul":
                months[0] = "April Usage";
                months[1] = "May Usage";
                months[2] = "June Usage";
                break;
            case "Aug":
                months[0] = "May Usage";
                months[1] = "June Usage";
                months[2] = "July Usage";
                break;
            case "Sep":
                months[0] = "June Usage";
                months[1] = "July Usage";
                months[2] = "August Usage";
                break;
            case "Oct":
                months[0] = "July Usage";
                months[1] = "August Usage";
                months[2] = "September Usage";
                break;
            case "Nov":
                months[0] = "August Usage";
                months[1] = "September Usage";
                months[2] = "October Usage";
                break;
            case "Dec":
                months[0] = "September Usage";
                months[1] = "October Usage";
                months[2] = "November Usage";
                break;
            default:
                break;
        }
        return months;
    }


    public static List<Map<String, Object>> readXlsFile(String filePath, int startRow, int startColumn) throws IOException {
        List<Map<String, Object>> dataList = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             HSSFWorkbook workbook = new HSSFWorkbook(fis)) {

            HSSFSheet sheet = workbook.getSheetAt(0);

            //get header row ( Adjust for 0-based index)
            HSSFRow headerRow = sheet.getRow(startRow - 1);

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



    private static Object getCellValue(org.apache.poi.ss.usermodel.Cell cell) {
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

    private static String getCellValueAsString(org.apache.poi.ss.usermodel.Cell cell) {
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
