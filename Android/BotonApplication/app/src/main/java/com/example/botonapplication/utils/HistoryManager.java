package com.example.botonapplication.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.icu.text.SimpleDateFormat;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Locale;

public class HistoryManager {
    private static final String PREFS_NAME = "AlarmHistoryPrefs";
    private static final String HISTORY_KEY = "alarm_history";

    private static final int MAX_HISTORY_ENTRIES = 20; // Límite de eventos


    public HistoryManager(Context context) {
        //this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    public void addEntry(Context context, String status, double latitude, double longitude, boolean hasValidLocation) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String historyJson = prefs.getString(HISTORY_KEY, "[]");

        try {
            JSONArray historyArray = new JSONArray(historyJson);

            while (historyArray.length() >= MAX_HISTORY_ENTRIES) {
                historyArray.remove(historyArray.length() - 1); // Elimina el más antiguo (último)
            }

            JSONObject newEntry = new JSONObject();
            newEntry.put("status", status);
            newEntry.put("timestamp", getCurrentTimestamp());
            newEntry.put("lat" , latitude);
            newEntry.put("lon" , longitude);
            newEntry.put("location_available", hasValidLocation);

            // Crea un NUEVO array combinando el nuevo + existente
            JSONArray newArray = new JSONArray();
            newArray.put(newEntry); // Primero el nuevo
            for (int i = 0; i < historyArray.length(); i++) {
                newArray.put(historyArray.getJSONObject(i)); // Luego los existentes
            }

            // Guarda el NUEVO array completo
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(HISTORY_KEY, newArray.toString());
            editor.apply();

            Log.d("HistoryDebug", "JSON guardado: " + newArray.toString());
        } catch (JSONException e) {
            Log.e("HistoryManager", "Error al guardar entrada", e);
        }
    }
    private String getCurrentTimestamp() {
        return new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(new Date());
    }
}