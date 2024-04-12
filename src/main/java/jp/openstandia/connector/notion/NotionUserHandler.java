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

import jp.openstandia.connector.util.ObjectHandler;
import jp.openstandia.connector.util.SchemaDefinition;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.*;

import java.util.Set;

import static jp.openstandia.connector.util.Utils.toZoneDateTimeForEpochMilli;
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.*;

public class NotionUserHandler implements ObjectHandler {

    public static final ObjectClass USER_OBJECT_CLASS = new ObjectClass("User");

    private static final Log LOGGER = Log.getLog(NotionUserHandler.class);

    private final NotionConfiguration configuration;
    private final NotionRESTClient client;
    private final SchemaDefinition schema;

    public NotionUserHandler(NotionConfiguration configuration, NotionRESTClient client,
                             SchemaDefinition schema) {
        this.configuration = configuration;
        this.client = client;
        this.schema = schema;
    }

    public static SchemaDefinition.Builder createSchema(NotionConfiguration configuration, NotionRESTClient client) {
        SchemaDefinition.Builder<NotionUserModel, PatchOperationsModel, NotionUserModel> sb
                = SchemaDefinition.newBuilder(USER_OBJECT_CLASS, NotionUserModel.class, PatchOperationsModel.class, NotionUserModel.class);

        // Notion supports SCIM v2.0 partially.
        // https://www.notion.so/help/provision-users-and-groups-with-scim

        // __UID__
        // The id for the user. Must be unique and unchangeable.
        sb.addUid("userId",
                SchemaDefinition.Types.STRING_CASE_IGNORE,
                null,
                (source) -> source.id,
                "id",
                NOT_CREATABLE, NOT_UPDATEABLE
        );

        // code (__NAME__)
        // The name for the user. Must be unique and changeable.
        // Also, it's case-sensitive.
        sb.addName("userName",
                SchemaDefinition.Types.STRING,
                (source, dest) -> dest.userName = source,
                (source, dest) -> dest.replace("userName", source),
                (source) -> source.userName,
                null,
                REQUIRED
        );

        // Attributes
        sb.add("name.formatted",
                SchemaDefinition.Types.STRING,
                (source, dest) -> {
                    if (dest.name == null) {
                        dest.name = new NotionUserModel.Name();
                    }
                    dest.name.formatted = source;
                },
                (source, dest) -> dest.replace("name.formatted", source),
                (source) -> source.name != null ? source.name.formatted : null,
                null
        );
        sb.add("name.givenName",
                SchemaDefinition.Types.STRING,
                (source, dest) -> {
                    if (dest.name == null) {
                        dest.name = new NotionUserModel.Name();
                    }
                    dest.name.givenName = source;
                },
                (source, dest) -> dest.replace("name.givenName", source),
                (source) -> source.name != null ? source.name.givenName : null,
                null
        );
        sb.add("name.familyName",
                SchemaDefinition.Types.STRING,
                (source, dest) -> {
                    if (dest.name == null) {
                        dest.name = new NotionUserModel.Name();
                    }
                    dest.name.familyName = source;
                },
                (source, dest) -> dest.replace("name.familyName", source),
                (source) -> source.name != null ? source.name.familyName : null,
                null
        );

        // Association
        // Notion doesn't support "groups" attributes

        // Metadata (readonly)
        sb.add("meta.created",
                SchemaDefinition.Types.DATETIME,
                null,
                (source) -> toZoneDateTimeForEpochMilli(source.meta.created),
                null,
                NOT_CREATABLE, NOT_UPDATEABLE
        );
        sb.add("meta.lastModified",
                SchemaDefinition.Types.DATETIME,
                null,
                (source) -> toZoneDateTimeForEpochMilli(source.meta.lastModified),
                null,
                NOT_CREATABLE, NOT_UPDATEABLE
        );

        LOGGER.ok("The constructed user schema");

        return sb;
    }

    @Override
    public SchemaDefinition getSchema() {
        return schema;
    }

    @Override
    public Uid create(Set<Attribute> attributes) {
        NotionUserModel user = new NotionUserModel();
        NotionUserModel mapped = schema.apply(attributes, user);

        Uid newUid = client.createUser(mapped);

        return newUid;
    }

    @Override
    public Set<AttributeDelta> updateDelta(Uid uid, Set<AttributeDelta> modifications, OperationOptions options) {
        PatchOperationsModel dest = new PatchOperationsModel();

        schema.applyDelta(modifications, dest);

        if (dest.hasAttributesChange()) {
            client.patchUser(uid, dest);
        }

        return null;
    }

    @Override
    public void delete(Uid uid, OperationOptions options) {
        client.deleteUser(uid);
    }

    @Override
    public int getByUid(Uid uid, ResultsHandler resultsHandler, OperationOptions options,
                        Set<String> returnAttributesSet, Set<String> fetchFieldsSet,
                        boolean allowPartialAttributeValues, int pageSize, int pageOffset) {
        NotionUserModel user = client.getUser(uid, options, fetchFieldsSet);

        if (user != null) {
            resultsHandler.handle(toConnectorObject(schema, user, returnAttributesSet, allowPartialAttributeValues));
            return 1;
        }
        return 0;
    }

    @Override
    public int getByName(Name name, ResultsHandler resultsHandler, OperationOptions options,
                         Set<String> returnAttributesSet, Set<String> fetchFieldsSet,
                         boolean allowPartialAttributeValues, int pageSize, int pageOffset) {
        NotionUserModel user = client.getUser(name, options, fetchFieldsSet);

        if (user != null) {
            resultsHandler.handle(toConnectorObject(schema, user, returnAttributesSet, allowPartialAttributeValues));
            return 1;
        }
        return 0;
    }

    @Override
    public int getAll(ResultsHandler resultsHandler, OperationOptions options,
                      Set<String> returnAttributesSet, Set<String> fetchFieldsSet,
                      boolean allowPartialAttributeValues, int pageSize, int pageOffset) {
        return client.getUsers((u) -> resultsHandler.handle(toConnectorObject(schema, u, returnAttributesSet, allowPartialAttributeValues)),
                options, fetchFieldsSet, pageSize, pageOffset);
    }
}
