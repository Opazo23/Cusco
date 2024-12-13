package com.example.cusco;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
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
                                    .addOnSuccessListener(aVoid -> {
                                        // Después de guardar los datos del usuario, enviamos el email de bienvenida
                                        sendWelcomeEmail(email, username, new OnEmailSentListener() {
                                            @Override
                                            public void onSuccess() {
                                                listener.onSuccess();
                                            }

                                            @Override
                                            public void onFailure(String error) {
                                                // Aún consideramos el registro exitoso aunque falle el email
                                                listener.onSuccess();
                                            }
                                        });
                                    })
                                    .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
                        }
                    } else {
                        listener.onFailure(task.getException() != null ?
                                task.getException().getMessage() : "Error en el registro");
                    }
                });
    }


    public void unpinNote(String noteId, OnNoteOperationCompleteListener listener) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            listener.onFailure("No hay usuario autenticado");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("order", Long.MAX_VALUE);  // Mover al final
        updates.put("isPinned", false);        // Marcar como no fijada

        db.collection("notes")
                .document(noteId)
                .update(updates)
                .addOnSuccessListener(aVoid -> listener.onSuccess(noteId))
                .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
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
                    noteMap.put("isPinned", false);  // Añadir campo isPinned

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

        Map<String, Object> updates = new HashMap<>();
        updates.put("order", 0);
        updates.put("isPinned", true);

        db.collection("notes")
                .document(noteId)
                .update(updates)
                .addOnSuccessListener(aVoid -> listener.onSuccess(noteId))
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
                    Map<String, Boolean> pinnedStates = new HashMap<>();  // Nuevo mapa para estados de fijado

                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        String content = document.getString("content");
                        Boolean isPinned = document.getBoolean("isPinned");
                        if (content != null) {
                            notesWithIds.put(content, document.getId());
                            if (isPinned != null) {
                                pinnedStates.put(document.getId(), isPinned);
                            }
                        }
                    }
                    listener.onSuccess(notesWithIds, pinnedStates);  // Modificar para incluir estados de fijado
                })
                .addOnFailureListener(e -> {
                    if (e != null && e.getMessage() != null) {
                        listener.onFailure(e.getMessage());
                    } else {
                        listener.onSuccess(new LinkedHashMap<>(), new HashMap<>());
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

        // Primero verificamos el estado actual de la nota
        db.collection("notes")
                .document(noteId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    Boolean isPinned = documentSnapshot.getBoolean("isPinned");
                    if (isPinned != null && isPinned) {
                        listener.onFailure("No se puede mover una nota fijada");
                        return;
                    }

                    // Obtener todas las notas no fijadas para actualizar el orden
                    db.collection("notes")
                            .whereEqualTo("user_id", currentUser.getUid())
                            .whereEqualTo("isPinned", false)
                            .get()
                            .addOnSuccessListener(queryDocumentSnapshots -> {
                                WriteBatch batch = db.batch();
                                List<DocumentSnapshot> unfixedNotes = queryDocumentSnapshots.getDocuments();

                                // Calcular el nuevo orden basado en la posición deseada
                                long newOrder = newPosition;

                                // Actualizar la nota con su nueva posición
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("order", newOrder);
                                updates.put("isPinned", false);

                                db.collection("notes")
                                        .document(noteId)
                                        .update(updates)
                                        .addOnSuccessListener(aVoid -> listener.onSuccess(noteId))
                                        .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
                            })
                            .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
                })
                .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
    }

    public void resetPassword(String email, OnPasswordResetListener listener) {
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        listener.onSuccess();
                    } else {
                        listener.onFailure(task.getException() != null ?
                                task.getException().getMessage() : "Error al enviar el email");
                    }
                });
    }


    public void sendWelcomeEmail(String email, String username, OnEmailSentListener listener) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.sendEmailVerification()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Guardar en Firestore que el email de bienvenida fue enviado
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("welcome_email_sent", true);
                            updates.put("welcome_email_sent_at", java.time.Instant.now().toString());

                            db.collection("users")
                                    .document(user.getUid())
                                    .update(updates)
                                    .addOnSuccessListener(aVoid -> listener.onSuccess())
                                    .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
                        } else {
                            listener.onFailure(task.getException() != null ?
                                    task.getException().getMessage() :
                                    "Error al enviar el email de bienvenida");
                        }
                    });
        } else {
            listener.onFailure("No hay usuario autenticado");
        }
    }

    // Añade esta interface al final de la clase
    public interface OnEmailSentListener {
        void onSuccess();
        void onFailure(String error);
    }

    // Añade esta interface al final de la clase
    public interface OnPasswordResetListener {
        void onSuccess();
        void onFailure(String error);
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
        void onSuccess(Map<String, String> notesWithIds, Map<String, Boolean> pinnedStates);
        void onFailure(String error);
    }
}