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

import com.datastax.driver.core.DataType;
import com.datastax.driver.core.UserType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CassandraDataType
{
    private CassandraField field;
    private List<CassandraDataType> typeArguments;

    @JsonCreator
    public CassandraDataType(
            @JsonProperty("field") CassandraField field,
            @JsonProperty("typeArguments") List<CassandraDataType> typeArguments)
    {
        this.field = field;
        this.typeArguments = typeArguments;
    }

    public static CassandraDataType toCassandraDataType(DataType dataType)
    {
        return toCassandraDataType(dataType, Optional.empty());
    }

    public static CassandraDataType toCassandraDataType(DataType dataType, Optional<String> columnName)
    {
        switch (dataType.getName()) {
            case SET:
            case MAP:
            case LIST:
                CassandraField collectionField = new CassandraField(dataType.getName());
                List<CassandraDataType> collectionFields = new ArrayList<>();
                for (DataType type : dataType.getTypeArguments()) {
                    collectionFields.add(toCassandraDataType(type));
                }
                return new CassandraDataType(collectionField, collectionFields);
            case UDT:
                CassandraField udtField = new CassandraField(dataType.getName());
                List<CassandraDataType> udtFields = new ArrayList<>();
                UserType userType = (UserType) dataType;
                for (String fieldName : userType.getFieldNames()) {
                    udtFields.add(toCassandraDataType(userType.getFieldType(fieldName), Optional.of(fieldName)));
                }
                return new CassandraDataType(udtField, udtFields);
            default:
                return new CassandraDataType(new CassandraField(dataType.getName(), columnName), null);
        }
    }

    @JsonProperty
    public CassandraField getField()
    {
        return field;
    }

    @JsonProperty
    public List<CassandraDataType> getTypeArguments()
    {
        return typeArguments;
    }

    public static class CassandraField
    {
        private DataType.Name typeName;
        private Optional<String> columnName;

        @JsonCreator
        public CassandraField(
                @JsonProperty("field") DataType.Name typeName,
                @JsonProperty("columnName") Optional<String> columnName)
        {
            this.typeName = typeName;
            this.columnName = columnName;
        }

        public CassandraField(DataType.Name typeName)
        {
            this(typeName, Optional.empty());
        }

        @JsonProperty
        public com.datastax.driver.core.DataType.Name getTypeName()
        {
            return typeName;
        }

        @JsonProperty
        public Optional<String> getColumnName()
        {
            return columnName;
        }
    }
}
