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

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.AbstractFilterTranslator;
import org.identityconnectors.framework.common.objects.filter.ContainsAllValuesFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;

public class NotionFilterTranslator extends AbstractFilterTranslator<NotionFilter> {

    private static final Log LOG = Log.getLog(NotionFilterTranslator.class);

    private final OperationOptions options;
    private final ObjectClass objectClass;

    public NotionFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        this.objectClass = objectClass;
        this.options = options;
    }

    @Override
    protected NotionFilter createEqualsExpression(EqualsFilter filter, boolean not) {
        if (not) {
            return null;
        }
        Attribute attr = filter.getAttribute();

        if (attr instanceof Uid) {
            Uid uid = (Uid) attr;
            NotionFilter uidFilter = new NotionFilter(uid.getName(),
                    NotionFilter.FilterType.EXACT_MATCH,
                    uid);
            return uidFilter;
        }
        if (attr instanceof Name) {
            Name name = (Name) attr;
            NotionFilter nameFilter = new NotionFilter(name.getName(),
                    NotionFilter.FilterType.EXACT_MATCH,
                    name);
            return nameFilter;
        }

        // Not supported searching by other attributes
        return null;
    }

    @Override
    protected NotionFilter createContainsAllValuesExpression(ContainsAllValuesFilter filter, boolean not) {
        if (not) {
            return null;
        }
        Attribute attr = filter.getAttribute();

        // Unfortunately, Notion doesn't support "groups" attribute in User schema.
        // So IDM try to fetch the groups which the user belongs to by using ContainsAllValuesFilter.
        if (objectClass.equals(NotionGroupHandler.GROUP_OBJECT_CLASS) &&
                attr.getName().equals("members.User.value")) {
            NotionFilter filterGroupByMember = new NotionFilter(attr.getName(),
                    NotionFilter.FilterType.EXACT_MATCH,
                    attr);
            return filterGroupByMember;
        }

        // Not supported searching by other attributes
        return null;
    }
}
