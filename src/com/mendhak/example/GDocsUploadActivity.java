package com.mendhak.example;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.google.api.client.auth.oauth2.draft10.AccessTokenResponse;
import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.googleapis.auth.oauth2.draft10.GoogleAccessProtectedResource;
import com.google.api.client.googleapis.json.JsonCParser;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;

public class GDocsUploadActivity extends Activity
{
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
                OAuth2Client.ClearAccessToken(getApplicationContext());
                SetStatus("Cleared auth token");
            }
        });
        
        buttonAuthorize.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                startActivity(new Intent().setClass(getApplicationContext(), OAuth2AuthorizationActivity.class));
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

    public void SetStatus(String msg)
    {
        TextView tv = (TextView)findViewById(R.id.lblStatus);
        tv.setText(msg);
    }

    public void UploadFileToGoogleDocs()
    {


        //Create an AccessTokenResponse from the stored data
        AccessTokenResponse accessTokenResponse = OAuth2Client.GetAccessToken(getApplicationContext());

        if(accessTokenResponse == null)
        {
            SetStatus("No access token available, you're not authorized");
            return;
        }


        try
        {

            GoogleAccessProtectedResource accessProtectedResource = GetAccessProtectedResource(
                    OAuth2Client.CLIENT_ID, OAuth2Client.CLIENT_SECRET,
                    accessTokenResponse.accessToken,  accessTokenResponse.refreshToken);


            //Slow, but always refresh token.  If it errors, then we're not authorized.
            accessProtectedResource.refreshToken();

            String gpsLoggerFolderFeed;


            //Search for the 'GPSLogger For Android' folder.
            HttpRequest searchForCollectionRequest = GetSearchForFolderRequest(accessProtectedResource);
            HttpResponse searchForCollectionResponse = searchForCollectionRequest.execute();

            gpsLoggerFolderFeed = GetFolderFeedUrl(searchForCollectionResponse);

            if (IsNullOrEmpty(gpsLoggerFolderFeed))
            {
                //Not found, create the folder
                HttpRequest createFolderRequest = GetCreateFolderRequest(accessProtectedResource);
                HttpResponse createFolderResponse = createFolderRequest.execute();
                gpsLoggerFolderFeed = GetFolderFeedUrl(createFolderResponse);
            }

            //Now that you have the collection feed url, search for the file 20120304.gpx
            HttpRequest searchForFileRequest = GetSearchForFileRequest(gpsLoggerFolderFeed, "20120304.gpx",
                    accessProtectedResource);
            HttpResponse searchForFileResponse = searchForFileRequest.execute();

            Document fileSearchNode = GetDocumentFromHttpResponse(searchForFileResponse);

            String fileEditUrl = GetFileEditUrl(fileSearchNode);

            if (IsNullOrEmpty(fileEditUrl))
            {
                //The file doesn't exist, you must create it.

                //Start a 'create file' session
                String fileUploadUrl = GetFileUploadUrl(fileSearchNode);

                String createFileAtomXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<entry xmlns=\"http://www.w3.org/2005/Atom\" xmlns:docs=\"http://schemas.google.com/docs/2007\">\n" +
                        "  <!-- Replace the following line appropriately to create another type of resource. -->\n" +
                        "  <category scheme=\"http://schemas.google.com/g/2005#kind\"\n" +
                        "      term=\"http://schemas.google.com/docs/2007#document\"/>\n" +
                        "  <title>" + "20120304.gpx" + "</title>\n" +
                        "</entry>";

                HttpRequest createFileSessionRequest = GetCreateFileRequest("20120304.gpx", createFileAtomXml,
                        fileUploadUrl + "?convert=false", accessProtectedResource);

                HttpResponse createFileSessionResponse = createFileSessionRequest.execute();

                //Get the actual file upload location
                String newUploadLocation = createFileSessionResponse.headers.location;

                //Write to actual file upload location
                String fileContents = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<a>1</a>";

                HttpRequest createFileRequest = GetCreateFileRequest("20120304.gpx", fileContents, newUploadLocation,
                        accessProtectedResource);

                HttpResponse createFileResponse = createFileRequest.execute();

            }
            else
            {
                //The file exists.  Update contents.

                String fileContents = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<b>2</b>";

                HttpRequest updateFileSessionRequest = GetUpdateFileRequest("20120304.gpx", fileContents,
                        fileEditUrl + "?convert=false", accessProtectedResource);

                HttpResponse updateFileSessionResponse = updateFileSessionRequest.execute();
                String newUpdateLocation = updateFileSessionResponse.headers.location;


                HttpRequest updateFileRequest = GetUpdateFileRequest("20120304.gpx", fileContents,
                        newUpdateLocation, accessProtectedResource);

                HttpResponse updateFileResponse = updateFileRequest.execute();

            }

            SetStatus("Uploaded");
        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());

        }


    }

    


    private GoogleAccessProtectedResource GetAccessProtectedResource(String clientId, String clientSecret,
                                                                     String accessToken, String refreshToken)
    {

        final JsonFactory jsonFactory = new JacksonFactory();
        HttpTransport transport = new NetHttpTransport();

        final GoogleAccessProtectedResource accessProtectedResource = new GoogleAccessProtectedResource(
                accessToken,
                transport,
                jsonFactory,
                clientId,
                clientSecret,
                refreshToken);

        return accessProtectedResource;
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

    private Document GetDocumentFromHttpResponse(HttpResponse searchForFileResponse)
    {
        Document doc = null;

        try
        {
            DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
            xmlFactory.setNamespaceAware(true);
            DocumentBuilder builder = null;
            builder = xmlFactory.newDocumentBuilder();
            doc = builder.parse(searchForFileResponse.getContent());
        }
        catch (Exception e)
        {
            doc = null;
        }

        return doc;

    }

    private HttpRequest GetSearchForFileRequest(String gpsLoggerFolderFeed, String fileTitle,
                                                final GoogleAccessProtectedResource accessProtectedResource) throws IOException
    {


        final JsonFactory jsonFactory = new JacksonFactory();
        HttpTransport transport = new NetHttpTransport();


        HttpRequestFactory httpBasicFactory = transport.createRequestFactory(new HttpRequestInitializer()
        {

            @Override
            public void initialize(HttpRequest request)
            {
                JsonCParser parser = new JsonCParser();
                parser.jsonFactory = jsonFactory;
                request.addParser(parser);
                GoogleHeaders headers = new GoogleHeaders();
                headers.setApplicationName("GPSLogger for Android");
                headers.set("Authorization", "Bearer " + accessProtectedResource.getAccessToken());
                headers.gdataVersion = "3";
                request.headers = headers;
            }
        });

        //https://docs.google.com/feeds/default/private/full/folder:...../contents?title=20120304.gpx
        return httpBasicFactory.buildGetRequest(new GenericUrl(gpsLoggerFolderFeed + "?title=" + fileTitle));
    }

    private HttpRequest GetSearchForFolderRequest(final GoogleAccessProtectedResource accessProtectedResource) throws IOException
    {


        final JsonFactory jsonFactory = new JacksonFactory();
        HttpTransport transport = new NetHttpTransport();

        HttpRequestFactory httpBasicFactory = transport.createRequestFactory(new HttpRequestInitializer()
        {

            @Override
            public void initialize(HttpRequest request)
            {
                JsonCParser parser = new JsonCParser();
                parser.jsonFactory = jsonFactory;
                request.addParser(parser);
                GoogleHeaders headers = new GoogleHeaders();
                headers.setApplicationName("GPSLogger for Android");
                headers.set("Authorization", "Bearer " + accessProtectedResource.getAccessToken());
                headers.gdataVersion = "3";
                request.headers = headers;
            }
        });

        return httpBasicFactory.buildGetRequest(
                new GenericUrl("https://docs.google.com/feeds/default/private/full?title=GPSLogger+For+Android&showfolders=true"));
    }


    private String GetFolderFeedUrl(HttpResponse createFolderResponse)
    {
        String gpsLoggerFolderFeed = "";

        try
        {
            Document createFolderDoc = GetDocumentFromHttpResponse(createFolderResponse);

            Node newFolderContentNode = createFolderDoc.getElementsByTagName("content").item(0);

            if (newFolderContentNode == null)
            {
                System.out.println("Failed to create a collection");
            }
            else
            {
                //<content type="application/atom+xml;type=feed" src=".../contents"/>
                gpsLoggerFolderFeed = createFolderDoc.getElementsByTagName("content").item(0)
                        .getAttributes().getNamedItem("src").getNodeValue();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return gpsLoggerFolderFeed;
    }


    private HttpRequest GetUpdateFileRequest(final String fileName, final String payload, String updateUrl,
                                             final GoogleAccessProtectedResource accessProtectedResource) throws IOException
    {

        final JsonFactory jsonFactory = new JacksonFactory();
        HttpTransport transport = new NetHttpTransport();


        HttpRequestFactory updateFactory = transport.createRequestFactory( new HttpRequestInitializer()
        {
            @Override
            public void initialize(HttpRequest request) throws IOException
            {
                // set the parser
                JsonCParser parser = new JsonCParser();
                parser.jsonFactory = jsonFactory;
                request.addParser(parser);
                // set up the Google headers
                GoogleHeaders headers = new GoogleHeaders();
                headers.setApplicationName("GPSLogger for Android");
                headers.set("Authorization", "Bearer " + accessProtectedResource.getAccessToken());
                headers.set("X-Upload-Content-Length", payload.length());
                headers.set("X-Upload-Content-Type", "text/xml");
                headers.set("Content-Type", "text/xml");
                headers.set("Content-Length", String.valueOf(payload.length()));
                headers.setSlugFromFileName(fileName);
                headers.set("If-Match", "*");
                headers.gdataVersion = "3";
                headers.putAll(request.headers);
                request.headers = headers;
            }
        });

        return updateFactory.buildPutRequest(new GenericUrl(updateUrl), new HttpContent()
        {
            @Override
            public long getLength() throws IOException
            {
                return payload.length();
            }

            @Override
            public String getEncoding()
            {
                return null;
            }

            @Override
            public String getType()
            {
                return "text/xml";
            }

            @Override
            public void writeTo(OutputStream outputStream) throws IOException
            {
                outputStream.write(payload.getBytes());
            }

            @Override
            public boolean retrySupported()
            {
                return false;
            }
        });

    }


    private HttpRequest GetCreateFileRequest(final String fileName, final String payload, String uploadUrl,
                                             final GoogleAccessProtectedResource accessProtectedResource
    ) throws IOException
    {


        final JsonFactory jsonFactory = new JacksonFactory();
        HttpTransport transport = new NetHttpTransport();


        HttpRequestFactory uploadFactory = transport.createRequestFactory(new HttpRequestInitializer()
        {

            @Override
            public void initialize(HttpRequest request)
            {
                // set the parser
                JsonCParser parser = new JsonCParser();
                parser.jsonFactory = jsonFactory;
                request.addParser(parser);
                // set up the Google headers
                GoogleHeaders headers = new GoogleHeaders();
                headers.setApplicationName("GPSLogger for Android");
                headers.set("Authorization", "Bearer " + accessProtectedResource.getAccessToken());
                headers.set("X-Upload-Content-Length", "0");
                headers.set("X-Upload-Content-Type", "text/xml");
                headers.set("Content-Type", "text/xml");
                headers.set("Content-Length", String.valueOf(payload.length()));
                headers.setSlugFromFileName(fileName);
                headers.gdataVersion = "3";
                headers.putAll(request.headers);
                request.headers = headers;
            }
        });

        return uploadFactory.buildPostRequest(new GenericUrl(uploadUrl), new HttpContent()
        {
            @Override
            public long getLength() throws IOException
            {
                return payload.length();
            }

            @Override
            public String getEncoding()
            {
                return null;
            }

            @Override
            public String getType()
            {
                return "text/xml";
            }

            @Override
            public void writeTo(OutputStream outputStream) throws IOException
            {
                outputStream.write(payload.getBytes());
            }

            @Override
            public boolean retrySupported()
            {
                return false;
            }
        });

    }


    private HttpRequest GetCreateFolderRequest(final GoogleAccessProtectedResource accessProtectedResource) throws IOException
    {


        final JsonFactory jsonFactory = new JacksonFactory();
        HttpTransport transport = new NetHttpTransport();


        HttpRequestFactory httpBasicFactory = transport.createRequestFactory(new HttpRequestInitializer()
        {

            @Override
            public void initialize(HttpRequest request)
            {
                JsonCParser parser = new JsonCParser();
                parser.jsonFactory = jsonFactory;
                request.addParser(parser);
                GoogleHeaders headers = new GoogleHeaders();
                headers.setApplicationName("GPSLogger for Android");
                headers.set("Authorization", "Bearer " + accessProtectedResource.getAccessToken());
                headers.gdataVersion = "3";
                request.headers = headers;
            }
        });

        return httpBasicFactory.buildPostRequest(
                new GenericUrl("https://docs.google.com/feeds/default/private/full"), new HttpContent()
        {

            String createXml = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                    "<entry xmlns=\"http://www.w3.org/2005/Atom\">\n" +
                    "  <category scheme=\"http://schemas.google.com/g/2005#kind\"\n" +
                    "      term=\"http://schemas.google.com/docs/2007#folder\"/>\n" +
                    "  <title>GPSLogger For Android</title>\n" +
                    "</entry>";

            @Override
            public long getLength() throws IOException
            {
                return createXml.length();
            }

            @Override
            public String getEncoding()
            {
                return null;
            }

            @Override
            public String getType()
            {
                return "application/atom+xml";
            }

            @Override
            public void writeTo(OutputStream outputStream) throws IOException
            {
                outputStream.write(createXml.getBytes());
            }

            @Override
            public boolean retrySupported()
            {
                return false;
            }
        });
    }

    private String inputStreamToString(InputStream is)
    {
        String line = "";
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
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        // Return full string
        return total.toString();
    }

    private boolean IsNullOrEmpty(String gpsLoggerFolderFeed)
    {
        return gpsLoggerFolderFeed == null || gpsLoggerFolderFeed.length() == 0;
    }

    
}
