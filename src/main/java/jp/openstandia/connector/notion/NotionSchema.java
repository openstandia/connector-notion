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
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionInfoBuilder;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.spi.operations.SearchOp;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Schema for Notion objects.
 *
 * @author Hiroyuki Wada
 */
public class NotionSchema {

    private final NotionConfiguration configuration;
    private final NotionRESTClient client;

    public final Schema schema;

    private Map<String, ObjectHandler> schemaHandlerMap;

    public NotionSchema(NotionConfiguration configuration, NotionRESTClient client) {
        this.configuration = configuration;
        this.client = client;
        this.schemaHandlerMap = new HashMap<>();

        SchemaBuilder schemaBuilder = new SchemaBuilder(NotionConnector.class);

        buildSchema(schemaBuilder, NotionUserHandler.createSchema(configuration, client).build(),
                (schema) -> new NotionUserHandler(configuration, client, schema));
        buildSchema(schemaBuilder, NotionGroupHandler.createSchema(configuration, client).build(),
                (schema) -> new NotionGroupHandler(configuration, client, schema));

        // Define operation options
        schemaBuilder.defineOperationOption(OperationOptionInfoBuilder.buildAttributesToGet(), SearchOp.class);
        schemaBuilder.defineOperationOption(OperationOptionInfoBuilder.buildReturnDefaultAttributes(), SearchOp.class);
        schemaBuilder.defineOperationOption(OperationOptionInfoBuilder.buildPageSize(), SearchOp.class);
        schemaBuilder.defineOperationOption(OperationOptionInfoBuilder.buildPagedResultsOffset(), SearchOp.class);

        this.schema = schemaBuilder.build();

    }

    private void buildSchema(SchemaBuilder builder, SchemaDefinition schemaDefinition, Function<SchemaDefinition, ObjectHandler> callback) {
        builder.defineObjectClass(schemaDefinition.getObjectClassInfo());
        ObjectHandler handler = callback.apply(schemaDefinition);
        this.schemaHandlerMap.put(schemaDefinition.getType(), handler);
    }

    public ObjectHandler getSchemaHandler(ObjectClass objectClass) {
        return schemaHandlerMap.get(objectClass.getObjectClassValue());
    }
}