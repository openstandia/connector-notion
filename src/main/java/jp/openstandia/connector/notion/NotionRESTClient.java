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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jp.openstandia.connector.util.AbstractRESTClient;
import jp.openstandia.connector.util.QueryHandler;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.io.IOException;
import java.util.*;

import static jp.openstandia.connector.notion.NotionGroupHandler.GROUP_OBJECT_CLASS;
import static jp.openstandia.connector.notion.NotionUserHandler.USER_OBJECT_CLASS;

public class NotionRESTClient extends AbstractRESTClient<NotionConfiguration> {
    private static final Log LOG = Log.getLog(NotionRESTClient.class);
    private ErrorHandler ERROR_HANDLER = new NotionErrorHandler();

    private String testEndpoint;
    private String userEndpoint;
    private String groupEndpoint;

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class UserListBody {
        public int totalResults;
        public int startIndex;
        public int itemPerPage;
        @JsonProperty("Resources")
        public List<NotionUserModel> resources;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GroupListBody {
        public int totalResults;
        public int startIndex;
        public int itemPerPage;
        @JsonProperty("Resources")
        public List<NotionGroupModel> resources;
    }

    static class NotionErrorHandler implements ErrorHandler {
        @Override
        public boolean inNotAuthenticated(Response response) {
            // {"schemas":["urn:ietf:params:scim:api:messages:2.0:Error"],"status":"401","detail":"UnauthorizedError: Invalid authentication"}
            return response.code() == 401;
        }

        @Override
        public boolean isAlreadyExists(Response response) {
            // {"schemas":["urn:ietf:params:scim:api:messages:2.0:Error"],"status":"409","scimType":"uniqueness"}
            return response.code() == 409;
        }

        @Override
        public boolean isInvalidRequest(Response response) {
            // {"schemas":["urn:ietf:params:scim:api:messages:2.0:Error"],"status":"400","scimType":"invalidSyntax","detail":"body failed validation: body.startIndex should be ≥ `1` or `undefined`, instead was `0`."}
            return response.code() == 400;
        }

        @Override
        public boolean isNotFound(Response response) {
            // {"schemas":["urn:ietf:params:scim:api:messages:2.0:Error"],"status":"404"}
            return response.code() == 404;
        }

        @Override
        public boolean isOk(Response response) {
            return response.code() == 200 || response.code() == 201 || response.code() == 204;
        }

        @Override
        public boolean isServerError(Response response) {
            return response.code() >= 500 && response.code() <= 599;
        }
    }

    public void init(String instanceName, NotionConfiguration configuration, OkHttpClient httpClient) {
        super.init(instanceName, configuration, httpClient, ERROR_HANDLER, false, "startIndex", "count");
        this.testEndpoint = configuration.getBaseURL() + "/scim/v2/ServiceProviderConfig";
        this.userEndpoint = configuration.getBaseURL() + "/scim/v2/Users";
        this.groupEndpoint = configuration.getBaseURL() + "/scim/v2/Groups";
    }

    public void test() {
        try (Response response = get(testEndpoint)) {
            if (response.code() != 200) {
                // Something wrong..
                String body = response.body().string();
                throw new ConnectionFailedException(String.format("Failed %s test response. statusCode: %s, body: %s",
                        instanceName,
                        response.code(),
                        body));
            }

            LOG.info("{0} connector's connection test is OK", instanceName);

        } catch (IOException e) {
            throw new ConnectionFailedException(String.format("Cannot connect to %s REST API", instanceName), e);
        }
    }

    // User

    public Uid createUser(NotionUserModel newUser) throws AlreadyExistsException {
        NotionUserModel created = callCreate(USER_OBJECT_CLASS, userEndpoint, newUser, newUser.userName, (response) -> {
            try {
                return MAPPER.readValue(response.body().byteStream(), NotionUserModel.class);
            } catch (IOException e) {
                throw new ConnectorIOException(String.format("Cannot parse %s REST API Response", instanceName), e);
            }
        });
        return new Uid(created.id, created.userName);
    }

    public NotionUserModel getUser(Uid uid, OperationOptions options, Set<String> fetchFieldsSet) throws UnknownUidException {
        try (Response response = callRead(USER_OBJECT_CLASS, userEndpoint, uid)) {
            if (response == null) {
                return null;
            }
            NotionUserModel user = MAPPER.readValue(response.body().byteStream(), NotionUserModel.class);
            return user;

        } catch (IOException e) {
            throw new ConnectorIOException(String.format("Cannot parse %s REST API Response", instanceName), e);
        }
    }

    public NotionUserModel getUser(Name name, OperationOptions options, Set<String> fetchFieldsSet) throws UnknownUidException {
        Map<String, String> params = new HashMap<>();
        params.put("filter", formatFilter("userName eq \"%s\"", name.getNameValue()));

        try (Response response = callSearch(USER_OBJECT_CLASS, userEndpoint, params)) {
            UserListBody list = MAPPER.readValue(response.body().byteStream(), UserListBody.class);
            if (list.resources == null || list.resources.size() != 1) {
                LOG.info("The {0} user is not found. userName={1}", instanceName, name.getNameValue());
                return null;
            }
            return list.resources.get(0);

        } catch (IOException e) {
            throw new ConnectorIOException(String.format("Cannot parse %s REST API Response", instanceName), e);
        }
    }

    private String formatFilter(String filter, String... values) {
        Object[] escaped = Arrays.stream(values)
                .map(v -> v.replace("\"", "\\\""))
                .toArray();
        return String.format(filter, escaped);
    }

    public void patchUser(Uid uid, PatchOperationsModel operations) {
        callPatch(USER_OBJECT_CLASS, userEndpoint + "/" + uid.getUidValue(), uid, operations);
    }

    public void deleteUser(Uid uid) {
        callDelete(USER_OBJECT_CLASS, userEndpoint + "/" + uid.getUidValue(), uid, null);
    }

    public int getUsers(QueryHandler<NotionUserModel> handler, OperationOptions options, Set<String> fetchFieldsSet, int pageSize, int pageOffset) {
        // ConnId starts from 1, 0 means no offset (requested all data)
        if (pageOffset < 1) {
            return getAll(handler, pageSize, (start, size) -> {
                Map<String, String> params = new HashMap<>();
                params.put(offsetKey, String.valueOf(start));
                params.put(countKey, String.valueOf(size));

                try (Response response = callSearch(USER_OBJECT_CLASS, userEndpoint, params)) {
                    UserListBody list = MAPPER.readValue(response.body().byteStream(), UserListBody.class);
                    return list.resources;

                } catch (IOException e) {
                    throw new ConnectorIOException(String.format("Cannot parse %s REST API Response", instanceName), e);
                }
            });
        }

        // Pagination
        // Notion(SCIM v2.0) starts from 1
        int start = resolveOffset(pageOffset);

        Map<String, String> params = new HashMap<>();
        params.put(offsetKey, String.valueOf(start));
        params.put(countKey, String.valueOf(pageSize));

        try (Response response = callSearch(USER_OBJECT_CLASS, userEndpoint, params)) {
            UserListBody list = MAPPER.readValue(response.body().byteStream(), UserListBody.class);
            for (NotionUserModel user : list.resources) {
                if (!handler.handle(user)) {
                    break;
                }
            }
            return list.totalResults;

        } catch (IOException e) {
            throw new ConnectorIOException(String.format("Cannot parse %s REST API Response", instanceName), e);
        }
    }

    // Group

    public Uid createGroup(NotionGroupModel newGroup) throws AlreadyExistsException {
        NotionGroupModel created = callCreate(GROUP_OBJECT_CLASS, groupEndpoint, newGroup, newGroup.displayName, (response) -> {
            try {
                return MAPPER.readValue(response.body().byteStream(), NotionGroupModel.class);
            } catch (IOException e) {
                throw new ConnectorIOException(String.format("Cannot parse %s REST API Response", instanceName), e);
            }
        });

        return new Uid(created.id, newGroup.displayName);
    }

    public void patchGroup(Uid uid, PatchOperationsModel operations) {
        callPatch(GROUP_OBJECT_CLASS, groupEndpoint + "/" + uid.getUidValue(), uid, operations);
    }

    public NotionGroupModel getGroup(Uid uid, OperationOptions options, Set<String> fetchFieldsSet) throws UnknownUidException {
        try (Response response = callRead(GROUP_OBJECT_CLASS, groupEndpoint, uid)) {
            if (response == null) {
                return null;
            }
            NotionGroupModel group = MAPPER.readValue(response.body().byteStream(), NotionGroupModel.class);
            return group;

        } catch (IOException e) {
            throw new ConnectorIOException(String.format("Cannot parse %s REST API Response", instanceName), e);
        }
    }

    public NotionGroupModel getGroup(Name name, OperationOptions options, Set<String> fetchFieldsSet) {
        Map<String, String> params = new HashMap<>();
        params.put("filter", formatFilter("displayName eq \"%s\"", name.getNameValue()));

        try (Response response = callSearch(GROUP_OBJECT_CLASS, groupEndpoint, params)) {
            GroupListBody list = MAPPER.readValue(response.body().byteStream(), GroupListBody.class);
            if (list.resources == null || list.resources.size() != 1) {
                LOG.info("The {0} group is not found. displayName={1}", instanceName, name.getNameValue());
                return null;
            }
            return list.resources.get(0);

        } catch (IOException e) {
            throw new ConnectorIOException(String.format("Cannot parse %s REST API Response", instanceName), e);
        }
    }

    public int getGroups(QueryHandler<NotionGroupModel> handler, OperationOptions options, Set<String> fetchFieldsSet, int pageSize, int pageOffset) {
        // ConnId starts from 1, 0 means no offset (requested all data)
        if (pageOffset < 1) {
            return getAll(handler, pageSize, (start, size) -> {
                Map<String, String> params = new HashMap<>();
                params.put(offsetKey, String.valueOf(start));
                params.put(countKey, String.valueOf(size));

                try (Response response = callSearch(GROUP_OBJECT_CLASS, groupEndpoint, params)) {
                    GroupListBody list = MAPPER.readValue(response.body().byteStream(), GroupListBody.class);
                    return list.resources;

                } catch (IOException e) {
                    throw new ConnectorIOException(String.format("Cannot parse %s REST API Response", instanceName), e);
                }
            });
        }

        // Pagination
        int start = resolveOffset(pageOffset);

        Map<String, String> params = new HashMap<>();
        params.put(offsetKey, String.valueOf(start));
        params.put(countKey, String.valueOf(pageSize));

        try (Response response = callSearch(GROUP_OBJECT_CLASS, groupEndpoint, params)) {
            GroupListBody list = MAPPER.readValue(response.body().byteStream(), GroupListBody.class);
            for (NotionGroupModel group : list.resources) {
                if (!handler.handle(group)) {
                    break;
                }
            }
            return list.totalResults;

        } catch (IOException e) {
            throw new ConnectorIOException(String.format("Cannot parse %s REST API Response", instanceName), e);
        }
    }

    public void deleteGroup(Uid uid) {
        callDelete(GROUP_OBJECT_CLASS, groupEndpoint + "/" + uid.getUidValue(), uid, null);
    }
}
