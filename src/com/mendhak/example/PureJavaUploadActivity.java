package com.mendhak.example;

import android.accounts.*;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * This version doesn't use any of the Google client APIs
 * Use this to save on APK bloat
 */
public class PureJavaUploadActivity extends Activity
{

    AccountManager accountManager;
    private static final int USER_RECOVERABLE_AUTH = 5;
    private static final int ACCOUNT_PICKER = 2;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        SetStatus("...");

        Button buttonAuthorize = (Button) findViewById(R.id.ButtonAuthorize);

        Button buttonClear = (Button) findViewById(R.id.ButtonClear);

        Button buttonUpload = (Button) findViewById(R.id.ButtonUpload);


        buttonClear.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                SaveAuthToken("");
                SetStatus("Cleared auth token");
            }
        });

        buttonAuthorize.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                Authorize();
                //startActivity(new Intent().setClass(getApplicationContext(), OAuth2AuthorizationActivity.class));

            }
        });

        buttonUpload.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                UploadFileToGoogleDocs();
            }
        });
    }

    @Override
    protected Dialog onCreateDialog(int id)
    {
        switch (id)
        {
            case 0:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Select a Google account");
                final Account[] accounts = accountManager.getAccountsByType("com.google");
                final int size = accounts.length;

                if (size == 1)
                {
                    gotAccount(accounts[0]);
                }
                else
                {
                    String[] names = new String[size];
                    for (int i = 0; i < size; i++)
                    {
                        names[i] = accounts[i].name;
                    }
                    builder.setItems(names, new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int which)
                        {
                            // Stuff to do when the account is selected by the user
                            gotAccount(accounts[which]);
                        }
                    });
                    return builder.create();
                }


        }
        return null;
    }


    private void gotAccount(Account account)
    {

        GoogleAuthUtil.invalidateToken(getApplicationContext(), GetAuthToken());
        SaveAuthToken("");
        new AuthTokenTask(this, account.name).execute();
    }


    private String GetAuthToken()
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return prefs.getString("GDOCS_AUTH_TOKEN", "");

    }

    private void SaveAuthToken(String authToken)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("GDOCS_AUTH_TOKEN", authToken);
        editor.commit();
    }

    private String[] getAccountNames()
    {
        AccountManager mAccountManager = AccountManager.get(getApplicationContext());
        Account[] accounts = mAccountManager.getAccountsByType(
                GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE);
        String[] names = new String[accounts.length];
        for (int i = 0; i < names.length; i++)
        {
            names[i] = accounts[i].name;
        }
        return names;
    }

    private void Authorize()
    {
        accountManager = AccountManager.get(this);

        showDialog(0);

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == ACCOUNT_PICKER && resultCode == RESULT_OK)
        {
            String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            SetStatus(accountName);

        }
        else if (requestCode == USER_RECOVERABLE_AUTH && resultCode == RESULT_OK)
        {
            String[] names = getAccountNames();
            new AuthTokenTask(this, names[0]).execute();
        }
        else if (requestCode == USER_RECOVERABLE_AUTH && resultCode == RESULT_CANCELED)
        {
            Toast.makeText(this, "User rejected authorization.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    class AuthTokenTask extends AsyncTask<Void, Void, String>
    {

        private PureJavaUploadActivity mActivity;
        private String mEmail;

        public AuthTokenTask(PureJavaUploadActivity mActivity, String mEmail)
        {
            this.mActivity = mActivity;
            this.mEmail = mEmail;
        }

        @Override
        protected void onPreExecute()
        {
        }

        @Override
        protected String doInBackground(Void... params)
        {
            try
            {

                String token = GoogleAuthUtil.getToken(mActivity, mEmail, "oauth2:https://www.googleapis.com/auth/drive");

                return token;

            }
            catch (UserRecoverableAuthException userRecoverableException)
            {
                mActivity.startActivityForResult(userRecoverableException.getIntent(), USER_RECOVERABLE_AUTH);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result)
        {
            if (result != null)
            {
                SaveAuthToken(result);
            }
            SetStatus(result);
        }

    }

    public void SetStatus(String msg)
    {
        TextView tv = (TextView) findViewById(R.id.lblStatus);
        tv.setText(msg);
    }

    public void UploadFileToGoogleDocs()
    {


        if (IsNullOrEmpty(GetAuthToken()))
        {
            SetStatus("No access token available, you're not authorized");
            return;
        }

        try
        {
            String gpsLoggerFolderId = SearchForGpsLoggerFile(GetAuthToken(),"GPSLogger For Android");

            if(IsNullOrEmpty(gpsLoggerFolderId))
            {
                //Couldn't find folder, must create it
                gpsLoggerFolderId = CreateFolder(GetAuthToken());
            }

            String gpxFileId = SearchForGpsLoggerFile(GetAuthToken(),"upload_test.gpx");

            if(gpxFileId == null)
            {
                gpxFileId = CreateFileMetadata(GetAuthToken(), "upload_test.gpx", gpsLoggerFolderId);
            }

            //Update the file
            UpdateFileContents(GetAuthToken(), gpxFileId);



        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());

        }
    }

    private void UpdateFileContents(String authToken, String gpxFileId)
    {
        HttpURLConnection conn = null;

        String fileUpdateUrl = "https://www.googleapis.com/upload/drive/v2/files/"+ gpxFileId + "?uploadType=media";

        String fileContents = "<strong>This is a test file</strong>";

        try
        {


            if (Integer.parseInt(Build.VERSION.SDK) < Build.VERSION_CODES.FROYO)
            {
                //Due to a pre-froyo bug
                //http://android-developers.blogspot.com/2011/09/androids-http-clients.html
                System.setProperty("http.keepAlive", "false");
            }

            URL url = new URL(fileUpdateUrl);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.addRequestProperty("client_id", OAuth2Client.CLIENT_ID);
            conn.addRequestProperty("client_secret", OAuth2Client.CLIENT_SECRET);
            conn.setRequestProperty("GData-Version", "3.0");
            conn.setRequestProperty("User-Agent", "GPSLogger for Android");
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
            conn.setRequestProperty("Content-Type", "application/xml");
            conn.setRequestProperty("Content-Length", String.valueOf(fileContents.getBytes().length));

            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);

            DataOutputStream wr = new DataOutputStream(
                    conn.getOutputStream());
            wr.writeBytes(fileContents);
            wr.flush();
            wr.close();

            String createFolderDoc = GetStringFromInputStream(conn.getInputStream());

            JSONObject folderJson = new JSONObject(createFolderDoc);
            String folderId = folderJson.getString("id");
            SetStatus("File updated at " + folderId);

        }
        catch (Exception e)
        {

            System.out.println(e.getMessage());
            System.out.println(e.getMessage());
        }
        finally
        {
            if (conn != null)
            {
                conn.disconnect();
            }

        }

    }


    private String CreateFileMetadata(String authToken, String fileName, String parentFolderId)
    {


        String folderId = null;
        HttpURLConnection conn = null;

        String createFolderUrl = "https://www.googleapis.com/drive/v2/files";

        String createFolderPayload = "   {\n" +
                "             \"title\": \"" + fileName +"\",\n" +
                "             \"mimeType\": \"application/xml\",\n" +
                "             \"parents\": [\n" +
                "              {\n" +
                "               \"id\": \"" + parentFolderId +"\"\n" +
                "              }\n" +
                "             ]\n" +
                "            }";

        try
        {


            if (Integer.parseInt(Build.VERSION.SDK) < Build.VERSION_CODES.FROYO)
            {
                //Due to a pre-froyo bug
                //http://android-developers.blogspot.com/2011/09/androids-http-clients.html
                System.setProperty("http.keepAlive", "false");
            }

            URL url = new URL(createFolderUrl);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.addRequestProperty("client_id", OAuth2Client.CLIENT_ID);
            conn.addRequestProperty("client_secret", OAuth2Client.CLIENT_SECRET);
            conn.setRequestProperty("GData-Version", "3.0");
            conn.setRequestProperty("User-Agent", "GPSLogger for Android");
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
            conn.setRequestProperty("Content-Type", "application/json");

            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);

            DataOutputStream wr = new DataOutputStream(
                    conn.getOutputStream());
            wr.writeBytes(createFolderPayload);
            wr.flush();
            wr.close();

            folderId = null;


            String createFolderDoc = GetStringFromInputStream(conn.getInputStream());


            JSONObject folderJson = new JSONObject(createFolderDoc);
            folderId = folderJson.getString("id");
            SetStatus("Empty file created at " + folderId);

        }
        catch (Exception e)
        {

            System.out.println(e.getMessage());
            System.out.println(e.getMessage());
        }
        finally
        {
            if (conn != null)
            {
                conn.disconnect();
            }

        }

        return folderId;
    }



    private String CreateFolder(String authToken)
    {

        String folderId = null;
        HttpURLConnection conn = null;

        String createFolderUrl = "https://www.googleapis.com/drive/v2/files";

        String createFolderPayload = "   {\n" +
                "             \"title\": \"GPSLogger For Android\",\n" +
                "             \"mimeType\": \"application/vnd.google-apps.folder\",\n" +
                "             \"parents\": [\n" +
                "              {\n" +
                "               \"id\": \"root\"\n" +
                "              }\n" +
                "             ]\n" +
                "            }";

        try
        {


            if (Integer.parseInt(Build.VERSION.SDK) < Build.VERSION_CODES.FROYO)
            {
                //Due to a pre-froyo bug
                //http://android-developers.blogspot.com/2011/09/androids-http-clients.html
                System.setProperty("http.keepAlive", "false");
            }

            URL url = new URL(createFolderUrl);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.addRequestProperty("client_id", OAuth2Client.CLIENT_ID);
            conn.addRequestProperty("client_secret", OAuth2Client.CLIENT_SECRET);
            conn.setRequestProperty("GData-Version", "3.0");
            conn.setRequestProperty("User-Agent", "GPSLogger for Android");
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
            conn.setRequestProperty("Content-Type", "application/json");

            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);

            DataOutputStream wr = new DataOutputStream(
                    conn.getOutputStream());
            wr.writeBytes(createFolderPayload);
            wr.flush();
            wr.close();

            folderId = null;


            String createFolderDoc = GetStringFromInputStream(conn.getInputStream());


            JSONObject folderJson = new JSONObject(createFolderDoc);
            folderId = folderJson.getString("id");
            SetStatus("Folder created at " + folderId);

        }
        catch (Exception e)
        {

            System.out.println(e.getMessage());
            System.out.println(e.getMessage());
        }
        finally
        {
            if (conn != null)
            {
                conn.disconnect();
            }

        }

        return folderId;
    }


    private String GetExistingFolderIdFromSearchResult(InputStream inputStream)
    {
        String folderId = null;


        String createFolderDoc = GetStringFromInputStream(inputStream);

        try
        {
            JSONObject folderJson = new JSONObject(createFolderDoc);
            if(folderJson.getJSONArray("items") != null && folderJson.getJSONArray("items").length() > 0)
            {
                folderId = folderJson.getJSONArray("items").getJSONObject(0).get("id").toString();
                SetStatus("Folder found at " + folderId);

            }
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }


        return folderId;

    }



    private String SearchForGpsLoggerFile(String authToken, String fileName)
    {

        String folderId = "";
        fileName = URLEncoder.encode(fileName);

        String searchUrl = "https://www.googleapis.com/drive/v2/files?q=title%20%3D%20%27" + fileName + "%27%20and%20trashed%20%3D%20false";
        HttpURLConnection conn = null;

        try
        {

            if (Integer.parseInt(Build.VERSION.SDK) < Build.VERSION_CODES.FROYO)
            {
                //Due to a pre-froyo bug
                //http://android-developers.blogspot.com/2011/09/androids-http-clients.html
                System.setProperty("http.keepAlive", "false");
            }

            URL url = new URL(searchUrl);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.addRequestProperty("client_id", OAuth2Client.CLIENT_ID);
            conn.addRequestProperty("client_secret", OAuth2Client.CLIENT_SECRET);
            conn.setRequestProperty("GData-Version", "3.0");
            conn.setRequestProperty("User-Agent", "GPSLogger for Android");
            conn.setRequestProperty("Authorization", "OAuth " + authToken);

            folderId = GetExistingFolderIdFromSearchResult(conn.getInputStream());
        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
        finally
        {
            if (conn != null)
            {
                conn.disconnect();
            }
        }

        return folderId;
    }

    public String GetStringFromInputStream(InputStream is)
    {
        String line;
        StringBuilder total = new StringBuilder();

        // Wrap a BufferedReader around the InputStream
        BufferedReader rd = new BufferedReader(new InputStreamReader(is));

        // Read response until the end
        try
        {
            while ((line = rd.readLine()) != null)
            {
                total.append(line);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            try
            {
                is.close();
            }
            catch (Exception e)
            {
                Log.v("ERROR", "GetStringFromInputStream - could not close stream");
            }
        }

        // Return full string
        return total.toString();
    }


    private boolean IsNullOrEmpty(String gpsLoggerFolderFeed)
    {
        return gpsLoggerFolderFeed == null || gpsLoggerFolderFeed.length() == 0;
    }


}