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

    private EditText titleEditText;
    private EditText noteEditText;
    private Button saveButton;
    private int notePosition = -1;
    private String noteId = null;
    private FirebaseAuthHelper authHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lista);

        // Inicializar FirebaseAuthHelper
        authHelper = new FirebaseAuthHelper();

        titleEditText = findViewById(R.id.titleEditText);
        noteEditText = findViewById(R.id.noteEditText);
        saveButton = findViewById(R.id.saveButton);

        // Manejar el botón de retroceso
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        // Obtener datos de la nota si estamos editando
        String existingNote = getIntent().getStringExtra("note_text");
        notePosition = getIntent().getIntExtra("note_position", -1);
        noteId = getIntent().getStringExtra("note_id");

        Log.d("FIREBASE_NOTE", "Iniciando CreateNota - noteId: " + noteId);

        if (existingNote != null && notePosition != -1) {
            String[] parts = existingNote.split("\n\n", 2);
            if (parts.length >= 2) {
                titleEditText.setText(parts[0]);
                noteEditText.setText(parts[1]);
            } else {
                noteEditText.setText(existingNote);
            }
        }

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String title = titleEditText.getText().toString().trim();
                String noteContent = noteEditText.getText().toString().trim();

                if (title.isEmpty() && noteContent.isEmpty()) {
                    Toast.makeText(CreateNota.this, "Por favor, escribe algo", Toast.LENGTH_SHORT).show();
                    return;
                }

                String completeNote = title + "\n\n" + noteContent;
                Intent resultIntent = new Intent();

                if (noteId != null) {
                    // Estamos editando una nota existente
                    Log.d("FIREBASE_NOTE", "Guardando nota editada con ID: " + noteId);
                    resultIntent.putExtra("note_text", completeNote);
                    resultIntent.putExtra("note_position", notePosition);
                    resultIntent.putExtra("note_id", noteId);
                } else {
                    // Es una nota nueva
                    Log.d("FIREBASE_NOTE", "Guardando nota nueva");
                    resultIntent.putExtra("note", completeNote);
                }

                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });
    }
}