package com.example.botonapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.text.SimpleDateFormat;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.botonapplication.utils.MailSender;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Locale;


/*public class HistoryActivity extends AppCompatActivity {
    private ListView listView;
    private Button btnSendEmail;

    private Button btnSendSelected;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        initializeViews();
        loadHistory();


        btnSendEmail.setOnClickListener(v -> sendHistoryByEmail());
        //btnSendSelected.setOnClickListener(v -> sendSelectedLogs());
    }

    private void initializeViews() {
        listView = findViewById(R.id.historyListView);
        btnSendEmail = findViewById(R.id.btnSendEmail);
        btnSendSelected = findViewById(R.id.btnSendSelected);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice);
        listView.setAdapter(adapter);
    }

    private void loadHistory() {
        adapter.clear();
        SharedPreferences prefs = getSharedPreferences("AlarmHistoryPrefs", MODE_PRIVATE);
        String historyJson = prefs.getString("alarm_history", "[]");

        try {
            JSONArray historyArray = new JSONArray(historyJson);
            for (int i = 0; i < historyArray.length(); i++) {

                JSONObject entry = historyArray.getJSONObject(i);
                adapter.add(createHistoryEntry(entry));
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


    private void sendSelectedLogs() {
        SparseBooleanArray selectedPositions = listView.getCheckedItemPositions();
        if (selectedPositions.size() == 0) {
            Toast.makeText(this, "Selecciona al menos un log", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("AlarmHistoryPrefs", MODE_PRIVATE);
        String historyJson = prefs.getString("alarm_history", "[]");
        StringBuilder selectedLogs = new StringBuilder("Logs seleccionados:\n\n");

        try {
            JSONArray historyArray = new JSONArray(historyJson);
            for (int i = 0; i < historyArray.length(); i++) {
                if (selectedPositions.get(i)) {
                    JSONObject entry = historyArray.getJSONObject(i);
                    selectedLogs.append(formatSingleEntry(entry)).append("\n\n");
                }
            }

            // Usar el mismo método de envío robusto
            sendCustomEmailWithReset(selectedLogs.toString(), "Logs seleccionados");

        } catch (JSONException e) {
            Toast.makeText(this, "Error al procesar selección", Toast.LENGTH_SHORT).show();
            Log.e("SELECTED_LOGS", "Error: ", e);
        }
    }

    private void sendCustomEmailWithReset(String body, String subjectPrefix) {
        String email = "tomasbeta@outlook.com";
        String subject = subjectPrefix + " - " + System.currentTimeMillis();
        String uri = "mailto:" + email +
                "?subject=" + Uri.encode(subject) +
                "&body=" + Uri.encode(body);

        resetEmailClient(); // Resetear antes de enviar

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse(uri));
                emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(Intent.createChooser(emailIntent, "Enviar usando:"));
            } catch (Exception e) {
                Toast.makeText(this, "Error al abrir cliente", Toast.LENGTH_SHORT).show();
            }
        }, 500);
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

}*/


/*
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
        // 1. Obtener datos frescos
        SharedPreferences prefs = getSharedPreferences("AlarmHistoryPrefs", MODE_PRIVATE);
        String currentHistoryJson = prefs.getString("alarm_history", "[]");
        String body = formatHistoryForEmail(currentHistoryJson);

        // 2. Configurar detalles del correo
        String toEmail = "tomasbeta@outlook.com";
        String subject = "Historial de Alertas - " +
                new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());

        // 3. Mostrar progreso
        Toast.makeText(this, "Preparando envío de correo...", Toast.LENGTH_SHORT).show();

        // 4. Enviar correo con callback para manejar resultado
        MailSender.sendEmail(toEmail, subject, body, new MailSender.EmailCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(HistoryActivity.this,
                            "Correo enviado correctamente", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(HistoryActivity.this,
                            "Error al enviar correo: " + error, Toast.LENGTH_LONG).show();
                    Log.e("HistoryActivity", "Error enviando email: " + error);
                });
            }
        });
    }

    private String formatHistoryForEmail(String historyJson) {
        StringBuilder sb = new StringBuilder("Historial de Alarmas:\n\n");
        try {
            JSONArray historyArray = new JSONArray(historyJson);
            for (int i = 0; i < historyArray.length(); i++) {
                JSONObject entry = historyArray.getJSONObject(i);
                sb.append(formatSingleEntry(entry)).append("\n\n");
            }

            // Agregar resumen al final
            sb.append("---\n");
            sb.append("Total de registros: ").append(historyArray.length()).append("\n");
            sb.append("Última actualización: ").append(
                    new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date())
            );

        } catch (JSONException e) {
            Log.e("HistoryActivity", "Error al formatear historial", e);
            return "Error generando historial para email";
        }
        return sb.toString();
    }

    private String formatSingleEntry(JSONObject entry) throws JSONException {
        return String.format(Locale.US,
                "⏰ [%s]\n🚨 Estado: %s\n📍 %s\n🌐 %s",
                entry.getString("timestamp"),
                entry.getString("status"),
                entry.optBoolean("location_available", false) ?
                        getCityFromCoordinates(entry.getDouble("lat"), entry.getDouble("lon")) :
                        "Ubicación no disponible",
                getLocationLink(entry.getDouble("lat"), entry.getDouble("lon"))
        );
    }

    private String getLocationLink(double lat, double lon) {
        return (lat == 0.0 && lon == 0.0) ?
                "Sin coordenadas" :
                String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", lat, lon);
    }

    private String getCityFromCoordinates(double lat, double lon) {
        try {
            List<Address> addresses = new Geocoder(this, Locale.getDefault())
                    .getFromLocation(lat, lon, 1);

            if (!addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder location = new StringBuilder();
                if (address.getLocality() != null) location.append(address.getLocality());
                if (address.getSubAdminArea() != null) {
                    if (location.length() > 0) location.append(", ");
                    location.append(address.getSubAdminArea());
                }
                return location.toString();
            }
        } catch (IOException e) {
            Log.e("Geocoder", "Error obteniendo ciudad", e);
        }
        return "Ubicación desconocida";
    }
}*/

public class HistoryActivity extends AppCompatActivity {
    private ListView listView;
    private Button btnSendEmail;
    private Button btnSendSelected;
    private ArrayAdapter<String> adapter;
    private JSONArray historyArray; // Mantenemos referencia al JSONArray original

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        initializeViews();
        loadHistory();

        btnSendEmail.setOnClickListener(v -> sendAllHistoryByEmail());
        btnSendSelected.setOnClickListener(v -> sendSelectedLogs());
    }

    private void initializeViews() {
        listView = findViewById(R.id.historyListView);
        btnSendEmail = findViewById(R.id.btnSendEmail);
        btnSendSelected = findViewById(R.id.btnSendSelected);

        // Configuramos el ListView para selección múltiple
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice);
        listView.setAdapter(adapter);
    }

    private void loadHistory() {
        adapter.clear();
        SharedPreferences prefs = getSharedPreferences("AlarmHistoryPrefs", MODE_PRIVATE);
        String historyJson = prefs.getString("alarm_history", "[]");

        try {
            historyArray = new JSONArray(historyJson);
            for (int i = 0; i < historyArray.length(); i++) {
                adapter.add(createHistoryEntry(historyArray.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.e("HistoryActivity", "Error al parsear JSON", e);
            adapter.add("Error cargando historial");
        }
    }

    private void sendSelectedLogs() {
        SparseBooleanArray selectedPositions = listView.getCheckedItemPositions();
        if (selectedPositions.size() == 0) {
            Toast.makeText(this, "Selecciona al menos un registro", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder selectedLogs = new StringBuilder("Registros seleccionados:\n\n");
        int count = 0;

        try {
            for (int i = 0; i < historyArray.length(); i++) {
                if (selectedPositions.get(i)) {
                    JSONObject entry = historyArray.getJSONObject(i);
                    selectedLogs.append(formatSingleEntry(entry)).append("\n\n");
                    count++;
                }
            }

            String subject = String.format(Locale.US,
                    "%d registros seleccionados - %s",
                    count,
                    new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date())
            );

            sendEmail("tomasbeta@outlook.com", subject, selectedLogs.toString());

        } catch (JSONException e) {
            Toast.makeText(this, "Error al procesar selección", Toast.LENGTH_SHORT).show();
            Log.e("HistoryActivity", "Error procesando selección", e);
        }
    }

    private String formatHistoryForEmail(String historyJson) {
        StringBuilder sb = new StringBuilder("Historial de Alarmas:\n\n");
        try {
            JSONArray historyArray = new JSONArray(historyJson);
            for (int i = 0; i < historyArray.length(); i++) {
                JSONObject entry = historyArray.getJSONObject(i);
                sb.append(formatSingleEntry(entry)).append("\n\n");
            }

            // Agregar resumen al final
            sb.append("---\n");
            sb.append("Total de registros: ").append(historyArray.length()).append("\n");
            sb.append("Última actualización: ").append(
                    new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date())
            );

        } catch (JSONException e) {
            Log.e("HistoryActivity", "Error al formatear historial", e);
            return "Error generando historial para email";
        }
        return sb.toString();
    }

    private void sendAllHistoryByEmail() {
        SharedPreferences prefs = getSharedPreferences("AlarmHistoryPrefs", MODE_PRIVATE);
        String currentHistoryJson = prefs.getString("alarm_history", "[]");
        String body = formatHistoryForEmail(currentHistoryJson);

        String subject = "Historial completo - " +
                new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());

        sendEmail("tomasbeta@outlook.com", subject, body);
    }

    private void sendEmail(String toEmail, String subject, String body) {
        Toast.makeText(this, "Preparando envío...", Toast.LENGTH_SHORT).show();

        MailSender.sendEmail(toEmail, subject, body, new MailSender.EmailCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(HistoryActivity.this,
                            "Correo enviado correctamente", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(HistoryActivity.this,
                            "Error: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });


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
    private String formatSingleEntry(JSONObject entry) throws JSONException {
        return String.format(Locale.US,
                "⏰ [%s]\n🚨 Estado: %s\n📍 %s\n🌐 %s",
                entry.getString("timestamp"),
                entry.getString("status"),
                entry.optBoolean("location_available", false) ?
                        getCityFromCoordinates(entry.getDouble("lat"), entry.getDouble("lon")) :
                        "Ubicación no disponible",
                getLocationLink(entry.getDouble("lat"), entry.getDouble("lon"))
        );
    }

    private String getLocationLink(double lat, double lon) {
        return (lat == 0.0 && lon == 0.0) ?
                "Sin coordenadas" :
                String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", lat, lon);
    }

    private String getCityFromCoordinates(double lat, double lon) {
        try {
            List<Address> addresses = new Geocoder(this, Locale.getDefault())
                    .getFromLocation(lat, lon, 1);

            if (!addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder location = new StringBuilder();
                if (address.getLocality() != null) location.append(address.getLocality());
                if (address.getSubAdminArea() != null) {
                    if (location.length() > 0) location.append(", ");
                    location.append(address.getSubAdminArea());
                }
                return location.toString();
            }
        } catch (IOException e) {
            Log.e("Geocoder", "Error obteniendo ciudad", e);
        }
        return "Ubicación desconocida";
    }
}