package com.example.cusco;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.view.LayoutInflater;
import androidx.appcompat.app.AlertDialog;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private EditText emailEditText, passwordEditText;
    private Button loginButton, registerButton;
    private FirebaseAuthHelper authHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);

        // Inicializar FirebaseAuthHelper
        authHelper = new FirebaseAuthHelper();

        // Verificar si ya hay una sesión activa
        if (authHelper.getCurrentUser() != null) {
            startActivity(new Intent(MainActivity.this, Principal.class));
            finish();
            return;
        }

        emailEditText = findViewById(R.id.username);  // Asumiendo que cambiamos el campo a email
        passwordEditText = findViewById(R.id.password);
        loginButton = findViewById(R.id.loginButton);
        registerButton = findViewById(R.id.registro);

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateLogin();
            }
        });

        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });

        setupForgotPassword();
    }

    private void validateLogin() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Por favor, complete todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mostrar mensaje de carga
        Toast.makeText(this, "Iniciando sesión...", Toast.LENGTH_SHORT).show();

        // Usar FirebaseAuthHelper para el login
        authHelper.loginUser(email, password, new FirebaseAuthHelper.OnLoginCompleteListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(MainActivity.this,
                        "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(MainActivity.this, Principal.class);
                startActivity(intent);
                finish();
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(MainActivity.this,
                        "Error: " + error, Toast.LENGTH_SHORT).show();
                passwordEditText.setError("Credenciales incorrectas");
                emailEditText.setError("Credenciales incorrectas");
            }
        });
    }

    private void setupForgotPassword() {
        TextView forgotPasswordText = findViewById(R.id.forgotPasswordText);
        forgotPasswordText.setOnClickListener(v -> showForgotPasswordDialog());
    }

    private void showForgotPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Recuperar contraseña");

        // Configurar el layout del diálogo
        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null);
        final EditText input = viewInflated.findViewById(R.id.emailInput);
        builder.setView(viewInflated);

        builder.setPositiveButton("Enviar", (dialog, which) -> {
            String email = input.getText().toString().trim();
            if (!TextUtils.isEmpty(email)) {
                authHelper.resetPassword(email, new FirebaseAuthHelper.OnPasswordResetListener() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(MainActivity.this,
                                "Se ha enviado un email para restablecer tu contraseña",
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onFailure(String error) {
                        Toast.makeText(MainActivity.this,
                                "Error: " + error,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                Toast.makeText(MainActivity.this,
                        "Por favor, introduce un email válido",
                        Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}