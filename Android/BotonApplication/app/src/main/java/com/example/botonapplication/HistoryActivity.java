package com.example.botonapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Locale;


public class HistoryActivity extends AppCompatActivity {
    private ListView listView;
    private Button btnSendEmail;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        initializeViews();
        loadHistory();

        btnSendEmail.setOnClickListener(v -> sendHistoryByEmail());
    }

    private void initializeViews() {
        listView = findViewById(R.id.historyListView);
        btnSendEmail = findViewById(R.id.btnSendEmail);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        listView.setAdapter(adapter);
    }

    private void loadHistory() {
        adapter.clear();
        SharedPreferences prefs = getSharedPreferences("AlarmHistoryPrefs", MODE_PRIVATE);
        String historyJson = prefs.getString("alarm_history", "[]");

        try {
            JSONArray historyArray = new JSONArray(historyJson);
            for (int i = 0; i < historyArray.length(); i++) {
                adapter.add(createHistoryEntry(historyArray.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.e("HistoryActivity", "Error al parsear JSON", e);
            adapter.add("Error cargando historial");
        }
    }
    private String createHistoryEntry(JSONObject entry) throws JSONException {
        return String.format(Locale.US,
                "%s - %s\n%s\nLat: %.6f, Lon: %.6f",
                entry.getString("timestamp"),
                entry.getString("status"),
                entry.optBoolean("location_available", false) ?
                        getCityFromCoordinates(entry.getDouble("lat"), entry.getDouble("lon")) :
                        "Ubicación no disponible",
                entry.getDouble("lat"),
                entry.getDouble("lon")
        );
    }


    private void sendHistoryByEmail() {
        // 1. Obtener datos FRESCOS y forzar recarga
        SharedPreferences prefs = getSharedPreferences("AlarmHistoryPrefs", MODE_PRIVATE);
        String currentHistoryJson = prefs.getString("alarm_history", "[]");
        Log.d("EMAIL_DEBUG", "Datos actuales: " + currentHistoryJson);

        // 2. Usar Intent.ACTION_SENDTO con URI forzada
        String email = "tomasbeta@outlook.com";
        String subject = "Historial de Alertas - " + System.currentTimeMillis();
        String body = formatHistoryForEmail(currentHistoryJson);

        String uri = "mailto:" + email +
                "?subject=" + Uri.encode(subject) +
                "&body=" + Uri.encode(body);

        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse(uri));
        emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // 3. Resetear el cliente ANTES de enviar (KEY)
        resetEmailClient();

        // 4. Delay estratégico y lanzamiento
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                startActivity(Intent.createChooser(emailIntent, "Enviar usando:"));
                Log.d("EMAIL_DEBUG", "Email lanzado: " + uri);
            } catch (Exception e) {
                Toast.makeText(this, "Error al abrir cliente", Toast.LENGTH_SHORT).show();
            }
        }, 500); // Delay de 300ms para asegurar el reset
        Toast.makeText(this, "Preparando cliente...", Toast.LENGTH_SHORT).show();
    }

    private void resetEmailClient() {
        try {
            Intent reset = new Intent(Intent.ACTION_MAIN);
            reset.addCategory(Intent.CATEGORY_APP_EMAIL);
            reset.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(reset);
        } catch (Exception e) {
            Log.e("EMAIL_RESET", "Error al resetear cliente", e);
        }
    }

    private String formatHistoryForEmail(String historyJson) {
        StringBuilder sb = new StringBuilder("Historial actualizado:\n\n");
        try {
            JSONArray historyArray = new JSONArray(historyJson);
            for (int i = 0; i < historyArray.length(); i++) {
                JSONObject entry = historyArray.getJSONObject(i);
                sb.append(formatSingleEntry(entry)).append("\n\n");
            }
        } catch (JSONException e) {
            Log.e("HistoryActivity", "Error al formatear", e);
            return "Error generando historial";
        }
        return sb.toString();
    }

    private String formatSingleEntry(JSONObject entry) throws JSONException {
        return String.format(Locale.US,
                "⏰ %s\n🚨 Estado: %s\n📍 %s",
                entry.getString("timestamp"),
                entry.getString("status"),
                getLocationLink(entry.getDouble("lat"), entry.getDouble("lon"))
        );
    }

    private String getLocationLink(double lat, double lon) {
        return (lat == 0.0 && lon == 0.0) ?
                "Ubicación no disponible" :
                String.format(Locale.US, "Mapa: https://maps.google.com/?q=%.6f,%.6f", lat, lon);
    }


    private String getCityFromCoordinates(double lat, double lon) {
        try {
            List<Address> addresses = new Geocoder(this, Locale.getDefault())
                    .getFromLocation(lat, lon, 1);

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