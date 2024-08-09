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

import jp.openstandia.connector.notion.testutil.AbstractTest;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.framework.spi.SearchResultsHandler;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static jp.openstandia.connector.notion.NotionUserHandler.USER_OBJECT_CLASS;
import static jp.openstandia.connector.util.Utils.toZoneDateTimeForEpochMilli;
import static org.junit.jupiter.api.Assertions.*;

class UserTest extends AbstractTest {

    @Test
    void addUser() {
        // Given
        String userId = "12345";
        String userName = "foo";
        String formatted = "Foo Bar";
        String familyName = "Bar";
        String givenName = "Foo";

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(userName));
        attrs.add(AttributeBuilder.build("name.formatted", formatted));
        attrs.add(AttributeBuilder.build("name.familyName", familyName));
        attrs.add(AttributeBuilder.build("name.givenName", givenName));

        AtomicReference<NotionUserModel> created = new AtomicReference<>();
        mockClient.createUser = ((user) -> {
            created.set(user);

            return new Uid(userId, new Name(userName));
        });

        // When
        Uid uid = connector.create(USER_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        // Then
        assertEquals(userId, uid.getUidValue());
        assertEquals(userName, uid.getNameHintValue());

        NotionUserModel newUser = created.get();
        assertEquals(userName, newUser.userName);
        assertEquals(userName, newUser.userName);
        assertEquals(formatted, newUser.name.formatted);
        assertEquals(familyName, newUser.name.familyName);
        assertEquals(givenName, newUser.name.givenName);
    }

    @Test
    void addUserButAlreadyExists() {
        // Given
        String userName = "foo";

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(userName));

        mockClient.createUser = ((user) -> {
            throw new AlreadyExistsException("");
        });

        // When
        Throwable expect = null;
        try {
            Uid uid = connector.create(USER_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());
        } catch (Throwable t) {
            expect = t;
        }

        // Then
        assertNotNull(expect);
        assertTrue(expect instanceof AlreadyExistsException);
    }

    @Test
    void updateUser() {
        // Given
        String currentUserName = "hoge";

        String userId = "12345";
        String userName = "foo";
        String formatted = "Foo Bar";
        String familyName = "Bar";
        String givenName = "Foo";
        ;

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build(Name.NAME, userName));
        modifications.add(AttributeDeltaBuilder.build("name.formatted", formatted));
        modifications.add(AttributeDeltaBuilder.build("name.familyName", familyName));
        modifications.add(AttributeDeltaBuilder.build("name.givenName", givenName));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        AtomicReference<PatchOperationsModel> updated = new AtomicReference<>();
        mockClient.patchUser = ((u, operations) -> {
            targetUid.set(u);
            updated.set(operations);
        });

        // When
        Set<AttributeDelta> affected = connector.updateDelta(USER_OBJECT_CLASS, new Uid(userId, new Name(currentUserName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        assertEquals(userId, targetUid.get().getUidValue());
        assertEquals(currentUserName, targetUid.get().getNameHintValue());

        PatchOperationsModel operation = updated.get();
        assertNotNull(operation.operations);
        assertTrue(operation.operations.stream().anyMatch(op -> op.path.equals("userName") && op.value.equals("foo")));
        assertTrue(operation.operations.stream().anyMatch(op -> op.path.equals("name.formatted") && op.value.equals("Foo Bar")));
        assertTrue(operation.operations.stream().anyMatch(op -> op.path.equals("name.familyName") && op.value.equals("Bar")));
        assertTrue(operation.operations.stream().anyMatch(op -> op.path.equals("name.givenName") && op.value.equals("Foo")));
    }

    @Test
    void updateUserWithNoValues() {
        ConnectorFacade connector = newFacade(configuration);

        // Given
        String currentUserName = "foo";

        String userId = "12345";
        String userName = "foo";

        Set<AttributeDelta> modifications = new HashSet<>();
        // IDM sets empty list to remove the single value
        modifications.add(AttributeDeltaBuilder.build("name.formatted", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("name.familyName", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("name.givenName", Collections.emptyList()));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        AtomicReference<PatchOperationsModel> updated = new AtomicReference<>();
        mockClient.patchUser = ((u, operations) -> {
            targetUid.set(u);
            updated.set(operations);
        });

        // When
        Set<AttributeDelta> affected = connector.updateDelta(USER_OBJECT_CLASS, new Uid(userId, new Name(currentUserName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        assertEquals(userId, targetUid.get().getUidValue());
        assertEquals(userName, targetUid.get().getNameHintValue());

        PatchOperationsModel operation = updated.get();
        assertNotNull(operation.operations);
        // Notion API treats empty string as removing the value, but name.* attributes are not supported removing with empty string
        assertTrue(operation.operations.stream().anyMatch(op -> op.op.equals("replace") && op.path.equals("name.formatted") && op.value.equals("")));
        assertTrue(operation.operations.stream().anyMatch(op -> op.op.equals("replace") & op.path.equals("name.familyName") && op.value.equals("")));
        assertTrue(operation.operations.stream().anyMatch(op -> op.op.equals("replace") & op.path.equals("name.givenName") && op.value.equals("")));
    }

    @Test
    void updateUserButNotFound() {
        // Given
        String currentUserName = "foo";

        String userId = "12345";
        String formatted = "Foo Bar";

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("name.formatted", formatted));

        mockClient.patchUser = ((u, operations) -> {
            throw new UnknownUidException();
        });

        // When
        Throwable expect = null;
        try {
            connector.updateDelta(USER_OBJECT_CLASS, new Uid(userId, new Name(currentUserName)), modifications, new OperationOptionsBuilder().build());
        } catch (Throwable t) {
            expect = t;
        }

        // Then
        assertNotNull(expect);
        assertTrue(expect instanceof UnknownUidException);
    }

    @Test
    void getUserByUid() {
        // Given
        String userId = "12345";
        String userName = "foo";
        String formatted = "Foo Bar";
        String familyName = "Bar";
        String givenName = "Foo";

        String createdDate = "1711676390675"; // "2024-03-29T01:39:50.675Z";
        String updatedDate = "1712709439214"; // "2024-04-12T00:37:19.214Z";

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getUserByUid = ((u) -> {
            targetUid.set(u);

            NotionUserModel result = new NotionUserModel();
            result.id = userId;
            result.userName = userName;
            result.name = new NotionUserModel.Name();
            result.name.formatted = formatted;
            result.name.familyName = familyName;
            result.name.givenName = givenName;

            result.meta = new NotionUserModel.Meta();
            result.meta.created = createdDate;
            result.meta.lastModified = updatedDate;

            return result;
        });
        AtomicReference<String> targetName = new AtomicReference<>();
        AtomicReference<Integer> targetPageSize = new AtomicReference<>();

        // When
        ConnectorObject result = connector.getObject(USER_OBJECT_CLASS, new Uid(userId, new Name(userName)), defaultGetOperation());

        // Then
        assertEquals(USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals(userId, result.getUid().getUidValue());
        assertEquals(userName, result.getName().getNameValue());
        assertEquals(formatted, singleAttr(result, "name.formatted"));
        assertEquals(familyName, singleAttr(result, "name.familyName"));
        assertEquals(givenName, singleAttr(result, "name.givenName"));

        assertEquals(toZoneDateTimeForEpochMilli(createdDate), singleAttr(result, "meta.created"));
        assertEquals(toZoneDateTimeForEpochMilli(updatedDate), singleAttr(result, "meta.lastModified"));

        assertNull(targetName.get());
        assertNull(targetPageSize.get());
    }

    @Test
    void getUserByUidWithEmpty() {
        ConnectorFacade connector = newFacade(configuration);

        // Given
        String userId = "12345";
        String userName = "foo";
        String createdDate = "1712894620306";
        String updatedDate = "1712894620306";

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getUserByUid = ((u) -> {
            targetUid.set(u);

            NotionUserModel result = new NotionUserModel();
            result.id = userId;
            result.userName = userName;
            result.name = new NotionUserModel.Name(); // Empty name
            result.meta = new NotionUserModel.Meta();
            result.meta.created = createdDate;
            result.meta.lastModified = updatedDate;
            return result;
        });

        // When
        ConnectorObject result = connector.getObject(USER_OBJECT_CLASS, new Uid(userId, new Name(userName)), defaultGetOperation());

        // Then
        assertEquals(USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals(userId, result.getUid().getUidValue());
        assertEquals(userName, result.getName().getNameValue());
        assertNull(result.getAttributeByName("name.formatted"));
        assertNull(result.getAttributeByName("name.familyName"));
        assertNull(result.getAttributeByName("name.givenName"));
    }

    @Test
    void getUserByUidWithNotFound() {
        ConnectorFacade connector = newFacade(configuration);

        // Given
        String userId = "12345";
        String userName = "foo";

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getUserByUid = ((u) -> {
            targetUid.set(u);
            return null;
        });

        // When
        ConnectorObject result = connector.getObject(USER_OBJECT_CLASS, new Uid(userId, new Name(userName)), defaultGetOperation());

        // Then
        assertNull(result);
        assertNotNull(targetUid.get());
    }

    @Test
    void getUserByName() {
        // Given
        String userId = "12345";
        String userName = "foo";
        String formatted = "Foo Bar";
        String givenName = "Foo";
        String familyName = "Bar";
        String createdDate = "1711676390675"; // "2024-03-29T01:39:50.675Z";
        String updatedDate = "1712709439214"; // "2024-04-12T00:37:19.214Z";

        AtomicReference<Name> targetName = new AtomicReference<>();
        mockClient.getUserByName = ((u) -> {
            targetName.set(u);

            NotionUserModel result = new NotionUserModel();
            result.id = userId;
            result.userName = userName;
            result.name = new NotionUserModel.Name();
            result.name.formatted = formatted;
            result.name.givenName = givenName;
            result.name.familyName = familyName;
            result.meta = new NotionUserModel.Meta();
            result.meta.created = createdDate;
            result.meta.lastModified = updatedDate;
            return result;
        });

        // When
        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(USER_OBJECT_CLASS, FilterBuilder.equalTo(new Name(userName)), handler, defaultSearchOperation());

        // Then
        assertEquals(1, results.size());
        ConnectorObject result = results.get(0);
        assertEquals(USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals(userId, result.getUid().getUidValue());
        assertEquals(userName, result.getName().getNameValue());
        assertEquals(formatted, singleAttr(result, "name.formatted"));
        assertEquals(givenName, singleAttr(result, "name.givenName"));
        assertEquals(familyName, singleAttr(result, "name.familyName"));
        assertEquals(toZoneDateTimeForEpochMilli(createdDate), singleAttr(result, "meta.created"));
        assertEquals(toZoneDateTimeForEpochMilli(updatedDate), singleAttr(result, "meta.lastModified"));
    }

    @Test
    void getUsers() {
        // Given
        String userId = "12345";
        String userName = "foo";
        String formatted = "Foo Bar";
        String createdDate = "1711676390675"; // "2024-03-29T01:39:50.675Z";
        String updatedDate = "1712709439214"; // "2024-04-12T00:37:19.214Z";

        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        AtomicReference<Integer> targetOffset = new AtomicReference<>();
        mockClient.getUsers = ((h, size, offset) -> {
            targetPageSize.set(size);
            targetOffset.set(offset);

            NotionUserModel result = new NotionUserModel();
            result.id = userId;
            result.userName = userName;
            result.name = new NotionUserModel.Name();
            result.name.formatted = formatted;
            result.meta = new NotionUserModel.Meta();
            result.meta.created = createdDate;
            result.meta.lastModified = updatedDate;
            h.handle(result);

            return 1;
        });

        // When
        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(USER_OBJECT_CLASS, null, handler, defaultSearchOperation());

        // Then
        assertEquals(1, results.size());
        ConnectorObject result = results.get(0);
        assertEquals(USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals(userId, result.getUid().getUidValue());
        assertEquals(userName, result.getName().getNameValue());
        assertEquals(formatted, singleAttr(result, "name.formatted"));
        assertEquals(toZoneDateTimeForEpochMilli(createdDate), singleAttr(result, "meta.created"));
        assertEquals(toZoneDateTimeForEpochMilli(updatedDate), singleAttr(result, "meta.lastModified"));

        assertEquals(20, targetPageSize.get(), "Not page size in the operation option");
        assertEquals(1, targetOffset.get());
    }

    @Test
    void getUsersZero() {
        // Given
        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        AtomicReference<Integer> targetOffset = new AtomicReference<>();
        mockClient.getUsers = ((h, size, offset) -> {
            targetPageSize.set(size);
            targetOffset.set(offset);

            return 0;
        });

        // When
        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(USER_OBJECT_CLASS, null, handler, defaultSearchOperation());

        // Then
        assertEquals(0, results.size());
        assertEquals(20, targetPageSize.get(), "Not default page size in the configuration");
        assertEquals(1, targetOffset.get());
    }

    @Test
    void getUsersTwo() {
        // Given
        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        AtomicReference<Integer> targetOffset = new AtomicReference<>();
        mockClient.getUsers = ((h, size, offset) -> {
            targetPageSize.set(size);
            targetOffset.set(offset);

            NotionUserModel result = new NotionUserModel();
            result.id = "1";
            result.userName = "a";
            result.name = new NotionUserModel.Name();
            result.name.formatted = "A";
            result.meta = new NotionUserModel.Meta();
            result.meta.created = "1711676390675";
            result.meta.lastModified = "1711676390675";
            h.handle(result);

            result = new NotionUserModel();
            result.id = "2";
            result.userName = "b";
            result.name = new NotionUserModel.Name();
            result.name.formatted = "B";
            result.meta = new NotionUserModel.Meta();
            result.meta.created = "1711676390675";
            result.meta.lastModified = "1711676390675";
            h.handle(result);

            return 2;
        });

        // When
        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(USER_OBJECT_CLASS, null, handler, defaultSearchOperation());

        // Then
        assertEquals(2, results.size());

        ConnectorObject result = results.get(0);
        assertEquals(USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals("1", result.getUid().getUidValue());
        assertEquals("a", result.getName().getNameValue());

        result = results.get(1);
        assertEquals(USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals("2", result.getUid().getUidValue());
        assertEquals("b", result.getName().getNameValue());

        assertEquals(20, targetPageSize.get(), "Not default page size in the configuration");
        assertEquals(1, targetOffset.get());
    }

    @Test
    void count() {
        // Given
        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        AtomicReference<Integer> targetOffset = new AtomicReference<>();
        mockClient.getUsers = ((h, size, offset) -> {
            targetPageSize.set(size);
            targetOffset.set(offset);

            NotionUserModel result = new NotionUserModel();
            result.id = "1";
            result.userName = "a";
            result.name = new NotionUserModel.Name();
            result.name.formatted = "A";
            result.meta = new NotionUserModel.Meta();
            result.meta.created = "1711676390675";
            result.meta.lastModified = "1711676390675";
            h.handle(result);

            return 10;
        });

        // When
        List<ConnectorObject> results = new ArrayList<>();
        AtomicReference<SearchResult> searchResult = new AtomicReference<>();
        SearchResultsHandler handler = new SearchResultsHandler() {
            @Override
            public void handleResult(SearchResult result) {
                searchResult.set(result);
            }

            @Override
            public boolean handle(ConnectorObject connectorObject) {
                results.add(connectorObject);
                return true;
            }
        };
        connector.search(USER_OBJECT_CLASS, null, handler, countSearchOperation());

        // Then
        assertEquals(1, results.size());
        assertEquals(1, targetPageSize.get());
        assertEquals(1, targetOffset.get());
        assertEquals(9, searchResult.get().getRemainingPagedResults());
        assertTrue(searchResult.get().isAllResultsReturned());
        assertNull(searchResult.get().getPagedResultsCookie());
    }

    @Test
    void pagedSearch() {
        // Given
        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        AtomicReference<Integer> targetOffset = new AtomicReference<>();
        mockClient.getUsers = ((h, size, offset) -> {
            targetPageSize.set(size);
            targetOffset.set(offset);

            NotionUserModel result = new NotionUserModel();
            result.id = "1";
            result.userName = "a";
            result.name = new NotionUserModel.Name();
            result.name.formatted = "A";
            result.meta = new NotionUserModel.Meta();
            result.meta.created = "1711676390675";
            result.meta.lastModified = "1711676390675";
            h.handle(result);

            return 6;
        });

        // When
        List<ConnectorObject> results = new ArrayList<>();
        AtomicReference<SearchResult> searchResult = new AtomicReference<>();
        SearchResultsHandler handler = new SearchResultsHandler() {
            @Override
            public void handleResult(SearchResult result) {
                searchResult.set(result);
            }

            @Override
            public boolean handle(ConnectorObject connectorObject) {
                results.add(connectorObject);
                return true;
            }
        };
        connector.search(USER_OBJECT_CLASS, null, handler, pagedSearchOperation(6, 1));

        // Then
        assertEquals(1, results.size());
        assertEquals(1, targetPageSize.get());
        assertEquals(6, targetOffset.get());
        assertEquals(0, searchResult.get().getRemainingPagedResults());
        assertTrue(searchResult.get().isAllResultsReturned());
        assertNull(searchResult.get().getPagedResultsCookie());
    }

    @Test
    void deleteUser() {
        // Given
        String userId = "12345";
        String userName = "foo";

        AtomicReference<Uid> deleted = new AtomicReference<>();
        mockClient.deleteUser = ((uid) -> {
            deleted.set(uid);
        });

        // When
        connector.delete(USER_OBJECT_CLASS, new Uid(userId, new Name(userName)), new OperationOptionsBuilder().build());

        // Then
        assertEquals(userId, deleted.get().getUidValue());
        assertEquals(userName, deleted.get().getNameHintValue());
    }

    @Test
    void deleteUserButNotFound() {
        // Given
        String userId = "12345";
        String userName = "foo";

        AtomicReference<Uid> deleted = new AtomicReference<>();
        mockClient.deleteUser = ((uid) -> {
            throw new UnknownUidException();
        });

        // When
        Throwable expect = null;
        try {
            connector.delete(USER_OBJECT_CLASS, new Uid(userId, new Name(userName)), new OperationOptionsBuilder().build());
        } catch (Throwable t) {
            expect = t;
        }

        // Then
        assertNotNull(expect);
        assertTrue(expect instanceof UnknownUidException);
    }
}
