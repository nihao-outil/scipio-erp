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

package org.ofbiz.shipment.verify;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.ServiceUtil;

/**
 * VerifyPickServices.
 * <p>
 * SCIPIO: 2018-11-28: All composed operations are now synchronized.
 */
public class VerifyPickServices {

    public static Map<String, Object> verifySingleItem(DispatchContext dctx, Map<String, ? extends Object> context) {
        Locale locale = (Locale) context.get("locale");
        VerifyPickSession pickSession = (VerifyPickSession) context.get("verifyPickSession");
        String productId = (String) context.get("productId");
        String originGeoId = (String) context.get("originGeoId");
        BigDecimal quantity = (BigDecimal) context.get("quantity");
        String orderItemSeqId = (String) context.get("orderItemSeqId");
        if (quantity != null) {
            try {
                pickSession.createRow( orderItemSeqId, productId, originGeoId, quantity, locale);
            } catch (GeneralException e) {
                return ServiceUtil.returnError(e.getMessage());
            }
        }
        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> verifyBulkItem(DispatchContext dctx, Map<String, ? extends Object> context) {
        Locale locale = (Locale) context.get("locale");
        VerifyPickSession pickSession = (VerifyPickSession) context.get("verifyPickSession");
        // SCIPIO: TODO: REVIEW: clearly something missing here
//        String selectedMap = null;
//        if (UtilValidate.isNotEmpty(context.get("selectedMap"))) {
//            selectedMap = (String) context.get("selectedMap");
//        }
        String orderItemSeqId = null;
        if (UtilValidate.isNotEmpty(context.get("ite"))) {
            orderItemSeqId = (String) context.get("ite");
        }
        String productId = null;
        if (UtilValidate.isNotEmpty(context.get("prd"))) {
            productId = (String) context.get("prd");
        }
        String originGeoId = null;
        if (UtilValidate.isNotEmpty(context.get("geo"))) {
            originGeoId = (String) context.get("geo");
        }
        String quantityStr = null;
        if (UtilValidate.isNotEmpty(context.get("qty"))) {
            quantityStr = (String) context.get("qty");
        }
//        if (selectedMap != null) {
//            for (String rowKey : selectedMap.keySet()) {
//                String orderItemSeqId = itemMap.get(rowKey);
//                String productId = productMap.get(rowKey);
//                String originGeoId = originGeoIdMap.get(rowKey);
//                String quantityStr = quantityMap.get(rowKey);
                if (UtilValidate.isNotEmpty(quantityStr)) {
                    BigDecimal quantity = new BigDecimal(quantityStr);
                    if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                        try {
                            pickSession.createRow(orderItemSeqId, productId, originGeoId, quantity, locale);
                        } catch (Exception ex) {
                            return ServiceUtil.returnError(ex.getMessage());
                        }
                    }
                }
//            }
//        }
        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> completeVerifiedPick(DispatchContext dctx, Map<String, ? extends Object> context) {
        Locale locale = (Locale) context.get("locale");
        String shipmentId = null;
        VerifyPickSession pickSession = (VerifyPickSession) context.get("verifyPickSession");
        String orderId = (String) context.get("orderId");
        String shipGroupSeqId = (String) context.get("shipGroupSeqId");
        try {
            synchronized (pickSession) { // SCIPIO
                if (!orderId.equals(pickSession.getOrderId())
                        && (UtilValidate.isNotEmpty(shipGroupSeqId) && shipGroupSeqId.equals(pickSession.getShipGroupSeqId()))) {
                    throw new GeneralException("orderId [" + orderId + "] passed is not equals to orderId stored in session [" + pickSession.getOrderId() + "] " +
                            "OR shipGroupSeqId [" + shipGroupSeqId + "] passed is not equals to shipGroupSeqId stored in session [" + pickSession.getShipGroupSeqId() + "]");
                }
                shipmentId = pickSession.complete(locale);
                Map<String, Object> shipment = new HashMap<String, Object>();
                shipment.put("shipmentId", shipmentId);
                shipment.put("orderId", orderId);
                pickSession.clearAllRows();
                return shipment;
            }
        } catch (GeneralException ex) {
            return ServiceUtil.returnError(ex.getMessage(), ex.getMessageList());
        }
    }

    public static Map<String, Object> cancelAllRows(DispatchContext dctx, Map<String, ? extends Object> context) {
        VerifyPickSession session = (VerifyPickSession) context.get("verifyPickSession");
        session.clearAllRows();
        return ServiceUtil.returnSuccess();
    }
}
