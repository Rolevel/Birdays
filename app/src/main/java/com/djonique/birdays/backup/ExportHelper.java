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

package com.djonique.birdays.backup;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.util.Xml;
import android.widget.Toast;

import com.djonique.birdays.R;
import com.djonique.birdays.database.DBHelper;
import com.djonique.birdays.models.Person;
import com.djonique.birdays.utils.ProgressDialogHelper;
import com.djonique.birdays.utils.Utils;

import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Calendar;
import java.util.List;

public class ExportHelper {

    private static final String PERSON = "person";
    private static final String NAME = "name";
    private static final String DATE = "date";
    private static final String UNKNOWN_YEAR = "unknown_year";
    private static final String PHONE_NUMBER = "phone_number";
    private static final String EMAIL = "email";
    private static final String BACKUP = "backup";
    private static final String UTF_8 = "UTF-8";
    private static final String RECORDS = "records";
    private static final String IO_EXCEPTION = "IOException";
    private static final String FILE_NOT_FOUND_EXCEPTION = "FileNotFoundException";
    private static final String ILLEGAL_ARGUMENT_EXCEPTION = "IllegalArgumentException";
    private static final String ILLEGAL_STATE_EXCEPTION = "IllegalStateException";
    private Context mContext;
    private File folder;
    private boolean isStorageAvailable = true;

    public ExportHelper(Context context) {
        mContext = context;
    }

    public void export() {
        new ExportAsyncTask().execute();
    }

    private boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    private String getBackupFileName() {
        long now = Calendar.getInstance().getTimeInMillis();
        return BACKUP + "_" + Utils.getDate(now) + "_" + String.valueOf(now) + ".xml";
    }

    private String writeXml(List<Person> persons) {
        XmlSerializer xmlSerializer = Xml.newSerializer();
        StringWriter stringWriter = new StringWriter();
        try {
            xmlSerializer.setOutput(stringWriter);
            xmlSerializer.startDocument(UTF_8, true);
            xmlSerializer.startTag(null, RECORDS);
            for (Person person : persons) {
                xmlSerializer.startTag(null, PERSON);
                // name
                xmlSerializer.startTag(null, NAME);
                xmlSerializer.text(person.getName());
                xmlSerializer.endTag(null, NAME);
                // date
                xmlSerializer.startTag(null, DATE);
                xmlSerializer.text(String.valueOf(person.getDate()));
                xmlSerializer.endTag(null, DATE);
                // unknown year
                xmlSerializer.startTag(null, UNKNOWN_YEAR);
                xmlSerializer.text(String.valueOf(person.isYearUnknown()));
                xmlSerializer.endTag(null, UNKNOWN_YEAR);
                // phone number
                xmlSerializer.startTag(null, PHONE_NUMBER);
                String phoneNumber = person.getPhoneNumber() == null ? "" : person.getPhoneNumber();
                xmlSerializer.text(phoneNumber);
                xmlSerializer.endTag(null, PHONE_NUMBER);
                // email
                xmlSerializer.startTag(null, EMAIL);
                String email = person.getEmail() == null ? "" : person.getEmail();
                xmlSerializer.text(email);
                xmlSerializer.endTag(null, EMAIL);
                xmlSerializer.endTag(null, PERSON);
            }
            xmlSerializer.endTag(null, RECORDS);
            xmlSerializer.endDocument();
            xmlSerializer.flush();
        } catch (IllegalArgumentException e) {
            Toast.makeText(mContext, ILLEGAL_ARGUMENT_EXCEPTION, Toast.LENGTH_LONG).show();
        } catch (IllegalStateException e) {
            Toast.makeText(mContext, ILLEGAL_STATE_EXCEPTION, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(mContext, IO_EXCEPTION, Toast.LENGTH_LONG).show();
        }
        return stringWriter.toString();
    }

    private void showAlertDialog(Context context, String text) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(text);
        builder.setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    private class ExportAsyncTask extends AsyncTask<Void, Void, Void> {

        ProgressDialogHelper progressDialogHelper = new ProgressDialogHelper(mContext);

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialogHelper.startProgressDialog(mContext.getString(R.string.exporting_records));
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Override
        protected Void doInBackground(Void... params) {
            File sd = Environment.getExternalStorageDirectory();
            if (isExternalStorageWritable()) {
                try {
                    folder = new File(sd.getPath() + File.separator + mContext.getString(R.string.app_name));
                    if (!folder.exists()) {
                        folder.mkdir();
                    }
                    File backupFile = new File(folder + File.separator + getBackupFileName());
                    backupFile.createNewFile();
                    FileOutputStream fileOutputStream = new FileOutputStream(backupFile);
                    List<Person> persons = new DBHelper(mContext).query().getPersons();
                    fileOutputStream.write(writeXml(persons).getBytes());
                    fileOutputStream.close();
                } catch (FileNotFoundException e) {
                    Toast.makeText(mContext, FILE_NOT_FOUND_EXCEPTION, Toast.LENGTH_LONG).show();
                } catch (IOException e) {
                    Toast.makeText(mContext, IO_EXCEPTION, Toast.LENGTH_LONG).show();
                }
            } else {
                isStorageAvailable = false;
                ((Activity) mContext).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showAlertDialog(mContext, mContext.getString(R.string.ext_storage_error));
                    }
                });
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            progressDialogHelper.dismissProgressDialog();
            if (isStorageAvailable) {
                showAlertDialog(mContext, mContext.getString(R.string.backup_finished) + folder);
            }
        }
    }
}