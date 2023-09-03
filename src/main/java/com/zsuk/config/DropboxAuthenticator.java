package com.zsuk.config;

import com.dropbox.core.*;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.oauth.DbxRefreshResult;
import com.dropbox.core.v2.DbxClientV2;

public class DropboxAuthenticator {

    private static final String CLIENT_ID = "zgne9tqix7c50tm";
    private static final String CLIENT_SECRET = "osd0pj8dnccnkzd";
    private static final String DROP_BOX_ACCESS_TOKEN = "sl.BlJcrneimlgqHEH9p_LSayHd92Gpgj1KQZUTZ_Nv5gZLRNafXkoYXcVRGvDSf6Wgwv1gqeJ1MwyqrZuP_Mm8az-h2vDrzQykMa8N1hGlW6IW4854pnvNTEncYWN8kS5NmAAK-u5QBIXr";
    private static final String DROP_BOX_REFRESH_TOKEN = "VRH5c08v--AAAAAAAAAAAaW6qtHxF7BEOPGVtmE-iW0FBI6-FMoVZqRrRErJhQKX";

    private DbxRequestConfig config;
    private DbxWebAuth webAuth;
    private DbxAppInfo appInfo;
    private DbxClientV2 client;
    private String refreshToken;//iNH3kLaMkWMAAAAAAAAAAcZHTTl2soXO0nkNpKGrZmakFHZ-stiXzTRdbFnQjGoO
    private String initialAccessToken;

    public DropboxAuthenticator(String initialAccessToken) {
        this.initialAccessToken = initialAccessToken;
        config = new DbxRequestConfig("invoice-automation-test");
        appInfo = new DbxAppInfo(CLIENT_ID, CLIENT_SECRET);
        webAuth = new DbxWebAuth(config, appInfo);

        this.client = new DbxClientV2(config, initialAccessToken);
        //this.refreshToken = initialRefreshToken;
    }

    public String getAuthoriseUrl() {
        DbxWebAuth.Request request = DbxWebAuth.newRequestBuilder()
                .withNoRedirect()
                .withTokenAccessType(TokenAccessType.OFFLINE)
                .build();
        return this.webAuth.authorize(request);
    }

    public String getAccessToken(String code) throws DbxException {
        DbxAuthFinish authFinish = this.webAuth.finishFromCode(code);
        System.out.println(authFinish.getRefreshToken());
        System.out.println(authFinish.getExpiresAt());
        return authFinish.getAccessToken();
    }

    public DbxClientV2 getClient() throws DbxException {
        if (!isValid(client)) {
            //client.refreshAccessToken();
            refreshToken();
        }

        return client;
    }

    private boolean isValid(DbxClientV2 client) {
        try {
            client.users().getCurrentAccount();
            return true;
        } catch (DbxException e) {
            return false;
        }
    }

    private void refreshToken() throws DbxException {
        DbxCredential credential = new DbxCredential(this.initialAccessToken, 1693516571545l, DROP_BOX_REFRESH_TOKEN, CLIENT_ID, CLIENT_SECRET);
        DbxRefreshResult refreshResult = credential.refresh(config);
        //DbxWebAuth.newRequestBuilder().
        //DbxRefreshResult refreshResult = DbxWebAuth.refreshAccessToken(appInfo, config, refreshToken);
        String newAccessToken = refreshResult.getAccessToken();
        System.out.println("new access token = " + newAccessToken);
        System.out.println("Expires at = " + refreshResult.getExpiresAt());

        //this.refreshToken = refreshResult.getRefreshToken(); // Update the refresh token

        // Update the client with the new access token
        this.client = new DbxClientV2(config, newAccessToken);
    }

    // ... rest of the class ...
}
