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

package io.prestosql.plugin.hive.security;

import io.prestosql.plugin.base.security.ForwardingConnectorAccessControl;
import io.prestosql.spi.connector.ColumnMetadata;
import io.prestosql.spi.connector.ConnectorAccessControl;
import io.prestosql.spi.connector.ConnectorTransactionHandle;
import io.prestosql.spi.connector.SchemaTableName;
import io.prestosql.spi.security.AccessDeniedException;
import io.prestosql.spi.security.ConnectorIdentity;

import java.util.List;
import java.util.Set;

import static io.prestosql.plugin.hive.HiveMetadata.getSourceTableNameForPartitionsTable;
import static io.prestosql.plugin.hive.HiveMetadata.isPartitionsSystemTable;
import static io.prestosql.spi.security.AccessDeniedException.denySelectTable;
import static io.prestosql.spi.security.AccessDeniedException.denyShowColumnsMetadata;
import static java.util.Objects.requireNonNull;

public class PartitionsAwareAccessControl
        extends ForwardingConnectorAccessControl
{
    private final ConnectorAccessControl delegate;

    public PartitionsAwareAccessControl(ConnectorAccessControl delegate)
    {
        this.delegate = requireNonNull(delegate, "delegate is null");
    }

    @Override
    protected ConnectorAccessControl delegate()
    {
        return delegate;
    }

    @Override
    public void checkCanShowColumnsMetadata(ConnectorTransactionHandle transactionHandle, ConnectorIdentity identity, SchemaTableName tableName)
    {
        if (isPartitionsSystemTable(tableName)) {
            try {
                checkCanShowColumnsMetadata(transactionHandle, identity, getSourceTableNameForPartitionsTable(tableName));
                return;
            }
            catch (AccessDeniedException e) {
                denyShowColumnsMetadata(tableName.toString());
            }
        }

        delegate.checkCanShowColumnsMetadata(transactionHandle, identity, tableName);
    }

    @Override
    public List<ColumnMetadata> filterColumns(ConnectorTransactionHandle transactionHandle, ConnectorIdentity identity, SchemaTableName tableName, List<ColumnMetadata> columns)
    {
        if (isPartitionsSystemTable(tableName)) {
            return filterColumns(transactionHandle, identity, getSourceTableNameForPartitionsTable(tableName), columns);
        }
        return delegate.filterColumns(transactionHandle, identity, tableName, columns);
    }

    @Override
    public void checkCanSelectFromColumns(ConnectorTransactionHandle transactionHandle, ConnectorIdentity identity, SchemaTableName tableName, Set<String> columnNames)
    {
        if (isPartitionsSystemTable(tableName)) {
            try {
                checkCanSelectFromColumns(transactionHandle, identity, getSourceTableNameForPartitionsTable(tableName), columnNames);
                return;
            }
            catch (AccessDeniedException e) {
                denySelectTable(tableName.toString());
            }
        }

        delegate.checkCanSelectFromColumns(transactionHandle, identity, tableName, columnNames);
    }
}
