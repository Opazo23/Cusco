package com.example.cusco;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {
    // Variables para manejar las notas y sus estados
    private List<String> noteList;              // Lista actual de notas
    private List<String> noteList_original;     // Lista original para búsquedas
    private Map<String, String> noteIds;        // Mapa de notas a sus IDs
    private Map<String, Boolean> pinnedNotes;   // Mapa para controlar notas fijadas
    private OnItemClickListener onItemClickListener;
    private Context context;
    private FirebaseAuthHelper authHelper;

    // Constructor: Inicializa las listas y mapas necesarios
    public NoteAdapter(List<String> noteList, FirebaseAuthHelper authHelper) {
        this.noteList = noteList;
        noteList_original = new ArrayList<>();
        noteList_original.addAll(noteList);
        this.noteIds = new HashMap<>();
        this.pinnedNotes = new HashMap<>();
        this.authHelper = authHelper;
    }

    // Actualiza los IDs de las notas en el adaptador
    public void setNoteIds(Map<String, String> noteIds) {
        this.noteIds = noteIds;
        Log.d("FIREBASE_NOTE", "IDs de notas actualizados en adapter: " + noteIds.size());
    }

    // Actualiza el estado de fijado de una nota específica
    @SuppressLint("NotifyDataSetChanged")
    public void setPinnedStatus(String noteId, boolean isPinned) {
        if (noteId != null) {
            pinnedNotes.put(noteId, isPinned);
            notifyDataSetChanged();
        }
    }

    // Interface para manejar diferentes eventos de click en las notas
    public interface OnItemClickListener {
        void onItemClick(View v);
        void onItemClick(String string);
        void onEditClick(String note, int position);
        void onPinClick(int position);
        void onUnpinClick(int position);
        void onDeleteClick(int position);
    }

    // Establece el listener para los eventos de click
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    // Crea una nueva vista para un elemento de la lista
    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.note_item, parent, false);
        return new NoteViewHolder(itemView);
    }

    // Vincula los datos con la vista para cada elemento
    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        String note = noteList.get(position);
        holder.noteTextView.setText(note);

        // Controla la visibilidad del icono de pin
        String noteId = noteIds.get(note);
        Boolean isPinned = pinnedNotes.get(noteId);
        holder.pinIcon.setVisibility(isPinned != null && isPinned ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> showPopupMenu(v, position, note));
    }

    // Muestra el menú contextual para una nota
    private void showPopupMenu(View view, int position, String note) {
        Log.d("FIREBASE_NOTE", "ShowPopupMenu iniciado");
        PopupMenu popup = new PopupMenu(new ContextThemeWrapper(context, R.style.CustomPopupMenu), view);
        popup.getMenuInflater().inflate(R.menu.note_context_menu, popup.getMenu());

        // Gestiona las opciones del menú según el estado de fijado
        String noteId = noteIds.get(note);
        Boolean isPinned = pinnedNotes.get(noteId);

        popup.getMenu().findItem(R.id.action_pin_note).setVisible(isPinned == null || !isPinned);
        popup.getMenu().findItem(R.id.action_unpin_note).setVisible(isPinned != null && isPinned);

        // Maneja los clicks en las opciones del menú
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_edit_note) {
                if (onItemClickListener != null) {
                    onItemClickListener.onEditClick(note, position);
                }
                return true;
            } else if (itemId == R.id.action_pin_note) {
                if (onItemClickListener != null) {
                    onItemClickListener.onPinClick(position);
                }
                return true;
            } else if (itemId == R.id.action_unpin_note) {
                if (onItemClickListener != null) {
                    onItemClickListener.onUnpinClick(position);
                }
                return true;
            } else if (itemId == R.id.action_delete_note) {
                if (onItemClickListener != null && noteId != null) {
                    onItemClickListener.onDeleteClick(position);
                }
                return true;
            }
            return false;
        });

        popup.show();
    }

    // Retorna el número total de elementos en la lista
    @Override
    public int getItemCount() {
        return noteList.size();
    }

    // ViewHolder para mantener las referencias a las vistas de cada elemento
    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView noteTextView;
        ImageView pinIcon;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            noteTextView = itemView.findViewById(R.id.noteTextView);
            pinIcon = itemView.findViewById(R.id.pinIcon);
        }
    }

    // Verifica si una nota está fijada
    public Boolean isPinned(String noteId) {
        return pinnedNotes.get(noteId);
    }

    // Actualiza el contenido de una nota existente
    @SuppressLint("NotifyDataSetChanged")
    public void updateNote(int position, String newText) {
        if (position >= 0 && position < noteList.size()) {
            String oldNote = noteList.get(position);
            noteList.set(position, newText);
            noteList_original.clear();
            noteList_original.addAll(noteList);
            String noteId = noteIds.remove(oldNote);
            if (noteId != null) {
                noteIds.put(newText, noteId);
            }
            notifyDataSetChanged();
        }
    }

    // Añade una nota a la lista original
    public void addToOriginalList(String note) {
        noteList_original.add(note);
    }

    // Actualiza la lista original completa
    public void updateOriginalList(List<String> newList) {
        noteList_original.clear();
        noteList_original.addAll(newList);
    }

    // Filtra las notas según el texto de búsqueda
    @SuppressLint("NotifyDataSetChanged")
    public void filter(String text) {
        if (text.isEmpty() || text.length() == 0) {
            noteList.clear();
            noteList.addAll(noteList_original);
        } else {
            noteList.clear();
            for (String note : noteList_original) {
                if (note.toLowerCase().trim().contains(text.toLowerCase().trim())) {
                    noteList.add(note);
                }
            }
        }
        notifyDataSetChanged();
    }
}