package com.example.cusco;

import androidx.appcompat.app.AlertDialog;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
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

import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
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
    private TextView noResultsText;
    private boolean isDragging = false;

    @Override
    //Método inicial que se ejecuta cuando se crea la actividad
    //Inicializa las variables y componentes principales
    //Configura el RecyclerView y el drag & drop
    //Configura la barra de búsqueda
    //Carga las notas existentes
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Inicializar variables
        authHelper = new FirebaseAuthHelper();
        noteList = new ArrayList<>();
        noteIds = new HashMap<>();

        // Configurar RecyclerView
        // En el método onCreate, después de inicializar el RecyclerView
        recyclerView = findViewById(R.id.recyclerView);
        noteAdapter = new NoteAdapter(noteList, authHelper);

        // Crea un GridLayoutManager personalizado que respete el orden
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 2);
        recyclerView.setLayoutManager(gridLayoutManager);
        recyclerView.setAdapter(noteAdapter);
        //campo para cuando ni hay resultados
        noResultsText = findViewById(R.id.noResultsText);


        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {

            @Override
            public boolean canDropOver(@NonNull RecyclerView recyclerView,
                                       @NonNull RecyclerView.ViewHolder current,
                                       @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = current.getAdapterPosition();
                int toPosition = target.getAdapterPosition();

                if (fromPosition < 0 || toPosition < 0) {
                    return false;
                }

                // Solo verificar si alguna de las notas está fijada
                String moveNoteContent = noteList.get(fromPosition);
                String targetNoteContent = noteList.get(toPosition);

                String moveNoteId = noteIds.get(moveNoteContent);
                String targetNoteId = noteIds.get(targetNoteContent);

                Boolean isMoveNotePinned = noteAdapter.isPinned(moveNoteId);
                Boolean isTargetNotePinned = noteAdapter.isPinned(targetNoteId);

                // No permitir mover si alguna nota está fijada
                if ((isMoveNotePinned != null && isMoveNotePinned) ||
                        (isTargetNotePinned != null && isTargetNotePinned)) {
                    return false;
                }

                return true;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();

                if (fromPosition < 0 || toPosition < 0) {
                    return false;
                }

                isDragging = true;  // Indicar que estamos en medio de un drag

                String movedNoteContent = noteList.get(fromPosition);
                String noteId = noteIds.get(movedNoteContent);

                Log.d("DragDrop", "Moviendo nota: " + movedNoteContent);
                Log.d("DragDrop", "ID de la nota: " + noteId);

                // Verificar si la nota está fijada
                Boolean isPinned = noteAdapter.isPinned(noteId);
                if (isPinned != null && isPinned) {
                    Toast.makeText(Principal.this,
                            "No se pueden mover notas fijadas",
                            Toast.LENGTH_SHORT).show();
                    return false;
                }

                // Crear una copia temporal de las listas para mantener el orden
                List<String> newNoteList = new ArrayList<>(noteList);

                // Realizar el movimiento en la copia
                String movedNote = newNoteList.remove(fromPosition);
                newNoteList.add(toPosition, movedNote);

                // Actualizar las listas reales
                noteList.clear();
                noteList.addAll(newNoteList);

                // Actualizar el adaptador
                noteAdapter.notifyItemMoved(fromPosition, toPosition);
                noteAdapter.updateOriginalList(new ArrayList<>(noteList));

                // Log del nuevo orden y verificación de IDs
                Log.d("DragDrop", "Nuevo orden después del movimiento:");
                for (int i = 0; i < noteList.size(); i++) {
                    String content = noteList.get(i);
                    String id = noteIds.get(content);
                    Log.d("DragDrop", String.format("Posición %d: %s (ID: %s)", i, content, id));
                }

                // Actualizar en Firebase
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                db.collection("notes")
                        .document(noteId)
                        .update("order", toPosition)
                        .addOnSuccessListener(aVoid -> {
                            // Esperar un momento para asegurar la sincronización
                            new Handler().postDelayed(() -> {
                                isDragging = false;  // Drag completado
                                Log.d("DragDrop", "Drag & Drop completado y sincronizado");
                            }, 500);  // Esperar 500ms
                        })
                        .addOnFailureListener(e -> {
                            // Revertir cambios en caso de error
                            isDragging = false;
                            noteList.clear();
                            noteList.addAll(newNoteList);
                            noteAdapter.notifyDataSetChanged();
                            Toast.makeText(Principal.this,
                                    "Error al actualizar el orden: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });

                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // No implementado
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(recyclerView);

        // Configurar SearchView
        txtbusca = findViewById(R.id.txtbuscar);
        if (txtbusca != null) {
            txtbusca.setOnQueryTextListener(this);
        }

        noteAdapter.setFilterResultListener(isEmpty -> {
            if (isEmpty) {
                noResultsText.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                noResultsText.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        });

        // Cargar notas existentes
        loadNotes();

        // Configurar listeners
        setupNoteAdapter();
        findViewById(R.id.fab).setOnClickListener(v -> showPopupMenu(v));
    }

    private void logNoteInfo(String noteContent, int position) {
        String noteId = noteIds.get(noteContent);
        Log.d("NoteOperation", "Nota en posición " + position + ":");
        Log.d("NoteOperation", "Contenido: " + noteContent);
        Log.d("NoteOperation", "ID: " + noteId);
        Boolean isPinned = noteAdapter.isPinned(noteId);
        Log.d("NoteOperation", "Está fijada: " + (isPinned != null && isPinned));
    }

    //Maneja el resultado de la edición de una nota
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

    //Configura todos los listeners del adaptador de notas
    //Maneja los clicks para editar, fijar, desfijar y eliminar notas
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
                if (isDragging) {
                    Toast.makeText(Principal.this,
                            "Por favor, espera un momento antes de fijar la nota",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // Como verificación adicional, recargar las notas antes de fijar
                FirebaseFirestore.getInstance()
                        .collection("notes")
                        .whereEqualTo("user_id", authHelper.getCurrentUser().getUid())
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            // Verificar que la posición sigue siendo válida
                            if (position >= noteList.size()) {
                                Toast.makeText(Principal.this,
                                        "Error: Posición de nota inválida",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }

                            String noteContent = noteList.get(position);
                            String noteId = noteIds.get(noteContent);

                            // Verificar que el ID existe en los resultados recientes
                            boolean found = false;
                            for (DocumentSnapshot doc : querySnapshot) {
                                if (doc.getId().equals(noteId) &&
                                        noteContent.equals(doc.getString("content"))) {
                                    found = true;
                                    break;
                                }
                            }

                            if (!found) {
                                Toast.makeText(Principal.this,
                                        "Error: La nota no se encontró en el estado actual",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }

                            authHelper.updateNoteOrder(noteId, noteContent,
                                    new FirebaseAuthHelper.OnNoteOperationCompleteListener() {
                                        @Override
                                        public void onSuccess(String updatedNoteId) {
                                            noteAdapter.setPinnedStatus(updatedNoteId, true);
                                            loadNotes();
                                            Toast.makeText(Principal.this,
                                                    "Nota fijada correctamente",
                                                    Toast.LENGTH_SHORT).show();
                                        }

                                        @Override
                                        public void onFailure(String error) {
                                            Toast.makeText(Principal.this,
                                                    "Error al fijar la nota: " + error,
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(Principal.this,
                                    "Error al verificar el estado actual: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });
            }


            @Override
            public void onUnpinClick(int position) {
                String noteToUnpin = noteList.get(position);
                String noteId = noteIds.get(noteToUnpin);

                if (noteId != null) {
                    authHelper.unpinNote(noteId, new FirebaseAuthHelper.OnNoteOperationCompleteListener() {
                        @Override
                        public void onSuccess(String unpinnedNoteId) {
                            // Actualizar estado local
                            noteAdapter.setPinnedStatus(noteId, false);

                            // Recargar todas las notas para asegurar el orden correcto
                            loadNotes();

                            Toast.makeText(Principal.this, "Nota desfijada", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFailure(String error) {
                            Toast.makeText(Principal.this,
                                    "Error al desfijar la nota: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
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
                                    // Eliminar de la lista principal
                                    noteList.remove(position);
                                    // Eliminar de la lista original
                                    noteAdapter.removeFromOriginalList(noteContent);

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

    //Carga todas las notas del usuario desde Firebase
    //Actualiza la interfaz con las notas cargadas
    private void loadNotes() {
        authHelper.loadUserNotes(new FirebaseAuthHelper.OnNotesLoadedListener() {
            @Override
            public void onSuccess(Map<String, String> notesWithIds, Map<String, Boolean> pinnedStates) {
                noteList.clear();
                noteIds.clear();
                if (notesWithIds != null) {
                    noteIds.putAll(notesWithIds);
                    noteList.addAll(notesWithIds.keySet());
                    noteAdapter.setNoteIds(noteIds);

                    // Actualizar estados de fijado
                    for (Map.Entry<String, Boolean> entry : pinnedStates.entrySet()) {
                        noteAdapter.setPinnedStatus(entry.getKey(), entry.getValue());
                    }

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


    //Muestra el menú popup cuando se hace clic en el botón FAB
    //Infla el menú con las opciones disponibles
    private void showPopupMenu(View view) {
        PopupMenu popupMenu = new PopupMenu(new ContextThemeWrapper(this, R.style.CustomPopupMenu), view);
        MenuInflater inflater = popupMenu.getMenuInflater();
        inflater.inflate(R.menu.menu_main, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(this);
        popupMenu.show();
    }

    @Override
    //Maneja los clicks en las opciones del menú popup
    //Implementa las acciones para crear nota, cerrar sesión y eliminar todas las notas
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

    //Añade una nueva nota a Firebase
    //Actualiza la interfaz con la nueva nota
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

    //metodo cerrar sesion
    private void cerrarSesion() {
        authHelper.signOut();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    //metodo borrar todas las notas del usuario
    private void deleteAllNotes() {
        FirebaseUser currentUser = authHelper.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "No hay usuario autenticado", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("notes")
                .whereEqualTo("user_id", currentUser.getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    WriteBatch batch = db.batch();

                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        batch.delete(document.getReference());
                    }

                    batch.commit().addOnSuccessListener(aVoid -> {
                        noteList.clear();
                        noteIds.clear();
                        noteAdapter.setNoteIds(noteIds);
                        noteAdapter.clearOriginalList(); // Añadir este método
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
    //Filtran las notas según el texto introducido
    public boolean onQueryTextSubmit(String query) {
        if (noteAdapter != null) {
            noteAdapter.filter(query);
        }
        return false;
    }

    @Override
    //Filtran las notas según el texto introducido
    public boolean onQueryTextChange(String newText) {
        if (noteAdapter != null) {
            noteAdapter.filter(newText);
        }
        return false;
    }

    @Override
    //Maneja el resultado de crear una nueva nota
    //Añade la nota nueva si la creación fue exitosa
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