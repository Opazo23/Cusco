package com.example.cusco;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FirebaseAuthHelper {
    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;

    public FirebaseAuthHelper() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    // Método para registrar un nuevo usuario
    public void registerUser(String email, String username, String password, OnRegisterCompleteListener listener) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            Map<String, Object> userMap = new HashMap<>();
                            userMap.put("username", username);
                            userMap.put("email", email);
                            userMap.put("created_at", java.time.Instant.now().toString());

                            db.collection("users")
                                    .document(user.getUid())
                                    .set(userMap)
                                    .addOnSuccessListener(aVoid -> listener.onSuccess())
                                    .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
                        }
                    } else {
                        listener.onFailure(task.getException() != null ?
                                task.getException().getMessage() : "Error en el registro");
                    }
                });
    }



    public void searchNotes(String searchText, OnNotesLoadedListener listener) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            listener.onFailure("No hay usuario autenticado");
            return;
        }

        db.collection("notes")
                .whereEqualTo("user_id", currentUser.getUid())
                .whereGreaterThanOrEqualTo("content", searchText)
                .whereLessThanOrEqualTo("content", searchText + "\uf8ff")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Map<String, String> notesWithIds = new LinkedHashMap<>();
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        String content = document.getString("content");
                        if (content != null) {
                            notesWithIds.put(content, document.getId());
                        }
                    }
                    listener.onSuccess(notesWithIds);
                })
                .addOnFailureListener(e -> {
                    if (e.getMessage() != null && e.getMessage().contains("requires an index")) {
                        listener.onFailure("Se está configurando la búsqueda, por favor espera unos minutos");
                    } else {
                        listener.onFailure(e.getMessage());
                    }
                });
    }



    // Método para iniciar sesión
    public void loginUser(String email, String password, OnLoginCompleteListener listener) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        listener.onSuccess();
                    } else {
                        listener.onFailure(task.getException() != null ?
                                task.getException().getMessage() : "Error en el inicio de sesión");
                    }
                });
    }

    // Método para añadir una nota a Firestore
    public void addNoteToFirestore(String note, OnNoteOperationCompleteListener listener) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            listener.onFailure("No hay usuario autenticado");
            return;
        }

        // Obtener el mayor orden actual
        db.collection("notes")
                .whereEqualTo("user_id", currentUser.getUid())
                .orderBy("order", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    long nextOrder = 0;
                    if (!queryDocumentSnapshots.isEmpty()) {
                        Long currentOrder = queryDocumentSnapshots.getDocuments().get(0).getLong("order");
                        nextOrder = currentOrder != null ? currentOrder + 1 : 0;
                    }

                    Map<String, Object> noteMap = new HashMap<>();
                    noteMap.put("user_id", currentUser.getUid());
                    noteMap.put("content", note);
                    noteMap.put("created_at", java.time.Instant.now().toString());
                    noteMap.put("order", nextOrder);

                    db.collection("notes")
                            .add(noteMap)
                            .addOnSuccessListener(documentReference ->
                                    listener.onSuccess(documentReference.getId()))
                            .addOnFailureListener(e ->
                                    listener.onFailure(e.getMessage()));
                })
                .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
    }

    // Método para actualizar una nota
    public void updateNoteInFirestore(String noteId, String newContent, OnNoteOperationCompleteListener listener) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            listener.onFailure("No hay usuario autenticado");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("content", newContent);
        updates.put("updated_at", java.time.Instant.now().toString());

        db.collection("notes")
                .document(noteId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    listener.onSuccess(noteId);
                })
                .addOnFailureListener(e -> {
                    listener.onFailure(e.getMessage());
                });
    }

    // Método para actualizar el orden de una nota
    public void updateNoteOrder(String noteId, OnNoteOperationCompleteListener listener) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            listener.onFailure("No hay usuario autenticado");
            return;
        }

        // Primero obtenemos todas las notas para reordenarlas
        db.collection("notes")
                .whereEqualTo("user_id", currentUser.getUid())
                .orderBy("order", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    // Incrementar el orden de todas las notas en 1
                    List<DocumentSnapshot> documents = queryDocumentSnapshots.getDocuments();
                    for (int i = 0; i < documents.size(); i++) {
                        DocumentSnapshot doc = documents.get(i);
                        if (!doc.getId().equals(noteId)) {
                            db.collection("notes")
                                    .document(doc.getId())
                                    .update("order", i + 1);
                        }
                    }

                    // Poner la nota seleccionada al principio (orden 0)
                    db.collection("notes")
                            .document(noteId)
                            .update("order", 0)
                            .addOnSuccessListener(aVoid -> listener.onSuccess(noteId))
                            .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
                })
                .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
    }

    // Método para cargar las notas
    public void loadUserNotes(OnNotesLoadedListener listener) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            listener.onFailure("No hay usuario autenticado");
            return;
        }

        db.collection("notes")
                .whereEqualTo("user_id", currentUser.getUid())
                .orderBy("order", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Map<String, String> notesWithIds = new LinkedHashMap<>();
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        String content = document.getString("content");
                        if (content != null) {
                            notesWithIds.put(content, document.getId());
                        }
                    }
                    // Aquí está el cambio: siempre llamamos a onSuccess, incluso si no hay notas
                    listener.onSuccess(notesWithIds);
                })
                .addOnFailureListener(e -> {
                    // Aquí solo llamamos a onFailure si hay un error real de Firebase
                    if (e != null && e.getMessage() != null) {
                        listener.onFailure(e.getMessage());
                    } else {
                        // Si no hay error específico, simplemente devolvemos un mapa vacío
                        listener.onSuccess(new LinkedHashMap<>());
                    }
                });
    }

    // Método para borrar nota
    public void deleteNoteFromFirestore(String noteId, OnNoteOperationCompleteListener listener) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            listener.onFailure("No hay usuario autenticado");
            return;
        }

        db.collection("notes")
                .document(noteId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    listener.onSuccess(noteId);
                })
                .addOnFailureListener(e -> {
                    listener.onFailure(e.getMessage());
                });
    }

    //drag and drop
    public void updateNoteOrderDragAndDrop(String noteId, int newPosition, OnNoteOperationCompleteListener listener) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            listener.onFailure("No hay usuario autenticado");
            return;
        }

        db.collection("notes")
                .document(noteId)
                .update("order", newPosition)
                .addOnSuccessListener(aVoid -> listener.onSuccess(noteId))
                .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
    }

    // Método para cerrar sesión
    public void signOut() {
        mAuth.signOut();
    }

    // Método para obtener el usuario actual
    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    // Interfaces para los callbacks
    public interface OnRegisterCompleteListener {
        void onSuccess();
        void onFailure(String error);
    }

    public interface OnLoginCompleteListener {
        void onSuccess();
        void onFailure(String error);
    }

    public interface OnNoteOperationCompleteListener {
        void onSuccess(String noteId);
        void onFailure(String error);
    }

    public interface OnNotesLoadedListener {
        void onSuccess(Map<String, String> notesWithIds);
        void onFailure(String error);
    }
}