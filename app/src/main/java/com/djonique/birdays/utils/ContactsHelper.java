/*
 * Copyright 2017 Evgeny Timofeev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.djonique.birdays.utils;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.widget.Toast;

import com.djonique.birdays.R;
import com.djonique.birdays.alarm.AlarmHelper;
import com.djonique.birdays.database.DBHelper;
import com.djonique.birdays.models.Person;

import java.util.ArrayList;
import java.util.List;

public class ContactsHelper {

    /**
     * Returns name from certain contact
     */
    public static String getContactName(ContentResolver contentResolver, String id) {
        String name = null;
        Cursor nameCursor = contentResolver.query(ContactsContract.Data.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID + " = ?",
                new String[]{id}, null);
        if (nameCursor != null && nameCursor.moveToFirst()) {
            name =
                    nameCursor.getString(nameCursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
        }
        if (nameCursor != null) {
            nameCursor.close();
        }
        return name;
    }

    /**
     * Returns phone number from certain contact
     */
    public static String getContactPhoneNumber(ContentResolver contentResolver, String id) {
        String phoneNumber = null;
        Cursor phoneCursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                new String[]{id}, null);
        if (phoneCursor != null && phoneCursor.moveToFirst()) {
            phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndex
                    (ContactsContract.CommonDataKinds.Phone.NUMBER));
        }
        if (phoneCursor != null) {
            phoneCursor.close();
        }
        return phoneNumber;
    }

    /**
     * Returns email from certain contact
     */
    public static String getContactEmail(ContentResolver contentResolver, String id) {
        String email = null;
        Cursor emailCursor = contentResolver.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                new String[]{id}, null);
        if (emailCursor != null && emailCursor.moveToFirst()) {
            email = emailCursor.getString(
                    emailCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.DATA));
        }
        if (emailCursor != null) {
            emailCursor.close();
        }
        return email;
    }

    /**
     * Returns all contacts with Birthdays
     */
    private static List<Person> getAllContactsWithBirthdays(ContentResolver contentResolver) {

        List<Person> contacts = new ArrayList<>();

        Cursor cursor = ContactsHelper.getContactsCursor(contentResolver);

        while (cursor.moveToNext()) {
            String id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.CONTACT_ID));
            String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
            String dateString = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE));
            long date;
            try {
                date = Utils.formatDateToLong(dateString);
            } catch (Exception e) {
                continue;
            }
            if (date == 0) continue;

            boolean isYearKnown = Utils.isYearKnown(dateString);
            String phoneNumber = ContactsHelper.getContactPhoneNumber(contentResolver, id);
            String email = ContactsHelper.getContactEmail(contentResolver, id);

            Person person = new Person(name, date, isYearKnown, phoneNumber, email);
            contacts.add(person);
        }
        cursor.close();
        return contacts;
    }

    /**
     * Returns cursor with contacts with specified Birthdays
     */
    private static Cursor getContactsCursor(ContentResolver contentResolver) {
        Uri uri = ContactsContract.Data.CONTENT_URI;

        String[] projection = new String[]{
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Event.CONTACT_ID,
                ContactsContract.CommonDataKinds.Event.START_DATE,
        };

        String where =
                ContactsContract.Data.MIMETYPE
                        + "= ? AND "
                        + ContactsContract.CommonDataKinds.Event.TYPE
                        + "="
                        + ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY;
        String[] selectionArgs = new String[]{ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE};
        return contentResolver.query(uri, projection, where, selectionArgs, null);
    }

    /**
     * Loads all persons with Birthdays from Contacts, compares them with persons from Database and
     * saves them into DB, sets alarm for added persons
     */
    public static void loadContacts(ContentResolver contentResolver,
                                    Context context,
                                    SharedPreferences preferences) {
        DBHelper dbHelper = new DBHelper(context);
        List<Person> dbPersons = dbHelper.query().getPersons();
        AlarmHelper alarmHelper = AlarmHelper.getInstance();

        if (PermissionHelper.permissionGranted(context)) {
            try {
                List<Person> contacts = ContactsHelper.getAllContactsWithBirthdays(contentResolver);

                for (Person person : contacts) {
                    if (!Utils.isPersonAlreadyInDB(person, dbPersons)) {
                        dbHelper.addRec(person);
                        alarmHelper.setAlarms(person);
                    }
                }
                preferences.edit().putBoolean(ConstantManager.CONTACTS_UPLOADED, true).apply();
                Toast.makeText(context, R.string.contacts_uploaded, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                preferences.edit().putBoolean(ConstantManager.WRONG_CONTACTS_FORMAT, true).apply();
                Toast.makeText(context, R.string.loading_contacts_error, Toast.LENGTH_LONG).show();
            }
        } else {
            PermissionHelper.requestPermission(((Activity) context));
        }
    }
}