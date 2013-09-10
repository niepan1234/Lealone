/*
 * Copyright 2011 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.codefollower.lealone.hbase.command.ddl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.util.Bytes;

import com.codefollower.lealone.command.ddl.CreateTable;
import com.codefollower.lealone.command.ddl.CreateTableData;
import com.codefollower.lealone.dbobject.Schema;
import com.codefollower.lealone.dbobject.table.Column;
import com.codefollower.lealone.dbobject.table.Table;
import com.codefollower.lealone.engine.Session;
import com.codefollower.lealone.hbase.dbobject.table.HBaseTable;
import com.codefollower.lealone.hbase.dbobject.table.HBaseTableEngine;
import com.codefollower.lealone.util.New;

public class CreateHBaseTable extends CreateTable {

    private ArrayList<CreateColumnFamily> cfList = New.arrayList();
    private ArrayList<String> splitKeys;

    private Options tableOptions;
    private Options defaultColumnFamilyOptions;

    private Map<String, ArrayList<Column>> columnFamilyMap = New.hashMap();

    public CreateHBaseTable(Session session, Schema schema) {
        super(session, schema);
    }

    public void setTableOptions(Options tableOptions) {
        this.tableOptions = tableOptions;
    }

    public void setDefaultColumnFamilyOptions(Options defaultColumnFamilyOptions) {
        this.defaultColumnFamilyOptions = defaultColumnFamilyOptions;
    }

    public void setSplitKeys(ArrayList<String> splitKeys) {
        this.splitKeys = splitKeys;
    }

    public void addCreateColumnFamily(CreateColumnFamily cf) {
        cfList.add(cf);
    }

    @Override
    public void addColumn(Column column) {
        super.addColumn(column);
        String cf = column.getColumnFamilyName();
        if (cf == null)
            cf = "";

        ArrayList<Column> list = columnFamilyMap.get(cf);
        if (list == null) {
            list = New.arrayList();
            columnFamilyMap.put(cf, list);
        }
        list.add(column);
    }

    private byte[][] getSplitKeys() {
        byte[][] splitKeys = null;
        if (this.splitKeys != null && this.splitKeys.size() > 0) {
            int size = this.splitKeys.size();
            splitKeys = new byte[size][];
            for (int i = 0; i < size; i++)
                splitKeys[i] = Bytes.toBytes(this.splitKeys.get(i));

            if (splitKeys != null && splitKeys.length > 0) {
                Arrays.sort(splitKeys, Bytes.BYTES_COMPARATOR);
                // Verify there are no duplicate split keys
                byte[] lastKey = null;
                for (byte[] splitKey : splitKeys) {
                    if (Bytes.compareTo(splitKey, HConstants.EMPTY_BYTE_ARRAY) == 0) {
                        throw new IllegalArgumentException("Empty split key must not be passed in the split keys.");
                    }
                    if (lastKey != null && Bytes.equals(splitKey, lastKey)) {
                        throw new IllegalArgumentException("All split keys must be unique, " + "found duplicate: "
                                + Bytes.toStringBinary(splitKey) + ", " + Bytes.toStringBinary(lastKey));
                    }
                    lastKey = splitKey;
                }
            }
        }
        return splitKeys;
    }

    @Override
    protected Table createTable(CreateTableData data) {
        String tableName = data.tableName;
        String defaultColumnFamilyName = null;
        String rowKeyName = null;
        if (tableOptions != null) {
            defaultColumnFamilyName = tableOptions.getDefaultColumnFamilyName();
            rowKeyName = tableOptions.getRowKeyName();
        }
        if (rowKeyName == null)
            rowKeyName = Options.DEFAULT_ROW_KEY_NAME;

        HTableDescriptor htd = new HTableDescriptor(tableName);
        if (!cfList.isEmpty()) {
            for (CreateColumnFamily cf : cfList) {
                if (defaultColumnFamilyName == null)
                    defaultColumnFamilyName = cf.getColumnFamilyName();
                htd.addFamily(cf.createHColumnDescriptor());
            }
        } else if (defaultColumnFamilyOptions != null) {
            defaultColumnFamilyName = HBaseTable.DEFAULT_COLUMN_FAMILY_NAME;
            HColumnDescriptor hcd = new HColumnDescriptor(defaultColumnFamilyName);
            defaultColumnFamilyOptions.initOptions(hcd);
            htd.addFamily(hcd);
        }

        if (defaultColumnFamilyName == null)
            defaultColumnFamilyName = HBaseTable.DEFAULT_COLUMN_FAMILY_NAME;

        if (tableOptions != null) {
            tableOptions.initOptions(htd);
        }

        htd.setValue(Options.ON_ROW_KEY_NAME, rowKeyName);
        if (session.getDatabase().getSettings().databaseToUpper)
            htd.setValue(Options.ON_DEFAULT_COLUMN_FAMILY_NAME, defaultColumnFamilyName.toUpperCase());
        else
            htd.setValue(Options.ON_DEFAULT_COLUMN_FAMILY_NAME, defaultColumnFamilyName);

        ArrayList<Column> list = columnFamilyMap.get("");
        if (list != null) {
            columnFamilyMap.remove("");
            ArrayList<Column> defaultColumns = columnFamilyMap.get(defaultColumnFamilyName);
            if (defaultColumns == null)
                defaultColumns = New.arrayList(list.size());
            for (Column c : list) {
                c.setColumnFamilyName(defaultColumnFamilyName);
                defaultColumns.add(c);
            }
            columnFamilyMap.put(defaultColumnFamilyName, defaultColumns);
        }

        data.schema = getSchema();
        data.tableEngine = HBaseTableEngine.class.getName();
        data.isHidden = false;
        HBaseTable table = new HBaseTable(!isDynamicTable(), data, columnFamilyMap, htd, getSplitKeys());
        table.setRowKeyName(rowKeyName);
        table.setTableEngine(HBaseTableEngine.class.getName());

        return table;
    }

}
