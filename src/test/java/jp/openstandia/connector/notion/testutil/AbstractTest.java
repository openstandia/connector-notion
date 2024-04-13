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

import jp.openstandia.connector.notion.NotionConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.test.common.TestHelpers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public abstract class AbstractTest {

    protected ConnectorFacade connector;
    protected NotionConfiguration configuration;
    protected MockClient mockClient;

    protected NotionConfiguration newConfiguration() {
        NotionConfiguration conf = new NotionConfiguration();
        conf.setBaseURL("https://example.com");
        conf.setToken(new GuardedString("dummy".toCharArray()));
        return conf;
    }

    protected ConnectorFacade newFacade(Configuration configuration) {
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        APIConfiguration impl = TestHelpers.createTestConfiguration(LocalNotionConnector.class, configuration);
        impl.getResultsHandlerConfiguration().setEnableAttributesToGetSearchResultsHandler(false);
        impl.getResultsHandlerConfiguration().setEnableNormalizingResultsHandler(false);
        impl.getResultsHandlerConfiguration().setEnableFilteredResultsHandler(false);
        return factory.newInstance(impl);
    }

    @BeforeEach
    void before() {
        MockClient.instance().init();

        this.configuration = newConfiguration();
        this.connector = newFacade(this.configuration);
        this.mockClient = MockClient.instance();
        this.mockClient.init("mock", this.configuration, null);
    }

    @AfterEach
    void after() {
        ConnectorFacadeFactory.getInstance().dispose();
    }

    // Utilities

    protected <T> List<T> list(T... s) {
        return Arrays.stream(s).collect(Collectors.toList());
    }

    protected <T> Set<T> set(T... s) {
        return Arrays.stream(s).collect(Collectors.toSet());
    }

    protected <T> Set<T> asSet(Collection<T> c) {
        return new HashSet<>(c);
    }

    protected String toPlain(GuardedString gs) {
        AtomicReference<String> plain = new AtomicReference<>();
        gs.access(c -> {
            plain.set(String.valueOf(c));
        });
        return plain.get();
    }

    protected OperationOptions defaultGetOperation(String... explicit) {
        List<String> attrs = Arrays.stream(explicit).collect(Collectors.toList());
        attrs.add(OperationalAttributes.PASSWORD_NAME);
        attrs.add(OperationalAttributes.ENABLE_NAME);

        return new OperationOptionsBuilder()
                .setReturnDefaultAttributes(true)
                .setAttributesToGet(attrs)
                .setAllowPartialResults(true)
                .build();
    }

    protected OperationOptions defaultSearchOperation(String... explicit) {
        List<String> attrs = Arrays.stream(explicit).collect(Collectors.toList());
        attrs.add(OperationalAttributes.PASSWORD_NAME);
        attrs.add(OperationalAttributes.ENABLE_NAME);

        return new OperationOptionsBuilder()
                .setReturnDefaultAttributes(true)
                .setAttributesToGet(attrs)
                .setAllowPartialAttributeValues(true)
                .setPagedResultsOffset(1)
                .setPageSize(20)
                .build();
    }

    protected OperationOptions countSearchOperation() {
        // IDM try to fetch 1 result to get total number effectively
        return pagedSearchOperation(1, 1);
    }

    protected OperationOptions pagedSearchOperation(int pageOffset, int pageSize) {
        return new OperationOptionsBuilder()
                .setAllowPartialAttributeValues(true)
                .setPagedResultsOffset(pageOffset)
                .setPageSize(pageSize)
                .build();
    }

    protected Object singleAttr(ConnectorObject connectorObject, String attrName) {
        Attribute attr = connectorObject.getAttributeByName(attrName);
        if (attr == null) {
            Assertions.fail(attrName + " is not contained in the connectorObject: " + connectorObject);
        }
        List<Object> value = attr.getValue();
        if (value == null || value.size() != 1) {
            Assertions.fail(attrName + " is not single value: " + value);
        }
        return value.get(0);
    }

    protected List<Object> multiAttr(ConnectorObject connectorObject, String attrName) {
        Attribute attr = connectorObject.getAttributeByName(attrName);
        if (attr == null) {
            Assertions.fail(attrName + " is not contained in the connectorObject: " + connectorObject);
        }
        List<Object> value = attr.getValue();
        if (value == null) {
            Assertions.fail(attrName + " is not multiple value: " + value);
        }
        return value;
    }

    protected boolean isIncompleteAttribute(Attribute attr) {
        if (attr == null) {
            return false;
        }
        return attr.getAttributeValueCompleteness().equals(AttributeValueCompleteness.INCOMPLETE);
    }
}
