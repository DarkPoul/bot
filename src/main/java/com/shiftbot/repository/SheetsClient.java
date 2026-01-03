package com.shiftbot.repository;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

public class SheetsClient {
    private static final Logger log = LoggerFactory.getLogger(SheetsClient.class);
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
        try {
            ValueRange body = new ValueRange().setValues(Collections.singletonList(row));
            return sheets.spreadsheets().values()
                    .append(spreadsheetId, range, body)
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
}
