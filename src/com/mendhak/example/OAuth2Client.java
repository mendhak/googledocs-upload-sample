package com.mendhak.example;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.google.api.client.auth.oauth2.draft10.AccessTokenResponse;

public class OAuth2Client
{

    //Obviously, this ClientID and ClientSecret are examples, use your own as these are revoked

    /** Value of the "Client ID" shown under "Client ID for installed applications". */
    public static final String CLIENT_ID = "889382808911.apps.googleusercontent.com";

    /** Value of the "Client secret" shown under "Client ID for installed applications". */
    public static final String CLIENT_SECRET = "idzyP8I8ynjNgri_XSqHkTrx";

    /** OAuth 2 scope to use */
    //https://docs.google.com/feeds/ gives full access to the user's documents
    public static final String SCOPE = "https://docs.google.com/feeds/";

    /** OAuth 2 redirect uri */
    public static final String REDIRECT_URI = "http://localhost";


    public static void SaveAccessToken(AccessTokenResponse accessTokenResponse, Context applicationContext)
    {
        //Store in preferences, we'll use it later.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("GDOCS_ACCESS_TOKEN",accessTokenResponse.accessToken );
        editor.putLong("GDOCS_EXPIRES_IN", accessTokenResponse.expiresIn);
        editor.putString("GDOCS_REFRESH_TOKEN", accessTokenResponse.refreshToken);
        editor.putString("GDOCS_SCOPE", accessTokenResponse.scope);
        editor.commit();
    }
    
    public static void ClearAccessToken(Context applicationContext)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        SharedPreferences.Editor editor = prefs.edit();

        editor.remove("GDOCS_ACCESS_TOKEN");
        editor.remove("GDOCS_EXPIRES_IN");
        editor.remove("GDOCS_REFRESH_TOKEN");
        editor.remove("GDOCS_SCOPE");
        editor.commit();
    }
    
    public static AccessTokenResponse GetAccessToken(Context applicationContext)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        AccessTokenResponse atr = new AccessTokenResponse();
        
        atr.accessToken = prefs.getString("GDOCS_ACCESS_TOKEN","");
        atr.expiresIn = prefs.getLong("GDOCS_EXPIRES_IN",0);
        atr.refreshToken = prefs.getString("GDOCS_REFRESH_TOKEN","");
        atr.scope =  prefs.getString("GDOCS_SCOPE","");
        
        if(atr.accessToken.length() == 0 || atr.refreshToken.length() == 0)
        {
            return null;
        }
        else
        {
            return atr;
        }

    }
}
