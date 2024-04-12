/*
 *  Copyright Nomura Research Institute, Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package jp.openstandia.connector.notion;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class NotionConfiguration extends AbstractConfiguration {

    private String baseURL;
    private GuardedString token;
    private String httpProxyHost;
    private int httpProxyPort = 3128;
    private String httpProxyUser;
    private GuardedString httpProxyPassword;
    private int defaultQueryPageSize = 50;
    private int connectionTimeoutInMilliseconds = 10000;
    private int readTimeoutInMilliseconds = 10000;
    private int writeTimeoutInMilliseconds = 10000;
    private Set<String> ignoreGroup = new HashSet<>();
    private boolean uniqueCheckGroupDisplayNameEnabled = true;

    @ConfigurationProperty(
            order = 1,
            displayMessageKey = "Notion Base URL",
            helpMessageKey = "Notion Base URL (e.g. https://api.notion.com).",
            required = true,
            confidential = false)
    public String getBaseURL() {
        return baseURL;
    }

    public void setBaseURL(String baseURL) {
        if (baseURL.endsWith("/")) {
            baseURL = baseURL.substring(0, baseURL.lastIndexOf("/"));
        }
        this.baseURL = baseURL;
    }

    @ConfigurationProperty(
            order = 2,
            displayMessageKey = "Token",
            helpMessageKey = "Token for the Notion API.",
            required = true,
            confidential = true)
    public GuardedString getToken() {
        return token;
    }

    public void setToken(GuardedString token) {
        this.token = token;
    }

    @ConfigurationProperty(
            order = 3,
            displayMessageKey = "HTTP Proxy Host",
            helpMessageKey = "Hostname for the HTTP Proxy.",
            required = false,
            confidential = false)
    public String getHttpProxyHost() {
        return httpProxyHost;
    }

    public void setHttpProxyHost(String httpProxyHost) {
        this.httpProxyHost = httpProxyHost;
    }

    @ConfigurationProperty(
            order = 4,
            displayMessageKey = "HTTP Proxy Port",
            helpMessageKey = "Port for the HTTP Proxy. (Default: 3128)",
            required = false,
            confidential = false)
    public int getHttpProxyPort() {
        return httpProxyPort;
    }

    public void setHttpProxyPort(int httpProxyPort) {
        this.httpProxyPort = httpProxyPort;
    }

    @ConfigurationProperty(
            order = 5,
            displayMessageKey = "HTTP Proxy User",
            helpMessageKey = "Username for the HTTP Proxy Authentication.",
            required = false,
            confidential = false)
    public String getHttpProxyUser() {
        return httpProxyUser;
    }

    public void setHttpProxyUser(String httpProxyUser) {
        this.httpProxyUser = httpProxyUser;
    }

    @ConfigurationProperty(
            order = 6,
            displayMessageKey = "HTTP Proxy Password",
            helpMessageKey = "Password for the HTTP Proxy Authentication.",
            required = false,
            confidential = true)
    public GuardedString getHttpProxyPassword() {
        return httpProxyPassword;
    }

    public void setHttpProxyPassword(GuardedString httpProxyPassword) {
        this.httpProxyPassword = httpProxyPassword;
    }

    @ConfigurationProperty(
            order = 7,
            displayMessageKey = "Default Query Page Size",
            helpMessageKey = "Number of results to return per page. (Default: 50)",
            required = false,
            confidential = false)
    public int getDefaultQueryPageSize() {
        return defaultQueryPageSize;
    }

    public void setDefaultQueryPageSize(int defaultQueryPageSize) {
        this.defaultQueryPageSize = defaultQueryPageSize;
    }

    @ConfigurationProperty(
            order = 8,
            displayMessageKey = "Connection Timeout (in milliseconds)",
            helpMessageKey = "Connection timeout when connecting to Notion. (Default: 10000)",
            required = false,
            confidential = false)
    public int getConnectionTimeoutInMilliseconds() {
        return connectionTimeoutInMilliseconds;
    }

    public void setConnectionTimeoutInMilliseconds(int connectionTimeoutInMilliseconds) {
        this.connectionTimeoutInMilliseconds = connectionTimeoutInMilliseconds;
    }

    @ConfigurationProperty(
            order = 9,
            displayMessageKey = "Connection Read Timeout (in milliseconds)",
            helpMessageKey = "Connection read timeout when connecting to Notion. (Default: 10000)",
            required = false,
            confidential = false)
    public int getReadTimeoutInMilliseconds() {
        return readTimeoutInMilliseconds;
    }

    public void setReadTimeoutInMilliseconds(int readTimeoutInMilliseconds) {
        this.readTimeoutInMilliseconds = readTimeoutInMilliseconds;
    }

    @ConfigurationProperty(
            order = 10,
            displayMessageKey = "Connection Write Timeout (in milliseconds)",
            helpMessageKey = "Connection write timeout when connecting to Notion. (Default: 10000)",
            required = false,
            confidential = false)
    public int getWriteTimeoutInMilliseconds() {
        return writeTimeoutInMilliseconds;
    }

    public void setWriteTimeoutInMilliseconds(int writeTimeoutInMilliseconds) {
        this.writeTimeoutInMilliseconds = writeTimeoutInMilliseconds;
    }

    @ConfigurationProperty(
            order = 11,
            displayMessageKey = "Ignore Group",
            helpMessageKey = "Define the group displayName to be ignored when fetching group membership. The displayName is case-insensitive.",
            required = false,
            confidential = false)
    public String[] getIgnoreGroup() {
        return ignoreGroup.toArray(new String[0]);
    }

    public void setIgnoreGroup(String[] ignoreGroup) {
        // To lower case for case-insensitive check
        this.ignoreGroup = Arrays.stream(ignoreGroup).map(String::toLowerCase).collect(Collectors.toSet());
    }

    /**
     * Returns the configured ignore group set which are converted to lower case for case-insensitive matching.
     *
     * @return
     */
    public Set<String> getIgnoreGroupSet() {
        return ignoreGroup;
    }

    @ConfigurationProperty(
            order = 12,
            displayMessageKey = "Unique check group displayName",
            helpMessageKey = "When set true, enables the unique check on the displayName of Group during creation. (Default: true)",
            required = false,
            confidential = false)
    public boolean isUniqueCheckGroupDisplayNameEnabled() {
        return uniqueCheckGroupDisplayNameEnabled;
    }

    public void setUniqueCheckGroupDisplayNameEnabled(boolean uniqueCheckGroupDisplayNameEnabled) {
        this.uniqueCheckGroupDisplayNameEnabled = uniqueCheckGroupDisplayNameEnabled;
    }

    @Override
    public void validate() {
        if (baseURL == null) {
            throw new ConfigurationException("Notion Base URL is required");
        }
        if (token == null) {
            throw new ConfigurationException("Notion token is required");
        }
    }
}
