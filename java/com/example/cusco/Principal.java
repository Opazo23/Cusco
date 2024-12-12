package com.example.cusco;

import androidx.appcompat.app.AlertDialog;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.app.Activity;
import androidx.annotation.NonNull;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.auth.FirebaseUser;

public class Principal extends AppCompatActivity implements PopupMenu.OnMenuItemClickListener, androidx.appcompat.widget.SearchView.OnQueryTextListener {

    private static final int CREATE_LIST_REQUEST = 1;
    private RecyclerView recyclerView;
    private NoteAdapter noteAdapter;
    private List<String> noteList;
    private Map<String, String> noteIds;
    private androidx.appcompat.widget.SearchView txtbusca;
    private FirebaseAuthHelper authHelper;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Inicializar variables
        authHelper = new FirebaseAuthHelper();
        noteList = new ArrayList<>();
        noteIds = new HashMap<>();

        // Configurar RecyclerView
        recyclerView = findViewById(R.id.recyclerView);
        noteAdapter = new NoteAdapter(noteList, authHelper);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerView.setAdapter(noteAdapter);


        // Configurar Drag and Drop
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();

                if (fromPosition < 0 || toPosition < 0) {
                    return false;
                }

                String movedNote = noteList.get(fromPosition);
                String noteId = noteIds.get(movedNote);

                // Mover localmente
                noteList.remove(fromPosition);
                noteList.add(toPosition, movedNote);
                noteAdapter.notifyItemMoved(fromPosition, toPosition);
                noteAdapter.updateOriginalList(noteList);

                // Actualizar orden en Firebase
                authHelper.updateNoteOrderDragAndDrop(noteId, toPosition,
                        new FirebaseAuthHelper.OnNoteOperationCompleteListener() {
                            @Override
                            public void onSuccess(String updatedNoteId) {
                                // La nota se movió exitosamente
                            }

                            @Override
                            public void onFailure(String error) {
                                // Revertir el movimiento local si falla
                                noteList.remove(toPosition);
                                noteList.add(fromPosition, movedNote);
                                noteAdapter.notifyItemMoved(toPosition, fromPosition);
                                noteAdapter.updateOriginalList(noteList);
                                Toast.makeText(Principal.this,
                                        "Error al mover la nota: " + error, Toast.LENGTH_SHORT).show();
                            }
                        });

                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // No implementamos deslizar
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                loadNotes();
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(recyclerView);

        // Configurar SearchView
        txtbusca = findViewById(R.id.txtbuscar);
        if (txtbusca != null) {
            txtbusca.setOnQueryTextListener(this);
        }

        // Cargar notas existentes
        loadNotes();

        // Configurar listeners
        setupNoteAdapter();
        findViewById(R.id.fab).setOnClickListener(v -> showPopupMenu(v));
    }


    private final ActivityResultLauncher<Intent> editNoteLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null) {
                        String editedNote = data.getStringExtra("note_text");
                        int position = data.getIntExtra("note_position", -1);
                        String noteId = data.getStringExtra("note_id");

                        if (editedNote != null && position != -1 && noteId != null) {
                            authHelper.updateNoteInFirestore(noteId, editedNote,
                                    new FirebaseAuthHelper.OnNoteOperationCompleteListener() {
                                        @Override
                                        public void onSuccess(String updatedNoteId) {
                                            noteList.set(position, editedNote);
                                            noteIds.put(editedNote, noteId);
                                            noteAdapter.setNoteIds(noteIds);
                                            noteAdapter.updateNote(position, editedNote);
                                            Toast.makeText(Principal.this,
                                                    "Nota actualizada", Toast.LENGTH_SHORT).show();
                                        }

                                        @Override
                                        public void onFailure(String error) {
                                            Toast.makeText(Principal.this,
                                                    "Error al actualizar la nota: " + error,
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        }
                    }
                }
            }
    );

    private void setupNoteAdapter() {
        noteAdapter.setOnItemClickListener(new NoteAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View v) {
                showPopupMenu(v);
            }

            @Override
            public void onItemClick(String string) {
                Toast.makeText(Principal.this, "Nota seleccionada", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onEditClick(String note, int position) {
                String noteId = noteIds.get(note);
                Log.d("FIREBASE_NOTE", "Editando nota con ID: " + noteId);

                if (noteId != null) {
                    Intent intent = new Intent(Principal.this, CreateNota.class);
                    intent.putExtra("note_text", note);
                    intent.putExtra("note_position", position);
                    intent.putExtra("note_id", noteId);
                    editNoteLauncher.launch(intent);
                }
            }

            @Override
            public void onPinClick(int position) {
                if (position > 0) {
                    String noteToPin = noteList.get(position);
                    String notePinId = noteIds.get(noteToPin);

                    authHelper.updateNoteOrder(notePinId, new FirebaseAuthHelper.OnNoteOperationCompleteListener() {
                        @Override
                        public void onSuccess(String noteId) {
                            loadNotes();
                            Toast.makeText(Principal.this, "Nota fijada", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFailure(String error) {
                            Toast.makeText(Principal.this,
                                    "Error al fijar la nota: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    Toast.makeText(Principal.this, "La nota ya está fijada", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onDeleteClick(int position) {
                String noteContent = noteList.get(position);
                String noteId = noteIds.get(noteContent);

                if (noteId != null) {
                    authHelper.deleteNoteFromFirestore(noteId,
                            new FirebaseAuthHelper.OnNoteOperationCompleteListener() {
                                @Override
                                public void onSuccess(String deletedNoteId) {
                                    noteList.remove(position);
                                    noteIds.remove(noteContent);
                                    noteAdapter.setNoteIds(noteIds);
                                    noteAdapter.notifyItemRemoved(position);
                                    Toast.makeText(Principal.this,
                                            "Nota eliminada", Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onFailure(String error) {
                                    Toast.makeText(Principal.this,
                                            "Error al eliminar la nota: " + error,
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                }
            }
        });
    }

    private void loadNotes() {
        authHelper.loadUserNotes(new FirebaseAuthHelper.OnNotesLoadedListener() {
            @Override
            public void onSuccess(Map<String, String> notesWithIds) {
                noteList.clear();
                noteIds.clear();
                if (notesWithIds != null) {
                    noteIds.putAll(notesWithIds);
                    noteList.addAll(notesWithIds.keySet());
                    noteAdapter.setNoteIds(noteIds);
                    noteAdapter.updateOriginalList(noteList);
                    noteAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFailure(String error) {
                if (error != null && !error.isEmpty() &&
                        !error.equals("No hay notas") &&
                        !error.equals("No data")) {
                    Toast.makeText(Principal.this,
                            "Error al cargar las notas: " + error,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showPopupMenu(View view) {
        PopupMenu popupMenu = new PopupMenu(new ContextThemeWrapper(this, R.style.CustomPopupMenu), view);
        MenuInflater inflater = popupMenu.getMenuInflater();
        inflater.inflate(R.menu.menu_main, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(this);
        popupMenu.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.action_create_list) {
            Toast.makeText(this, "Has pulsado Crear Nota", Toast.LENGTH_SHORT).show();
            startActivityForResult(new Intent(this, CreateNota.class), CREATE_LIST_REQUEST);
            return true;
        } else if (itemId == R.id.action_salir) {
            //Toast.makeText(this, "Has pulsado Cerrar sesion", Toast.LENGTH_SHORT).show();
            new AlertDialog.Builder(Principal.this)
                    .setTitle("Cerrar Sesión")
                    .setMessage("¿Estás seguro de que quieres cerrar sesión")
                    .setPositiveButton("Sí", (dialog, which) -> {
                        cerrarSesion();
                    })
                    .setNegativeButton("No", null)
                    .show();
            return true;
        }else if (itemId == R.id.action_delete_all) {
            new AlertDialog.Builder(Principal.this)
                    .setTitle("Borrar todas las notas")
                    .setMessage("¿Estás seguro de que quieres borrar todas las notas?")
                    .setPositiveButton("Sí", (dialog, which) -> {
                        deleteAllNotes();
                    })
                    .setNegativeButton("No", null)
                    .show();
            return true;
        }
        return false;
    }

    @SuppressLint("NotifyDataSetChanged")
    private void addNote(String note) {
        authHelper.addNoteToFirestore(note, new FirebaseAuthHelper.OnNoteOperationCompleteListener() {
            @Override
            public void onSuccess(String noteId) {
                noteList.add(note);
                noteIds.put(note, noteId);
                noteAdapter.setNoteIds(noteIds);
                noteAdapter.addToOriginalList(note);
                noteAdapter.notifyDataSetChanged();
                Toast.makeText(Principal.this, "Nota guardada", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(Principal.this, "Error al guardar la nota: " + error,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void cerrarSesion() {
        authHelper.signOut();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void deleteAllNotes() {
        FirebaseUser currentUser = authHelper.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "No hay usuario autenticado", Toast.LENGTH_SHORT).show();
            return;
        }

        // Primero obtenemos todas las notas del usuario
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("notes")
                .whereEqualTo("user_id", currentUser.getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    // Creamos un batch para realizar todas las eliminaciones en una sola transacción
                    WriteBatch batch = db.batch();

                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        batch.delete(document.getReference());
                    }

                    // Ejecutamos el batch
                    batch.commit().addOnSuccessListener(aVoid -> {
                        // Limpiamos las listas locales
                        noteList.clear();
                        noteIds.clear();
                        noteAdapter.setNoteIds(noteIds);
                        noteAdapter.updateOriginalList(noteList);
                        noteAdapter.notifyDataSetChanged();

                        Toast.makeText(Principal.this,
                                "Todas las notas han sido eliminadas",
                                Toast.LENGTH_SHORT).show();
                    }).addOnFailureListener(e ->
                            Toast.makeText(Principal.this,
                                    "Error al eliminar las notas: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Error al obtener las notas: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        if (noteAdapter != null) {
            noteAdapter.filter(query);
        }
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (noteAdapter != null) {
            noteAdapter.filter(newText);
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_LIST_REQUEST && resultCode == RESULT_OK) {
            if (data != null) {
                String note = data.getStringExtra("note");
                if (note != null) {
                    addNote(note);
                }
            }
        }
    }
}