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
    // Declaración de variables para los elementos de la interfaz
    private EditText emailEditText, passwordEditText;
    private Button loginButton, registerButton;
    private FirebaseAuthHelper authHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);

        // Inicializar el helper de autenticación de Firebase
        authHelper = new FirebaseAuthHelper();

        // Verificar si existe una sesión activa
        // Si existe, redirige directamente a la pantalla principal
        if (authHelper.getCurrentUser() != null) {
            startActivity(new Intent(MainActivity.this, Principal.class));
            finish();
            return;
        }

        // Vinculación de los elementos de la interfaz con sus IDs
        emailEditText = findViewById(R.id.username);
        passwordEditText = findViewById(R.id.password);
        loginButton = findViewById(R.id.loginButton);
        registerButton = findViewById(R.id.registro);

        // Configurar el listener para el botón de login
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateLogin();
            }
        });

        // Configurar el listener para el botón de registro
        // Redirige a la actividad de registro
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });

        // Configurar la funcionalidad de "Olvidé mi contraseña"
        setupForgotPassword();
    }

    // Método para validar y procesar el login
    private void validateLogin() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Verificar que los campos no estén vacíos
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Por favor, complete todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mostrar mensaje de proceso
        Toast.makeText(this, "Iniciando sesión...", Toast.LENGTH_SHORT).show();

        // Intentar login con Firebase
        authHelper.loginUser(email, password, new FirebaseAuthHelper.OnLoginCompleteListener() {
            // Si el login es exitoso
            @Override
            public void onSuccess() {
                Toast.makeText(MainActivity.this,
                        "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(MainActivity.this, Principal.class);
                startActivity(intent);
                finish();
            }

            // Si el login falla
            @Override
            public void onFailure(String error) {
                Toast.makeText(MainActivity.this,
                        "Error: " + error, Toast.LENGTH_SHORT).show();
                passwordEditText.setError("Credenciales incorrectas");
                emailEditText.setError("Credenciales incorrectas");
            }
        });
    }

    // Configura el TextView de "Olvidé mi contraseña"
    private void setupForgotPassword() {
        TextView forgotPasswordText = findViewById(R.id.forgotPasswordText);
        forgotPasswordText.setOnClickListener(v -> showForgotPasswordDialog());
    }

    // Muestra el diálogo para recuperar la contraseña
    private void showForgotPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Recuperar contraseña");

        // Inflar y configurar el layout del diálogo
        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null);
        final EditText input = viewInflated.findViewById(R.id.emailInput);
        builder.setView(viewInflated);

        // Configurar el botón "Enviar"
        builder.setPositiveButton("Enviar", (dialog, which) -> {
            String email = input.getText().toString().trim();
            if (!TextUtils.isEmpty(email)) {
                // Intentar enviar el email de recuperación
                authHelper.resetPassword(email, new FirebaseAuthHelper.OnPasswordResetListener() {
                    // Si el envío es exitoso
                    @Override
                    public void onSuccess() {
                        Toast.makeText(MainActivity.this,
                                "Se ha enviado un email para restablecer tu contraseña",
                                Toast.LENGTH_LONG).show();
                    }

                    // Si el envío falla
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

        // Configurar el botón "Cancelar"
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}