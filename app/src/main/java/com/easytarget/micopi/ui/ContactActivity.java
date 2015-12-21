/*
 * Copyright (C) 2014 Easy Target
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.easytarget.micopi.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.easytarget.micopi.Contact;
import com.easytarget.micopi.DeviceHelper;
import com.easytarget.micopi.FileHelper;
import com.easytarget.micopi.R;
import com.easytarget.micopi.engine.ColorUtilities;
import com.easytarget.micopi.engine.ImageFactory;

/**
 * Activity that displays the generated image and all the options.
 * <p/>
 * Created by Michel on 03.02.14.
 */
public class ContactActivity extends TaskActivity {

    private static final String TAG = ContactActivity.class.getSimpleName();

    public static final int WRITE_STORAGE_PERMISSION_CODE = 50;

    public static final int WRITE_CONTACTS_PERMISSION_CODE = 52;

    /**
     * Key for Contact object, used for instance saving and restoration
     */
    private static final String STORED_CONTACT = "stored_contact";

    /**
     * Key for boolean value, used for instance saving and restoration
     */
    private static final String STORED_PICKED = "stored_picked";

    /**
     * Currently handled contact
     */
    private Contact mContact;

    /**
     * Keeps the user from performing any input while performing a task such as generating an image
     */
    private boolean mGuiIsLocked = false;

    private int mColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_contact);

        // Check whether we're recreating a previously destroyed instance
        if (savedInstanceState != null && savedInstanceState.containsKey(STORED_CONTACT)) {
//            mColor = savedInstanceState.getInt(Constants.EXTRA_COLOR);
            setContact((Contact) savedInstanceState.getParcelable(STORED_CONTACT));
//            mHasPickedContact = savedInstanceState.getBoolean(STORED_PICKED);
        }

        // Immediately show the contact picker if no contact has been selected, yet.
        if (mContact == null) pickContact();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String permissions[],
            @NonNull int[] grantResults
    ) {
        if (grantResults.length < 1) return;

        switch (requestCode) {
            case WRITE_STORAGE_PERMISSION_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // This permission request is only called when pressing the Store File button.
                    new SaveImageTask().execute();
                }
                return;

            case WRITE_CONTACTS_PERMISSION_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // This permission request is only called
                    // when pressing the Save To Contact button.
                    confirmAssignContactImage();
                }
        }
    }

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.main, menu);
//        return true;
//    }

    @Override
    public boolean onSearchRequested() {
        if (!mGuiIsLocked) pickContact();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!mGuiIsLocked) {
            // Handle presses on the action bar items.
            switch (item.getItemId()) {
                case R.id.action_assign:
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        confirmAssignContactImage();
                    } else {
                        final int contactPerm = checkSelfPermission(
                                Manifest.permission.WRITE_CONTACTS
                        );

                        final boolean hasWritePerm;
                        hasWritePerm = contactPerm == PackageManager.PERMISSION_GRANTED;

                        if (hasWritePerm) {
                            confirmAssignContactImage();
                        } else {
                            Log.d(TAG, "Permission to write contacts not given.");
                            // Once the permission is given, the mail will be sent.
                            // See onRequestPermissionsResult().
                            ActivityCompat.requestPermissions(
                                    this,
                                    new String[]{Manifest.permission.READ_CONTACTS},
                                    WRITE_CONTACTS_PERMISSION_CODE
                            );
                        }

                    }
                    return true;

                case R.id.action_previous_image:
                    mContact.modifyRetryFactor(false);
                    generateImage();
                    return true;

                case R.id.action_next_image:
                    mContact.modifyRetryFactor(true);
                    generateImage();
                    return true;

                case R.id.action_search:
                    pickContact();
                    return true;

                case R.id.action_save:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        final int writePerm = checkSelfPermission(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        );

                        if (writePerm != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(
                                    this,
                                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                    WRITE_STORAGE_PERMISSION_CODE
                            );
                            return true;
                        }
                    }
                    final ImageView imageView = (ImageView) findViewById(R.id.image_contact);

                    new SaveImageTask().execute(
                            ((BitmapDrawable) imageView.getDrawable()).getBitmap()
                    );
                    return true;

                default:
                    return super.onOptionsItemSelected(item);
            }
        }

        return true;
    }

    public void onButtonClicked(View view) {
        switch (view.getId()) {
            case R.id.button_assign:
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    confirmAssignContactImage();
                } else {
                    final int contactPerm = checkSelfPermission(
                            Manifest.permission.WRITE_CONTACTS
                    );

                    final boolean hasWritePerm;
                    hasWritePerm = contactPerm == PackageManager.PERMISSION_GRANTED;

                    if (hasWritePerm) {
                        confirmAssignContactImage();
                    } else {
                        Log.d(TAG, "Permission to write contacts not given.");
                        // Once the permission is given, the mail will be sent.
                        // See onRequestPermissionsResult().
                        ActivityCompat.requestPermissions(
                                this,
                                new String[]{Manifest.permission.READ_CONTACTS},
                                WRITE_CONTACTS_PERMISSION_CODE
                        );
                    }

                }
                return;

            case R.id.button_search:
                pickContact();
                return;

            case R.id.button_prev:
                mContact.modifyRetryFactor(false);
                generateImage();
                return;

            case R.id.button_next:
                mContact.modifyRetryFactor(true);
                generateImage();
                return;

            case R.id.button_save:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    final int writePerm = checkSelfPermission(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    );

                    if (writePerm != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(
                                this,
                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                WRITE_STORAGE_PERMISSION_CODE
                        );
                        return;
                    }
                }
                final ImageView imageView = (ImageView) findViewById(R.id.image_contact);

                new SaveImageTask().execute(
                        ((BitmapDrawable) imageView.getDrawable()).getBitmap()
                );
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putParcelable(STORED_CONTACT, mContact);
    }

    /*
    GUI ACTION
     */

    /**
     * Return code of contact picker
     */
    private static final int PICK_CONTACT = 1;

    /**
     * Opens the contact picker and allows the user to chose a contact.
     * onActivityResult will be called when returning to MainActivity.
     */
    public void pickContact() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        startActivityForResult(intent, PICK_CONTACT);
    }

    /**
     * Method called by contact picker when a person was chosen.
     *
     * @param reqCode    Request code (1 when coming from contact picker)
     * @param resultCode Result code
     * @param data       Contact data
     */
    @Override
    public void onActivityResult(int reqCode, int resultCode, Intent data) {
        super.onActivityResult(reqCode, resultCode, data);

        // Close the app if the back button was pressed on first contact picker.
        if (mContact == null && resultCode != RESULT_OK) finish();

        // Check if the activity result is ok and check the request code.
        // The latter should be 1 indicating a picked contact.
        if (resultCode == RESULT_OK && reqCode == PICK_CONTACT) {
            setContact(Contact.buildContact(this, data));
        }
    }

    private void setContact(final Contact contact) {
        mContact = contact;

        final TextView nameView = (TextView) findViewById(R.id.text_contact_name);
        final View descriptionView = findViewById(R.id.text_description);

        if (mContact == null) {
            Log.e("generateImageTask", "ERROR: Contact is null.");
            nameView.setText(R.string.no_contact_selected);
            descriptionView.setVisibility(View.GONE);

            // TODO: Show error.
            return;
        } else {
            nameView.setText(mContact.getFullName());
            descriptionView.setVisibility(View.VISIBLE);
            descriptionView.setVisibility(View.VISIBLE);
        }

        generateImage();
    }

    private void generateImage() {
        final ImageView imageView = (ImageView) findViewById(R.id.image_contact);
        imageView.setImageDrawable(null);


        final Bitmap generatedBitmap;
        generatedBitmap = ImageFactory.bitmapFrom(mContact, DeviceHelper.getBestImageSize(this));
        imageView.setImageBitmap(generatedBitmap);

        if (generatedBitmap == null) {
            Log.e(TAG, "Generated null bitmap.");
            setColor(getResources().getColor(R.color.primary));
        } else {
            setColor(ColorUtilities.getAverageColor(generatedBitmap));
        }

    }

    /**
     * Opens a YES/NO dialog for the user to confirm that the contact's image will be overwritten.
     */
    public void confirmAssignContactImage() {
        if (mContact == null) return;

        final ImageView imageView = (ImageView) findViewById(R.id.image_contact);

        final DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    final boolean didAssign = FileHelper.assignImageToContact(
                            ContactActivity.this,
                            ((BitmapDrawable) imageView.getDrawable()).getBitmap(),
                            mContact.getId()
                    );

                    if (didAssign) {
                        Toast.makeText(
                                ContactActivity.this,
                                String.format(
                                        getString(R.string.got_new_picture),
                                        mContact.getFullName()
                                ),
                                Toast.LENGTH_SHORT
                        ).show();
                    } else {
                        Toast.makeText(
                                ContactActivity.this,
                                R.string.error_assign,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(
                String.format(
                        getResources().getString(R.string.want_to_assign),
                        mContact.getFullName())
        );
        builder.setNegativeButton(android.R.string.no, dialogClickListener);
        builder.setPositiveButton(android.R.string.yes, dialogClickListener);
        builder.show();
    }

    /*
    THREADS
     */

    /**
     * Save the generated image to a file on the device
     */
    private class SaveImageTask extends AsyncTask<Bitmap, Void, String> {

        @Override
        protected void onPreExecute() {
            mGuiIsLocked = true;
        }

        @Override
        protected String doInBackground(Bitmap... params) {
            if (mContact == null) return null;
            if (params == null || params.length < 1) return null;

            return new FileHelper().storeImage(
                    ContactActivity.this,
                    params[0],
                    mContact.getFullName(),
                    mContact.getMD5EncryptedString().charAt(0)
            );
        }

        @Override
        protected void onPostExecute(String fileName) {
            mGuiIsLocked = false;

            if (!TextUtils.isEmpty(fileName)) {
                Toast.makeText(
                        ContactActivity.this,
                        String.format(
                                getResources().getString(R.string.saved_picture_as),
                                fileName),
                        Toast.LENGTH_SHORT
                ).show();
            } else {
                Toast.makeText(
                        ContactActivity.this,
                        String.format(
                                getResources().getString(R.string.error_saving),
                                fileName),
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    /**
     * Changes the color of the action bar and status bar
     *
     * @param color ARGB Color to apply
     */
    private void setColor(int color) {
        mColor = color;
        final View mainView = findViewById(R.id.layout_contact);
        if (mainView == null) {
            Log.e("MainActivity:setColor()", "WARNING: Did not find root view.");
        } else {
            mainView.setBackgroundColor(mColor);
        }

        /*
        Set the action bar colour to the average colour of the generated image and
        the status bar colour for Android Version >= 5.0 accordingly.
        */

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Get the window through a reference to the activity.
            final Window window = ContactActivity.this.getWindow();
            // Set the status bar colour of this window.
            int statusColor = ColorUtilities.getDarkenedColor(mColor);
            window.setStatusBarColor(statusColor);
        }
    }
}
