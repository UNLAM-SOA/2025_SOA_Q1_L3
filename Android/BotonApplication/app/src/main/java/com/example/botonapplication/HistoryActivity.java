package com.example.botonapplication;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class HistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        ListView listView = findViewById(R.id.historyListView);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        SharedPreferences prefs = getSharedPreferences("AlarmHistoryPrefs", MODE_PRIVATE);
        String historyJson = prefs.getString("alarm_history", "[]");

        Log.d("HistoryDebug", "JSON leído: " + historyJson); // Verifica qué se está leyendo

        try {
            JSONArray historyArray = new JSONArray(historyJson);
            for (int i = 0; i < historyArray.length(); i++) {
                JSONObject entry = historyArray.getJSONObject(i);
                String item = entry.getString("timestamp") + " - " + entry.getString("status");
                adapter.add(item);
            }
        } catch (JSONException e) {
            Log.e("HistoryActivity", "Error al parsear JSON", e);
            adapter.add("Error cargando historial");
        }

        listView.setAdapter(adapter);
    }
}