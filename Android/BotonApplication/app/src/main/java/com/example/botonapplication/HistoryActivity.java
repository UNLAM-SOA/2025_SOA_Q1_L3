package com.example.botonapplication;

import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

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
                String item = String.format(Locale.US,
                        "%s - %s\n%s\nLat: %.6f, Lon: %.6f",
                        entry.getString("timestamp"),
                        entry.getString("status"),
                        entry.optBoolean("location_available", false) ?
                                getCityFromCoordinates(entry.getDouble("lat"), entry.getDouble("lon")) :
                                "Ubicación no disponible",
                        entry.getDouble("lat"),
                        entry.getDouble("lon")
                );
                adapter.add(item);
            }
        } catch (JSONException e) {
            Log.e("HistoryActivity", "Error al parsear JSON", e);
            adapter.add("Error cargando historial");
        }

        listView.setAdapter(adapter);
    }

    private String getCityFromCoordinates(double lat, double lon) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (!addresses.isEmpty()) {
                Address address = addresses.get(0);
               return address.getLocality() + ", " + address.getSubAdminArea();
            }
        } catch (IOException e) {
            Log.e("Geocoder", "Error obteniendo ciudad", e);
        }
        return "Ubicación desconocida";
    }
}