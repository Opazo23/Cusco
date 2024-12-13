package com.example.cusco;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {

    private EditText username, email, password, confirmPassword;
    private Button registerButton;
    private FirebaseAuthHelper authHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registro);

        // Inicializar FirebaseAuthHelper
        authHelper = new FirebaseAuthHelper();

        // Vinculamos los componentes del layout
        username = findViewById(R.id.username);
        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        confirmPassword = findViewById(R.id.confirmPassword);
        registerButton = findViewById(R.id.registerButton);

        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (validateRegistration()) {
                    registerUser();
                }
            }
        });
    }

    private void registerUser() {
        String usernameStr = username.getText().toString().trim();
        String emailStr = email.getText().toString().trim();
        String passwordStr = password.getText().toString().trim();

        // Mostrar un mensaje de carga
        Toast.makeText(RegisterActivity.this, "Registrando usuario...", Toast.LENGTH_SHORT).show();

        // Registrar usuario usando FirebaseAuthHelper
        authHelper.registerUser(emailStr, usernameStr, passwordStr,
                new FirebaseAuthHelper.OnRegisterCompleteListener() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(RegisterActivity.this,
                                "Registro exitoso. Revisa tu email para verificar tu cuenta",
                                Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onFailure(String error) {
                        Toast.makeText(RegisterActivity.this,
                                "Error en el registro: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private boolean validateRegistration() {
        String userNameInput = username.getText().toString().trim();
        String emailInput = email.getText().toString().trim();
        String passwordInput = password.getText().toString().trim();
        String confirmPasswordInput = confirmPassword.getText().toString().trim();

        if (userNameInput.isEmpty()) {
            username.setError("El nombre de usuario es obligatorio");
            return false;
        }

        if (emailInput.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailInput).matches()) {
            email.setError("Introduce un correo electrónico válido");
            return false;
        }

        if (passwordInput.isEmpty() || !isPasswordValid(passwordInput)) {
            password.setError("La contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula y un carácter especial");
            return false;
        }

        if (!passwordInput.equals(confirmPasswordInput)) {
            confirmPassword.setError("Las contraseñas no coinciden");
            return false;
        }

        return true;
    }

    private boolean isPasswordValid(String password) {
        /*return password.length() >= 8 &&
                password.matches(".*[A-Z].*") && // Al menos una mayúscula
                password.matches(".*[a-z].*") && // Al menos una minúscula
                password.matches(".*[@#$%^&+=!?_*].*"); // Al menos un carácter especial*/
        return true;
    }
}