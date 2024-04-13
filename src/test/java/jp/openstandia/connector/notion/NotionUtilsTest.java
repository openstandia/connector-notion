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

import jp.openstandia.connector.util.SchemaDefinition;
import jp.openstandia.connector.util.Utils;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.*;
import static org.junit.jupiter.api.Assertions.*;

class NotionUtilsTest {

    @Test
    void shouldReturnPartialAttributeValues() {
        OperationOptions noOptions = new OperationOptionsBuilder().build();
        assertFalse(Utils.shouldAllowPartialAttributeValues(noOptions));

        OperationOptions falseOption = new OperationOptionsBuilder().setAllowPartialAttributeValues(false).build();
        assertFalse(Utils.shouldAllowPartialAttributeValues(falseOption));

        OperationOptions trueOption = new OperationOptionsBuilder().setAllowPartialAttributeValues(true).build();
        assertTrue(Utils.shouldAllowPartialAttributeValues(trueOption));
    }

    @Test
    void createFullAttributesToGet() {
        SchemaDefinition.Builder<NotionUserModel, NotionUserModel, NotionUserModel> builder = SchemaDefinition.newBuilder(NotionUserHandler.USER_OBJECT_CLASS, NotionUserModel.class, NotionUserModel.class);
        builder.addUid("userId",
                SchemaDefinition.Types.STRING_CASE_IGNORE,
                null,
                (source) -> source.id,
                null,
                NOT_CREATABLE, NOT_UPDATEABLE
        );
        builder.addName("userName",
                SchemaDefinition.Types.STRING_CASE_IGNORE,
                (source, dest) -> dest.userName = source,
                (source) -> source.userName,
                null,
                REQUIRED
        );
        SchemaDefinition schemaDefinition = builder.build();

        OperationOptions noOptions = new OperationOptionsBuilder().build();
        Map<String, String> fullAttributesToGet = Utils.createFullAttributesToGet(schemaDefinition, noOptions);
        assertEquals(2, fullAttributesToGet.size());
        assertTrue(fullAttributesToGet.containsKey(Uid.NAME));
        assertTrue(fullAttributesToGet.containsKey(Name.NAME));
        assertEquals("userId", fullAttributesToGet.get(Uid.NAME));
        assertEquals("userName", fullAttributesToGet.get(Name.NAME));

        OperationOptions returnDefaultAttributes = new OperationOptionsBuilder().setReturnDefaultAttributes(true).build();
        fullAttributesToGet = Utils.createFullAttributesToGet(schemaDefinition, returnDefaultAttributes);
        assertEquals(2, fullAttributesToGet.size());
        assertTrue(fullAttributesToGet.containsKey(Uid.NAME));
        assertTrue(fullAttributesToGet.containsKey(Name.NAME));
        assertEquals("userId", fullAttributesToGet.get(Uid.NAME));
        assertEquals("userName", fullAttributesToGet.get(Name.NAME));
    }
}