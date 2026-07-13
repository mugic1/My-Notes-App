package com.example.notesapp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class AddNoteActivity extends Activity {
    private EditText editTextTitle;
    private EditText editTextContent;
    private Button buttonSave;
    private NotesDatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_note);

        editTextTitle = findViewById(R.id.editTextTitle);
        editTextContent = findViewById(R.id.editTextContent);
        buttonSave = findViewById(R.id.buttonSave);
        dbHelper = new NotesDatabaseHelper(this);

        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String title = editTextTitle.getText().toString().trim();
                String content = editTextContent.getText().toString().trim();

                if (title.isEmpty()) {
                    Toast.makeText(AddNoteActivity.this, "Please enter a title", Toast.LENGTH_SHORT).show();
                    return;
                }

                dbHelper.addNote(title, content);
                Toast.makeText(AddNoteActivity.this, "Note saved!", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
}