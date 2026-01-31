package com.shiftbot.repository.sheets;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;

import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SheetsClient {
    private static final Logger log = LoggerFactory.getLogger(SheetsClient.class);
    private static final Pattern RANGE_PATTERN = Pattern.compile("(?:(?<sheet>[^!]+)!)?(?<startCol>[A-Z]+)(?<startRow>\\d+):(?<endCol>[A-Z]+)(?<endRow>\\d*)");
    private final Sheets sheets;
    private final String spreadsheetId;

    public SheetsClient(String spreadsheetId, String credentialsPath) {
        try {
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
            GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsPath))
                    .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));
            this.sheets = new Sheets.Builder(httpTransport, jsonFactory, new HttpCredentialsAdapter(credentials))
                    .setApplicationName("shift-scheduler-bot")
                    .setHttpRequestInitializer(new HttpCredentialsAdapter(credentials))
                    .build();
            this.spreadsheetId = spreadsheetId;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Failed to init Google Sheets client", e);
        }
    }

    public List<List<Object>> readRange(String range) {
        try {
            ValueRange response = sheets.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute();
            return response.getValues();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read range: " + range, e);
        }
    }

    public AppendValuesResponse appendRow(String range, List<Object> row) {
        String appendRange = normalizeAppendRange(range);
        try {
            ValueRange body = new ValueRange().setValues(Collections.singletonList(row));
            return sheets.spreadsheets().values()
                    .append(spreadsheetId, appendRange, body)
                    .setValueInputOption("USER_ENTERED")
                    .execute();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to append row: " + range, e);
        }
    }

    public void updateRange(String range, List<List<Object>> values) {
        try {
            ValueRange body = new ValueRange().setValues(values);
            sheets.spreadsheets().values()
                    .update(spreadsheetId, range, body)
                    .setValueInputOption("USER_ENTERED")
                    .execute();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to update range: " + range, e);
        }
    }

    public static String columnIndexToLetter(int columnIndex) {
        if (columnIndex <= 0) {
            throw new IllegalArgumentException("Column index must be positive: " + columnIndex);
        }
        StringBuilder result = new StringBuilder();
        int current = columnIndex;
        while (current > 0) {
            int remainder = (current - 1) % 26;
            result.insert(0, (char) ('A' + remainder));
            current = (current - 1) / 26;
        }
        return result.toString();
    }

    public void updateRow(String range, int rowIndex, List<Object> row) {
        batchUpdateRows(range, Collections.singletonList(rowIndex), Collections.singletonList(row));
    }

    public void batchUpdateRows(String range, List<Integer> rowIndexes, List<List<Object>> rows) {
        if (rowIndexes.size() != rows.size()) {
            throw new IllegalArgumentException("Row indexes and rows size mismatch");
        }
        try {
            List<ValueRange> data = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                data.add(new ValueRange()
                        .setRange(buildRowRange(range, rowIndexes.get(i)))
                        .setValues(Collections.singletonList(rows.get(i))));
            }
            BatchUpdateValuesRequest body = new BatchUpdateValuesRequest()
                    .setData(data)
                    .setValueInputOption("USER_ENTERED");
            sheets.spreadsheets().values()
                    .batchUpdate(spreadsheetId, body)
                    .execute();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to batch update rows: " + range, e);
        }
    }

    private String buildRowRange(String range, int rowIndex) {
        Matcher matcher = RANGE_PATTERN.matcher(range);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported range format: " + range);
        }
        String sheet = matcher.group("sheet");
        String startCol = matcher.group("startCol");
        String endCol = matcher.group("endCol");
        int startRow = Integer.parseInt(matcher.group("startRow"));
        int targetRow = startRow + rowIndex;
        String sheetPrefix = sheet == null ? "" : sheet + "!";
        return sheetPrefix + startCol + targetRow + ":" + endCol + targetRow;
    }

    private String normalizeAppendRange(String range) {
        Matcher matcher = RANGE_PATTERN.matcher(range);
        if (!matcher.matches()) {
            return range;
        }
        String endRow = matcher.group("endRow");
        if (endRow != null && !endRow.isEmpty()) {
            return range;
        }
        String sheet = matcher.group("sheet");
        String startCol = matcher.group("startCol");
        String endCol = matcher.group("endCol");
        String sheetPrefix = sheet == null ? "" : sheet + "!";
        return sheetPrefix + startCol + ":" + endCol;
    }
}
