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
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.ContainsAllValuesFilter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static jp.openstandia.connector.notion.NotionGroupHandler.GROUP_OBJECT_CLASS;
import static org.junit.jupiter.api.Assertions.*;

class GroupTest extends AbstractTest {

    @Test
    void addGroup() {
        // Given
        String groupId = "1";
        String displayName = "foo";
        String member1 = "a1074ce4-b7e0-4454-975e-37ca2c1e8936";

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(displayName));
        attrs.add(AttributeBuilder.build("members.User.value", member1));

        AtomicReference<NotionGroupModel> created = new AtomicReference<>();
        mockClient.createGroup = ((g) -> {
            created.set(g);

            return new Uid(groupId, new Name(displayName));
        });
        mockClient.getGroupByName = ((name) -> {
            return null;
        });

        // When
        Uid uid = connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        // Then
        assertEquals(groupId, uid.getUidValue());
        assertEquals(displayName, uid.getNameHintValue());

        NotionGroupModel newGroup = created.get();
        assertEquals(displayName, newGroup.displayName);
        assertNotNull(newGroup.members);
        assertEquals(1, newGroup.members.size());
        assertEquals(member1, newGroup.members.get(0).value);
    }

    @Test
    void addGroupButAlreadyExists() {
        // Given
        String groupId = "1";
        String displayName = "foo";

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(displayName));

        mockClient.getGroupByName = ((name) -> {
            // With default connector configuration, unique check displayName is enabled
            throw new AlreadyExistsException();
        });

        // When
        Throwable expect = null;
        try {
            Uid uid = connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());
        } catch (Throwable t) {
            expect = t;
        }

        // Then
        assertNotNull(expect);
        assertTrue(expect instanceof AlreadyExistsException);
    }

    @Test
    void updateGroup() {
        // Given
        String currentId = "1";
        String currentDisplayName = "foo";

        String displayName = "bar";

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build(Name.NAME, displayName));

        AtomicReference<Uid> targetUid1 = new AtomicReference<>();
        AtomicReference<PatchOperationsModel> updated = new AtomicReference<>();
        mockClient.patchGroup = ((u, operation) -> {
            targetUid1.set(u);
            updated.set(operation);
        });

        // When
        Set<AttributeDelta> affected = connector.updateDelta(GROUP_OBJECT_CLASS, new Uid(currentId, new Name(currentDisplayName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        assertEquals(currentId, targetUid1.get().getUidValue());
        assertEquals(currentDisplayName, targetUid1.get().getNameHintValue());

        PatchOperationsModel operation = updated.get();
        assertNotNull(operation.operations);
        assertTrue(operation.operations.stream().anyMatch(op -> op.op.equals("replace") && op.path.equals("displayName") && op.value.equals(displayName)));
    }

    @Test
    void updateGroupMembers() {
        // Given
        String currentId = "1";
        String currentDisplayName = "foo";

        String memberAdd1 = "a1074ce4-b7e0-4454-975e-37ca2c1e8936";
        String memberRemove2 = "550e8400-e29b-41d4-a716-446655440000";

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("members.User.value", Collections.singletonList(memberAdd1), Collections.singletonList(memberRemove2)));

        AtomicReference<Uid> targetUid1 = new AtomicReference<>();
        AtomicReference<PatchOperationsModel> updated = new AtomicReference<>();
        mockClient.patchGroup = ((u, operation) -> {
            targetUid1.set(u);
            updated.set(operation);
        });

        // When
        Set<AttributeDelta> affected = connector.updateDelta(GROUP_OBJECT_CLASS, new Uid(currentId, new Name(currentDisplayName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        PatchOperationsModel operation = updated.get();
        assertNotNull(operation.operations);
        assertEquals(2, operation.operations.size());
        assertTrue(operation.operations.stream().anyMatch(op -> op.op.equals("add") && op.path.equals("members")
                && op.value instanceof List && ((List<PatchOperationsModel.Member>) op.value).size() == 1
                && ((List<PatchOperationsModel.Member>) op.value).get(0).value.equals(memberAdd1)));
        assertTrue(operation.operations.stream().anyMatch(op -> op.op.equals("remove") && op.path.equals("members")
                && op.value instanceof List && ((List<PatchOperationsModel.Member>) op.value).size() == 1
                && ((List<PatchOperationsModel.Member>) op.value).get(0).value.equals(memberRemove2)));
    }

    @Test
    void updateGroupMembersWithMultiple() {
        // Given
        String currentId = "1";
        String currentDisplayName = "foo";

        String memberAdd1 = "a1074ce4-b7e0-4454-975e-37ca2c1e8936";
        String memberAdd2 = "550e8400-e29b-41d4-a716-446655440000";
        String memberRemove1 = "176a78cf-4e1a-4f58-b79a-51f79acc50aa";
        String memberRemove2 = "822af548-1875-45d3-825e-e61b8576202a";

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("members.User.value", Arrays.asList(memberAdd1, memberAdd2), Arrays.asList(memberRemove1, memberRemove2)));

        AtomicReference<Uid> targetUid1 = new AtomicReference<>();
        AtomicReference<PatchOperationsModel> updated = new AtomicReference<>();
        mockClient.patchGroup = ((u, operation) -> {
            targetUid1.set(u);
            updated.set(operation);
        });

        // When
        Set<AttributeDelta> affected = connector.updateDelta(GROUP_OBJECT_CLASS, new Uid(currentId, new Name(currentDisplayName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        PatchOperationsModel operation = updated.get();
        assertNotNull(operation.operations);
        assertEquals(2, operation.operations.size());
        assertTrue(operation.operations.stream().anyMatch(op -> op.op.equals("add") && op.path.equals("members")
                && op.value instanceof List && ((List<PatchOperationsModel.Member>) op.value).size() == 2
                && ((List<PatchOperationsModel.Member>) op.value).get(0).value.equals(memberAdd1)));
        assertTrue(operation.operations.stream().anyMatch(op -> op.op.equals("add") && op.path.equals("members")
                && op.value instanceof List && ((List<PatchOperationsModel.Member>) op.value).size() == 2
                && ((List<PatchOperationsModel.Member>) op.value).get(1).value.equals(memberAdd2)));

        assertTrue(operation.operations.stream().anyMatch(op -> op.op.equals("remove") && op.path.equals("members")
                && op.value instanceof List && ((List<PatchOperationsModel.Member>) op.value).size() == 2
                && ((List<PatchOperationsModel.Member>) op.value).get(0).value.equals(memberRemove1)));
        assertTrue(operation.operations.stream().anyMatch(op -> op.op.equals("remove") && op.path.equals("members")
                && op.value instanceof List && ((List<PatchOperationsModel.Member>) op.value).size() == 2
                && ((List<PatchOperationsModel.Member>) op.value).get(1).value.equals(memberRemove2)));
    }

    @Test
    void updateGroupButNotFound() {
        // Given
        String currentId = "1";
        String currentDisplayName = "foo";

        String displayName = "bar";

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build(Name.NAME, displayName));

        mockClient.patchGroup = ((u, operation) -> {
            throw new UnknownUidException();
        });

        // When
        Throwable expect = null;
        try {
            connector.updateDelta(GROUP_OBJECT_CLASS, new Uid(currentId, new Name(displayName)), modifications, new OperationOptionsBuilder().build());
        } catch (Throwable t) {
            expect = t;
        }

        // Then
        assertNotNull(expect);
        assertTrue(expect instanceof UnknownUidException);
    }

    @Test
    void getGroupByUid() {
        // Given
        String currentId = "1";
        String currentDisplayName = "foo";
        String currentMember1 = "a1074ce4-b7e0-4454-975e-37ca2c1e8936";

        String createdDate = "1711676390675"; // "2024-03-29T01:39:50.675Z";
        String updatedDate = "1712709439214"; // "2024-04-12T00:37:19.214Z";

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getGroupByUid = ((u) -> {
            targetUid.set(u);

            NotionGroupModel result = new NotionGroupModel();
            result.id = currentId;
            result.displayName = currentDisplayName;
            result.members = new ArrayList<>();
            NotionGroupModel.Member member = new NotionGroupModel.Member();
            member.value = currentMember1;
            member.type = "User";
            member.ref = "https://example.com/scim/v2/User/" + currentMember1;
            result.members.add(member);
            result.meta = new NotionGroupModel.Meta();
            result.meta.created = createdDate;
            result.meta.lastModified = updatedDate;
            return result;
        });

        // When
        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS, new Uid(currentId, new Name(currentDisplayName)), defaultGetOperation());

        // Then
        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals(currentId, result.getUid().getUidValue());
        assertEquals(currentDisplayName, result.getName().getNameValue());
        Attribute memberAttribute = result.getAttributeByName("members.User.value");
        assertNotNull(memberAttribute);
        List<Object> members = memberAttribute.getValue();
        assertEquals(1, members.size());
        assertEquals(currentMember1, members.get(0));
    }

    @Test
    void getGroupByName() {
        // Given
        String currentId = "1";
        String currentDisplayName = "foo";

        String createdDate = "1711676390675"; // "2024-03-29T01:39:50.675Z";
        String updatedDate = "1712709439214"; // "2024-04-12T00:37:19.214Z";

        AtomicReference<Name> targetName = new AtomicReference<>();
        mockClient.getGroupByName = ((name) -> {
            targetName.set(name);

            NotionGroupModel result = new NotionGroupModel();
            result.id = currentId;
            result.displayName = currentDisplayName;
            result.members = new ArrayList<>();
            NotionGroupModel.Member member = new NotionGroupModel.Member();
            result.members.add(member);
            result.meta = new NotionGroupModel.Meta();
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
        connector.search(GROUP_OBJECT_CLASS, FilterBuilder.equalTo(new Name(currentDisplayName)), handler, defaultSearchOperation());

        // Then
        assertEquals(1, results.size());
        ConnectorObject result = results.get(0);
        assertEquals(currentId, result.getUid().getUidValue());
        assertEquals(currentDisplayName, result.getName().getNameValue());
    }

    @Test
    void getGroups() {
        // Given
        String currentId = "1";
        String currentDisplayName = "foo";

        String createdDate = "1711676390675"; // "2024-03-29T01:39:50.675Z";
        String updatedDate = "1712709439214"; // "2024-04-12T00:37:19.214Z";

        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        AtomicReference<Integer> targetOffset = new AtomicReference<>();
        mockClient.getGroups = ((h, size, offset) -> {
            targetPageSize.set(size);
            targetOffset.set(offset);

            NotionGroupModel result = new NotionGroupModel();
            result.id = currentId;
            result.displayName = currentDisplayName;
            result.meta = new NotionGroupModel.Meta();
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
        connector.search(GROUP_OBJECT_CLASS, null, handler, defaultSearchOperation());

        // Then
        assertEquals(1, results.size());
        ConnectorObject result = results.get(0);
        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals(currentId, result.getUid().getUidValue());
        assertEquals(currentDisplayName, result.getName().getNameValue());

        assertEquals(20, targetPageSize.get(), "Not page size in the operation option");
        assertEquals(1, targetOffset.get());
    }

    @Test
    void getGroupsZero() {
        // Given
        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        AtomicReference<Integer> targetOffset = new AtomicReference<>();
        mockClient.getGroups = ((h, size, offset) -> {
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
        connector.search(GROUP_OBJECT_CLASS, null, handler, defaultSearchOperation());

        // Then
        assertEquals(0, results.size());
        assertEquals(20, targetPageSize.get(), "Not default page size in the configuration");
        assertEquals(1, targetOffset.get());
    }

    @Test
    void getGroupsTwo() {
        // Given
        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        AtomicReference<Integer> targetOffset = new AtomicReference<>();
        mockClient.getGroups = ((h, size, offset) -> {
            targetPageSize.set(size);
            targetOffset.set(offset);

            NotionGroupModel result = new NotionGroupModel();
            result.id = "1";
            result.displayName = "a";
            result.meta = new NotionGroupModel.Meta();
            result.meta.created = "1711676390675";
            result.meta.lastModified = "1711676390675";
            h.handle(result);

            result = new NotionGroupModel();
            result.id = "2";
            result.displayName = "b";
            result.meta = new NotionGroupModel.Meta();
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
        connector.search(GROUP_OBJECT_CLASS, null, handler, defaultSearchOperation());

        // Then
        assertEquals(2, results.size());

        ConnectorObject result = results.get(0);
        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals("1", result.getUid().getUidValue());
        assertEquals("a", result.getName().getNameValue());

        result = results.get(1);
        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals("2", result.getUid().getUidValue());
        assertEquals("b", result.getName().getNameValue());

        assertEquals(20, targetPageSize.get(), "Not default page size in the configuration");
        assertEquals(1, targetOffset.get());
    }

    @Test
    void getGroupsByMembers() {
        // Given
        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        AtomicReference<Integer> targetOffset = new AtomicReference<>();
        mockClient.getGroups = ((h, size, offset) -> {
            targetPageSize.set(size);
            targetOffset.set(offset);

            // 1
            NotionGroupModel result = new NotionGroupModel();
            result.id = "1";
            result.displayName = "a";

            result.members = new ArrayList<>();
            NotionGroupModel.Member member = new NotionGroupModel.Member();
            member.value = "user001";
            member.type = "User";
            member.ref = "https://example.com/scim/v2/User/user001";
            result.members.add(member);

            member = new NotionGroupModel.Member();
            member.value = "user002";
            member.type = "User";
            member.ref = "https://example.com/scim/v2/User/user002";
            result.members.add(member);

            member = new NotionGroupModel.Member();
            member.value = "user003";
            member.type = "User";
            member.ref = "https://example.com/scim/v2/User/user003";
            result.members.add(member);

            result.meta = new NotionGroupModel.Meta();
            result.meta.created = "1711676390675";
            result.meta.lastModified = "1711676390675";
            h.handle(result);

            // 2
            result = new NotionGroupModel();
            result.id = "2";
            result.displayName = "b";

            result.members = new ArrayList<>();
            member = new NotionGroupModel.Member();
            member.value = "user001";
            member.type = "User";
            member.ref = "https://example.com/scim/v2/User/user001";
            result.members.add(member);

            result.meta = new NotionGroupModel.Meta();
            result.meta.created = "1711676390675";
            result.meta.lastModified = "1711676390675";
            h.handle(result);

            // 3
            result = new NotionGroupModel();
            result.id = "3";
            result.displayName = "c";

            result.members = new ArrayList<>();
            member = new NotionGroupModel.Member();
            member.value = "user003";
            member.type = "User";
            member.ref = "https://example.com/scim/v2/User/user003";
            result.members.add(member);

            result.meta = new NotionGroupModel.Meta();
            result.meta.created = "1711676390675";
            result.meta.lastModified = "1711676390675";
            h.handle(result);

            // 4
            result = new NotionGroupModel();
            result.id = "4";
            result.displayName = "d";

            result.members = new ArrayList<>();

            result.meta = new NotionGroupModel.Meta();
            result.meta.created = "1711676390675";
            result.meta.lastModified = "1711676390675";
            h.handle(result);

            return 4;
        });

        // When
        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        Attribute user001 = AttributeBuilder.build("members.User.value", Collections.singletonList("user001"));
        connector.search(GROUP_OBJECT_CLASS, new ContainsAllValuesFilter(user001), handler, defaultSearchOperation());

        // Then
        assertEquals(2, results.size());

        ConnectorObject result = results.get(0);
        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals("1", result.getUid().getUidValue());
        assertEquals("a", result.getName().getNameValue());

        result = results.get(1);
        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals("2", result.getUid().getUidValue());
        assertEquals("b", result.getName().getNameValue());

        assertEquals(20, targetPageSize.get(), "Not default page size in the configuration");
        assertEquals(1, targetOffset.get());

        // When
        List<ConnectorObject> results2 = new ArrayList<>();
        ResultsHandler handler2 = connectorObject -> {
            results2.add(connectorObject);
            return true;
        };
        Attribute user001AndUser002 = AttributeBuilder.build("members.User.value", Arrays.asList("user001", "user002"));
        connector.search(GROUP_OBJECT_CLASS, new ContainsAllValuesFilter(user001AndUser002), handler2, defaultSearchOperation());

        // Then
        assertEquals(1, results2.size());

        result = results2.get(0);
        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals("1", result.getUid().getUidValue());
        assertEquals("a", result.getName().getNameValue());
    }

    @Test
    void deleteGroup() {
        // Given
        String currentId = "foo";
        String currentDisplayName = "foo";

        AtomicReference<Uid> deleted = new AtomicReference<>();
        mockClient.deleteGroup = ((u) -> {
            deleted.set(u);
        });

        // When
        connector.delete(GROUP_OBJECT_CLASS, new Uid(currentId, new Name(currentDisplayName)), new OperationOptionsBuilder().build());

        // Then
        assertEquals(currentId, deleted.get().getUidValue());
        assertEquals(currentDisplayName, deleted.get().getNameHintValue());
    }

    @Test
    void deleteGroupButNotFound() {
        // Given
        String currentId = "1";
        String currentDisplayName = "foo";

        mockClient.deleteGroup = ((u) -> {
            throw new UnknownUidException();
        });

        // When
        Throwable expect = null;
        try {
            connector.delete(GROUP_OBJECT_CLASS, new Uid(currentId, new Name(currentDisplayName)), new OperationOptionsBuilder().build());
        } catch (Throwable t) {
            expect = t;
        }

        // Then
        assertNotNull(expect);
        assertTrue(expect instanceof UnknownUidException);
    }
}
