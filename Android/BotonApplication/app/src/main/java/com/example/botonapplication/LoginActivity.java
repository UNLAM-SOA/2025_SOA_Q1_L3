package com.example.botonapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;


public class LoginActivity extends AppCompatActivity {

    private static final String USER_CORRECTO = "admin";
    private static final String PASS_CORRECTO = "1234";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText inputUser = findViewById(R.id.inputUser);
        EditText inputPass = findViewById(R.id.inputPass);
        Button btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> {
            String user = inputUser.getText().toString().trim();
            String pass = inputPass.getText().toString().trim();

            if (user.equals(USER_CORRECTO) && pass.equals(PASS_CORRECTO)) {
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                finish(); // Para que no se pueda volver con "Atrás"
                Log.d("APP_DEBUG", "Redirigiendo a MainActivity");
            } else {
                Toast.makeText(this, "Usuario o contraseña incorrectos", Toast.LENGTH_SHORT).show();
            }
        });
    }
}

