This is a sample Android application which uses the [google-api-java-client](http://code.google.com/p/google-api-java-client/) library to demonstrate uploading a file to Google Docs.

This app will

*  Perform OAuth2 authorization against Google Docs
*  Create a folder (known as a collection) in Google Docs
*  Upload a file to Google Docs (inside that folder)
*  Update a file on Google Docs

I'm creating this sample app because there is very little in the way of examples on uploading to Google Docs and using the google-api-java-client library.


This app uses the [google-api-java-client](http://code.google.com/p/google-api-java-client/) library.

It uses [OAuth2](https://developers.google.com/accounts/docs/OAuth2InstalledApp) for authorization.

It follows [Google Documents List Data API v3.0](http://code.google.com/apis/documents/docs/3.0/developers_guide_protocol.html).