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
package io.prestosql.plugin.cassandra;

import com.google.common.collect.ImmutableMap;
import io.airlift.tpch.TpchTable;
import io.prestosql.Session;
import io.prestosql.plugin.tpch.TpchPlugin;
import io.prestosql.testing.DistributedQueryRunner;

import java.util.List;

import static io.prestosql.plugin.cassandra.CassandraTestingUtils.createKeyspace;
import static io.prestosql.plugin.tpch.TpchMetadata.TINY_SCHEMA_NAME;
import static io.prestosql.testing.QueryAssertions.copyTpchTables;
import static io.prestosql.testing.TestingSession.testSessionBuilder;

public class CassandraQueryRunner
{
    private final CassandraServer cassandraServer;

    private boolean tpchLoaded;

    public CassandraQueryRunner()
            throws Exception
    {
        this.cassandraServer = new CassandraServer();
    }

    public DistributedQueryRunner createCassandraQueryRunner()
            throws Exception
    {
        DistributedQueryRunner queryRunner = new DistributedQueryRunner(createCassandraSession("tpch"), 4);

        queryRunner.installPlugin(new TpchPlugin());
        queryRunner.createCatalog("tpch", "tpch");

        queryRunner.installPlugin(new CassandraPlugin());
        queryRunner.createCatalog("cassandra", "cassandra", ImmutableMap.of(
                "cassandra.contact-points", cassandraServer.getHost(),
                "cassandra.native-protocol-port", Integer.toString(cassandraServer.getPort()),
                "cassandra.allow-drop-table", "true"));

        if (!tpchLoaded) {
            createKeyspace(cassandraServer.getSession(), "tpch");
            List<TpchTable<?>> tables = TpchTable.getTables();
            copyTpchTables(queryRunner, "tpch", TINY_SCHEMA_NAME, createCassandraSession("tpch"), tables);
            for (TpchTable<?> table : tables) {
                cassandraServer.refreshSizeEstimates("tpch", table.getTableName());
            }
            tpchLoaded = true;
        }

        return queryRunner;
    }

    public CassandraSession getCassandraSession()
    {
        return cassandraServer.getSession();
    }

    public static Session createCassandraSession(String schema)
    {
        return testSessionBuilder()
                .setCatalog("cassandra")
                .setSchema(schema)
                .build();
    }
}
