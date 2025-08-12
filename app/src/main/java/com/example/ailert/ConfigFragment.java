package com.example.ailert;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class ConfigFragment extends Fragment {

    private static final int PERMISSION_REQUEST_CONTACTS = 200;
    private static final int PICK_CONTACT = 201;

    private CheckBox cbBalas, cbGritos, cbVidrio, cbFuego, cbSirenas;
    private EditText etMensaje;
    private TextView tvContacto;
    private Button btnSelectContact, btnSave;
    private SharedPreferences preferences;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_config, container, false);

        cbBalas = view.findViewById(R.id.sound_balas);
        cbGritos = view.findViewById(R.id.sound_gritos);
        cbVidrio = view.findViewById(R.id.sound_vidrio);
        cbFuego = view.findViewById(R.id.sound_fuego);
        cbSirenas = view.findViewById(R.id.sound_sirenas);
        etMensaje = view.findViewById(R.id.message_input);
        tvContacto = view.findViewById(R.id.contact_text);
        btnSelectContact = view.findViewById(R.id.select_contact_button);
        btnSave = view.findViewById(R.id.save_button);

        preferences = requireActivity().getSharedPreferences("AIlertPrefs", Context.MODE_PRIVATE);

        loadPreferences();

        btnSelectContact.setOnClickListener(v -> requestContactPermission());
        btnSave.setOnClickListener(v -> savePreferences());

        return view;
    }

    private void loadPreferences() {
        cbBalas.setChecked(preferences.getBoolean("sound_balas", false));
        cbGritos.setChecked(preferences.getBoolean("sound_gritos", false));
        cbVidrio.setChecked(preferences.getBoolean("sound_vidrio", false));
        cbFuego.setChecked(preferences.getBoolean("sound_fuego", false));
        cbSirenas.setChecked(preferences.getBoolean("sound_sirenas", false));

        etMensaje.setText(preferences.getString("mensaje_base", "¡Ayuda! Esta es una emergencia."));
        tvContacto.setText(preferences.getString("contacto_nombre", "No seleccionado"));
    }

    private void savePreferences() {
        if (!cbBalas.isChecked() && !cbGritos.isChecked() && !cbVidrio.isChecked()
                && !cbFuego.isChecked() && !cbSirenas.isChecked()) {
            Toast.makeText(getContext(), "Selecciona al menos un sonido", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences.Editor editor = preferences.edit();

        editor.putBoolean("sound_balas", cbBalas.isChecked());
        editor.putBoolean("sound_gritos", cbGritos.isChecked());
        editor.putBoolean("sound_vidrio", cbVidrio.isChecked());
        editor.putBoolean("sound_fuego", cbFuego.isChecked());
        editor.putBoolean("sound_sirenas", cbSirenas.isChecked());

        String baseMessage = etMensaje.getText().toString().trim();
        if (baseMessage.isEmpty()) {
            baseMessage = "¡Ayuda! Esta es una emergencia.";
        }
        editor.putString("mensaje_base", baseMessage);
        editor.apply();

        Toast.makeText(getContext(), "Configuración guardada", Toast.LENGTH_SHORT).show();
    }

    private void requestContactPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.READ_CONTACTS},
                    PERMISSION_REQUEST_CONTACTS);
        } else {
            openContactPicker();
        }
    }

    private void openContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        startActivityForResult(intent, PICK_CONTACT);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CONTACTS &&
                grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openContactPicker();
        } else {
            Toast.makeText(getContext(), "Permiso de contactos denegado", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CONTACT && resultCode == Activity.RESULT_OK && data != null) {
            Uri contactUri = data.getData();
            if (contactUri == null) return;

            String contactId = null;
            String contactName = null;

            try (Cursor cursor = requireActivity().getContentResolver().query(
                    contactUri,
                    new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME},
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                    contactName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                    tvContacto.setText(contactName);
                }
            }

            if (contactId != null) {
                try (Cursor phoneCursor = requireActivity().getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        new String[]{contactId}, null)) {
                    if (phoneCursor != null && phoneCursor.moveToFirst()) {
                        String phoneNumber = phoneCursor.getString(
                                phoneCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));

                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putString("contacto_nombre", contactName);
                        editor.putString("contacto_numero", phoneNumber);
                        editor.apply();

                        Toast.makeText(getContext(), "Contacto guardado: " + contactName, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "El contacto no tiene número registrado", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }
}