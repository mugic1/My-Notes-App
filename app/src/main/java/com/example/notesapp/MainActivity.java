package com.example.notesapp;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends Activity {
    private ListView listViewNotes;
    private Button buttonAddNote, buttonSettings;
    private EditText editTextTitle, editTextContent;
    private LinearLayout layoutPlaceholder, layoutEditor, sidebarLayout, rootLayout;
    private FrameLayout rightPanelRoot;
    private View verticalSeparator, titleSeparator;
    private TextView textPlaceholderTitle, textPlaceholderSub;
    
    // Pro Features Buttons Layout
    private LinearLayout layoutEditorToolbar;
    private Button buttonHighlight, buttonAddTask, buttonSetReminder;
    
    private NotesDatabaseHelper dbHelper;
    private List<Note> notes;
    private ArrayAdapter<Note> adapter;
    private int selectedNoteId = -1;

    private SharedPreferences prefs;
    private String currentTheme, currentFontTitle, currentFontContent, currentFontApp, currentLayout, currentFontSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        if (getActionBar() != null) {
            getActionBar().hide();
        }

        prefs = getSharedPreferences("EditorConfigs", MODE_PRIVATE);

        rootLayout = findViewById(R.id.rootLayout);
        sidebarLayout = findViewById(R.id.sidebarLayout);
        listViewNotes = findViewById(R.id.listViewNotes);
        buttonAddNote = findViewById(R.id.buttonAddNote);
        buttonSettings = findViewById(R.id.buttonSettings);
        editTextTitle = findViewById(R.id.editTextTitle);
        editTextContent = findViewById(R.id.editTextContent);
        layoutPlaceholder = findViewById(R.id.layoutPlaceholder);
        layoutEditor = findViewById(R.id.layoutEditor);
        rightPanelRoot = findViewById(R.id.rightPanelRoot);
        verticalSeparator = findViewById(R.id.verticalSeparator);
        titleSeparator = findViewById(R.id.titleSeparator);
        textPlaceholderTitle = findViewById(R.id.textPlaceholderTitle);
        textPlaceholderSub = findViewById(R.id.textPlaceholderSub);

        // Feature UI Initialisations
        layoutEditorToolbar = findViewById(R.id.layoutEditorToolbar);
        buttonHighlight = findViewById(R.id.buttonHighlight);
        buttonAddTask = findViewById(R.id.buttonAddTask);
        buttonSetReminder = findViewById(R.id.buttonSetReminder);

        dbHelper = new NotesDatabaseHelper(this);
        showPlaceholderState();

        applySavedCustomizations();

        editTextTitle.setCursorVisible(false);
        editTextContent.setCursorVisible(false);

        View.OnTouchListener editTextTouchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (v instanceof EditText) {
                        ((EditText) v).setCursorVisible(true);
                    }
                }
                return false; 
            }
        };
        editTextTitle.setOnTouchListener(editTextTouchListener);
        editTextContent.setOnTouchListener(editTextTouchListener);

        buttonAddNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoSaveCurrentNote();
                selectedNoteId = -1;
                editTextTitle.setText("");
                editTextContent.setText("");
                layoutPlaceholder.setVisibility(View.GONE);
                layoutEditor.setVisibility(View.VISIBLE);
                closeKeyboardAndClearFocus();
            }
        });

        listViewNotes.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                autoSaveCurrentNote();
                Note selectedNote = (Note) notes.get(position);
                selectedNoteId = selectedNote.getId();
                
                // Rich Text support to render highlights correctly
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    editTextTitle.setText(Html.fromHtml(selectedNote.getTitle(), Html.FROM_HTML_MODE_LEGACY));
                    editTextContent.setText(Html.fromHtml(selectedNote.getContent(), Html.FROM_HTML_MODE_LEGACY));
                } else {
                    editTextTitle.setText(Html.fromHtml(selectedNote.getTitle()));
                    editTextContent.setText(Html.fromHtml(selectedNote.getContent()));
                }
                
                layoutPlaceholder.setVisibility(View.GONE);
                layoutEditor.setVisibility(View.VISIBLE);
                closeKeyboardAndClearFocus();
            }
        });

        buttonSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMasterSettingsDialog();
            }
        });

        // 🎨 FEATURE 1: Text Highlighter Implementation
        buttonHighlight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int startSelection = editTextContent.getSelectionStart();
                int endSelection = editTextContent.getSelectionEnd();
                
                if (startSelection != endSelection) {
                    Spannable spannable = editTextContent.getText();
                    // Basic Neon yellow highlighter tint
                    spannable.setSpan(new BackgroundColorSpan(Color.parseColor("#FFFF00")), 
                            startSelection, endSelection, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    Toast.makeText(MainActivity.this, "Highlight karne ke liye text select karein", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 📝 FEATURE 2: Task Checklist Bullet Tracker
        buttonAddTask.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int currentCursorPos = editTextContent.getSelectionStart();
                String currentText = editTextContent.getText().toString();
                
                // Injects a modern, visually clean checklist marker
                String checkboxBullet = "\n▢ [ ] "; 
                SpannableStringBuilder ssb = new SpannableStringBuilder(currentText);
                ssb.insert(currentCursorPos, checkboxBullet);
                
                editTextContent.setText(ssb);
                editTextContent.setSelection(currentCursorPos + checkboxBullet.length());
            }
        });

        // ⏰ FEATURE 3: Scheduling Reminders Engine
        buttonSetReminder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Calendar currentTime = Calendar.getInstance();
                int hour = currentTime.get(Calendar.HOUR_OF_DAY);
                int minute = currentTime.get(Calendar.MINUTE);
                
                TimePickerDialog timePickerDialog = new TimePickerDialog(MainActivity.this, 
                        new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minuteOfHour) {
                        Calendar targetTime = Calendar.getInstance();
                        targetTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        targetTime.set(Calendar.MINUTE, minuteOfHour);
                        targetTime.set(Calendar.SECOND, 0);

                        // Trigger Android system Alarm service
                        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                        Intent intent = new Intent("com.example.notesapp.ACTION_REMINDER_NOTIFICATION");
                        intent.putExtra("note_title", editTextTitle.getText().toString().trim());
                        
                        int requestCode = selectedNoteId != -1 ? selectedNoteId : 999;
                        PendingIntent pendingIntent = PendingIntent.getBroadcast(MainActivity.this, 
                                requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        
                        if (alarmManager != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTime.getTimeInMillis(), pendingIntent);
                            } else {
                                alarmManager.set(AlarmManager.RTC_WAKEUP, targetTime.getTimeInMillis(), pendingIntent);
                            }
                            Toast.makeText(MainActivity.this, "Reminder set for " + hourOfDay + ":" + minuteOfHour, Toast.LENGTH_LONG).show();
                        }
                    }
                }, hour, minute, true);
                timePickerDialog.show();
            }
        });

        refreshNotes();
    }

    private Typeface getTypefaceByName(String name) {
        if ("Monospace".equals(name)) return Typeface.create("monospace", Typeface.NORMAL);
        if ("Serif".equals(name)) return Typeface.create("serif", Typeface.NORMAL);
        
        if ("Kalam (Hindi Hand)".equals(name)) {
            try { return Typeface.createFromAsset(getAssets(), "fonts/kalam.ttf"); } 
            catch (Exception e) { return Typeface.create("casual", Typeface.NORMAL); }
        }
        if ("Amita (Hindi Casual)".equals(name)) {
            try { return Typeface.createFromAsset(getAssets(), "fonts/amita.ttf"); } 
            catch (Exception e) { return Typeface.create("casual", Typeface.NORMAL); }
        }
        if ("Yatra (Hindi Artistic)".equals(name)) {
            try { return Typeface.createFromAsset(getAssets(), "fonts/yatra.ttf"); } 
            catch (Exception e) { return Typeface.create("casual", Typeface.NORMAL); }
        }
        
        return Typeface.create("sans-serif", Typeface.NORMAL);
    }

    private AlertDialog.Builder getThemedDialogBuilder() {
        if ("Light Neo".equals(currentTheme) || "Solarized Light".equals(currentTheme)) {
            return new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
        } else {
            return new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        }
    }

    private void applySavedCustomizations() {
        currentTheme = prefs.getString("theme", "Obsidian Dark");
        currentFontTitle = prefs.getString("font_title", "Sans-Serif");
        currentFontContent = prefs.getString("font_content", "Sans-Serif");
        currentFontApp = prefs.getString("font_app", "Sans-Serif");
        currentLayout = prefs.getString("layout", "Left Sidebar");
        currentFontSize = prefs.getString("fontSize", "Medium");

        rootLayout.removeAllViews();
        if ("Right Sidebar".equals(currentLayout)) {
            rootLayout.addView(rightPanelRoot);
            rootLayout.addView(verticalSeparator);
            rootLayout.addView(sidebarLayout);
        } else {
            rootLayout.addView(sidebarLayout);
            rootLayout.addView(verticalSeparator);
            rootLayout.addView(rightPanelRoot);
        }

        editTextTitle.setTypeface(getTypefaceByName(currentFontTitle), Typeface.BOLD);
        editTextContent.setTypeface(getTypefaceByName(currentFontContent));
        
        Typeface appTf = getTypefaceByName(currentFontApp);
        buttonAddNote.setTypeface(appTf, Typeface.BOLD);
        buttonSettings.setTypeface(appTf);
        textPlaceholderTitle.setTypeface(appTf, Typeface.BOLD);
        textPlaceholderSub.setTypeface(appTf);
        
        // Toolbar UI Typography Bind
        buttonHighlight.setTypeface(appTf, Typeface.BOLD);
        buttonAddTask.setTypeface(appTf, Typeface.BOLD);
        buttonSetReminder.setTypeface(appTf, Typeface.BOLD);

        if ("Small".equals(currentFontSize)) editTextContent.setTextSize(14);
        else if ("Large".equals(currentFontSize)) editTextContent.setTextSize(22);
        else editTextContent.setTextSize(17);

        switch (currentTheme) {
            case "Light Neo":
                setAppThemeColors("#FAFAFA", "#F4F4F5", "#E4E4E7", "#18181B", "#A1A1AA", "#27272A", "#D4D4D8", "#71717A", "#E4E4E7");
                setToolbarColors("#E4E4E7", "#27272A");
                break;
            case "Solarized Light":
                setAppThemeColors("#FDF6E3", "#EEE8D5", "#93A1A1", "#586E75", "#93A1A1", "#657B83", "#93A1A1", "#586E75", "#EEE8D5");
                setToolbarColors("#EEE8D5", "#586E75");
                break;
            case "Cyberpunk Neon":
                setAppThemeColors("#030712", "#111827", "#F43F5E", "#F43F5E", "#4B5563", "#34D399", "#1F2937", "#F43F5E", "#1F2937");
                setToolbarColors("#1F2937", "#F43F5E");
                buttonAddNote.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4F46E5")));
                break;
            case "Dracula Classic":
                setAppThemeColors("#282A36", "#21222C", "#44475A", "#FF79C6", "#6272A4", "#F8F8F2", "#44475A", "#BD93F9", "#21222C");
                setToolbarColors("#44475A", "#FF79C6");
                break;
            case "Monokai Pro":
                setAppThemeColors("#2D2A2E", "#221F22", "#403E41", "#FFD866", "#726E75", "#FCFCFA", "#403E41", "#A9DC76", "#221F22");
                setToolbarColors("#403E41", "#FFD866");
                break;
            default: 
                setAppThemeColors("#121316", "#18191E", "#262930", "#FFFFFF", "#4B5563", "#D1D5DB", "#374151", "#9CA3AF", "#262930");
                setToolbarColors("#262930", "#FFFFFF");
                buttonAddNote.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#2563EB")));
                break;
        }
        refreshNotes();
    }

    private void setAppThemeColors(String bg, String sideBg, String sep, String tColor, String tHint, String cColor, String pTitle, String bText, String bBg) {
        int colorBg = Color.parseColor(bg);
        rootLayout.setBackgroundColor(colorBg);
        rightPanelRoot.setBackgroundColor(colorBg);
        sidebarLayout.setBackgroundColor(Color.parseColor(sideBg));
        
        int separatorColor = Color.parseColor(sep);
        verticalSeparator.setBackgroundColor(separatorColor);
        titleSeparator.setBackgroundColor(separatorColor);
        
        editTextTitle.setTextColor(Color.parseColor(tColor));
        editTextTitle.setHintTextColor(Color.parseColor(tHint));
        editTextContent.setTextColor(Color.parseColor(cColor));
        editTextContent.setHintTextColor(Color.parseColor(tHint));
        
        textPlaceholderTitle.setTextColor(Color.parseColor(pTitle));
        textPlaceholderSub.setTextColor(Color.parseColor(pTitle));
        
        buttonAddNote.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(tColor)));
        buttonAddNote.setTextColor(colorBg);
        
        buttonSettings.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(bBg)));
        buttonSettings.setTextColor(Color.parseColor(bText));
    }

    private void setToolbarColors(String toolbarBg, String itemTextColor) {
        layoutEditorToolbar.setBackgroundColor(Color.parseColor(toolbarBg));
        int textClr = Color.parseColor(itemTextColor);
        buttonHighlight.setTextColor(textClr);
        buttonAddTask.setTextColor(textClr);
        buttonSetReminder.setTextColor(textClr);
    }

    private void showMasterSettingsDialog() {
        String[] options = {
            "🎨 Change UI Theme", 
            "🔤 Edit Title Font Style", 
            "✍️ Edit Content Font Style", 
            "📱 Edit App UI Font Style", 
            "📐 Change Sidebar Layout", 
            "📏 Change Editor Font Size"
        };
        AlertDialog.Builder builder = getThemedDialogBuilder();
        builder.setTitle("DevCode Customiser Panel");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) showThemeSelector();
                else if (which == 1) showFontSelector("font_title");
                else if (which == 2) showFontSelector("font_content");
                else if (which == 3) showFontSelector("font_app");
                else if (which == 4) showLayoutSelector();
                else if (which == 5) showFontSizeSelector();
            }
        });
        builder.create().show();
    }

    private void showThemeSelector() {
        final String[] themes = {"Obsidian Dark", "Light Neo", "Cyberpunk Neon", "Dracula Classic", "Monokai Pro", "Solarized Light"};
        AlertDialog.Builder builder = getThemedDialogBuilder();
        builder.setTitle("Select UI Skin Theme");
        builder.setItems(themes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                prefs.edit().putString("theme", themes[which]).apply();
                applySavedCustomizations();
            }
        });
        builder.create().show();
    }

    private void showFontSelector(final String preferencesKey) {
        final String[] fonts = {"Sans-Serif", "Monospace", "Serif", "Kalam (Hindi Hand)", "Amita (Hindi Casual)", "Yatra (Hindi Artistic)"};
        AlertDialog.Builder builder = getThemedDialogBuilder();
        builder.setTitle("Select Typographical Style");
        builder.setItems(fonts, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                prefs.edit().putString(preferencesKey, fonts[which]).apply();
                applySavedCustomizations();
            }
        });
        builder.create().show();
    }

    private void showLayoutSelector() {
        final String[] layouts = {"Left Sidebar", "Right Sidebar"};
        AlertDialog.Builder builder = getThemedDialogBuilder();
        builder.setTitle("Configure Panel Side Grid");
        builder.setItems(layouts, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                prefs.edit().putString("layout", layouts[which]).apply();
                applySavedCustomizations();
            }
        });
        builder.create().show();
    }

    private void showFontSizeSelector() {
        final String[] sizes = {"Small", "Medium", "Large"};
        AlertDialog.Builder builder = getThemedDialogBuilder();
        builder.setTitle("Editor Canvas Text Scaling");
        builder.setItems(sizes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                prefs.edit().putString("fontSize", sizes[which]).apply();
                applySavedCustomizations();
            }
        });
        builder.create().show();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (ev.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                int[] loc = new int[2];
                listViewNotes.getLocationOnScreen(loc);
                float x = ev.getRawX() - loc[0];
                float y = ev.getRawY() - loc[1];
                
                if (x >= 0 && x < listViewNotes.getWidth() && y >= 0 && y < listViewNotes.getHeight()) {
                    int position = listViewNotes.pointToPosition((int) x, (int) y);
                    if (position != AdapterView.INVALID_POSITION) {
                        showCustomActionMenuDialog(position);
                        return true; 
                    }
                }
            }
            int[] titleLoc = new int[2];
            editTextTitle.getLocationOnScreen(titleLoc);
            boolean insideTitle = (ev.getRawX() >= titleLoc[0] && ev.getRawX() <= titleLoc[0] + editTextTitle.getWidth() &&
                                   ev.getRawY() >= titleLoc[1] && ev.getRawY() <= titleLoc[1] + editTextTitle.getHeight());

            int[] contentLoc = new int[2];
            editTextContent.getLocationOnScreen(contentLoc);
            boolean insideContent = (ev.getRawX() >= contentLoc[0] && ev.getRawX() <= contentLoc[0] + editTextContent.getWidth() &&
                                     ev.getRawY() >= contentLoc[1] && ev.getRawY() <= contentLoc[1] + editTextContent.getHeight());

            if (layoutEditor.getVisibility() == View.VISIBLE && !insideTitle && !insideContent) {
                // Ensure clicks on new toolbar buttons don't accidentally drop focus
                int[] toolbarLoc = new int[2];
                layoutEditorToolbar.getLocationOnScreen(toolbarLoc);
                boolean insideToolbar = (ev.getRawX() >= toolbarLoc[0] && ev.getRawX() <= toolbarLoc[0] + layoutEditorToolbar.getWidth() &&
                                         ev.getRawY() >= toolbarLoc[1] && ev.getRawY() <= toolbarLoc[1] + layoutEditorToolbar.getHeight());
                
                if(!insideToolbar) {
                    closeKeyboardAndClearFocus();
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void showCustomActionMenuDialog(final int position) {
        final Note noteToPerformAction = (Note) notes.get(position);
        String[] menuItems = {"📥 Save As File / Compile", "❌ Delete Note Asset"};
        
        AlertDialog.Builder builder = getThemedDialogBuilder();
        builder.setTitle("Operations Dashboard");
        builder.setItems(menuItems, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    showExtensionSelectionDialog(noteToPerformAction);
                } else if (which == 1) {
                    if (selectedNoteId == noteToPerformAction.getId()) {
                        selectedNoteId = -1;
                        showPlaceholderState();
                    }
                    dbHelper.deleteNote(noteToPerformAction.getId());
                    Toast.makeText(MainActivity.this, "Note asset successfully deleted", Toast.LENGTH_SHORT).show();
                    refreshNotes();
                }
            }
        });
        builder.create().show();
    }

    private void showExtensionSelectionDialog(final Note note) {
        final String[] displayNames = {
            ".html (HTML5)", ".css (CSS3)", ".js (JavaScript)", ".ts (TypeScript)", ".jsx (React JS)", ".tsx (React TS)", ".php (PHP)",
            ".py (Python)", ".java (Java)", ".cpp (C++)", ".c (C Language)", ".cs (C#)", ".go (Golang)", ".rs (Rust)", ".rb (Ruby)",
            ".kt (Kotlin)", ".swift (Swift)", ".dart (Flutter/Dart)",
            ".json (JSON Data)", ".yaml (YAML Config)", ".xml (XML)", ".csv (CSV Data)", ".sql (SQL Query)",
            ".txt (Plain Text)", ".md (Markdown)", ".sh (Bash Script)", ".bat (Windows Batch)"
        };
        final String[] actualExtensions = {
            ".html", ".css", ".js", ".ts", ".jsx", ".tsx", ".php",
            ".py", ".java", ".cpp", ".c", ".cs", ".go", ".rs", ".rb",
            ".kt", ".swift", ".dart",
            ".json", ".yaml", ".xml", ".csv", ".sql",
            ".txt", ".md", ".sh", ".bat"
        };
        AlertDialog.Builder builder = getThemedDialogBuilder();
        builder.setTitle("Compile Format Selection");
        builder.setItems(displayNames, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                exportNoteAsFile(note, actualExtensions[which]);
            }
        });
        builder.create().show();
    }

    private void exportNoteAsFile(Note note, String extension) {
        String fileName = note.getTitle().trim();
        if (fileName.isEmpty() || fileName.equalsIgnoreCase("Untitled Note")) {
            fileName = "Untitled_Note";
        }
        fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_") + extension;
        
        // Strip HTML styling codes before downloading raw formats
        String plainTextContent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            plainTextContent = Html.fromHtml(note.getContent(), Html.FROM_HTML_MODE_LEGACY).toString();
        } else {
            plainTextContent = Html.fromHtml(note.getContent()).toString();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try {
                    OutputStream outputStream = getContentResolver().openOutputStream(uri);
                    if (outputStream != null) {
                        outputStream.write(plainTextContent.getBytes());
                        outputStream.close();
                        Toast.makeText(this, "Saved to Downloads: " + fileName, Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Save error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            try {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(downloadsDir, fileName);
                FileWriter writer = new FileWriter(file);
                writer.write(plainTextContent);
                writer.close();
                Toast.makeText(this, "Saved to Downloads: " + fileName, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        autoSaveCurrentNote();
    }

    private void autoSaveCurrentNote() {
        if (layoutEditor.getVisibility() != View.VISIBLE) return;
        
        // Export text along with highlight tags to keep styling saved in DB
        String titleHtml = Html.toHtml(editTextTitle.getText());
        String contentHtml = Html.toHtml(editTextContent.getText());
        
        if (editTextTitle.getText().toString().trim().isEmpty() && editTextContent.getText().toString().trim().isEmpty()) return;
        
        String finalTitle = editTextTitle.getText().toString().trim().isEmpty() ? "Untitled Note" : titleHtml;

        if (selectedNoteId == -1) {
            long newId = dbHelper.addNote(finalTitle, contentHtml);
            selectedNoteId = (int) newId;
        } else {
            dbHelper.updateNote(selectedNoteId, finalTitle, contentHtml);
        }
        refreshNotes();
    }

    private void closeKeyboardAndClearFocus() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View currentFocusView = getCurrentFocus();
        if (currentFocusView != null && imm != null) {
            imm.hideSoftInputFromWindow(currentFocusView.getWindowToken(), 0);
            currentFocusView.clearFocus();
        }
        editTextTitle.setCursorVisible(false);
        editTextContent.setCursorVisible(false);
        rootLayout.requestFocus();
    }

    private void showPlaceholderState() {
        layoutEditor.setVisibility(View.GONE);
        layoutPlaceholder.setVisibility(View.VISIBLE);
    }

    private void refreshNotes() {
        notes = dbHelper.getAllNotes();
        adapter = new ArrayAdapter<Note>(this, R.layout.note_list_item, notes) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView tv = v.findViewById(android.R.id.text1);
                if (tv != null) {
                    tv.setTypeface(getTypefaceByName(currentFontApp));
                    
                    // Render plaintext previews inside the sidebar lists
                    Note currentNote = notes.get(position);
                    String plainTitle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ?
                            Html.fromHtml(currentNote.getTitle(), Html.FROM_HTML_MODE_LEGACY).toString() :
                            Html.fromHtml(currentNote.getTitle()).toString();
                    tv.setText(plainTitle);

                    if ("Light Neo".equals(currentTheme)) {
                        tv.setTextColor(Color.parseColor("#18181B"));
                    } else if ("Solarized Light".equals(currentTheme)) {
                        tv.setTextColor(Color.parseColor("#657B83"));
                    } else if ("Cyberpunk Neon".equals(currentTheme)) {
                        tv.setTextColor(Color.parseColor("#34D399"));
                    } else if ("Dracula Classic".equals(currentTheme)) {
                        tv.setTextColor(Color.parseColor("#F8F8F2"));
                    } else if ("Monokai Pro".equals(currentTheme)) {
                        tv.setTextColor(Color.parseColor("#FCFCFA"));
                    } else {
                        tv.setTextColor(Color.parseColor("#E3E3E6"));
                    }
                }
                return v;
            }
        };
        listViewNotes.setAdapter(adapter);
    }
}