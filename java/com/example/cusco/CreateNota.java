package com.example.cusco;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;

public class CreateNota extends AppCompatActivity {
    // Variables para los elementos de la UI
    private EditText titleEditText;      // Campo para el título de la nota
    private EditText noteEditText;       // Campo para el contenido de la nota
    private Button saveButton;           // Botón de guardado

    // Variables para el control de estado
    private int notePosition = -1;       // Posición de la nota (-1 si es nueva)
    private String noteId = null;        // ID de Firebase (null si es nueva)
    private FirebaseAuthHelper authHelper; // Helper para operaciones con Firebase

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lista);

        // Inicialización del helper de Firebase
        authHelper = new FirebaseAuthHelper();

        // Vinculación de los elementos de la UI con sus IDs
        titleEditText = findViewById(R.id.titleEditText);
        noteEditText = findViewById(R.id.noteEditText);
        saveButton = findViewById(R.id.saveButton);

        // Configuración del manejo del botón de retroceso del dispositivo
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setResult(RESULT_CANCELED);  // Indica que se canceló la operación
                finish();                    // Cierra la actividad
            }
        });

        // Recuperación de datos si estamos editando una nota existente
        String existingNote = getIntent().getStringExtra("note_text");
        notePosition = getIntent().getIntExtra("note_position", -1);
        noteId = getIntent().getStringExtra("note_id");

        // Log para debugging
        Log.d("FIREBASE_NOTE", "Iniciando CreateNota - noteId: " + noteId);

        // Si hay una nota existente, separa el título del contenido
        if (existingNote != null && notePosition != -1) {
            String[] parts = existingNote.split("\n\n", 2);
            if (parts.length >= 2) {
                // Si hay dos partes, la primera es el título y la segunda el contenido
                titleEditText.setText(parts[0]);
                noteEditText.setText(parts[1]);
            } else {
                // Si no hay dos partes, todo es contenido
                noteEditText.setText(existingNote);
            }
        }

        // Configuración del listener para el botón de guardar
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Obtiene el texto de los campos
                String title = titleEditText.getText().toString().trim();
                String noteContent = noteEditText.getText().toString().trim();

                // Validación básica
                if (title.isEmpty() && noteContent.isEmpty()) {
                    Toast.makeText(CreateNota.this, "Por favor, escribe algo", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Combina título y contenido
                String completeNote = title + "\n\n" + noteContent;
                Intent resultIntent = new Intent();

                if (noteId != null) {
                    // Si existe noteId, estamos editando una nota existente
                    Log.d("FIREBASE_NOTE", "Guardando nota editada con ID: " + noteId);
                    resultIntent.putExtra("note_text", completeNote);
                    resultIntent.putExtra("note_position", notePosition);
                    resultIntent.putExtra("note_id", noteId);
                } else {
                    // Si no existe noteId, es una nota nueva
                    Log.d("FIREBASE_NOTE", "Guardando nota nueva");
                    resultIntent.putExtra("note", completeNote);
                }

                // Establece el resultado y cierra la actividad
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });
    }
}