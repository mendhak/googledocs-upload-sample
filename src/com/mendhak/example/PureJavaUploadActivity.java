package com.mendhak.example;

import android.accounts.*;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 This version doesn't use any of the Google client APIs
 Use this to save on APK bloat
 */
public class PureJavaUploadActivity extends Activity
{
    
    String authToken;
    AccountManager accountManager;
    
    /** Called when the activity is first created. */
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
                authToken = null;
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

        Bundle options = new Bundle();

        accountManager.getAuthToken(
                account,                     // Account retrieved using getAccountsByType()
                "oauth2:https://docs.google.com/feeds/",            // Auth scope
                //"writely",            // Auth scope, doesn't work :(
                options,                        // Authenticator-specific options
                this,                           // Your activity
                new OnTokenAcquired(),          // Callback called when a token is successfully acquired
                null);    // Callback called if an error occurs
    }

    private class OnTokenAcquired implements AccountManagerCallback<Bundle>
    {
        @Override
        public void run(AccountManagerFuture<Bundle> bundleAccountManagerFuture)
        {
            try
            {
                authToken = bundleAccountManagerFuture.getResult().getString(AccountManager.KEY_AUTHTOKEN);
                SetStatus(authToken);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

        }
    }


    private void Authorize()
    {
        //To revoke access, adb -e shell 'sqlite3 /data/system/accounts.db "delete from grants;"'
        if (accountManager != null)
        {
            accountManager.invalidateAuthToken("com.google", authToken);
        }

        accountManager = AccountManager.get(this);

        showDialog(0);
    }

    public void SetStatus(String msg)
    {
        TextView tv = (TextView)findViewById(R.id.lblStatus);
        tv.setText(msg);
    }

    public void UploadFileToGoogleDocs()
    {

        Authorize();

        if(IsNullOrEmpty(authToken))
        {
            SetStatus("No access token available, you're not authorized");
            return;
        }

        try
        {
            String gpsLoggerFolderFeed = SearchForGpsLoggerFolder(authToken);
            
            if(IsNullOrEmpty(gpsLoggerFolderFeed))
            {
                //Couldn't find anything, need to create it. 
                gpsLoggerFolderFeed = CreateFolder(authToken);
            }
            
            SetStatus(gpsLoggerFolderFeed);
            
            FileAccessLocations fileSearch = SearchForFile(gpsLoggerFolderFeed, "20120304.gpx");

            SetStatus(fileSearch.CreateUrl);
            SetStatus(fileSearch.UpdateUrl);
            
            if(IsNullOrEmpty(fileSearch.UpdateUrl))
            {
                //The file doesn't exist, you must create it.
                CreateFile(fileSearch, "20120304.gpx", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<a>1</a>");
            }
            else
            {
                //The file exists, update its contents instead
                UpdateFile(fileSearch, "20120304.gpx", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<a>2</a>");
            }

        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());

        }
    }

    private void UpdateFile(FileAccessLocations accessLocations, String fileName, String fileContents)
    {

        String resumableFileUploadUrl = UploadFileContentsToResumableUrl(accessLocations.UpdateUrl+"?convert=false",
                                    fileName, fileContents,true);

        UploadFileContentsToResumableUrl(resumableFileUploadUrl, fileName, fileContents, true);
        
    }

    private void CreateFile(FileAccessLocations accessLocations, String fileName, String fileContents)
    {
        String createFileAtomXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<entry xmlns=\"http://www.w3.org/2005/Atom\" xmlns:docs=\"http://schemas.google.com/docs/2007\">\n" +
                "  <category scheme=\"http://schemas.google.com/g/2005#kind\"\n" +
                "      term=\"http://schemas.google.com/docs/2007#document\"/>\n" +
                "  <title>" + fileName + "</title>\n" +
                "</entry>";

        String resumableFileUploadUrl = UploadFileContentsToResumableUrl(accessLocations.CreateUrl + "?convert=false",
                fileName, createFileAtomXml, false);

        UploadFileContentsToResumableUrl(resumableFileUploadUrl, fileName, fileContents, false);
     
    }
    
    private String UploadFileContentsToResumableUrl(String resumableFileUploadUrl, String fileName, String fileContents, boolean isUpdate)
    {
        //This method gets used 4 times - to get the resumable location for create/edit, and to do the actual uploads.

        String newLocation = "";
        HttpURLConnection conn = null;

        try
        {
            if (Integer.parseInt(Build.VERSION.SDK) < Build.VERSION_CODES.FROYO)
            {
                //Due to a pre-froyo bug
                //http://android-developers.blogspot.com/2011/09/androids-http-clients.html
                System.setProperty("http.keepAlive", "false");
            }

            URL url = new URL(resumableFileUploadUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.addRequestProperty("client_id", OAuth2Client.CLIENT_ID);
            conn.addRequestProperty("client_secret", OAuth2Client.CLIENT_SECRET);
            conn.setRequestProperty("Authorization", "OAuth " + authToken);

            conn.setRequestProperty("X-Upload-Content-Length", String.valueOf(fileContents.length())); //back to 0
            conn.setRequestProperty("X-Upload-Content-Type", "text/xml");
            conn.setRequestProperty("Content-Type", "text/xml");
            conn.setRequestProperty("Content-Length", String.valueOf(fileContents.length()));
            conn.setRequestProperty("Slug", fileName);
            
            if(isUpdate)
            {
                conn.setRequestProperty("If-Match", "*");
                conn.setRequestMethod("PUT");
            }
            else
            {
                conn.setRequestMethod("POST");
            }

            conn.setRequestProperty("GData-Version", "3.0");
            conn.setRequestProperty("User-Agent", "GPSLogger for Android");


            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);

            DataOutputStream wr = new DataOutputStream(
                    conn.getOutputStream());
            wr.writeBytes(fileContents);
            wr.flush();
            wr.close();

            int code = conn.getResponseCode();
            newLocation = conn.getHeaderField("location");
        }
        catch (Exception e)
        {
            SetStatus(e.getMessage());
        }
        finally
        {
            if(conn != null)
            {
                conn.disconnect();
            }
        }

        return newLocation;

    }

    private class FileAccessLocations
    {
        public String CreateUrl;
        public String UpdateUrl;
    }
    
    
    private FileAccessLocations SearchForFile(String gpsLoggerFolderFeed, String fileName)
    {

        FileAccessLocations fal = new FileAccessLocations();
        HttpURLConnection conn = null;
        String searchUrl = gpsLoggerFolderFeed + "?title=" + fileName;

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

            Document doc = GetDocumentFromInputStream(conn.getInputStream());
            fal.CreateUrl = GetFileUploadUrl(doc);
            fal.UpdateUrl = GetFileEditUrl(doc);
        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
        finally
        {
            if(conn != null)
            {
                conn.disconnect();
            }
        }
        
        return fal;

    }


    private String GetFileUploadUrl(Document fileSearchNode)
    {
        String fileUploadUrl = "";

        NodeList linkNodes = fileSearchNode.getElementsByTagName("link");

        for (int i = 0; i < linkNodes.getLength(); i++)
        {
            String rel = linkNodes.item(i).getAttributes().getNamedItem("rel").getNodeValue();

            if (rel.equalsIgnoreCase("http://schemas.google.com/g/2005#resumable-create-media"))
            {
                fileUploadUrl = linkNodes.item(i).getAttributes().getNamedItem("href").getNodeValue();
            }
        }

        return fileUploadUrl;

    }


    private String GetFileEditUrl(Document fileSearchNode)
    {
        String fileEditUrl = "";

        NodeList linkNodes = fileSearchNode.getElementsByTagName("link");

        for (int i = 0; i < linkNodes.getLength(); i++)
        {
            String rel = linkNodes.item(i).getAttributes().getNamedItem("rel").getNodeValue();

            if (rel.equalsIgnoreCase("http://schemas.google.com/g/2005#resumable-edit-media"))
            {
                fileEditUrl = linkNodes.item(i).getAttributes().getNamedItem("href").getNodeValue();
            }
        }

        return fileEditUrl;
    }
    

    private String CreateFolder(String authToken)
    {

        String folderFeedUrl = "";
        HttpURLConnection conn = null;

        String createFolderUrl = "https://docs.google.com/feeds/default/private/full";

        String createXml = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<entry xmlns=\"http://www.w3.org/2005/Atom\">\n" +
                "  <category scheme=\"http://schemas.google.com/g/2005#kind\"\n" +
                "      term=\"http://schemas.google.com/docs/2007#folder\"/>\n" +
                "  <title>GPSLogger For Android</title>\n" +
                "</entry>";

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
            conn.setRequestProperty("Authorization", "OAuth " + authToken);
            conn.setRequestProperty("Content-Type", "application/atom+xml");

            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);

            DataOutputStream wr = new DataOutputStream(
                    conn.getOutputStream());
            wr.writeBytes(createXml);
            wr.flush();
            wr.close();
            
            folderFeedUrl = GetFolderFeedUrlFromInputStream(conn.getInputStream());

        }
        catch (Exception e)
        {

            System.out.println(e.getMessage());
            System.out.println(e.getMessage());
        }
        finally
        {
            if(conn != null)
            {
                conn.disconnect();
            }

        }

        return folderFeedUrl;
    }
    
    

    private String GetFolderFeedUrlFromInputStream(InputStream inputStream)
    {
        String folderFeedUrl = "";
        
        Document createFolderDoc = GetDocumentFromInputStream(inputStream);

        Node newFolderContentNode = createFolderDoc.getElementsByTagName("content").item(0);

        if (newFolderContentNode == null)
        {
            System.out.println("Failed to create a collection");
        }
        else
        {
            //<content type="application/atom+xml;type=feed" src=".../contents"/>
            folderFeedUrl = createFolderDoc.getElementsByTagName("content").item(0)
                    .getAttributes().getNamedItem("src").getNodeValue();
        }
        
        return folderFeedUrl;
    }


    private String SearchForGpsLoggerFolder(String authToken)
    {

        String folderFeedUrl = "";

        String searchUrl = "https://docs.google.com/feeds/default/private/full?title=GPSLogger+For+Android&showfolders=true";
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

            folderFeedUrl = GetFolderFeedUrlFromInputStream(conn.getInputStream());
        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
        finally
        {
            if(conn != null)
            {
                conn.disconnect();
            }
        }

        return folderFeedUrl;
    }

    private Document GetDocumentFromInputStream(InputStream stream)
    {
        Document doc = null;

        try
        {
            DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
            xmlFactory.setNamespaceAware(true);
            DocumentBuilder builder = null;
            builder = xmlFactory.newDocumentBuilder();
            doc = builder.parse(stream);
        }
        catch (Exception e)
        {
            doc = null;
        }

        return doc;
    }


    private boolean IsNullOrEmpty(String gpsLoggerFolderFeed)
    {
        return gpsLoggerFolderFeed == null || gpsLoggerFolderFeed.length() == 0;
    }



}