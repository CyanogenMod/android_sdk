/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sdklib.internal.repository;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.util.Pair;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.params.AuthPNames;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.ProxySelectorRoutePlanner;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class holds methods for adding URLs management.
 * @see #openUrl(String, ITaskMonitor, Header[])
 */
public class UrlOpener {

    public static class CanceledByUserException extends Exception {
        private static final long serialVersionUID = -7669346110926032403L;

        public CanceledByUserException(String message) {
            super(message);
        }
    }

    private static Map<String, UserCredentials> sRealmCache =
            new HashMap<String, UserCredentials>();

    /**
     * Opens a URL. It can be a simple URL or one which requires basic
     * authentication.
     * <p/>
     * Tries to access the given URL. If http response is either
     * {@code HttpStatus.SC_UNAUTHORIZED} or
     * {@code HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED}, asks for
     * login/password and tries to authenticate into proxy server and/or URL.
     * <p/>
     * This implementation relies on the Apache Http Client due to its
     * capabilities of proxy/http authentication. <br/>
     * Proxy configuration is determined by {@link ProxySelectorRoutePlanner} using the JVM proxy
     * settings by default.
     * <p/>
     * For more information see: <br/>
     * - {@code http://hc.apache.org/httpcomponents-client-ga/} <br/>
     * - {@code http://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/impl/conn/ProxySelectorRoutePlanner.html}
     * <p/>
     * There's a very simple realm cache implementation.
     * Login/Password for each realm are stored in a static {@link Map}.
     * Before asking the user the method verifies if the information is already
     * available in the memory cache.
     *
     * @param url the URL string to be opened.
     * @param monitor {@link ITaskMonitor} which is related to this URL
     *            fetching.
     * @param headers An optional array of HTTP headers to use in the GET request.
     * @return Returns an {@link InputStream} holding the URL content and
     *      the HttpResponse (locale, headers and an status line).
     *      This never returns null; an exception is thrown instead in case of
     *      error or if the user canceled an authentication dialog.
     * @throws IOException Exception thrown when there are problems retrieving
     *             the URL or its content.
     * @throws CanceledByUserException Exception thrown if the user cancels the
     *              authentication dialog.
     */
    static @NonNull Pair<InputStream, HttpResponse> openUrl(
            @NonNull String url,
            @NonNull ITaskMonitor monitor,
            @Nullable Header[] headers)
        throws IOException, CanceledByUserException {

        try {
            return openWithHttpClient(url, monitor, headers);

        } catch (ClientProtocolException e) {
            // If the protocol is not supported by HttpClient (e.g. file:///),
            // revert to the standard java.net.Url.open

            URL u = new URL(url);
            InputStream is = u.openStream();
            HttpResponse response = new BasicHttpResponse(
                    new ProtocolVersion(u.getProtocol(), 1, 0),
                    200, "");
            return Pair.of(is, response);
        }
    }

    private static @NonNull Pair<InputStream, HttpResponse> openWithHttpClient(
            @NonNull String url,
            @NonNull ITaskMonitor monitor,
            Header[] headers)
            throws IOException, ClientProtocolException, CanceledByUserException {
        UserCredentials result = null;
        String realm = null;

        // use the simple one
        final DefaultHttpClient httpClient = new DefaultHttpClient();

        // create local execution context
        HttpContext localContext = new BasicHttpContext();
        final HttpGet httpGet = new HttpGet(url);
        if (headers != null) {
            for (Header header : headers) {
                httpGet.addHeader(header);
            }
        }

        // retrieve local java configured network in case there is the need to
        // authenticate a proxy
        ProxySelectorRoutePlanner routePlanner = new ProxySelectorRoutePlanner(
                    httpClient.getConnectionManager().getSchemeRegistry(),
                    ProxySelector.getDefault());
        httpClient.setRoutePlanner(routePlanner);

        // Set preference order for authentication options.
        // In particular, we don't add AuthPolicy.SPNEGO, which is given preference over NTLM in
        // servers that support both, as it is more secure. However, we don't seem to handle it
        // very well, so we leave it off the list.
        // See http://hc.apache.org/httpcomponents-client-ga/tutorial/html/authentication.html for
        // more info.
        List<String> authpref = new ArrayList<String>();
        authpref.add(AuthPolicy.BASIC);
        authpref.add(AuthPolicy.DIGEST);
        authpref.add(AuthPolicy.NTLM);
        httpClient.getParams().setParameter(AuthPNames.PROXY_AUTH_PREF, authpref);
        httpClient.getParams().setParameter(AuthPNames.TARGET_AUTH_PREF, authpref);

        boolean trying = true;
        // loop while the response is being fetched
        while (trying) {
            // connect and get status code
            HttpResponse response = httpClient.execute(httpGet, localContext);
            int statusCode = response.getStatusLine().getStatusCode();

            // check whether any authentication is required
            AuthState authenticationState = null;
            if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
                // Target host authentication required
                authenticationState = (AuthState) localContext
                        .getAttribute(ClientContext.TARGET_AUTH_STATE);
            }
            if (statusCode == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED) {
                // Proxy authentication required
                authenticationState = (AuthState) localContext
                        .getAttribute(ClientContext.PROXY_AUTH_STATE);
            }
            if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_NOT_MODIFIED) {
                // in case the status is OK and there is a realm and result,
                // cache it
                if (realm != null && result != null) {
                    sRealmCache.put(realm, result);
                }
            }

            // there is the need for authentication
            if (authenticationState != null) {

                // get scope and realm
                AuthScope authScope = authenticationState.getAuthScope();

                // If the current realm is different from the last one it means
                // a pass was performed successfully to the last URL, therefore
                // cache the last realm
                if (realm != null && !realm.equals(authScope.getRealm())) {
                    sRealmCache.put(realm, result);
                }

                realm = authScope.getRealm();

                // in case there is cache for this Realm, use it to authenticate
                if (sRealmCache.containsKey(realm)) {
                    result = sRealmCache.get(realm);
                } else {
                    // since there is no cache, request for login and password
                    result = monitor.displayLoginCredentialsPrompt("Site Authentication",
                            "Please login to the following domain: " + realm +
                            "\n\nServer requiring authentication:\n" + authScope.getHost());
                    if (result == null) {
                        throw new CanceledByUserException("User canceled login dialog.");
                    }
                }

                // retrieve authentication data
                String user = result.getUserName();
                String password = result.getPassword();
                String workstation = result.getWorkstation();
                String domain = result.getDomain();

                // proceed in case there is indeed a user
                if (user != null && user.length() > 0) {
                    Credentials credentials = new NTCredentials(user, password,
                            workstation, domain);
                    httpClient.getCredentialsProvider().setCredentials(authScope, credentials);
                    trying = true;
                } else {
                    trying = false;
                }
            } else {
                trying = false;
            }

            HttpEntity entity = response.getEntity();

            if (entity != null) {
                if (trying) {
                    // in case another pass to the Http Client will be performed, close the entity.
                    entity.getContent().close();
                } else {
                    // since no pass to the Http Client is needed, retrieve the
                    // entity's content.

                    // Note: don't use something like a BufferedHttpEntity since it would consume
                    // all content and store it in memory, resulting in an OutOfMemory exception
                    // on a large download.
                    InputStream is = new FilterInputStream(entity.getContent()) {
                        @Override
                        public void close() throws IOException {
                            // Since Http Client is no longer needed, close it.

                            // Bug #21167: we need to tell http client to shutdown
                            // first, otherwise the super.close() would continue
                            // downloading and not return till complete.

                            httpClient.getConnectionManager().shutdown();
                            super.close();
                        }
                    };

                    HttpResponse outResponse = new BasicHttpResponse(response.getStatusLine());
                    outResponse.setHeaders(response.getAllHeaders());
                    outResponse.setLocale(response.getLocale());

                    return Pair.of(is, outResponse);
                }
            } else if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
                // It's ok to not have an entity (e.g. nothing to download) for a 304
                HttpResponse outResponse = new BasicHttpResponse(response.getStatusLine());
                outResponse.setHeaders(response.getAllHeaders());
                outResponse.setLocale(response.getLocale());

                return Pair.of(null, outResponse);
            }
        }

        // We get here if we did not succeed. Callers do not expect a null result.
        httpClient.getConnectionManager().shutdown();
        throw new FileNotFoundException(url);
    }
}
