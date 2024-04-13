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
import org.identityconnectors.framework.common.objects.*;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SchemaTest extends AbstractTest {

    @Test
    void schema() {
        Schema schema = connector.schema();

        assertNotNull(schema);
        assertEquals(2, schema.getObjectClassInfo().size());
    }

    @Test
    void user() {
        Schema schema = connector.schema();

        Optional<ObjectClassInfo> user = schema.getObjectClassInfo().stream().filter(o -> o.is("User")).findFirst();

        assertTrue(user.isPresent());

        ObjectClassInfo userSchema = user.get();
        Set<AttributeInfo> attributeInfo = userSchema.getAttributeInfo();

        assertEquals(7, attributeInfo.size());
        assertAttributeInfo(attributeInfo, Uid.NAME);
        assertAttributeInfo(attributeInfo, Name.NAME);
        assertAttributeInfo(attributeInfo, "name.formatted");
        assertAttributeInfo(attributeInfo, "name.familyName");
        assertAttributeInfo(attributeInfo, "name.givenName");
        assertAttributeInfo(attributeInfo, "meta.created");
        assertAttributeInfo(attributeInfo, "meta.lastModified");
    }

    @Test
    void group() {
        Schema schema = connector.schema();

        Optional<ObjectClassInfo> group = schema.getObjectClassInfo().stream().filter(o -> o.is("Group")).findFirst();

        assertTrue(group.isPresent());

        ObjectClassInfo groupSchema = group.get();
        Set<AttributeInfo> attributeInfo = groupSchema.getAttributeInfo();

        assertEquals(5, attributeInfo.size());
        assertAttributeInfo(attributeInfo, Uid.NAME);
        assertAttributeInfo(attributeInfo, Name.NAME);
        assertAttributeInfo(attributeInfo, "members.User.value", true);
        assertAttributeInfo(attributeInfo, "meta.created");
        assertAttributeInfo(attributeInfo, "meta.lastModified");
    }

    protected void assertAttributeInfo(Set<AttributeInfo> info, String attrName) {
        assertAttributeInfo(info, attrName, false);
    }

    protected void assertAttributeInfo(Set<AttributeInfo> info, String attrName, boolean isMultiple) {
        Optional<AttributeInfo> attributeInfo = info.stream().filter(x -> x.is(attrName)).findFirst();
        assertTrue(attributeInfo.isPresent(), attrName);
        assertEquals(isMultiple, attributeInfo.get().isMultiValued(), "Unexpected multiValued of " + attrName);
    }
}
