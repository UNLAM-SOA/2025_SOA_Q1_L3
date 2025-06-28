package com.example.botonapplication;


import android.content.SharedPreferences;
import android.icu.text.SimpleDateFormat;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
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

public class HistoryActivity extends AppCompatActivity {


    private static final String TAG = "HistoryActivity";
    private static final String PREFS_NAME = "AlarmHistoryPrefs";
    private static final String PREFS_KEY_HISTORY = "alarm_history";
    private static final String EMAIL_DESTINO = "tomasbeta@outlook.com";

    private static final String LOCATION_LOADING_TEXT = "Cargando ubicación...";
    private static final String LOCATION_NOT_AVAILABLE_TEXT = "Ubicación no disponible";
    private static final String LOCATION_UNKNOWN_TEXT = "Ubicación desconocida";
    private static final String NO_COORDINATES_TEXT = "Sin coordenadas";

    private static final String EMAIL_SELECTED_LOGS_SUBJECT_FORMAT = "%d registros seleccionados - %s";
    private static final String EMAIL_FULL_HISTORY_SUBJECT_PREFIX = "Historial completo - ";

    private static final String DATE_FORMAT_SHORT = "dd/MM/yyyy HH:mm";
    private static final String DATE_FORMAT_LONG = "dd/MM/yyyy HH:mm:ss";

    private ListView listView;
    private Button btnSendEmail;
    private Button btnSendSelected;
    private ArrayAdapter<String> adapter;
    private JSONArray historyArray; // Mantener referencia al JSONArray original

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        initializeViews();
        loadHistory();
        //getSharedPreferences("AlarmHistoryPrefs", MODE_PRIVATE).edit().clear().apply();
        btnSendEmail.setOnClickListener(v -> sendAllHistoryByEmail());
        btnSendSelected.setOnClickListener(v -> sendSelectedLogs());
    }

    private void initializeViews() {
        listView = findViewById(R.id.historyListView);
        btnSendEmail = findViewById(R.id.btnSendEmail);
        btnSendSelected = findViewById(R.id.btnSendSelected);

        // Permitir selección múltiple en la lista
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice);
        listView.setAdapter(adapter);
    }

    private void loadHistory() {
        adapter.clear();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String historyJson = prefs.getString(PREFS_KEY_HISTORY, "[]");

        try {
            historyArray = new JSONArray(historyJson);

            for (int i = 0; i < historyArray.length(); i++) {
                JSONObject entry = historyArray.getJSONObject(i);

                String timestamp = entry.getString("timestamp");
                String status = entry.getString("status");
                double lat = entry.getDouble("lat");
                double lon = entry.getDouble("lon");
                boolean hasLocation = entry.optBoolean("location_available", false);

                String initialLocationText = hasLocation ? LOCATION_LOADING_TEXT : LOCATION_NOT_AVAILABLE_TEXT;

                String initialEntryText = String.format(Locale.US,
                        "%s - %s\n%s\nLat: %.6f, Lon: %.6f",
                        timestamp, status, initialLocationText, lat, lon);

                adapter.add(initialEntryText);

                // Actualizar ubicación asíncronamente si está disponible
                if (hasLocation) {
                    final int index = i;
                    new Thread(() -> {
                        String city = getCityFromCoordinates(lat, lon);
                        String updatedEntryText = String.format(Locale.US,
                                "%s - %s\n%s\nLat: %.6f, Lon: %.6f",
                                timestamp, status, city, lat, lon);

                        runOnUiThread(() -> {
                            adapter.remove(initialEntryText);
                            adapter.insert(updatedEntryText, index);
                        });
                    }).start();
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error al parsear JSON", e);
            adapter.add("Error cargando historial");
        }
    }

    private void sendSelectedLogs() {
        SparseBooleanArray selectedPositions = listView.getCheckedItemPositions();
        if (selectedPositions.size() == 0) {
            Toast.makeText(this, "Selecciona al menos un registro", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder selectedLogsBuilder = new StringBuilder("Registros seleccionados:\n\n");
        int count = 0;

        try {
            for (int i = 0; i < historyArray.length(); i++) {
                if (selectedPositions.get(i)) {
                    JSONObject entry = historyArray.getJSONObject(i);
                    selectedLogsBuilder.append(formatSingleEntry(entry)).append("\n\n");
                    count++;
                }
            }

            String subject = String.format(Locale.US,
                    EMAIL_SELECTED_LOGS_SUBJECT_FORMAT,
                    count,
                    new SimpleDateFormat(DATE_FORMAT_SHORT, Locale.getDefault()).format(new Date())
            );

            sendEmail(EMAIL_DESTINO, subject, selectedLogsBuilder.toString());

        } catch (JSONException e) {
            Toast.makeText(this, "Error al procesar selección", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error procesando selección", e);
        }
    }

    private String formatHistoryForEmail(String historyJson) {
        StringBuilder sb = new StringBuilder("Historial de Alarmas:\n\n");
        try {
            JSONArray historyArrayLocal = new JSONArray(historyJson);

            for (int i = 0; i < historyArrayLocal.length(); i++) {
                JSONObject entry = historyArrayLocal.getJSONObject(i);
                sb.append(formatSingleEntry(entry)).append("\n\n");
            }

            // Resumen al final
            sb.append("---\n");
            sb.append("Total de registros: ").append(historyArrayLocal.length()).append("\n");
            sb.append("Última actualización: ").append(
                    new SimpleDateFormat(DATE_FORMAT_LONG, Locale.getDefault()).format(new Date())
            );

        } catch (JSONException e) {
            Log.e(TAG, "Error al formatear historial", e);
            return "Error generando historial para email";
        }
        return sb.toString();
    }

    private void sendAllHistoryByEmail() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentHistoryJson = prefs.getString(PREFS_KEY_HISTORY, "[]");
        String body = formatHistoryForEmail(currentHistoryJson);

        String subject = EMAIL_FULL_HISTORY_SUBJECT_PREFIX +
                new SimpleDateFormat(DATE_FORMAT_SHORT, Locale.getDefault()).format(new Date());

        sendEmail(EMAIL_DESTINO, subject, body);
    }

    private void sendEmail(String toEmail, String subject, String body) {
        Toast.makeText(this, "Preparando envío...", Toast.LENGTH_SHORT).show();

        MailSender.sendEmail(toEmail, subject, body, new MailSender.EmailCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> Toast.makeText(HistoryActivity.this,
                        "Correo enviado correctamente", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(HistoryActivity.this,
                        "Error: " + error, Toast.LENGTH_LONG).show());
            }
        });
    }

    private String formatSingleEntry(JSONObject entry) throws JSONException {
        return String.format(Locale.US,
                "⏰ [%s]\n🚨 Estado: %s\n📍 %s\n🌐 %s",
                entry.getString("timestamp"),
                entry.getString("status"),
                entry.optBoolean("location_available", false) ?
                        getCityFromCoordinates(entry.getDouble("lat"), entry.getDouble("lon")) :
                        LOCATION_NOT_AVAILABLE_TEXT,
                getLocationLink(entry.getDouble("lat"), entry.getDouble("lon"))
        );
    }

    private String getLocationLink(double lat, double lon) {
        return (lat == 0.0 && lon == 0.0) ?
                NO_COORDINATES_TEXT :
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
            Log.e(TAG, "Error obteniendo ciudad", e);
        }
        return LOCATION_UNKNOWN_TEXT;
    }
}
