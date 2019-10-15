/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.plugin.faker;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import io.prestosql.spi.PrestoException;
import io.prestosql.spi.connector.ColumnMetadata;
import io.prestosql.spi.connector.ConnectorTableMetadata;
import io.prestosql.spi.connector.SchemaNotFoundException;
import io.prestosql.spi.connector.SchemaTableName;
import io.prestosql.spi.connector.TableNotFoundException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.Optional;

import static io.prestosql.spi.type.VarcharType.createUnboundedVarcharType;
import static io.prestosql.testing.TestingConnectorSession.SESSION;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

@Test(singleThreaded = true)
public class TestFakerMetadata
{
    private static final String CONNECTOR_ID = "TEST";
    private FakerTableHandle numbersTableHandle;
    private FakerMetadata metadata;

    @BeforeMethod
    public void setUp()
    {
        URL numbersUrl = Resources.getResource(TestFakerClient.class, "/example-data/numbers-1.csv");
        numbersTableHandle = new FakerTableHandle(CONNECTOR_ID, "csv", numbersUrl.toString());

        URL metadataUrl = Resources.getResource(TestFakerClient.class, "/example-data/example-metadata.json");
        assertNotNull(metadataUrl, "metadataUrl is null");
        FakerClient client = new FakerClient(new FakerConfig());
        metadata = new FakerMetadata(new FakerConnectorId(CONNECTOR_ID), client);
    }

    @Test
    public void testListSchemaNames()
    {
        assertEquals(metadata.listSchemaNames(SESSION), ImmutableSet.of("csv", "tsv", "txt", "raw", "excel"));
    }

    @Test
    public void testGetTableHandle()
    {
        assertNull(metadata.getTableHandle(SESSION, new SchemaTableName("example", "unknown")));
        assertNull(metadata.getTableHandle(SESSION, new SchemaTableName("unknown", "numbers")));
        assertNull(metadata.getTableHandle(SESSION, new SchemaTableName("unknown", "unknown")));
    }

    @Test
    public void testGetColumnHandles()
    {
        // known table
        assertEquals(metadata.getColumnHandles(SESSION, numbersTableHandle), ImmutableMap.of(
                "one", new FakerColumnHandle(CONNECTOR_ID, "one", createUnboundedVarcharType(), 0),
                "1", new FakerColumnHandle(CONNECTOR_ID, "1", createUnboundedVarcharType(), 1)));

        // unknown table
        try {
            metadata.getColumnHandles(SESSION, new FakerTableHandle(CONNECTOR_ID, "unknown", "unknown"));
            fail("Expected getColumnHandle of unknown table to throw a TableNotFoundException");
        }
        catch (SchemaNotFoundException expected) {
        }
        try {
            metadata.getColumnHandles(SESSION, new FakerTableHandle(CONNECTOR_ID, "csv", "unknown"));
            fail("Expected getColumnHandle of unknown table to throw a TableNotFoundException");
        }
        catch (TableNotFoundException expected) {
        }
    }

    @Test
    public void getTableMetadata()
    {
        // known table
        ConnectorTableMetadata tableMetadata = metadata.getTableMetadata(SESSION, numbersTableHandle);
        assertEquals(tableMetadata.getTable().getSchemaName(), "csv");
        assertEquals(tableMetadata.getColumns(), ImmutableList.of(
                new ColumnMetadata("one", createUnboundedVarcharType()),
                new ColumnMetadata("1", createUnboundedVarcharType())));

        // unknown tables should produce null
        assertNull(metadata.getTableMetadata(SESSION, new FakerTableHandle(CONNECTOR_ID, "unknown", "unknown")));
        assertNull(metadata.getTableMetadata(SESSION, new FakerTableHandle(CONNECTOR_ID, "example", "unknown")));
        assertNull(metadata.getTableMetadata(SESSION, new FakerTableHandle(CONNECTOR_ID, "unknown", "numbers")));
    }

    @Test
    public void testListTables()
    {
        // all schemas
        assertEquals(ImmutableSet.copyOf(metadata.listTables(SESSION, Optional.empty())), ImmutableSet.of());

        // specific schema
        assertEquals(ImmutableSet.copyOf(metadata.listTables(SESSION, Optional.of("tsv"))), ImmutableSet.of());
        assertEquals(ImmutableSet.copyOf(metadata.listTables(SESSION, Optional.of("csv"))), ImmutableSet.of());

        // unknown schema
        assertEquals(ImmutableSet.copyOf(metadata.listTables(SESSION, Optional.of("unknown"))), ImmutableSet.of());
    }

    @Test
    public void getColumnMetadata()
    {
        assertEquals(metadata.getColumnMetadata(SESSION, numbersTableHandle, new FakerColumnHandle(CONNECTOR_ID, "text", createUnboundedVarcharType(), 0)),
                new ColumnMetadata("text", createUnboundedVarcharType()));

        // example connector assumes that the table handle and column handle are
        // properly formed, so it will return a metadata object for any
        // FakerTableHandle and FakerColumnHandle passed in.  This is on because
        // it is not possible for the Presto Metadata system to create the handles
        // directly.
    }

    @Test(expectedExceptions = PrestoException.class)
    public void testCreateTable()
    {
        metadata.createTable(
                SESSION,
                new ConnectorTableMetadata(
                        new SchemaTableName("example", "foo"),
                        ImmutableList.of(new ColumnMetadata("text", createUnboundedVarcharType()))),
                false);
    }

    @Test(expectedExceptions = PrestoException.class)
    public void testDropTableTable()
    {
        metadata.dropTable(SESSION, numbersTableHandle);
    }
}
