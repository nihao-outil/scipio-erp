/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.ofbiz.entity.model;

import java.io.Serializable;
import java.util.Locale;

import org.ofbiz.base.util.UtilXml;
import org.ofbiz.entity.jdbc.JdbcValueHandler;
import org.w3c.dom.Element;

/**
 * Generic Entity - FieldType model class
 *
 */

@SuppressWarnings("serial")
public class ModelFieldType implements Serializable {

    //private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    /** The type of the Field */
    protected String type = null;

    /** The java-type of the Field */
    protected String javaType = null;

    /** The JDBC value handler for this Field */
    protected JdbcValueHandler<?> jdbcValueHandler = null;

    /** The sql-type of the Field */
    protected String sqlType = null;

    /** The sql-type-alias of the Field, this is optional */
    protected String sqlTypeAlias = null;

    /**
     * Whether this is a JSON-storing field.
     *
     * <p>SCIPIO: 3.0.0: Added.</p>
     */
    protected boolean json = false;

    /** Default Constructor */
    public ModelFieldType() {}

    /** XML Constructor */
    public ModelFieldType(Element fieldTypeElement) {
        this.type = UtilXml.checkEmpty(fieldTypeElement.getAttribute("type")).intern();
        this.javaType = UtilXml.checkEmpty(fieldTypeElement.getAttribute("java-type")).intern();
        this.sqlType = UtilXml.checkEmpty(fieldTypeElement.getAttribute("sql-type")).intern();
        this.sqlTypeAlias = UtilXml.checkEmpty(fieldTypeElement.getAttribute("sql-type-alias")).intern();
        this.jdbcValueHandler = JdbcValueHandler.getInstance(this.javaType, this.sqlType);
        this.json = this.type.startsWith("json");
    }

    /** The type of the Field */
    public String getType() {
        return this.type;
    }

    /** The java-type of the Field */
    public String getJavaType() {
        return this.javaType;
    }

    /** Returns the JDBC value handler for this field type */
    public JdbcValueHandler<?> getJdbcValueHandler() {
        return this.jdbcValueHandler;
    }

    /** The sql-type of the Field */
    public String getSqlType() {
        return this.sqlType;
    }

    /** The sql-type-alias of the Field */
    public String getSqlTypeAlias() {
        return this.sqlTypeAlias;
    }

    /** A simple function to derive the max length of a String created from the field value, based on the sql-type
     * @return max length of a String representing the Field value
     */
    public int stringLength() {
        String sqlTypeUpperCase = sqlType.toUpperCase(Locale.getDefault());
        if (sqlTypeUpperCase.indexOf("VARCHAR") >= 0) {
            if (sqlTypeUpperCase.indexOf('(') > 0 && sqlTypeUpperCase.indexOf(')') > 0) {
                String length = sqlTypeUpperCase.substring(sqlTypeUpperCase.indexOf('(') + 1, sqlTypeUpperCase.indexOf(')'));

                return Integer.parseInt(length);
            } else {
                return 255;
            }
        } else if (sqlTypeUpperCase.indexOf("CHAR") >= 0) {
            if (sqlTypeUpperCase.indexOf('(') > 0 && sqlTypeUpperCase.indexOf(')') > 0) {
                String length = sqlTypeUpperCase.substring(sqlTypeUpperCase.indexOf('(') + 1, sqlTypeUpperCase.indexOf(')'));

                return Integer.parseInt(length);
            } else {
                return 255;
            }
        } else if (sqlTypeUpperCase.indexOf("TEXT") >= 0 || sqlTypeUpperCase.indexOf("LONG") >= 0 || sqlTypeUpperCase.indexOf("CLOB") >= 0) {
            return 5000;
        }
        return 20;
    }

    /**
     * Returns true if this is a specifically-defined JSON type (json, json-array, json-object).
     *
     * <p>SCIPIO: 3.0.0: Added.</p>
     */
    public boolean isJson() {
        return json;
    }
}
