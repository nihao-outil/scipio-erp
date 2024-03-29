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
package org.ofbiz.webapp.view;

import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.PropertyMessage;

import java.util.Collection;

/**
 * ViewHandlerException - View Handler Exception
 */
@SuppressWarnings("serial")
public class ViewHandlerException extends GeneralException {

    public ViewHandlerException() {
    }

    public ViewHandlerException(String msg) {
        super(msg);
    }

    public ViewHandlerException(String msg, Throwable nested) {
        super(msg, nested);
    }

    public ViewHandlerException(Throwable nested) {
        super(nested);
    }

    public ViewHandlerException(String msg, Collection<?> messageList) {
        super(msg, messageList);
    }

    public ViewHandlerException(String msg, Collection<?> messageList, Throwable nested) {
        super(msg, messageList, nested);
    }

    public ViewHandlerException(Collection<?> messageList, Throwable nested) {
        super(messageList, nested);
    }

    public ViewHandlerException(Collection<?> messageList) {
        super(messageList);
    }

    public ViewHandlerException(PropertyMessage propMsg) {
        super(propMsg);
    }

    public ViewHandlerException(PropertyMessage propMsg, Throwable nested) {
        super(propMsg, nested);
    }

}
