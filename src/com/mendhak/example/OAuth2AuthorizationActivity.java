package com.mendhak.example;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.google.api.client.auth.oauth2.draft10.AccessTokenResponse;
import com.google.api.client.googleapis.auth.oauth2.draft10.GoogleAccessTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.draft10.GoogleAuthorizationRequestUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;

import java.io.IOException;

public class OAuth2AuthorizationActivity extends Activity
{
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

    }

    @Override
    public void onResume()
    {

        super.onResume();

        WebView webview = new WebView(this);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.setVisibility(View.VISIBLE);
        setContentView(webview);

        String authorizationUrl = new GoogleAuthorizationRequestUrl(OAuth2Client.CLIENT_ID,
                OAuth2Client.REDIRECT_URI, OAuth2Client.SCOPE).build();

        /* WebViewClient must be set BEFORE calling loadUrl! */
        webview.setWebViewClient(new WebViewClient()
        {

            @Override
            public void onPageStarted(WebView view, String url, Bitmap bitmap)
            {
                System.out.println("onPageStarted : " + url);
            }

            @Override
            public void onPageFinished(WebView view, String url)
            {

                if (url.startsWith(OAuth2Client.REDIRECT_URI))
                {
                    try
                    {
                        if (url.indexOf("code=") != -1)
                        {

                            String code = extractCodeFromUrl(url);

                            AccessTokenResponse accessTokenResponse =
                                    new GoogleAccessTokenRequest.GoogleAuthorizationCodeGrant(
                                            new NetHttpTransport(),
                                            new JacksonFactory(),
                                            OAuth2Client.CLIENT_ID,
                                            OAuth2Client.CLIENT_SECRET,
                                            code,
                                            OAuth2Client.REDIRECT_URI).execute();

                            OAuth2Client.SaveAccessToken(accessTokenResponse, getApplicationContext());

                            view.setVisibility(View.INVISIBLE);
                            startActivity(new Intent(OAuth2AuthorizationActivity.this, GDocsUploadActivity.class));
                        }
                        else if (url.indexOf("error=") != -1)
                        {
                            view.setVisibility(View.INVISIBLE);
                            OAuth2Client.ClearAccessToken(getApplicationContext());
                            startActivity(new Intent(OAuth2AuthorizationActivity.this, GDocsUploadActivity.class));
                        }

                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }

                }
                System.out.println("onPageFinished : " + url);
            }

            private String extractCodeFromUrl(String url)
            {
                return url.substring(OAuth2Client.REDIRECT_URI.length() + 7, url.length());
            }
        });

        webview.loadUrl(authorizationUrl);


    }
}