/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.guacamole.auth.quickconnect.utility;

import java.io.UnsupportedEncodingException;
import java.lang.StringBuilder;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.guacamole.GuacamoleClientException;
import org.apache.guacamole.GuacamoleServerException;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.protocol.GuacamoleConfiguration;

/**
 * A utility class to parse out a URI into the settings necessary
 * to generate a GuacamoleConfiguration object.
 */
public class QCParser {

    /**
     * The default protocol to use if one is not provided in
     * the incoming URI.
     */
    public static final String DEFAULT_URI_PROTOCOL = "ssh";

    /**
     * The default host to use if one is not defined.
     */
    public static final String DEFAULT_URI_HOST = "localhost";

    /**
     * The regex to use to split username and password.
     */
    private static final Pattern userinfoPattern = Pattern.compile("(^[^:]+):?(.*)");

    /**
     * The regex group of the username.
     */
    private static final int USERNAME_GROUP = 1;

    /**
     * THe regex group of the password.
     */
    private static final int PASSWORD_GROUP = 2;

    /**
     * Parse out a URI string and get a GuacamoleConfiguration
     * from that string, or an exception if the parsing fails.
     *
     * @param uri
     *     The string form of the URI to be parsed.
     *
     * @return
     *     A GuacamoleConfiguration using a combination of the parsed
     *     URI values and default values when not specified in the
     *     URI.
     *
     * @throws GuacamoleException
     *     When an error occurs parsing the URI.
     */
    public static GuacamoleConfiguration getConfiguration(String uri)
            throws GuacamoleException {

        // Parse the URI object from provided string.
        URI qcUri;
        try {
            qcUri = new URI(uri);
        }
        catch (URISyntaxException e) {
            throw new GuacamoleClientException("Invalid URI Syntax", e);
        }

        // Break out individual components of the URI.
        String protocol = qcUri.getScheme();
        String host = qcUri.getHost();
        int port = qcUri.getPort();
        String userInfo = qcUri.getUserInfo();
        String query = qcUri.getQuery();

        // Generate a new GuacamoleConfiguration
        GuacamoleConfiguration qcConfig = new GuacamoleConfiguration();

        // Check for provided protocol or use default
        if (protocol != null && !protocol.isEmpty())
            qcConfig.setProtocol(protocol);
        else
            qcConfig.setProtocol(DEFAULT_URI_PROTOCOL);

        // Check for provided port number
        if (port > 0)
            qcConfig.setParameter("port", Integer.toString(port));

        // Check for provided host or use default
        if (host != null && !host.isEmpty())
            qcConfig.setParameter("hostname", host);
        else
            qcConfig.setParameter("hostname", DEFAULT_URI_HOST);

        // Look for extra query parameters and parse them out.
        if (query != null && !query.isEmpty()) {
            try {
                Map<String, String> queryParams = parseQueryString(query);
                if (queryParams != null)
                    for (Map.Entry<String, String> entry: queryParams.entrySet())
                        qcConfig.setParameter(entry.getKey(), entry.getValue());
            }
            catch (UnsupportedEncodingException e) {
                throw new GuacamoleServerException("Unexpected lack of UTF-8 encoding support.", e);
            }
        }

        // Look for the username and password and parse them out.
        if (userInfo != null && !userInfo.isEmpty()) {

            try {
                Map<String, String> userInfoParams = parseUserInfo(userInfo);

                if (userInfoParams.containsKey("username"))
                    qcConfig.setParameter("username", userInfoParams.get("username"));

                if (userInfoParams.containsKey("password"))
                    qcConfig.setParameter("password", userInfoParams.get("password"));
            }
            catch (UnsupportedEncodingException e) {
                throw new GuacamoleServerException("Unexpected lack of UTF-8 encoding support.", e);
            }
        }

        return qcConfig;
        
    }

    /**
     * Parse the given string for parameter key/value pairs and return
     * a map with the parameters.
     *
     * @param queryStr
     *     The query string to parse for key/value pairs.
     *
     * @return
     *     A map with the key/value pairs.
     *
     * @throws UnsupportedEncodingException
     *     If Java lacks UTF-8 support.
     */
    public static Map<String, String> parseQueryString(String queryStr)
            throws UnsupportedEncodingException {

        // Split the query string into the pairs
        List<String> paramList = Arrays.asList(queryStr.split("&"));
        Map<String, String> parameters = new HashMap<String,String>();

        // Loop through key/value pairs and put them in the Map.
        for (String param : paramList) {
            String[] paramArray = param.split("=", 2);
            parameters.put(URLDecoder.decode(paramArray[0], "UTF-8"),
                           URLDecoder.decode(paramArray[1], "UTF-8"));
        }

        return parameters;
    }

    /**
     * Parse the given string for username and password values,
     * and return a map containing the username, password
     * or both.
     *
     * @param userInfo
     *     The string to parse for username/password values.
     * 
     * @return
     *     A map with the username, password, or both.
     *
     * @throws UnsupportedEncodingException
     *     If Java lacks UTF-8 support.
     */
    public static Map<String, String> parseUserInfo(String userInfo)
            throws UnsupportedEncodingException {

        Map<String, String> userInfoMap = new HashMap<String, String>();
        Matcher userinfoMatcher = userinfoPattern.matcher(userInfo);

        if (userinfoMatcher.matches()) {
            String username = URLDecoder.decode(
                    userinfoMatcher.group(USERNAME_GROUP), "UTF-8");
            String password = URLDecoder.decode(
                    userinfoMatcher.group(PASSWORD_GROUP), "UTF-8");

            if (username != null && !username.isEmpty())
                userInfoMap.put("username", username);

            if (password != null && !password.isEmpty())
                userInfoMap.put("password", password);
        }

        return userInfoMap;

    }

    /**
     * Given a GuacamoleConfiguration object, generate a name
     * for the configuration based on the protocol, host, user
     * and port in the configuration, and return the string value.
     *
     * @param config
     *     The GuacamoleConfiguration object to use to generate
     *     the name.
     *
     * @return
     *     The String value of the name that is generated.
     *
     * @throws GuacamoleException
     *     If an error occurs getting items in the configuration.
     */
    public static String getName(GuacamoleConfiguration config)
            throws GuacamoleException {

        if (config == null)
            return null;

        String protocol = config.getProtocol();
        String host = config.getParameter("hostname");
        String port = config.getParameter("port");
        String user = config.getParameter("username");

        StringBuilder name = new StringBuilder();

        if (protocol != null && !protocol.isEmpty())
            name.append(protocol).append("://");

        if (user != null && !user.isEmpty())
            name.append(user).append("@");

        if (host != null && !host.isEmpty())
            name.append(host);

        if (port != null && !port.isEmpty())
            name.append(":").append(port);

        name.append("/");

        return name.toString();
    }

}
