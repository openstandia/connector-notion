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
package jp.openstandia.connector.notion.testutil;

import jp.openstandia.connector.notion.NotionGroupModel;
import jp.openstandia.connector.notion.NotionRESTClient;
import jp.openstandia.connector.notion.NotionUserModel;
import jp.openstandia.connector.notion.PatchOperationsModel;
import jp.openstandia.connector.util.QueryHandler;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.Set;

public class MockClient extends NotionRESTClient {

    private static MockClient INSTANCE = new MockClient();

    // User
    public MockFunction<NotionUserModel, Uid> createUser;
    public MockBiConsumer<Uid, PatchOperationsModel> patchUser;
    public MockFunction<Uid, NotionUserModel> getUserByUid;
    public MockFunction<Name, NotionUserModel> getUserByName;
    public MockTripleFunction<QueryHandler<NotionUserModel>, Integer, Integer, Integer> getUsers;
    public MockConsumer<Uid> deleteUser;

    // Group
    public MockFunction<NotionGroupModel, Uid> createGroup;
    public MockBiConsumer<Uid, PatchOperationsModel> patchGroup;
    public MockBiConsumer<Uid, String> renameGroup;
    public MockFunction<Uid, NotionGroupModel> getGroupByUid;
    public MockFunction<Name, NotionGroupModel> getGroupByName;
    public MockTripleFunction<QueryHandler<NotionGroupModel>, Integer, Integer, Integer> getGroups;
    public MockConsumer<Uid> deleteGroup;

    public boolean closed = false;

    public void init() {
        INSTANCE = new MockClient();
    }

    private MockClient() {
    }

    public static MockClient instance() {
        return INSTANCE;
    }

    @Override
    public void test() {
    }

    @Override
    public void close() {
        closed = true;
    }

    // User

    @Override
    public Uid createUser(NotionUserModel newUser) throws AlreadyExistsException {
        return createUser.apply(newUser);
    }

    @Override
    public void patchUser(Uid uid, PatchOperationsModel operations) {
        patchUser.accept(uid, operations);
    }

    @Override
    public NotionUserModel getUser(Uid uid, OperationOptions options, Set<String> fetchFieldsSet) throws UnknownUidException {
        return getUserByUid.apply(uid);
    }

    @Override
    public NotionUserModel getUser(Name name, OperationOptions options, Set<String> fetchFieldsSet) throws UnknownUidException {
        return getUserByName.apply(name);
    }

    @Override
    public int getUsers(QueryHandler<NotionUserModel> handler, OperationOptions options, Set<String> fetchFieldsSet, int pageSize, int pageOffset) {
        return getUsers.apply(handler, pageSize, pageOffset);
    }

    @Override
    public void deleteUser(Uid uid) {
        deleteUser.accept(uid);
    }

    // Group

    @Override
    public Uid createGroup(NotionGroupModel group) throws AlreadyExistsException {
        return createGroup.apply(group);
    }

    @Override
    public void patchGroup(Uid uid, PatchOperationsModel operations) {
        patchGroup.accept(uid, operations);
    }

    @Override
    public NotionGroupModel getGroup(Uid uid, OperationOptions options, Set<String> fetchFieldsSet) {
        return getGroupByUid.apply(uid);
    }

    @Override
    public NotionGroupModel getGroup(Name name, OperationOptions options, Set<String> fetchFieldsSet) {
        return getGroupByName.apply(name);
    }

    @Override
    public int getGroups(QueryHandler<NotionGroupModel> handler, OperationOptions options, Set<String> fetchFieldsSet, int pageSize, int pageOffset) {
        return getGroups.apply(handler, pageSize, pageOffset);
    }

    @Override
    public void deleteGroup(Uid uid) {
        deleteGroup.accept(uid);
    }

    // Mock Interface

    @FunctionalInterface
    public interface MockFunction<T, R> {
        R apply(T t);
    }

    @FunctionalInterface
    public interface MockBiFunction<T, U, R> {
        R apply(T t, U u);
    }

    @FunctionalInterface
    public interface MockTripleFunction<T, U, V, R> {
        R apply(T t, U u, V v);
    }

    @FunctionalInterface
    public interface MockConsumer<T> {
        void accept(T t);
    }

    @FunctionalInterface
    public interface MockBiConsumer<T, U> {
        void accept(T t, U u);
    }

    @FunctionalInterface
    public interface MockTripleConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }
}
