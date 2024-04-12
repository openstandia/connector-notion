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
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.objects.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static jp.openstandia.connector.util.Utils.toZoneDateTimeForEpochMilli;
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.*;

public class NotionGroupHandler implements ObjectHandler {

    public static final ObjectClass GROUP_OBJECT_CLASS = new ObjectClass("Group");

    private static final Log LOGGER = Log.getLog(NotionGroupHandler.class);

    private final NotionConfiguration configuration;
    private final NotionRESTClient client;
    private final SchemaDefinition schema;

    public NotionGroupHandler(NotionConfiguration configuration, NotionRESTClient client,
                              SchemaDefinition schema) {
        this.configuration = configuration;
        this.client = client;
        this.schema = schema;
    }

    public static SchemaDefinition.Builder createSchema(NotionConfiguration configuration, NotionRESTClient client) {
        SchemaDefinition.Builder<NotionGroupModel, PatchOperationsModel, NotionGroupModel> sb
                = SchemaDefinition.newBuilder(GROUP_OBJECT_CLASS, NotionGroupModel.class, PatchOperationsModel.class, NotionGroupModel.class);

        // __UID__
        // The id for the group. Must be unique and unchangeable.
        sb.addUid("groupId",
                SchemaDefinition.Types.STRING_CASE_IGNORE,
                null,
                (source) -> source.id,
                "id",
                NOT_CREATABLE, NOT_UPDATEABLE
        );

        // displayName (__NAME__)
        // The name for the group. Must be unique for ConnId Name attribute though it's not required and unique in Notion.
        // Also, it's case-insensitive.
        sb.addName("displayName",
                SchemaDefinition.Types.STRING_CASE_IGNORE,
                (source, dest) -> dest.displayName = source,
                (source, dest) -> dest.replace("displayName", source),
                (source) -> StringUtil.isEmpty(source.displayName) ? source.id : source.displayName,
                null,
                REQUIRED
        );

        // Attributes
        // Nothing

        // Association
        sb.addAsMultiple("members.User.value",
                SchemaDefinition.Types.STRING,
                (source, dest) -> dest.addMembers(source),
                (add, dest) -> dest.addMembers(add),
                (remove, dest) -> dest.removeMembers(remove),
                (source) -> source.members == null ? null : source.members.stream().map(m -> m.value),
                null
        );

        // Metadata (readonly)
        sb.add("meta.created",
                SchemaDefinition.Types.DATETIME,
                null,
                (source) -> source.meta == null || source.meta.created == null ? null : toZoneDateTimeForEpochMilli(source.meta.created),
                null,
                NOT_CREATABLE, NOT_UPDATEABLE
        );
        sb.add("meta.lastModified",
                SchemaDefinition.Types.DATETIME,
                null,
                (source) -> source.meta == null || source.meta.lastModified == null ? null : toZoneDateTimeForEpochMilli(source.meta.lastModified),
                null,
                NOT_CREATABLE, NOT_UPDATEABLE
        );
        LOGGER.ok("The constructed group schema");

        return sb;
    }

    private static Stream<String> filterGroups(NotionConfiguration configuration, Stream<String> groups) {
        Set<String> ignoreGroup = configuration.getIgnoreGroupSet();
        return groups.filter(g -> !ignoreGroup.contains(g));
    }

    @Override
    public SchemaDefinition getSchema() {
        return schema;
    }

    @Override
    public Uid create(Set<Attribute> attributes) {
        NotionGroupModel group = new NotionGroupModel();
        NotionGroupModel mapped = schema.apply(attributes, group);

        if (configuration.isUniqueCheckGroupDisplayNameEnabled()) {
            OperationOptions options = new OperationOptionsBuilder().setPageSize(1).setPagedResultsOffset(1).build();
            NotionGroupModel found = client.getGroup(new Name(group.displayName), options, Collections.emptySet());
            if (found != null && found.displayName.equalsIgnoreCase(group.displayName)) {
                throw new AlreadyExistsException(String.format("Group \"%s\" already exists", group.displayName));
            }
        }

        Uid newUid = client.createGroup(mapped);

        return newUid;
    }

    @Override
    public Set<AttributeDelta> updateDelta(Uid uid, Set<AttributeDelta> modifications, OperationOptions options) {
        PatchOperationsModel dest = new PatchOperationsModel();

        schema.applyDelta(modifications, dest);

        if (dest.hasAttributesChange()) {
            client.patchGroup(uid, dest);
        }

        return null;
    }

    @Override
    public void delete(Uid uid, OperationOptions options) {
        client.deleteGroup(uid);
    }

    @Override
    public int getByUid(Uid uid, ResultsHandler resultsHandler, OperationOptions options,
                        Set<String> returnAttributesSet, Set<String> fetchFieldsSet,
                        boolean allowPartialAttributeValues, int pageSize, int pageOffset) {
        NotionGroupModel group = client.getGroup(uid, options, fetchFieldsSet);

        if (group != null) {
            resultsHandler.handle(toConnectorObject(schema, group, returnAttributesSet, allowPartialAttributeValues));
            return 1;
        }
        return 0;
    }

    @Override
    public int getByName(Name name, ResultsHandler resultsHandler, OperationOptions options,
                         Set<String> returnAttributesSet, Set<String> fetchFieldsSet,
                         boolean allowPartialAttributeValues, int pageSize, int pageOffset) {
        NotionGroupModel group = client.getGroup(name, options, fetchFieldsSet);

        if (group != null) {
            resultsHandler.handle(toConnectorObject(schema, group, returnAttributesSet, allowPartialAttributeValues));
            return 1;
        }
        return 0;
    }

    @Override
    public int getByMembers(Attribute attribute, ResultsHandler resultsHandler, OperationOptions options,
                            Set<String> returnAttributesSet, Set<String> fetchFieldSet, boolean allowPartialAttributeValues, int pageSize, int pageOffset) {
        // Unfortunately, Notion doesn't support filter by member (It supports displayName filter only).
        // So, we need to fetch all groups.
        Set<Object> memberIds = new HashSet<>(attribute.getValue());
        return client.getGroups((g) -> {
            // Ignored group
            Set<String> ignoreGroupSet = configuration.getIgnoreGroupSet();
            // displayName is case-insensitive
            if (ignoreGroupSet.contains(g.displayName.toLowerCase())) {
                return true;
            }

            // Filter by member's value
            boolean contains = g.members.stream()
                    .map(m -> m.value)
                    .collect(Collectors.toSet())
                    .containsAll(memberIds);
            if (contains) {
                return resultsHandler.handle(toConnectorObject(schema, g, returnAttributesSet, allowPartialAttributeValues));
            }

            return true;
        }, options, fetchFieldSet, pageSize, pageOffset);
    }

    @Override
    public int getAll(ResultsHandler resultsHandler, OperationOptions options,
                      Set<String> returnAttributesSet, Set<String> fetchFieldsSet,
                      boolean allowPartialAttributeValues, int pageSize, int pageOffset) {
        return client.getGroups((g) -> resultsHandler.handle(toConnectorObject(schema, g, returnAttributesSet, allowPartialAttributeValues)),
                options, fetchFieldsSet, pageSize, pageOffset);
    }
}
