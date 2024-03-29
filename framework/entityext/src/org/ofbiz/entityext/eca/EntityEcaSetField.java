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

package org.ofbiz.entityext.eca;

import java.lang.reflect.Method;
import java.util.Map;

import com.ilscipio.scipio.service.def.Service;
import com.ilscipio.scipio.service.def.eeca.Eeca;
import com.ilscipio.scipio.service.def.eeca.EecaSet;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.string.FlexibleStringExpander;
import org.ofbiz.entity.model.ModelUtil;
import org.w3c.dom.Element;

/**
 * EntityEcaSetField
 */
@SuppressWarnings("serial")
public final class EntityEcaSetField implements java.io.Serializable { // SCIPIO: added Serializable

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    private final String fieldName;
    private final String envName;
    private final String value;
    private final String format;

    public EntityEcaSetField(Element set) {
        this.fieldName = set.getAttribute("field-name");
        this.envName = set.getAttribute("env-name");
        this.value = set.getAttribute("value");
        this.format = set.getAttribute("format");
    }

    /**
     * Annotations constructor.
     *
     * <p>NOTE: serviceClass null when serviceMethod set and vice-versa.</p>
     *
     * <p>SCIPIO: 3.0.0: Added for annotations support.</p>
     */
    public EntityEcaSetField(EecaSet setDef, Eeca secaDef, Service serviceDef, Class<?> serviceClass, Method serviceMethod) {
        this.fieldName = setDef.fieldName();
        this.envName = setDef.envName();
        this.value = setDef.value();
        this.format = setDef.format();
    }

    public void eval(Map<String, Object> context) {
        if (!fieldName.isEmpty()) {
            String valueExpanded = FlexibleStringExpander.expandString(value, context);
            if (!valueExpanded.isEmpty()) {
                context.put(fieldName, this.format(valueExpanded, context));
            } else if (!envName.isEmpty() && context.get(envName) != null) {
                context.put(fieldName, this.format((String) context.get(envName), context));
            }
        }
    }

    private Object format(String s, Map<String, ? extends Object> c) {
        if (UtilValidate.isEmpty(s) || UtilValidate.isEmpty(format)) {
            return s;
        }

        // string formats
        if ("append".equalsIgnoreCase(format) && envName != null) {
            StringBuilder newStr = new StringBuilder();
            if (c.get(envName) != null) {
                newStr.append(c.get(envName));
            }
            newStr.append(s);
            return newStr.toString();
        }
        if ("to-upper".equalsIgnoreCase(format)) {
            return s.toUpperCase();
        }
        if ("to-lower".equalsIgnoreCase(format)) {
            return s.toLowerCase();
        }
        if ("hash-code".equalsIgnoreCase(format)) {
            return s.hashCode();
        }
        if ("long".equalsIgnoreCase(format)) {
            return Long.valueOf(s);
        }
        if ("double".equalsIgnoreCase(format)) {
            return Double.valueOf(s);
        }

        // entity formats
        if ("upper-first-char".equalsIgnoreCase(format)) {
            return ModelUtil.upperFirstChar(s);
        }
        if ("lower-first-char".equalsIgnoreCase(format)) {
            return ModelUtil.lowerFirstChar(s);
        }
        if ("db-to-java".equalsIgnoreCase(format)) {
            return ModelUtil.dbNameToVarName(s);
        }
        if ("java-to-db".equalsIgnoreCase(format)) {
            return ModelUtil.javaNameToDbName(s);
        }

        Debug.logWarning("Format function not found [" + format + "] return string unchanged - " + s, module);
        return s;
    }

    public String getFieldName() {
        return this.fieldName;
    }

    public String getRValue() {
        if (!this.value.isEmpty()) {
            return "\"".concat(this.value).concat("\"");
        }
        return this.envName;
    }
}
