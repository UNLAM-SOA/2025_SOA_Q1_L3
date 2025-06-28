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

    private static final String TAG = "HistoryManager";

    // Constantes para SharedPreferences
    private static final String PREFS_NAME = "AlarmHistoryPrefs";
    private static final String HISTORY_KEY = "alarm_history";

    // Límite máximo de entradas en el historial
    private static final int MAX_HISTORY_ENTRIES = 20;

    // Formato de fecha para timestamps
    private static final String TIMESTAMP_FORMAT = "HH:mm dd/MM/yyyy";

    public HistoryManager(Context context) {

    }

    public void addEntry(Context context, String status, double latitude, double longitude, boolean hasValidLocation) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String historyJson = prefs.getString(HISTORY_KEY, "[]");

        try {
            JSONArray historyArray = new JSONArray(historyJson);

            // Mantener el tamaño del historial limitado, eliminando entradas más antiguas
            while (historyArray.length() >= MAX_HISTORY_ENTRIES) {
                historyArray.remove(historyArray.length() - 1); // Elimina la última (más antigua)
            }

            // Crear nueva entrada con los datos recibidos y timestamp actual
            JSONObject newEntry = new JSONObject();
            newEntry.put("status", status);
            newEntry.put("timestamp", getCurrentTimestamp());
            newEntry.put("lat", latitude);
            newEntry.put("lon", longitude);
            newEntry.put("location_available", hasValidLocation);

            // Crear nuevo JSONArray con la entrada nueva primero, seguido del historial previo
            JSONArray newArray = new JSONArray();
            newArray.put(newEntry);
            for (int i = 0; i < historyArray.length(); i++) {
                newArray.put(historyArray.getJSONObject(i));
            }

            // Guardar historial actualizado en SharedPreferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(HISTORY_KEY, newArray.toString());
            editor.apply();

            Log.d(TAG, "JSON guardado: " + newArray.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error al guardar entrada", e);
        }
    }

    private String getCurrentTimestamp() {
        return new SimpleDateFormat(TIMESTAMP_FORMAT, Locale.getDefault()).format(new Date());
    }

    /**
     * Obtiene la última entrada (más reciente) del historial.
     * @param context Context para acceder a SharedPreferences
     * @return JSONObject con la última entrada o null si no existe o error.
     */
    public JSONObject getLastEntry(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String historyJson = prefs.getString(HISTORY_KEY, "[]");

        try {
            JSONArray historyArray = new JSONArray(historyJson);
            if (historyArray.length() > 0) {
                return historyArray.getJSONObject(0); // Retorna la más reciente
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error obteniendo última entrada", e);
        }
        return null;
    }
}
