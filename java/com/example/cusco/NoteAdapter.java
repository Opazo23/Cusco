package com.example.cusco;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {
    private List<String> noteList;
    private List<String> noteList_original;
    private Map<String, String> noteIds;
    private OnItemClickListener onItemClickListener;
    private Context context;
    private FirebaseAuthHelper authHelper;

    public NoteAdapter(List<String> noteList, FirebaseAuthHelper authHelper) {
        this.noteList = noteList;
        noteList_original = new ArrayList<>();
        noteList_original.addAll(noteList);
        this.noteIds = new HashMap<>();
        this.authHelper = authHelper;
    }

    public void setNoteIds(Map<String, String> noteIds) {
        this.noteIds = noteIds;
        Log.d("FIREBASE_NOTE", "IDs de notas actualizados en adapter: " + noteIds.size());
    }

    public interface OnItemClickListener {
        void onItemClick(View v);
        void onItemClick(String string);
        void onEditClick(String note, int position);
        void onPinClick(int position);
        void onDeleteClick(int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.note_item, parent, false);
        return new NoteViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        String note = noteList.get(position);
        holder.noteTextView.setText(note);
        holder.itemView.setOnClickListener(v -> showPopupMenu(v, position, note));
    }

    private void showPopupMenu(View view, int position, String note) {
        Log.d("FIREBASE_NOTE", "ShowPopupMenu iniciado");
        PopupMenu popup = new PopupMenu(new ContextThemeWrapper(context, R.style.CustomPopupMenu), view);
        popup.getMenuInflater().inflate(R.menu.note_context_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_edit_note) {
                Log.d("FIREBASE_NOTE", "Opción editar seleccionada");
                if (onItemClickListener != null) {
                    String noteId = noteIds.get(note);
                    onItemClickListener.onEditClick(note, position);
                }
                return true;
            } else if (itemId == R.id.action_pin_note) {
                Log.d("FIREBASE_NOTE", "Opción fijar seleccionada");
                if (onItemClickListener != null) {
                    onItemClickListener.onPinClick(position);
                }
                return true;
            } else if (itemId == R.id.action_delete_note) {
                Log.d("FIREBASE_NOTE", "Opción borrar seleccionada");
                if (onItemClickListener != null) {
                    String noteId = noteIds.get(note);
                    if (noteId != null) {
                        onItemClickListener.onDeleteClick(position);
                    }
                }
                return true;
            }
            return false;
        });

        popup.show();
    }

    @Override
    public int getItemCount() {
        return noteList.size();
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView noteTextView;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            noteTextView = itemView.findViewById(R.id.noteTextView);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateNote(int position, String newText) {
        if (position >= 0 && position < noteList.size()) {
            String oldNote = noteList.get(position);
            // Actualizar la lista principal
            noteList.set(position, newText);

            // Actualizar la lista original
            noteList_original.clear();
            noteList_original.addAll(noteList);

            // Actualizar los IDs
            String noteId = noteIds.remove(oldNote);
            if (noteId != null) {
                noteIds.put(newText, noteId);
            }
            notifyDataSetChanged();
        }
    }

    public void addToOriginalList(String note) {
        noteList_original.add(note);
    }

    public void updateOriginalList(List<String> newList) {
        noteList_original.clear();
        noteList_original.addAll(newList);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void filter(String text) {
        if (text.isEmpty() || text.length() == 0) {
            // Si no hay texto de búsqueda, restaurar la lista original
            noteList.clear();
            noteList.addAll(noteList_original);
        } else {
            // Si hay texto de búsqueda, filtrar las notas
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