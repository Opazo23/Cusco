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
    // Instancias principales de Firebase
    private final FirebaseAuth mAuth;        // Para autenticación
    private final FirebaseFirestore db;      // Para base de datos

    // Constructor: Inicializa las instancias de Firebase
    public FirebaseAuthHelper() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    // Registra un nuevo usuario en Firebase
    // Crea la cuenta y guarda información adicional en Firestore
    public void registerUser(String email, String username, String password, OnRegisterCompleteListener listener) {
        // Crea el usuario en Firebase Auth
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Prepara los datos del usuario para Firestore
                            Map<String, Object> userMap = new HashMap<>();
                            userMap.put("username", username);
                            userMap.put("email", email);
                            userMap.put("created_at", java.time.Instant.now().toString());

                            // Guarda los datos en Firestore
                            db.collection("users")
                                    .document(user.getUid())
                                    .set(userMap)
                                    .addOnSuccessListener(aVoid -> {
                                        // Envía email de bienvenida
                                        sendWelcomeEmail(email, username, new OnEmailSentListener() {
                                            @Override
                                            public void onSuccess() {
                                                listener.onSuccess();
                                            }

                                            @Override
                                            public void onFailure(String error) {
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

    // Desancla una nota (quita el pin)
    public void unpinNote(String noteId, OnNoteOperationCompleteListener listener) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            listener.onFailure("No hay usuario autenticado");
            return;
        }

        // Obtener todas las notas no fijadas para calcular el nuevo orden
        db.collection("notes")
                .whereEqualTo("user_id", currentUser.getUid())
                .whereEqualTo("isPinned", false)
                .orderBy("order", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    long newOrder;
                    if (queryDocumentSnapshots.isEmpty()) {
                        newOrder = 0;
                    } else {
                        DocumentSnapshot lastDoc = queryDocumentSnapshots.getDocuments().get(0);
                        Long lastOrder = lastDoc.getLong("order");
                        newOrder = (lastOrder != null ? lastOrder : 0) + 1;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("order", newOrder);  // Nuevo orden al final de la lista
                    updates.put("isPinned", false);  // Quitar fijado

                    db.collection("notes")
                            .document(noteId)
                            .update(updates)
                            .addOnSuccessListener(aVoid -> listener.onSuccess(noteId))
                            .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
                })
                .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
    }

    // Inicia sesión con email y contraseña
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

    // Añade una nueva nota a Firestore
    public void addNoteToFirestore(String note, OnNoteOperationCompleteListener listener) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            listener.onFailure("No hay usuario autenticado");
            return;
        }

        // Obtiene el siguiente orden para la nota
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

                    // Prepara los datos de la nota
                    Map<String, Object> noteMap = new HashMap<>();
                    noteMap.put("user_id", currentUser.getUid());
                    noteMap.put("content", note);
                    noteMap.put("created_at", java.time.Instant.now().toString());
                    noteMap.put("order", nextOrder);
                    noteMap.put("isPinned", false);

                    // Guarda la nota en Firestore
                    db.collection("notes")
                            .add(noteMap)
                            .addOnSuccessListener(documentReference ->
                                    listener.onSuccess(documentReference.getId()))
                            .addOnFailureListener(e ->
                                    listener.onFailure(e.getMessage()));
                })
                .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
    }

    // Actualiza el contenido de una nota existente
    public void updateNoteInFirestore(String noteId, String newContent, OnNoteOperationCompleteListener listener) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            listener.onFailure("No hay usuario autenticado");
            return;
        }

        // Prepara los datos a actualizar
        Map<String, Object> updates = new HashMap<>();
        updates.put("content", newContent);
        updates.put("updated_at", java.time.Instant.now().toString());

        // Actualiza la nota en Firestore
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

    // Actualiza el orden de una nota (fijar nota)
    public void updateNoteOrder(String noteId, String noteContent, OnNoteOperationCompleteListener listener) {
        FirebaseUser currentUser = getCurrentUser();
        if (currentUser == null) {
            listener.onFailure("No hay usuario autenticado");
            return;
        }

        // Primero verificar que la nota existe y tiene el contenido correcto
        db.collection("notes")
                .document(noteId)
                .get()
                .addOnSuccessListener(noteDoc -> {
                    if (!noteDoc.exists()) {
                        listener.onFailure("Nota no encontrada");
                        return;
                    }

                    String currentContent = noteDoc.getString("content");
                    if (!noteContent.equals(currentContent)) {
                        listener.onFailure("ID de nota no coincide con el contenido");
                        return;
                    }

                    // Primero actualizar la nota que queremos fijar
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("isPinned", true);
                    updates.put("order", 0);

                    db.collection("notes")
                            .document(noteId)
                            .update(updates)
                            .addOnSuccessListener(aVoid -> listener.onSuccess(noteId))
                            .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
                })
                .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
    }

    public void loadUserNotes(OnNotesLoadedListener listener) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            listener.onFailure("No hay usuario autenticado");
            return;
        }

        db.collection("notes")
                .whereEqualTo("user_id", currentUser.getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Map<String, String> notesWithIds = new LinkedHashMap<>();
                    Map<String, Boolean> pinnedStates = new HashMap<>();
                    List<DocumentSnapshot> pinnedNotes = new ArrayList<>();
                    List<DocumentSnapshot> unpinnedNotes = new ArrayList<>();

                    // Separar notas fijadas y no fijadas
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        Boolean isPinned = document.getBoolean("isPinned");
                        if (isPinned != null && isPinned) {
                            pinnedNotes.add(document);
                        } else {
                            unpinnedNotes.add(document);
                        }
                    }

                    // Ordenar notas fijadas por orden
                    pinnedNotes.sort((a, b) -> {
                        Long orderA = a.getLong("order");
                        Long orderB = b.getLong("order");
                        if (orderA == null) orderA = 0L;
                        if (orderB == null) orderB = 0L;
                        return orderA.compareTo(orderB);
                    });

                    // Ordenar notas no fijadas por orden
                    unpinnedNotes.sort((a, b) -> {
                        Long orderA = a.getLong("order");
                        Long orderB = b.getLong("order");
                        if (orderA == null) orderA = 0L;
                        if (orderB == null) orderB = 0L;
                        return orderA.compareTo(orderB);
                    });

                    // Primero añadir notas fijadas
                    for (DocumentSnapshot document : pinnedNotes) {
                        String content = document.getString("content");
                        if (content != null) {
                            notesWithIds.put(content, document.getId());
                            pinnedStates.put(document.getId(), true);
                        }
                    }

                    // Luego añadir notas no fijadas
                    for (DocumentSnapshot document : unpinnedNotes) {
                        String content = document.getString("content");
                        if (content != null) {
                            notesWithIds.put(content, document.getId());
                            pinnedStates.put(document.getId(), false);
                        }
                    }

                    listener.onSuccess(notesWithIds, pinnedStates);
                })
                .addOnFailureListener(e -> {
                    if (e != null && e.getMessage() != null) {
                        listener.onFailure(e.getMessage());
                    } else {
                        listener.onSuccess(new LinkedHashMap<>(), new HashMap<>());
                    }
                });
    }

    // Elimina una nota específica
    public void deleteNoteFromFirestore(String noteId, OnNoteOperationCompleteListener listener) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            listener.onFailure("No hay usuario autenticado");
            return;
        }

        // Elimina la nota de Firestore
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

    // Actualiza el orden de las notas en drag and drop
    public void updateNoteOrderDragAndDrop(String noteId, int newPosition, OnNoteOperationCompleteListener listener) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            listener.onFailure("No hay usuario autenticado");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("order", newPosition);
        updates.put("isPinned", false);

        db.collection("notes")
                .document(noteId)
                .update(updates)
                .addOnSuccessListener(aVoid -> listener.onSuccess(noteId))
                .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
    }

    // Envía email para restablecer contraseña
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

    // Envía email de bienvenida y verificación
    public void sendWelcomeEmail(String email, String username, OnEmailSentListener listener) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.sendEmailVerification()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Registra el envío del email en Firestore
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

    // Cierra la sesión del usuario
    public void signOut() {
        mAuth.signOut();
    }

    // Obtiene el usuario actualmente autenticado
    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    // Interfaces para los callbacks
    public interface OnEmailSentListener {
        void onSuccess();
        void onFailure(String error);
    }

    public interface OnPasswordResetListener {
        void onSuccess();
        void onFailure(String error);
    }

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