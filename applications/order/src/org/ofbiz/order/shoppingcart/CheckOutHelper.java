/*
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
 */
package org.ofbiz.order.shoppingcart;

import com.ilscipio.scipio.common.SolicitationTypeEnum;
import org.apache.commons.lang3.StringUtils;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.ObjectType;
import org.ofbiz.base.util.StringUtil;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilFormatOut;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilNumber;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityExpr;
import org.ofbiz.entity.condition.EntityFunction;
import org.ofbiz.entity.condition.EntityJoinOperator;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityTypeUtil;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.entity.util.EntityUtilProperties;
import org.ofbiz.order.finaccount.FinAccountHelper;
import org.ofbiz.order.order.OrderChangeHelper;
import org.ofbiz.order.order.OrderReadHelper;
import org.ofbiz.order.shoppingcart.product.ProductPromoWorker;
import org.ofbiz.order.shoppingcart.shipping.ShippingEvents;
import org.ofbiz.order.thirdparty.paypal.ExpressCheckoutEvents;
import org.ofbiz.party.contact.ContactHelper;
import org.ofbiz.party.contact.ContactMechWorker;
import org.ofbiz.product.store.ProductStoreWorker;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ModelService;
import org.ofbiz.service.ServiceUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A facade over the ShoppingCart to simplify the relatively complex
 * processing required to create an order in the system.
 */
public class CheckOutHelper {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    public static final String resource = "OrderUiLabels";
    public static final String resource_error = "OrderErrorUiLabels";

    public static final int scale = UtilNumber.getBigDecimalScale("order.decimals");
    public static final RoundingMode rounding = UtilNumber.getRoundingMode("order.rounding");

    // SCIPIO: 2018-11: Fields now final.

    protected final LocalDispatcher dispatcher;
    protected final Delegator delegator;
    protected final ShoppingCart cart;

    public CheckOutHelper(LocalDispatcher dispatcher, Delegator delegator, ShoppingCart cart) {
        this.delegator = delegator;
        this.dispatcher = dispatcher;
        this.cart = cart;
    }

    protected Locale getLocale() { // SCIPIO
        return (cart != null ? cart.getLocale() : Locale.getDefault());
    }
    
    public Map<String, Object> setCheckOutShippingAddress(String shippingContactMechId) {
        List<String> errorMessages = new ArrayList<>();
        Map<String, Object> result;
        String errMsg = null;

        if (UtilValidate.isNotEmpty(this.cart)) {
            errorMessages.addAll(setCheckOutShippingAddressInternal(shippingContactMechId));
        } else {
            errMsg = UtilProperties.getMessage(resource_error,"checkhelper.no_items_in_cart", (cart != null ? cart.getLocale() : Locale.getDefault()));
            errorMessages.add(errMsg);
        }
        if (errorMessages.size() == 1) {
            result = ServiceUtil.returnError(errorMessages.get(0));
        } else if (errorMessages.size() > 0) {
            result = ServiceUtil.returnError(errorMessages);
        } else {
            result = ServiceUtil.returnSuccess();
        }

        return result;
    }

    private List<String> setCheckOutShippingAddressInternal(String shippingContactMechId) {
        List<String> errorMessages = new ArrayList<>();
        String errMsg = null;

        // set the shipping address
        if (UtilValidate.isNotEmpty(shippingContactMechId)) {
            this.cart.setAllShippingContactMechId(shippingContactMechId);
        } else if (cart.shippingApplies() && !this.cart.getShipmentMethodTypeId().equals("NO_SHIPPING")) {
            // only return an error if shipping is required for this purchase
            errMsg = UtilProperties.getMessage(resource_error,"checkhelper.select_shipping_destination", cart.getLocale());
            errorMessages.add(errMsg);
        }

        return errorMessages;
    }

    public Map<String, Object> setCheckOutShippingOptions(String shippingMethod, String shippingInstructions,
            String orderAdditionalEmails, String maySplit, String giftMessage, String isGift, String internalCode, String shipBeforeDate, String shipAfterDate) {
        List<String> errorMessages = new ArrayList<>();
        Map<String, Object> result;
        String errMsg = null;

        if (UtilValidate.isNotEmpty(this.cart)) {
            errorMessages.addAll(setCheckOutShippingOptionsInternal(shippingMethod, shippingInstructions,
                    orderAdditionalEmails, maySplit, giftMessage, isGift, internalCode, shipBeforeDate, shipAfterDate));
        } else {
            errMsg = UtilProperties.getMessage(resource_error,"checkhelper.no_items_in_cart", (cart != null ? cart.getLocale() : Locale.getDefault()));
            errorMessages.add(errMsg);
        }

        if (errorMessages.size() == 1) {
            result = ServiceUtil.returnError(errorMessages.get(0));
        } else if (errorMessages.size() > 0) {
            result = ServiceUtil.returnError(errorMessages);
        } else {
            result = ServiceUtil.returnSuccess();
        }

        return result;
    }

    private List<String> setCheckOutShippingOptionsInternal(String shippingMethod, String shippingInstructions, String orderAdditionalEmails,
            String maySplit, String giftMessage, String isGift, String internalCode, String shipBeforeDate, String shipAfterDate) {
        List<String> errorMessages = new ArrayList<>();
        String errMsg = null;

        // set the general shipping options
        if (UtilValidate.isNotEmpty(shippingMethod)) {
            int delimiterPos = shippingMethod.indexOf('@');
            String shipmentMethodTypeId = null;
            String carrierPartyId = null;

            if (delimiterPos > 0) {
                shipmentMethodTypeId = shippingMethod.substring(0, delimiterPos);
                carrierPartyId = shippingMethod.substring(delimiterPos + 1);
            }

            this.cart.setAllShipmentMethodTypeId(shipmentMethodTypeId);
            this.cart.setAllCarrierPartyId(carrierPartyId);
        } else if (cart.shippingApplies()) {
            // only return an error if shipping is required for this purchase
            errMsg = UtilProperties.getMessage(resource_error,"checkhelper.select_shipping_method", cart.getLocale());
            errorMessages.add(errMsg);
        }

        if (!this.cart.getShipmentMethodTypeId().equals("NO_SHIPPING")) {
            // set the shipping instructions
            this.cart.setAllShippingInstructions(shippingInstructions);

            if (UtilValidate.isNotEmpty(maySplit)) {
                cart.setAllMaySplit(Boolean.valueOf(maySplit));
            } else {
                errMsg = UtilProperties.getMessage(resource_error, "checkhelper.select_splitting_preference", cart.getLocale());
                errorMessages.add(errMsg);
            }

            // set the gift message
            this.cart.setAllGiftMessage(giftMessage);

            if (UtilValidate.isNotEmpty(isGift)) {
                cart.setAllIsGift(Boolean.valueOf(isGift));
            } else {
                errMsg = UtilProperties.getMessage(resource_error, "checkhelper.specify_if_order_is_gift", cart.getLocale());
                errorMessages.add(errMsg);
            }

            // interal code
            this.cart.setInternalCode(internalCode);

            if (UtilValidate.isNotEmpty(shipBeforeDate)) {
                if (UtilValidate.isDate(shipBeforeDate)) {
                    cart.setShipBeforeDate(UtilDateTime.toTimestamp(shipBeforeDate));
                } else {
                    errMsg = UtilProperties.getMessage(resource_error, "checkhelper.specify_if_shipBeforeDate_is_date", cart.getLocale());
                    errorMessages.add(errMsg);
                }
            }

            if (UtilValidate.isNotEmpty(shipAfterDate)) {
                if (UtilValidate.isDate(shipAfterDate)) {
                    cart.setShipAfterDate(UtilDateTime.toTimestamp(shipAfterDate));
                } else {
                    errMsg = UtilProperties.getMessage(resource_error, "checkhelper.specify_if_shipAfterDate_is_date", cart.getLocale());
                    errorMessages.add(errMsg);
                }
            }
        }

        // set any additional notification emails
        // SCIPIO: Validation for add. emails (error prone)
        List<String> emailErrorMessages = new ArrayList<>();
        orderAdditionalEmails = cleanVerifyEmailListToString(orderAdditionalEmails, emailErrorMessages);
        if (errorMessages.size() <= 0) {
            this.cart.setOrderAdditionalEmails(orderAdditionalEmails);
        } else {
            errorMessages.addAll(emailErrorMessages);
        }

        return errorMessages;
    }

    /**
     * Stores checkout payment in cart.
     * <p>
     * SCIPIO: WARN: The stock code in this method does not adjust pay meth amounts. That is done later
     * by {@link #validatePaymentMethods()}.
     */
    public Map<String, Object> setCheckOutPayment(Map<String, Map<String, Object>> selectedPaymentMethods, List<String> singleUsePayments, String billingAccountId) {
        List<String> errorMessages = new ArrayList<>();
        Map<String, Object> result;
        String errMsg = null;

        if (UtilValidate.isNotEmpty(this.cart)) {
            errorMessages.addAll(setCheckOutPaymentInternal(selectedPaymentMethods, singleUsePayments, billingAccountId));
        } else {
            errMsg = UtilProperties.getMessage(resource_error,"checkhelper.no_items_in_cart", (cart != null ? cart.getLocale() : Locale.getDefault()));
            errorMessages.add(errMsg);
        }

        if (errorMessages.size() == 1) {
            result = ServiceUtil.returnError(errorMessages.get(0));
        } else if (errorMessages.size() > 0) {
            result = ServiceUtil.returnError(errorMessages);
        } else {
            result = ServiceUtil.returnSuccess();
        }

        return result;
    }

    public List<String> setCheckOutPaymentInternal(Map<String, Map<String, Object>> selectedPaymentMethods, List<String> singleUsePayments, String billingAccountId) {
        List<String> errorMessages = new ArrayList<>();
        String errMsg = null;

        if (singleUsePayments == null) {
            singleUsePayments = new ArrayList<>();
        }

        // set the payment method option
        if (UtilValidate.isNotEmpty(selectedPaymentMethods)) {
            // clear out the old payments
            cart.clearPayments();

            if (UtilValidate.isNotEmpty(billingAccountId)) {
                Map<String, Object> billingAccountMap = selectedPaymentMethods.get("EXT_BILLACT");
                BigDecimal billingAccountAmt = (BigDecimal)billingAccountMap.get("amount");
                // set cart billing account data and generate a payment method containing the amount we will be charging
                cart.setBillingAccount(billingAccountId, (billingAccountAmt != null ? billingAccountAmt: BigDecimal.ZERO));
                // copy the billing account terms as order terms
                try {
                    List<GenericValue> billingAccountTerms = EntityQuery.use(delegator).from("BillingAccountTerm").where("billingAccountId", billingAccountId).queryList();
                    if (UtilValidate.isNotEmpty(billingAccountTerms)) {
                        for (GenericValue billingAccountTerm : billingAccountTerms) {
                            // the term is not copied if in the cart a term of the same type is already set
                            if (!cart.hasOrderTerm(billingAccountTerm.getString("termTypeId"))) {
                                cart.addOrderTerm(billingAccountTerm.getString("termTypeId"), billingAccountTerm.getBigDecimal("termValue"), billingAccountTerm.getLong("termDays"));
                            }
                        }
                    }
                } catch (GenericEntityException gee) {
                    Debug.logWarning("Error copying billing account terms to order terms: " + gee.getMessage(), module);
                }
            } else {
                // remove the billing account from the cart
                cart.setBillingAccount(null, BigDecimal.ZERO);
            }

            // if checkoutPaymentId == EXT_BILLACT, then we have billing account only, so make sure we have enough credit
            if (selectedPaymentMethods.containsKey("EXT_BILLACT") && selectedPaymentMethods.size() == 1) {
                BigDecimal accountCredit = this.availableAccountBalance(cart.getBillingAccountId());
                BigDecimal amountToUse = cart.getBillingAccountAmount();

                // if an amount was entered, check that it doesn't exceed available amount
                if (amountToUse.compareTo(BigDecimal.ZERO) > 0 && amountToUse.compareTo(accountCredit) > 0) {
                    errMsg = UtilProperties.getMessage(resource_error,"checkhelper.insufficient_credit_available_on_account",
                            cart.getLocale());
                    errorMessages.add(errMsg);
                } else {
                    // otherwise use the available account credit (The user might enter 10.00 for an order worth 20.00 from an account with 30.00. This makes sure that the 30.00 is used)
                    amountToUse = accountCredit;
                }

                // check that the amount to use is enough to fulfill the order
                BigDecimal grandTotal = cart.getGrandTotal();
                if (grandTotal.compareTo(amountToUse) > 0) {
                    cart.setBillingAccount(null, BigDecimal.ZERO); // erase existing billing account data
                    errMsg = UtilProperties.getMessage(resource_error,"checkhelper.insufficient_credit_available_on_account",
                            cart.getLocale());
                    errorMessages.add(errMsg);
                } else {
                    // since this is the only selected payment method, let's make this amount the grand total for convenience
                    amountToUse = grandTotal;
                }

                // associate the cart billing account amount and EXT_BILLACT selected payment method with whatever amount we have now
                // XX: Note that this step is critical for the billing account to be charged correctly
                if (amountToUse.compareTo(BigDecimal.ZERO) > 0) {
                    cart.setBillingAccount(billingAccountId, amountToUse);
                    selectedPaymentMethods.put("EXT_BILLACT", UtilMisc.<String, Object>toMap("amount", amountToUse, "securityCode", null));
                }
            }

            for (String checkOutPaymentId : selectedPaymentMethods.keySet()) {
                String finAccountId = null;

                if (checkOutPaymentId.indexOf('|') > -1) {
                    // split type -- ID|Actual
                    String[] splitStr = checkOutPaymentId.split("\\|");
                    checkOutPaymentId = splitStr[0];
                    if ("FIN_ACCOUNT".equals(checkOutPaymentId)) {
                        finAccountId = splitStr[1];
                    }
                    if (Debug.verboseOn()) {
                        Debug.logVerbose("Split checkOutPaymentId: " + splitStr[0] + " / " + splitStr[1], module);
                    }
                }

                // get the selected amount to use
                BigDecimal paymentAmount = null;
                String securityCode = null;
                if (selectedPaymentMethods.get(checkOutPaymentId) != null) {
                    Map<String, Object> checkOutPaymentInfo = selectedPaymentMethods.get(checkOutPaymentId);
                    paymentAmount = (BigDecimal) checkOutPaymentInfo.get("amount");
                    securityCode = (String) checkOutPaymentInfo.get("securityCode");
                }

                boolean singleUse = singleUsePayments.contains(checkOutPaymentId);
                ShoppingCart.CartPaymentInfo inf = cart.addPaymentAmount(checkOutPaymentId, paymentAmount, singleUse);
                if (finAccountId != null) {
                    inf.finAccountId = finAccountId;
                }
                if (securityCode != null) {
                    inf.securityCode = securityCode;
                }
            }
        } else { // if (cart.getGrandTotal().compareTo(BigDecimal.ZERO) != 0)
            // SCIPIO: 2016-04-21: This is patched so that storefront and backend processes can be tweaked
            // and also will have same behavior. The default here is changed to Y so that the backend
            // functions the same as the frontend, and default Y is chosen because it is the most "safe" default
            // and is the stock default for ecommerce.
            GenericValue productStore = ProductStoreWorker.getProductStore(cart.getProductStoreId(), delegator);
            if (cart.getGrandTotal().compareTo(BigDecimal.ZERO) != 0 || !(productStore != null && "N".equals(productStore.getString("reqPayMethForFreeOrders")))) {
                // only return an error if the order total is not 0.00
                errMsg = UtilProperties.getMessage(resource_error,"checkhelper.select_method_of_payment",
                        cart.getLocale());
                errorMessages.add(errMsg);
            }
        }

        return errorMessages;
    }

    public Map<String, Object> setCheckOutDates(Timestamp shipBefore, Timestamp shipAfter) {
          List<String> errorMessages = new ArrayList<>();
          Map<String, Object> result = null;
          String errMsg = null;

          if (UtilValidate.isNotEmpty(this.cart)) {
              this.cart.setShipBeforeDate(shipBefore);
              this.cart.setShipAfterDate(shipAfter);
          } else {
              errMsg = UtilProperties.getMessage(resource_error,"checkhelper.no_items_in_cart",
                                                     (cart != null ? cart.getLocale() : Locale.getDefault()));
              errorMessages.add(errMsg);
          }

          if (errorMessages.size() == 1) {
              result = ServiceUtil.returnError(errorMessages.get(0));
          } else if (errorMessages.size() > 0) {
              result = ServiceUtil.returnError(errorMessages);
          } else {
              result = ServiceUtil.returnSuccess();
          }
          return result;
    }


    public Map<String, Object> setCheckOutOptions(String shippingMethod, String shippingContactMechId, Map<String, Map<String, Object>> selectedPaymentMethods,
            List<String> singleUsePayments, String billingAccountId, String shippingInstructions,
            String orderAdditionalEmails, String maySplit, String giftMessage, String isGift, String internalCode, String shipBeforeDate, String shipAfterDate) {
        List<String> errorMessages = new ArrayList<>();
        Map<String, Object> result = null;
        String errMsg = null;


        if (UtilValidate.isNotEmpty(this.cart)) {
            // set the general shipping options and method
            errorMessages.addAll(setCheckOutShippingOptionsInternal(shippingMethod, shippingInstructions,
                    orderAdditionalEmails, maySplit, giftMessage, isGift, internalCode, shipBeforeDate, shipAfterDate));

            // set the shipping address
            errorMessages.addAll(setCheckOutShippingAddressInternal(shippingContactMechId));

            // Recalc shipping costs before setting payment
            Map<String, Object> shipEstimateMap = ShippingEvents.getShipGroupEstimate(dispatcher, delegator, cart, 0);
            
            // SCIPIO: 2018-11-12: Make sure we got a valid estimate result (again), otherwise something is wrong here
            if (ServiceUtil.isError(shipEstimateMap)) {
                errMsg = UtilProperties.getMessage(resource_error, "shippingevents.problem_calculating_shipping", (cart != null ? cart.getLocale() : Locale.getDefault()));
                errorMessages.add(errMsg);
            } else {
                BigDecimal shippingTotal = (BigDecimal) shipEstimateMap.get("shippingTotal");
                if (shippingTotal == null) {
                    shippingTotal = BigDecimal.ZERO;
                }
                cart.setItemShipGroupEstimate(shippingTotal, 0);
            
                ProductPromoWorker.doPromotions(cart, dispatcher);
    
                //Recalc tax before setting payment
                try {
                    this.calcAndAddTax();
                } catch (GeneralException e) {
                    Debug.logError(e, module);
                }
                // set the payment method(s) option
                errorMessages.addAll(setCheckOutPaymentInternal(selectedPaymentMethods, singleUsePayments, billingAccountId));
            }
        } else {
            errMsg = UtilProperties.getMessage(resource_error,"checkhelper.no_items_in_cart", (cart != null ? cart.getLocale() : Locale.getDefault()));
            errorMessages.add(errMsg);
        }

        if (errorMessages.size() == 1) {
            result = ServiceUtil.returnError(errorMessages.get(0));
        } else if (errorMessages.size() > 0) {
            result = ServiceUtil.returnError(errorMessages);
        } else {
            result = ServiceUtil.returnSuccess();
        }

        return result;
    }

    public Map<String, Object> checkGiftCard(Map<String, Object> params, Map<String, Map<String, Object>> selectedPaymentMethods) {
        List<String> errorMessages = new ArrayList<>();
        Map<String, Object> errorMaps = new HashMap<>();
        Map<String, Object> result = new HashMap<>();
        String errMsg = null;
        // handle gift card payment
        // SCIPIO: This is too sensitive. Make a check for "Y" instead.
        //if (params.get("addGiftCard") != null) {
        if ("Y".equals(params.get("addGiftCard"))) {
            String gcNum = (String) params.get("giftCardNumber");
            String gcPin = (String) params.get("giftCardPin");
            String gcAmt = (String) params.get("giftCardAmount");
            BigDecimal gcAmount = BigDecimal.ONE.negate();

            boolean gcFieldsOkay = true;
            if (UtilValidate.isEmpty(gcNum)) {
                errMsg = UtilProperties.getMessage(resource_error,"checkhelper.enter_gift_card_number", (cart != null ? cart.getLocale() : Locale.getDefault()));
                errorMessages.add(errMsg);
                gcFieldsOkay = false;
            }
            if (cart.isPinRequiredForGC(delegator)) {
                //  if a PIN is required, make sure the PIN is valid
                if (UtilValidate.isEmpty(gcPin)) {
                    errMsg = UtilProperties.getMessage(resource_error,"checkhelper.enter_gift_card_pin_number", cart.getLocale());
                    errorMessages.add(errMsg);
                    gcFieldsOkay = false;
                }
            }
            // See if we should validate gift card code against FinAccount's accountCode
            if (cart.isValidateGCFinAccount(delegator)) {
                try {
                    // No PIN required - validate gift card number against account code
                    if (!cart.isPinRequiredForGC(delegator)) {
                        GenericValue finAccount = FinAccountHelper.getFinAccountFromCode(gcNum, delegator);
                        if (finAccount == null) {
                            errMsg = UtilProperties.getMessage(resource_error,"checkhelper.gift_card_does_not_exist", cart.getLocale());
                            errorMessages.add(errMsg);
                            gcFieldsOkay = false;
                        } else if ((finAccount.getBigDecimal("availableBalance") == null) ||
                                !((finAccount.getBigDecimal("availableBalance")).compareTo(FinAccountHelper.ZERO) > 0)) {
                            // if account's available balance (including authorizations) is not greater than zero, then return an error
                            errMsg = UtilProperties.getMessage(resource_error,"checkhelper.gift_card_has_no_value", cart.getLocale());
                            errorMessages.add(errMsg);
                            gcFieldsOkay = false;
                        }
                    }
                    // TODO: else case when pin is required - we should validate gcNum and gcPin
                } catch (GenericEntityException ex) {
                    errorMessages.add(ex.getMessage());
                    gcFieldsOkay = false;
                }
            }

            if (UtilValidate.isNotEmpty(selectedPaymentMethods)) {
                if (UtilValidate.isEmpty(gcAmt)) {
                    errMsg = UtilProperties.getMessage(resource_error,"checkhelper.enter_amount_to_place_on_gift_card", cart.getLocale());
                    errorMessages.add(errMsg);
                    gcFieldsOkay = false;
                }
            }
            if (UtilValidate.isNotEmpty(gcAmt)) {
                try {
                    gcAmount = new BigDecimal(gcAmt);
                } catch (NumberFormatException e) {
                    Debug.logError(e, module);
                    errMsg = UtilProperties.getMessage(resource_error,"checkhelper.invalid_amount_for_gift_card", cart.getLocale());
                    errorMessages.add(errMsg);
                    gcFieldsOkay = false;
                }
            }

            if (gcFieldsOkay) {
                // store the gift card
                Map<String, Object> gcCtx = new HashMap<>();
                gcCtx.put("partyId", params.get("partyId"));
                gcCtx.put("cardNumber", gcNum);
                if (cart.isPinRequiredForGC(delegator)) {
                    gcCtx.put("pinNumber", gcPin);
                }
                gcCtx.put("userLogin", cart.getUserLogin());
                Map<String, Object> gcResult = null;
                try {
                    gcResult = dispatcher.runSync("createGiftCard", gcCtx);
                    if (ServiceUtil.isError(gcResult)) {
                        return ServiceUtil.returnError(ServiceUtil.getErrorMessage(gcResult));
                    }
                } catch (GenericServiceException e) {
                    Debug.logError(e, module);
                    errorMessages.add(e.getMessage());
                }
                if (gcResult != null) {
                    ServiceUtil.addErrors(errorMessages, errorMaps, gcResult);

                    if (errorMessages.size() == 0 && errorMaps.size() == 0) {
                        // set the GC payment method
                        BigDecimal giftCardAmount = null;
                        if (gcAmount.compareTo(BigDecimal.ZERO) > 0) {
                            giftCardAmount = gcAmount;
                        }
                        String gcPaymentMethodId = (String) gcResult.get("paymentMethodId");
                        result = ServiceUtil.returnSuccess();
                        result.put("paymentMethodId", gcPaymentMethodId);
                        result.put("amount", giftCardAmount);
                    }
                } else {
                    errMsg = UtilProperties.getMessage(resource_error,"checkhelper.problem_with_gift_card_information", cart.getLocale());
                    errorMessages.add(errMsg);
                }
            }
        } else {
            result = ServiceUtil.returnSuccess();
        }

        // see whether we need to return an error or not
        if (errorMessages.size() > 0) {
            result.put(ModelService.ERROR_MESSAGE_LIST, errorMessages);
            result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_ERROR);
        }
        if (errorMaps.size() > 0) {
            result.put(ModelService.ERROR_MESSAGE_MAP, errorMaps);
            result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_ERROR);
        }

        return result;
    }

    public Map<String, Object> createOrder(GenericValue userLogin) {
        return createOrder(userLogin, null, null, null, false, null, cart.getWebSiteId());
    }

    // Create order event - uses createOrder service for processing
    public Map<String, Object> createOrder(GenericValue userLogin, String distributorId, String affiliateId,
            List<GenericValue> trackingCodeOrders, boolean areOrderItemsExploded, String visitId, String webSiteId) {
        if (this.cart == null) {
            return null;
        }
        String orderId = this.cart.getOrderId();
        String supplierPartyId = (String) this.cart.getAttribute("supplierPartyId");
        String originOrderId = (String) this.cart.getAttribute("originOrderId");

        this.cart.clearAllItemStatus();

        BigDecimal grandTotal = this.cart.getGrandTotal();

        // store the order - build the context
        Map<String, Object> context = this.cart.makeCartMap(this.dispatcher, areOrderItemsExploded);

        //get the TrackingCodeOrder List
        context.put("trackingCodeOrders", trackingCodeOrders);

        if (distributorId != null) {
            context.put("distributorId", distributorId);
        }
        if (affiliateId != null) {
            context.put("affiliateId", affiliateId);
        }

        context.put("orderId", orderId);
        context.put("supplierPartyId", supplierPartyId);
        context.put("grandTotal", grandTotal);
        context.put("userLogin", userLogin);
        context.put("visitId", visitId);
        if (UtilValidate.isEmpty(webSiteId)) {
            webSiteId = cart.getWebSiteId();
        }
        context.put("webSiteId", webSiteId);
        context.put("originOrderId", originOrderId);

        // need the partyId; don't use userLogin in case of an order via order mgr
        String partyId = this.cart.getPartyId();
        String productStoreId = cart.getProductStoreId();

        // store the order - invoke the service
        Map<String, Object> storeResult = null;

        try {
            storeResult = dispatcher.runSync("storeOrder", context);
            orderId = (String) storeResult.get("orderId");
            if (UtilValidate.isNotEmpty(orderId)) {
                this.cart.setOrderId(orderId);
                if (this.cart.getFirstAttemptOrderId() == null) {
                    this.cart.setFirstAttemptOrderId(orderId);
                }
            }
        } catch (GenericServiceException e) {
            String service = e.getMessage();
            Map<String, Object> messageMap = UtilMisc.<String, Object>toMap("service", service);
            String errMsg = UtilProperties.getMessage(resource_error, "checkhelper.could_not_create_order_invoking_service", messageMap, cart.getLocale());
            Debug.logError(e, errMsg, module);
            return ServiceUtil.returnError(errMsg);
        }

        // check for error message(s)
        if (ServiceUtil.isError(storeResult)) {
            String errMsg = UtilProperties.getMessage(resource_error, "checkhelper.did_not_complete_order_following_occurred", cart.getLocale());
            List<String> resErrorMessages = new LinkedList<>();
            resErrorMessages.add(errMsg);
            resErrorMessages.add(ServiceUtil.getErrorMessage(storeResult));
            return ServiceUtil.returnError(resErrorMessages);
        }

        // ----------
        // If needed, the production runs are created and linked to the order lines.
        //
        List<GenericValue> orderItems = UtilGenerics.checkList(context.get("orderItems"));
        int counter = 0;
        for (GenericValue orderItem : orderItems) {
            String productId = orderItem.getString("productId");
            if (productId != null) {
                try {
                    // do something tricky here: run as the "system" user
                    // that can actually create and run a production run
                    GenericValue permUserLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", "system").cache().queryOne();
                    GenericValue productStore = ProductStoreWorker.getProductStore(productStoreId, delegator);
                    GenericValue product = EntityQuery.use(delegator).from("Product").where("productId", productId).queryOne();
                    if (EntityTypeUtil.hasParentType(delegator, "ProductType", "productTypeId", product.getString("productTypeId"), "parentTypeId", "AGGREGATED")) {
                        org.ofbiz.product.config.ProductConfigWrapper config = this.cart.findCartItem(counter).getConfigWrapper();
                        Map<String, Object> inputMap = new HashMap<>();
                        inputMap.put("config", config);
                        inputMap.put("facilityId", productStore.getString("inventoryFacilityId"));
                        inputMap.put("orderId", orderId);
                        inputMap.put("orderItemSeqId", orderItem.getString("orderItemSeqId"));
                        inputMap.put("quantity", orderItem.getBigDecimal("quantity"));
                        inputMap.put("userLogin", permUserLogin);

                        Map<String, Object> prunResult = dispatcher.runSync("createProductionRunFromConfiguration", inputMap);
                        if (ServiceUtil.isError(prunResult)) {
                            Debug.logError(ServiceUtil.getErrorMessage(prunResult) + " for input:" + inputMap, module);
                        }
                    }
                } catch (GenericEntityException e) {
                    String service = e.getMessage();
                    Map<String, String> messageMap = UtilMisc.toMap("service", service);
                    String errMsg = UtilProperties.getMessage(resource_error, "checkhelper.problems_reading_database", cart.getLocale()); 
                    errMsg += UtilProperties.getMessage(resource_error, "checkhelper.could_not_create_order_invoking_service", messageMap, cart.getLocale());
                    Debug.logError(e, errMsg, module);
                    return ServiceUtil.returnError(errMsg);
                } catch (Exception e) { // SCIPIO: 2018-10-09: Keep Exception here for now to be safe: GenericServiceException
                    String service = e.getMessage();
                    Map<String, String> messageMap = UtilMisc.toMap("service", service);
                    String errMsg = UtilProperties.getMessage(resource_error, "checkhelper.could_not_create_order_invoking_service", messageMap, cart.getLocale());
                    Debug.logError(e, errMsg, module);
                    return ServiceUtil.returnError(errMsg);
                }
            }
            counter++;
        }
        // ----------

        // ----------
        // The status of the requirement associated to the shopping cart lines is set to "ordered".
        //
        for (ShoppingCartItem shoppingCartItem : this.cart.items()) {
            String requirementId = shoppingCartItem.getRequirementId();
            if (requirementId != null) {
                try {
                    /* OrderRequirementCommitment records will map which POs which are created from which requirements. With the help of this mapping requirements will be updated to Ordered when POs will be approved.  */
                    Map<String, Object> inputMap = UtilMisc.toMap("userLogin", userLogin, "orderId", orderId, "orderItemSeqId", shoppingCartItem.getOrderItemSeqId(), "requirementId", requirementId, "quantity", shoppingCartItem.getQuantity());
                    Map<String, Object> serviceResult = dispatcher.runSync("createOrderRequirementCommitment", inputMap);
                    if (ServiceUtil.isError(serviceResult)) {
                        return ServiceUtil.returnError(ServiceUtil.getErrorMessage(serviceResult));
                    }
                } catch (GenericServiceException e) {
                    String service = e.getMessage();
                    Map<String, String> messageMap = UtilMisc.toMap("service", service);
                    String errMsg = UtilProperties.getMessage(resource_error, "checkhelper.could_not_create_order_invoking_service", messageMap, cart.getLocale());
                    Debug.logError(e, errMsg, module);
                    return ServiceUtil.returnError(errMsg);
                }
            }
        }
        // ----------

        // set the orderId for use by chained events
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("orderId", orderId);
        result.put("orderAdditionalEmails", this.cart.getOrderAdditionalEmails());

        // save the emails to the order
        List<GenericValue> toBeStored = new LinkedList<>();

        GenericValue party = null;
        try {
            party = EntityQuery.use(delegator).from("Party").where("partyId", partyId).queryOne();
        } catch (GenericEntityException e) {
            Debug.logWarning(e, UtilProperties.getMessage(resource_error,"OrderProblemsGettingPartyRecord", Debug.getLogLocale()), module); // SCIPIO: log locale
        }

        // create order contact mechs for the email address(s)
        if (party != null) {
            Iterator<GenericValue> emailIter = UtilMisc.toIterator(ContactHelper.getContactMechByType(party, "EMAIL_ADDRESS", false));
            while (emailIter != null && emailIter.hasNext()) {
                GenericValue email = emailIter.next();
                if (this.cart.allowedSolicitations.contains(SolicitationTypeEnum.EMAIL)) {
                    try {
                        GenericValue partyContactMech = EntityQuery.use(delegator).from("PartyContactMech").where("contactMechId", email.getString("contactMechId"), "partyId", partyId).filterByDate().queryFirst();
                        partyContactMech.put("allowSolicitation", "Y");
                        toBeStored.add(partyContactMech);
                    } catch (GenericEntityException e) {
                        Debug.logError(e, module);
                    }
                }
                GenericValue orderContactMech = this.delegator.makeValue("OrderContactMech",
                        UtilMisc.toMap("orderId", orderId, "contactMechId", email.getString("contactMechId"), "contactMechPurposeTypeId", "ORDER_EMAIL"));
                toBeStored.add(orderContactMech);
                if (UtilValidate.isEmpty(ContactHelper.getContactMechByPurpose(party, "ORDER_EMAIL", false))) {
                    GenericValue partyContactMechPurpose = this.delegator.makeValue("PartyContactMechPurpose",
                            UtilMisc.toMap("partyId", party.getString("partyId"), "contactMechId", email.getString("contactMechId"), "contactMechPurposeTypeId", "ORDER_EMAIL", "fromDate", UtilDateTime.nowTimestamp()));
                    toBeStored.add(partyContactMechPurpose);
                }
            }
        }

        // create dummy contact mechs and order contact mechs for the additional emails
        String additionalEmails = this.cart.getOrderAdditionalEmails();
        List<String> emailList = StringUtil.split(additionalEmails, ",");
        if (emailList == null) {
            emailList = new ArrayList<>();
        }
        for (String email : emailList) {
            String contactMechId = this.delegator.getNextSeqId("ContactMech");
            GenericValue contactMech = this.delegator.makeValue("ContactMech",
                    UtilMisc.toMap("contactMechId", contactMechId, "contactMechTypeId", "EMAIL_ADDRESS", "infoString", email));

            GenericValue orderContactMech = this.delegator.makeValue("OrderContactMech",
                    UtilMisc.toMap("orderId", orderId, "contactMechId", contactMechId, "contactMechPurposeTypeId", "ORDER_EMAIL"));
            toBeStored.add(contactMech);
            toBeStored.add(orderContactMech);
        }

        if (toBeStored.size() > 0) {
            try {
                if (Debug.verboseOn()) {
                    Debug.logVerbose("To Be Stored: " + toBeStored, module);
                }
                this.delegator.storeAll(toBeStored);
            } catch (GenericEntityException e) {
                // not a fatal error; so just print a message
                Debug.logWarning(e, UtilProperties.getMessage(resource_error,"OrderProblemsStoringOrderEmailContactInformation", Debug.getLogLocale()), module); // SCIPIO: log locale
            }
        }

        return result;
    }

    public void calcAndAddTax() throws GeneralException {
        calcAndAddTax(null, false);
    }

    public void calcAndAddTax(boolean skipEmptyAddresses) throws GeneralException {
        calcAndAddTax(null, skipEmptyAddresses);
    }

    public void calcAndAddTax(GenericValue shipAddress) throws GeneralException {
        calcAndAddTax(shipAddress, false);
    }

    public void calcAndAddTax(GenericValue shipAddress, boolean skipEmptyAddresses) throws GeneralException {
        if (UtilValidate.isEmpty(cart.getShippingContactMechId()) && cart.getBillingAddress() == null && shipAddress == null) {
            return;
        }

        int shipGroups = this.cart.getShipGroupSize();
        for (int i = 0; i < shipGroups; i++) {
            ShoppingCart.CartShipInfo csi = cart.getShipInfo(i);
            Map<Integer, ShoppingCartItem> shoppingCartItemIndexMap = new HashMap<>();
            Map<String, Object> serviceContext = this.makeTaxContext(i, shipAddress, shoppingCartItemIndexMap, cart.getFacilityId(), skipEmptyAddresses);
            if (skipEmptyAddresses && serviceContext == null) {
                csi.clearAllTaxInfo();
                continue;
            }
            List<List<? extends Object>> taxReturn = this.getTaxAdjustments(dispatcher, "calcTax", serviceContext);

            if (Debug.verboseOn()) {
                Debug.logVerbose("ReturnList: " + taxReturn, module);
            }
            List<GenericValue> orderAdj = UtilGenerics.checkList(taxReturn.get(0));
            List<List<GenericValue>> itemAdj = UtilGenerics.checkList(taxReturn.get(1));

            // set the item adjustments
            if (itemAdj != null) {
                for (int x = 0; x < itemAdj.size(); x++) {
                    List<GenericValue> adjs = itemAdj.get(x);
                    ShoppingCartItem item = shoppingCartItemIndexMap.get(x);
                    if (adjs == null) {
                        adjs = new LinkedList<>();
                    }
                    csi.setItemInfo(item, adjs);
                    if (Debug.verboseOn()) {
                        Debug.logVerbose("Added item adjustments to ship group [" + i + " / " + x + "] - " + adjs, module);
                    }
                }
            }

            // need to manually clear the order adjustments
            csi.shipTaxAdj.clear();
            csi.shipTaxAdj.addAll(orderAdj);
        }
    }

    private Map<String, Object> makeTaxContext(int shipGroup, GenericValue shipAddress, Map<Integer, ShoppingCartItem> shoppingCartItemIndexMap, String originFacilityId, boolean skipEmptyAddresses) {
        ShoppingCart.CartShipInfo csi = cart.getShipInfo(shipGroup);
        int totalItems = csi.shipItemInfo.size();

        List<GenericValue> product = new ArrayList<>(totalItems);
        List<BigDecimal> amount = new ArrayList<>(totalItems);
        List<BigDecimal> price = new ArrayList<>(totalItems);
        List<BigDecimal> quantity = new ArrayList<>(totalItems);
        List<BigDecimal> shipAmt = new ArrayList<>(totalItems);

        Iterator<ShoppingCartItem> it = csi.shipItemInfo.keySet().iterator();
        for (int i = 0; i < totalItems; i++) {
            ShoppingCartItem cartItem = it.next();
            ShoppingCart.CartShipInfo.CartShipItemInfo itemInfo = csi.getShipItemInfo(cartItem);
            product.add(i, cartItem.getProduct());
            amount.add(i, cartItem.getItemSubTotal(itemInfo.quantity));
            price.add(i, cartItem.getBasePrice());
            quantity.add(i, cartItem.getQuantity());
            shipAmt.add(i, BigDecimal.ZERO); // no per item shipping yet
            shoppingCartItemIndexMap.put(i, cartItem);
        }

        //add promotion adjustments
        List<GenericValue> allAdjustments = cart.getAdjustments();
        BigDecimal orderPromoAmt = OrderReadHelper.calcOrderPromoAdjustmentsBd(allAdjustments);

        BigDecimal shipAmount = csi.shipEstimate;
        if (shipAddress == null) {
            shipAddress = cart.getShippingAddress(shipGroup);
        }

        if (shipAddress == null && skipEmptyAddresses) {
            return null;
        }

        // no shipping address; try the billing address
        if (shipAddress == null) {
            for (int i = 0; i < cart.selectedPayments(); i++) {
                ShoppingCart.CartPaymentInfo cpi = cart.getPaymentInfo(i);
                GenericValue billAddr = cpi.getBillingAddress(delegator);
                if (billAddr != null) {
                    shipAddress = billAddr;
                    Debug.logInfo("In makeTaxContext no shipping address, but found address with ID [" + shipAddress.get("contactMechId") + "] from payment method.", module);
                    break;
                }
            }
        }

        if (shipAddress == null) {
            // face-to-face order; use the facility address
            if (originFacilityId != null) {
                GenericValue facilityContactMech = ContactMechWorker.getFacilityContactMechByPurpose(delegator, originFacilityId, UtilMisc.toList("SHIP_ORIG_LOCATION", "PRIMARY_LOCATION"));
                if (facilityContactMech != null) {
                    try {
                        shipAddress = EntityQuery.use(delegator).from("PostalAddress").where("contactMechId", facilityContactMech.getString("contactMechId")).queryOne();
                    } catch (GenericEntityException e) {
                        Debug.logError(e, module);
                    }
                }
            }
        }

        // if shippingAddress is still null then don't calculate tax; it may be an situation where no tax is applicable, or the data is bad and we don't have a way to find an address to check tax for
        if (shipAddress == null) {
            Debug.logWarning("Not calculating tax for new order because there is no shipping address, no billing address, and no address on the origin facility [" + originFacilityId + "]", module);
        }

        Map<String, Object> serviceContext = UtilMisc.<String, Object>toMap("productStoreId", cart.getProductStoreId());
        serviceContext.put("payToPartyId", cart.getBillFromVendorPartyId());
        serviceContext.put("billToPartyId", cart.getBillToCustomerPartyId());
        serviceContext.put("itemProductList", product);
        serviceContext.put("itemAmountList", amount);
        serviceContext.put("itemPriceList", price);
        serviceContext.put("itemQuantityList", quantity);
        serviceContext.put("itemShippingList", shipAmt);
        serviceContext.put("orderShippingAmount", shipAmount);
        serviceContext.put("shippingAddress", shipAddress);
        serviceContext.put("orderPromotionsAmount", orderPromoAmt);

        return serviceContext;
    }

    // Calc the tax adjustments.
    private List<List<? extends Object>> getTaxAdjustments(LocalDispatcher dispatcher, String taxService, Map<String, Object> serviceContext) throws GeneralException {
        Map<String, Object> serviceResult = null;

        try {
            serviceResult = dispatcher.runSync(taxService, serviceContext);
        } catch (GenericServiceException e) {
            Debug.logError(e, module);
            throw new GeneralException("Problem occurred in tax service (" + e.getMessage() + ")", e);
        }

        if (ServiceUtil.isError(serviceResult)) {
            throw new GeneralException(ServiceUtil.getErrorMessage(serviceResult));
        }

        // the adjustments (returned in order) from taxware.
        List<GenericValue> orderAdj = UtilGenerics.checkList(serviceResult.get("orderAdjustments"));
        List<List<GenericValue>> itemAdj = UtilGenerics.checkList(serviceResult.get("itemAdjustments"));

        return UtilMisc.toList(orderAdj, itemAdj);
    }

    public Map<String, Object> processPayment(GenericValue productStore, GenericValue userLogin) throws GeneralException {
        // SCIPIO: 2019-03-07: Added locale
        return CheckOutHelper.processPayment(this.cart.getOrderId(), this.cart.getGrandTotal(), this.cart.getCurrency(), productStore, userLogin, false, false, dispatcher, delegator, getLocale());
    }

    public Map<String, Object> processPayment(GenericValue productStore, GenericValue userLogin, boolean faceToFace) throws GeneralException {
        // SCIPIO: 2019-03-07: Added locale
        return CheckOutHelper.processPayment(this.cart.getOrderId(), this.cart.getGrandTotal(), this.cart.getCurrency(), productStore, userLogin, faceToFace, false, dispatcher, delegator, getLocale());
    }

    public Map<String, Object> processPayment(GenericValue productStore, GenericValue userLogin, boolean faceToFace, boolean manualHold) throws GeneralException {
        // SCIPIO: 2019-03-07: Added locale
        return CheckOutHelper.processPayment(this.cart.getOrderId(), this.cart.getGrandTotal(), this.cart.getCurrency(), productStore, userLogin, faceToFace, manualHold, dispatcher, delegator, getLocale());
    }

    /**
     * processPayment (instance method).
     * <p>
     * NOTE: This is preferred over the static overload 
     * SCIPIO: Added 2019-03-07.
     */
    public Map<String, Object> processPayment(String orderId, BigDecimal orderTotal, String currencyUomId, GenericValue productStore, GenericValue userLogin, boolean faceToFace, boolean manualHold) throws GeneralException {
        return processPayment(orderId, orderTotal, currencyUomId, productStore, userLogin, faceToFace, manualHold, dispatcher, delegator, getLocale());
    }

    /**
     * processPayment (static method).
     * <p>
     * SCIPIO: NOTE: It is recommended to *not* use this method and rather use the instance methods with the same name
     * (e.g. {@link #processPayment(String, BigDecimal, String, GenericValue, GenericValue, boolean, boolean)} where possible, due to this breaking abstraction, wher
     */
    public static Map<String, Object> processPayment(String orderId, BigDecimal orderTotal, String currencyUomId, GenericValue productStore, GenericValue userLogin, boolean faceToFace, boolean manualHold,
            LocalDispatcher dispatcher, Delegator delegator, Locale locale) throws GeneralException { // SCIPIO: 2019-03-07: Added locale
        if (locale == null) {
            locale = Locale.getDefault();
            Debug.logWarning("processPayment called without a locale; using system default (" + locale + ")", module); 
        }
        
        // Get some payment related strings
        // SCIPIO: 2019-03-07: Refactored
        //String DECLINE_MESSAGE = productStore.getString("authDeclinedMessage", locale); // SCIPIO: 2019-03-07: Added locale lookup (for *Labels.xml-based localization)
        //String ERROR_MESSAGE = productStore.getString("authErrorMessage", locale);
        String RETRY_ON_ERROR = productStore.getString("retryFailedAuths", locale);
        if (RETRY_ON_ERROR == null) {
            RETRY_ON_ERROR = "Y";
        }

        List<GenericValue> allPaymentPreferences = null;
        try {
            allPaymentPreferences = EntityQuery.use(delegator).from("OrderPaymentPreference").where("orderId", orderId).queryList();
        } catch (GenericEntityException e) {
            throw new GeneralException("Problems getting payment preferences", e);
        }

        // filter out cancelled preferences
        List<EntityExpr> canExpr = UtilMisc.toList(EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "PAYMENT_CANCELLED"));
        allPaymentPreferences = EntityUtil.filterByAnd(allPaymentPreferences, canExpr);

        // check for online payment methods or in-hand payment types with verbal or external refs
        EntityCondition exprs = EntityCondition.makeCondition("manualRefNum", EntityOperator.NOT_EQUAL, null);
        // SCIPIO (05-28-20): This doesn't make much sense as an orderPaymentPref may contain a manualRefNum but NOT a manualAuthCode,
        // so marking it as AUTHORIZED without it is plain wrong (if not, what's the point of manualAuthCode?).
        // Not sure what the implications would be by adding manualAuthCode != null in the condition below
        // (may affect in a bad way for existing paymentMethodTypes), so for now it will be added in conjunction with paymentMethodTypeId = EXT_STRIPE
        EntityCondition stripeExprs = EntityCondition.makeCondition(
                EntityCondition.makeCondition("manualRefNum", EntityOperator.NOT_EQUAL, null),
                EntityCondition.makeCondition("manualAuthCode", EntityOperator.EQUALS, null),
                EntityCondition.makeCondition("paymentMethodTypeId", EntityOperator.EQUALS, "EXT_STRIPE")
        );
        List<GenericValue> manualRefPaymentPrefs = EntityUtil.filterByAnd(allPaymentPreferences,
                UtilMisc.toList(EntityCondition.makeCondition(exprs, EntityJoinOperator.AND, stripeExprs)));
        if (UtilValidate.isNotEmpty(manualRefPaymentPrefs)) {
            for (GenericValue opp : manualRefPaymentPrefs) {
                Map<String, Object> authCtx = new HashMap<>();
                authCtx.put("orderPaymentPreference", opp);
                if (opp.get("paymentMethodId") == null) {
                    authCtx.put("serviceTypeEnum", "PRDS_PAY_EXTERNAL");
                }
                authCtx.put("processAmount", opp.getBigDecimal("maxAmount"));
                authCtx.put("authRefNum", opp.getString("manualRefNum"));
                authCtx.put("authResult", Boolean.TRUE);
                authCtx.put("userLogin", userLogin);
                authCtx.put("currencyUomId", currencyUomId);

                Map<String, Object> authResp = dispatcher.runSync("processAuthResult", authCtx);
                if (ServiceUtil.isError(authResp)) {
                    throw new GeneralException(ServiceUtil.getErrorMessage(authResp));
                }

                // approve the order
                OrderChangeHelper.approveOrder(dispatcher, userLogin, orderId, manualHold);

                if ("Y".equalsIgnoreCase(productStore.getString("manualAuthIsCapture"))) {
                    Map<String, Object> captCtx = new HashMap<>();
                    captCtx.put("orderPaymentPreference", opp);
                    if (opp.get("paymentMethodId") == null) {
                        captCtx.put("serviceTypeEnum", "PRDS_PAY_EXTERNAL");
                    }
                    captCtx.put("payToPartyId", productStore.get("payToPartyId"));
                    captCtx.put("captureResult", Boolean.TRUE);
                    captCtx.put("captureAmount", opp.getBigDecimal("maxAmount"));
                    captCtx.put("captureRefNum", opp.getString("manualRefNum"));
                    captCtx.put("userLogin", userLogin);
                    captCtx.put("currencyUomId", currencyUomId);

                    Map<String, Object> capResp = dispatcher.runSync("processCaptureResult", captCtx);
                    if (ServiceUtil.isError(capResp)) {
                        throw new GeneralException(ServiceUtil.getErrorMessage(capResp));
                    }
                }
            }
        }

        // check for a paypal express checkout needing completion
        List<EntityExpr> payPalExprs = UtilMisc.toList(
                EntityCondition.makeCondition("paymentMethodId", EntityOperator.NOT_EQUAL, null),
                EntityCondition.makeCondition("paymentMethodTypeId", "EXT_PAYPAL")
           );
        List<GenericValue> payPalPaymentPrefs = EntityUtil.filterByAnd(allPaymentPreferences, payPalExprs);
        if (UtilValidate.isNotEmpty(payPalPaymentPrefs)) {
            GenericValue payPalPaymentPref = EntityUtil.getFirst(payPalPaymentPrefs);
            ExpressCheckoutEvents.doExpressCheckout(productStore.getString("productStoreId"), orderId, payPalPaymentPref, userLogin, delegator, dispatcher);
        }

        // check for online payment methods needing authorization
        Map<String, Object> paymentFields = UtilMisc.<String, Object>toMap("statusId", "PAYMENT_NOT_AUTH");
        List<GenericValue> onlinePaymentPrefs = EntityUtil.filterByAnd(allPaymentPreferences, paymentFields);

        // Check the payment preferences; if we have ANY w/ status PAYMENT_NOT_AUTH invoke payment service.
        // Invoke payment processing.
        if (UtilValidate.isNotEmpty(onlinePaymentPrefs)) {
            boolean autoApproveOrder = UtilValidate.isEmpty(productStore.get("autoApproveOrder")) || "Y".equalsIgnoreCase(productStore.getString("autoApproveOrder"));
            if (orderTotal.compareTo(BigDecimal.ZERO) == 0 && autoApproveOrder) {
                // if there is nothing to authorize; don't bother
                boolean ok = OrderChangeHelper.approveOrder(dispatcher, userLogin, orderId, manualHold);
                if (!ok) {
                    throw new GeneralException("Problem with order change; see above error");
                }
            }

            // now there should be something to authorize; go ahead
            Map<String, Object> paymentResult = null;
            try {
                // invoke the payment gateway service.
                paymentResult = dispatcher.runSync("authOrderPayments",
                        UtilMisc.<String, Object>toMap("orderId", orderId, "userLogin", userLogin), 180, false);
            } catch (GenericServiceException e) {
                Debug.logWarning(e, module);
                throw new GeneralException("Error in authOrderPayments service: " + e.toString(), e.getNested());
            }
            if (Debug.verboseOn()) {
                Debug.logVerbose("Finished w/ Payment Service", module);
            }
            if (ServiceUtil.isError(paymentResult)) {
                throw new GeneralException(ServiceUtil.getErrorMessage(paymentResult));
            }
            if (paymentResult != null && paymentResult.containsKey("processResult")) {
                // grab the customer messages -- only passed back in the case of an error or failure
                List<String> messages = UtilGenerics.checkList(paymentResult.get("authResultMsgs"));

                String authResp = (String) paymentResult.get("processResult");

                if ("FAILED".equals(authResp)) {
                    // order was NOT approved
                    if (Debug.verboseOn()) {
                        Debug.logVerbose("Payment auth was NOT a success!", module);
                    }

                    boolean ok = OrderChangeHelper.rejectOrder(dispatcher, userLogin, orderId);
                    if (!ok) {
                        throw new GeneralException("Problem with order change; see above error");
                    }
                    if (UtilValidate.isEmpty(messages)) {
                        return ServiceUtil.returnError(getStoreAuthMsg(productStore, "authDeclinedMessage", locale)); // SCIPIO: Refactored: DECLINE_MESSAGE
                    }
                    return ServiceUtil.returnError(messages);
                } else if ("APPROVED".equals(authResp)) {
                    // order WAS approved
                    if (Debug.verboseOn()) {
                        Debug.logVerbose("Payment auth was a success!", module);
                    }

                    // set the order and item status to approved
                    if (autoApproveOrder) {
                        List<GenericValue> productStorePaymentSettingList = EntityQuery.use(delegator).from("ProductStorePaymentSetting")
                                .where("productStoreId", productStore.getString("productStoreId"), "paymentMethodTypeId", "CREDIT_CARD", "paymentService", "cyberSourceCCAuth")
                                .queryList();
                        if (productStorePaymentSettingList.size() > 0) {
                            String decision = (String) paymentResult.get("authCode");
                            if (UtilValidate.isNotEmpty(decision)) {
                                if ("ACCEPT".equalsIgnoreCase(decision)) {
                                    boolean ok = OrderChangeHelper.approveOrder(dispatcher, userLogin, orderId, manualHold);
                                    if (!ok) {
                                        throw new GeneralException("Problem with order change; see above error");
                                    }
                                }
                            } else {
                                boolean ok = OrderChangeHelper.approveOrder(dispatcher, userLogin, orderId, manualHold);
                                if (!ok) {
                                    throw new GeneralException("Problem with order change; see above error");
                                }
                            }
                        } else {
                            boolean ok = OrderChangeHelper.approveOrder(dispatcher, userLogin, orderId, manualHold);
                            if (!ok) {
                                throw new GeneralException("Problem with order change; see above error");
                            }
                        }
                    }
                } else if ("ERROR".equals(authResp)) {
                    // service failed
                    if (Debug.verboseOn()) {
                        Debug.logVerbose("Payment auth failed due to processor trouble.", module);
                    }
                    if (!faceToFace && "Y".equalsIgnoreCase(RETRY_ON_ERROR)) {
                        // never do this for a face to face purchase regardless of store setting
                        return ServiceUtil.returnSuccess(getStoreAuthMsg(productStore, "authErrorMessage", locale)); // SCIPIO: Refactored: ERROR_MESSAGE
                    }
                    boolean ok = OrderChangeHelper.cancelOrder(dispatcher, userLogin, orderId);
                    if (!ok) {
                        throw new GeneralException("Problem with order change; see above error");
                    }
                    if (UtilValidate.isEmpty(messages)) {
                        return ServiceUtil.returnError(getStoreAuthMsg(productStore, "authErrorMessage", locale)); // SCIPIO: Refactored: ERROR_MESSAGE
                    }
                    return ServiceUtil.returnError(messages);
                } else {
                    // should never happen
                    return ServiceUtil.returnError(UtilProperties.getMessage(resource_error,"OrderPleaseContactCustomerServicePaymentReturnCodeUnknown", Locale.getDefault()));
                }
            } else {
                // result returned null == service failed
                if (Debug.verboseOn()) {
                    Debug.logVerbose("Payment auth failed due to processor trouble.", module);
                }
                if (!faceToFace && "Y".equalsIgnoreCase(RETRY_ON_ERROR)) {
                    // never do this for a face to face purchase regardless of store setting
                    return ServiceUtil.returnSuccess(getStoreAuthMsg(productStore, "authErrorMessage", locale)); // SCIPIO: Refactored: ERROR_MESSAGE
                }
                boolean ok = OrderChangeHelper.cancelOrder(dispatcher, userLogin, orderId);
                if (!ok) {
                    throw new GeneralException("Problem with order change; see above error");
                }
                return ServiceUtil.returnError(getStoreAuthMsg(productStore, "authErrorMessage", locale)); // SCIPIO: Refactored: ERROR_MESSAGE
            }
        } else {
            // Get the paymentMethodTypeIds - this will need to change when ecom supports multiple payments
            List<EntityExpr> cashCodPcBaExpr = UtilMisc.toList(EntityCondition.makeCondition("paymentMethodTypeId", EntityOperator.EQUALS, "CASH"),
                                           EntityCondition.makeCondition("paymentMethodTypeId", EntityOperator.EQUALS, "EXT_COD"),
                                           EntityCondition.makeCondition("paymentMethodTypeId", EntityOperator.EQUALS, "PERSONAL_CHECK"),
                                           EntityCondition.makeCondition("paymentMethodTypeId", EntityOperator.EQUALS, "EXT_BILLACT"));
            List<GenericValue> cashCodPcBaPaymentPreferences = EntityUtil.filterByOr(allPaymentPreferences, cashCodPcBaExpr);

            if (UtilValidate.isNotEmpty(cashCodPcBaPaymentPreferences) &&
                    UtilValidate.isNotEmpty(allPaymentPreferences) &&
                    cashCodPcBaPaymentPreferences.size() == allPaymentPreferences.size()) {

                //if there are Check type, approve the order only if it is face to face
                List<GenericValue> checkPreferences = EntityUtil.filterByAnd(cashCodPcBaPaymentPreferences, UtilMisc.toMap("paymentMethodTypeId", "PERSONAL_CHECK"));
                if (UtilValidate.isNotEmpty(checkPreferences)) {
                    if (faceToFace) {
                        boolean ok = OrderChangeHelper.approveOrder(dispatcher, userLogin, orderId, manualHold);
                        if (!ok) {
                            throw new GeneralException("Problem with order change; see above error");
                        }
                    }
                // approve this as long as there are only CASH, COD and Billing Account types
                } else {
                    boolean ok = OrderChangeHelper.approveOrder(dispatcher, userLogin, orderId, manualHold);
                    if (!ok) {
                        throw new GeneralException("Problem with order change; see above error");
                    }
                }

            } else {
                // There is nothing to do, we just treat this as a success
            }
        }

        // check to see if we should auto-invoice/bill
        if (faceToFace) {
            if (Debug.verboseOn()) {
                Debug.logVerbose("Face-To-Face Sale - " + orderId, module);
            }
            CheckOutHelper.adjustFaceToFacePayment(orderId, orderTotal, allPaymentPreferences, userLogin, delegator);
            boolean ok = OrderChangeHelper.completeOrder(dispatcher, userLogin, orderId);
            if (Debug.verboseOn()) {
                Debug.logVerbose("Complete Order Result - " + ok, module);
            }
            if (!ok) {
                throw new GeneralException("Problem with order change; see error logs");
            }
        }
        return ServiceUtil.returnSuccess();
    }

    private static String getStoreAuthMsg(GenericValue productStore, String msgFieldName, Locale locale) { // SCIPIO: Refactored
        String msg = productStore.getString(msgFieldName, locale); // SCIPIO: 2019-03-07: Added locale lookup (for *Labels.xml-based localization)
        return UtilValidate.isNotEmpty(msg) ? msg : UtilProperties.getMessage("ProductEntityLabels", "ProductStore." + msgFieldName + ".DefaultShop", locale);
    }

    /**
     * @deprecated SCIPIO: 2019-03-07: Please use a {@link #processPayment} overload that accepts a Locale instead (for messages).
     */
    @Deprecated
    public static Map<String, Object> processPayment(String orderId, BigDecimal orderTotal, String currencyUomId, GenericValue productStore, GenericValue userLogin, boolean faceToFace, boolean manualHold,
            LocalDispatcher dispatcher, Delegator delegator) throws GeneralException {
        return processPayment(orderId, orderTotal, currencyUomId, productStore, userLogin, faceToFace, manualHold, dispatcher, delegator, null);
    }
    
    public static void adjustFaceToFacePayment(String orderId, BigDecimal cartTotal, List<GenericValue> allPaymentPrefs, GenericValue userLogin, Delegator delegator) throws GeneralException {
        BigDecimal prefTotal = BigDecimal.ZERO;
        if (allPaymentPrefs != null) {
            for (GenericValue pref : allPaymentPrefs) {
                BigDecimal maxAmount = pref.getBigDecimal("maxAmount");
                if (maxAmount == null) {
                    maxAmount = BigDecimal.ZERO;
                }
                prefTotal = prefTotal.add(maxAmount);
            }
        }

        if (prefTotal.compareTo(cartTotal) > 0) {
            BigDecimal change = prefTotal.subtract(cartTotal).negate();
            GenericValue newPref = delegator.makeValue("OrderPaymentPreference");
            newPref.set("orderId", orderId);
            newPref.set("paymentMethodTypeId", "CASH");
            newPref.set("statusId", "PAYMENT_RECEIVED");
            newPref.set("maxAmount", change);
            newPref.set("createdDate", UtilDateTime.nowTimestamp());
            if (userLogin != null) {
                newPref.set("createdByUserLogin", userLogin.getString("userLoginId"));
            }
            delegator.createSetNextSeqId(newPref);
        }
    }

    public Map<String, Object> checkOrderBlackList() {
        if (cart == null) {
            return ServiceUtil.returnSuccess("success");
        }
        GenericValue shippingAddressObj = this.cart.getShippingAddress();
        if (shippingAddressObj == null) {
            return ServiceUtil.returnSuccess("success");
        }
        String shippingAddress = UtilFormatOut.checkNull(shippingAddressObj.getString("address1")).toUpperCase(Locale.getDefault());
        shippingAddress = UtilFormatOut.makeSqlSafe(shippingAddress);
        List<EntityExpr> exprs = UtilMisc.toList(EntityCondition.makeCondition(
                EntityCondition.makeCondition(EntityFunction.UPPER_FIELD("blacklistString"), EntityOperator.EQUALS, EntityFunction.UPPER(shippingAddress)),
                EntityOperator.AND,
                EntityCondition.makeCondition("orderBlacklistTypeId", EntityOperator.EQUALS, "BLACKLIST_ADDRESS")));
        String errMsg=null;

        List<GenericValue> paymentMethods = this.cart.getPaymentMethods();
        for (GenericValue paymentMethod : paymentMethods) {
            if ((paymentMethod != null) && ("CREDIT_CARD".equals(paymentMethod.getString("paymentMethodTypeId")))) {
                GenericValue creditCard = null;
                GenericValue billingAddress = null;
                try {
                    creditCard = paymentMethod.getRelatedOne("CreditCard", false);
                    if (creditCard != null) {
                        billingAddress = creditCard.getRelatedOne("PostalAddress", false);
                    }
                } catch (GenericEntityException e) {
                    Debug.logError(e, "Problems getting credit card from payment method", module);
                    errMsg = UtilProperties.getMessage(resource_error,"checkhelper.problems_reading_database", cart.getLocale());
                    return ServiceUtil.returnError(errMsg);
                }
                if (creditCard != null) {
                    String creditCardNumber = UtilFormatOut.checkNull(creditCard.getString("cardNumber"));
                    exprs.add(EntityCondition.makeCondition(
                            EntityCondition.makeCondition("blacklistString", EntityOperator.EQUALS, creditCardNumber), EntityOperator.AND,
                            EntityCondition.makeCondition("orderBlacklistTypeId", EntityOperator.EQUALS, "BLACKLIST_CREDITCARD")));
                }
                if (billingAddress != null) {
                    String address = UtilFormatOut.checkNull(billingAddress.getString("address1").toUpperCase(Locale.getDefault()));
                    address = UtilFormatOut.makeSqlSafe(address);
                    exprs.add(EntityCondition.makeCondition(
                            EntityCondition.makeCondition(EntityFunction.UPPER_FIELD("blacklistString"), EntityOperator.EQUALS, EntityFunction.UPPER(address)),
                            EntityOperator.AND,
                            EntityCondition.makeCondition("orderBlacklistTypeId", EntityOperator.EQUALS, "BLACKLIST_ADDRESS")));
                }
            }
        }

        List<GenericValue> blacklistFound = null;
        if (exprs.size() > 0) {
            try {
                blacklistFound = EntityQuery.use(this.delegator).from("OrderBlacklist").where(exprs).queryList();
            } catch (GenericEntityException e) {
                Debug.logError(e, "Problems with OrderBlacklist lookup.", module);
                errMsg = UtilProperties.getMessage(resource_error,"checkhelper.problems_reading_database", cart.getLocale());
                return ServiceUtil.returnError(errMsg);
            }
        }

        if (UtilValidate.isNotEmpty(blacklistFound)) {
            return ServiceUtil.returnFailure(UtilProperties.getMessage(resource_error,"OrderFailed", cart.getLocale()));
        }
        return ServiceUtil.returnSuccess("success");
    }

    @Deprecated
    public Map<String, Object> checkOrderBlacklist(GenericValue userLogin) {
        return checkOrderBlackList();
    }

    public Map<String, Object> failedBlacklistCheck(GenericValue userLogin, GenericValue productStore) {
        Map<String, Object> result;
        String errMsg=null;
        // SCIPIO: 2019-03-07: Refactored
        //String REJECT_MESSAGE = productStore.getString("authFraudMessage");
        String orderId = this.cart.getOrderId();

        try {
            if (userLogin != null) {
                // nuke the userlogin
                userLogin.set("enabled", "N");
                userLogin.store();
            } else {
                userLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", "system").cache().queryOne();
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
            errMsg = UtilProperties.getMessage(resource_error,"checkhelper.database_error", getLocale()); // SCIPIO: Replaced: cart.getLocale()
            result = ServiceUtil.returnError(errMsg);
            return result;
        }

        // set the order/item status - reverse inv
        OrderChangeHelper.rejectOrder(dispatcher, userLogin, orderId);
        result = ServiceUtil.returnSuccess();
        result.put(ModelService.ERROR_MESSAGE, getStoreAuthMsg(productStore, "authFraudMessage", getLocale()));

        // wipe the cart and session
        this.cart.clear();
        return result;
    }

    public Map<String, Object> checkExternalPayment(String orderId) {
        Map<String, Object> result;
        String errMsg = null;
        // warning there can only be ONE payment preference for this to work
        // you cannot accept multiple payment type when using an external gateway
        GenericValue orderHeader = null;
        try {
            orderHeader = EntityQuery.use(delegator).from("OrderHeader").where("orderId", orderId).queryOne();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Problems getting order header", module);
            errMsg = UtilProperties.getMessage(resource_error,"checkhelper.problems_getting_order_header", (cart != null ? cart.getLocale() : Locale.getDefault()));
            result = ServiceUtil.returnError(errMsg);
            return result;
        }
        if (orderHeader != null) {
            List<GenericValue> paymentPrefs = null;
            try {
                paymentPrefs = orderHeader.getRelated("OrderPaymentPreference", null, null, false);
            } catch (GenericEntityException e) {
                Debug.logError(e, "Problems getting order payments", module);
                errMsg = UtilProperties.getMessage(resource_error,"checkhelper.problems_getting_payment_preference", (cart != null ? cart.getLocale() : Locale.getDefault()));
                result = ServiceUtil.returnError(errMsg);
                return result;
            }
            if (UtilValidate.isNotEmpty(paymentPrefs)) {
                if (paymentPrefs.size() > 1) {
                    Debug.logError("Too many payment preferences, you cannot have more then one when using external gateways", module);
                }
                GenericValue paymentPreference = EntityUtil.getFirst(paymentPrefs);
                String paymentMethodTypeId = paymentPreference.getString("paymentMethodTypeId");
                if (paymentMethodTypeId.startsWith("EXT_")) {
                    // PayPal with a PaymentMethod is not an external payment method
                    if (!("EXT_PAYPAL".equals(paymentMethodTypeId) && UtilValidate.isNotEmpty(paymentPreference.getString("paymentMethodId")))) {
                        String type = paymentMethodTypeId.substring(4);
                        result = ServiceUtil.returnSuccess();
                        result.put("type", type.toLowerCase(Locale.getDefault()));
                        return result;
                    }
                }
            }
            result = ServiceUtil.returnSuccess();
            result.put("type", "none");
            return result;
        }
        errMsg = UtilProperties.getMessage(resource_error, "checkhelper.problems_getting_order_header", (cart != null
                ? cart.getLocale()
                : Locale.getDefault()));
        result = ServiceUtil.returnError(errMsg);
        result.put("type", "error");
        return result;
    }

    /**
     * Sets the shipping contact mechanism for a given ship group on the cart
     *
     * @param shipGroupIndex The index of the ship group in the cart
     * @param shippingContactMechId The identifier of the contact
     * @return A Map conforming to the OFBiz Service conventions containing
     * any error messages
     */
    public Map<String, Object> finalizeOrderEntryShip(int shipGroupIndex, String shippingContactMechId, String supplierPartyId) {
        Map<String, Object> result;
        String errMsg=null;
        //Verify the field is valid
        if (UtilValidate.isNotEmpty(shippingContactMechId)) {
            this.cart.setShippingContactMechId(shipGroupIndex, shippingContactMechId);
            if (UtilValidate.isNotEmpty(supplierPartyId)) {
                this.cart.setSupplierPartyId(shipGroupIndex, supplierPartyId);
            }
            result = ServiceUtil.returnSuccess();
        } else {
            errMsg = UtilProperties.getMessage(resource_error,"checkhelper.enter_shipping_address", (cart != null ? cart.getLocale() : Locale.getDefault()));
            result = ServiceUtil.returnError(errMsg);
        }

        return result;
    }

    /**
     * Sets the options associated with the order for a given ship group
     *
     * @param shipGroupIndex The index of the ship group in the cart
     * @param shippingMethod The shipping method indicating the carrier and
     * shipment type to use
     * @param shippingInstructions Any additional handling instructions
     * @param maySplit "true" or anything else for <code>false</code>
     * @param giftMessage A message to have included for the recipient
     * @param isGift "true" or anything else for <code>false</code>
     * @param internalCode an internal code associated with the order
     * @return A Map conforming to the OFBiz Service conventions containing
     * any error messages
     */
    public Map<String, Object> finalizeOrderEntryOptions(int shipGroupIndex, String shippingMethod, String shippingInstructions, String maySplit,
            String giftMessage, String isGift, String internalCode, String shipBeforeDate, String shipAfterDate, String orderAdditionalEmails) {
        // SCIPIO: Validation for add. emails (error prone)
        List<String> errorMessages = new ArrayList<>();
        orderAdditionalEmails = cleanVerifyEmailListToString(orderAdditionalEmails, errorMessages);
        if (errorMessages.size() > 0) {
            return ServiceUtil.returnError(errorMessages);
        }
        this.cart.setOrderAdditionalEmails(orderAdditionalEmails);
        return finalizeOrderEntryOptions(shipGroupIndex, shippingMethod, shippingInstructions, maySplit, giftMessage, isGift, internalCode, shipBeforeDate, shipAfterDate, null, null);
    }
    public Map<String, Object> finalizeOrderEntryOptions(int shipGroupIndex, String shippingMethod, String shippingInstructions, String maySplit,
            String giftMessage, String isGift, String internalCode, String shipBeforeDate, String shipAfterDate, String internalOrderNotes, String shippingNotes, BigDecimal shipEstimate) {
        this.cart.setItemShipGroupEstimate(shipEstimate, shipGroupIndex);
        return finalizeOrderEntryOptions(shipGroupIndex, shippingMethod, shippingInstructions, maySplit, giftMessage, isGift, internalCode, shipBeforeDate, shipAfterDate, internalOrderNotes, shippingNotes);
    }
    public Map<String, Object> finalizeOrderEntryOptions(int shipGroupIndex, String shippingMethod, String shippingInstructions, String maySplit,
            String giftMessage, String isGift, String internalCode, String shipBeforeDate, String shipAfterDate, String internalOrderNotes, String shippingNotes) {

        Map<String, Object> result = ServiceUtil.returnSuccess();

        String errMsg=null;
        //Verify the shipping method is valid
        if (UtilValidate.isNotEmpty(shippingMethod)) {
            int delimiterPos = shippingMethod.indexOf('@');
            String shipmentMethodTypeId = null;
            String carrierPartyId = null;

            if (delimiterPos > 0) {
                shipmentMethodTypeId = shippingMethod.substring(0, delimiterPos);
                carrierPartyId = shippingMethod.substring(delimiterPos + 1);
            }

            this.cart.setShipmentMethodTypeId(shipGroupIndex, shipmentMethodTypeId);
            this.cart.setCarrierPartyId(shipGroupIndex, carrierPartyId);
        } else {
            errMsg = UtilProperties.getMessage(resource_error,"checkhelper.select_shipping_method", cart.getLocale());
            result = ServiceUtil.returnError(errMsg);
        }

        //Set the remaining order options
        this.cart.setShippingInstructions(shipGroupIndex, shippingInstructions);
        this.cart.setGiftMessage(shipGroupIndex, giftMessage);
        this.cart.setMaySplit(shipGroupIndex, Boolean.valueOf(maySplit));
        this.cart.setIsGift(shipGroupIndex, Boolean.valueOf(isGift));
        this.cart.setInternalCode(internalCode); // FIXME: the internalCode is not a ship group field and should be moved outside of this method
        if (UtilValidate.isNotEmpty(internalOrderNotes)) {
            this.cart.addInternalOrderNote(internalOrderNotes);
        }

        // set ship before date
        if ((shipBeforeDate != null) && (shipBeforeDate.length() > 8)) {
           shipBeforeDate = shipBeforeDate.trim();
           if (shipBeforeDate.length() < 14) {
               shipBeforeDate = shipBeforeDate + " " + "00:00:00.000";
           }

           try {
               this.cart.setShipBeforeDate(shipGroupIndex, (Timestamp) ObjectType.simpleTypeConvert(shipBeforeDate, "Timestamp", null, null));
           } catch (Exception e) {
               errMsg = "Ship Before Date must be a valid date formed ";
               result = ServiceUtil.returnError(errMsg);
           }
        }

        // set ship after date
        if ((shipAfterDate != null) && (shipAfterDate.length() > 8)) {
           shipAfterDate = shipAfterDate.trim();
           if (shipAfterDate.length() < 14) {
               shipAfterDate = shipAfterDate + " " + "00:00:00.000";
           }

           try {
               this.cart.setShipAfterDate(shipGroupIndex, (Timestamp) ObjectType.simpleTypeConvert(shipAfterDate,"Timestamp", null, null));
            } catch (Exception e) {
              errMsg = "Ship After Date must be a valid date formed ";
              result = ServiceUtil.returnError(errMsg);
            }
        }

        // Shipping Notes for order will be public
        if (UtilValidate.isNotEmpty(shippingNotes)) {
            this.cart.addOrderNote(shippingNotes);
        }

        return result;
    }

    /**
     * Sets the payment ID to use during the checkout process
     *
     * @param checkOutPaymentId The payment ID to be associated with the cart
     * @return A Map conforming to the OFBiz Service conventions containing
     * any error messages.
     */
    public Map<String, Object> finalizeOrderEntryPayment(String checkOutPaymentId, BigDecimal amount, boolean singleUse, boolean append) {
        Map<String, Object> result = ServiceUtil.returnSuccess();

        if (UtilValidate.isNotEmpty(checkOutPaymentId)) {
            if (!append) {
                cart.clearPayments();
            }
            cart.addPaymentAmount(checkOutPaymentId, amount, singleUse);
        }

        return result;
    }

    public static BigDecimal availableAccountBalance(String billingAccountId, LocalDispatcher dispatcher) {
        if (billingAccountId == null) {
            return BigDecimal.ZERO;
        }
        try {
            Map<String, Object> res = dispatcher.runSync("calcBillingAccountBalance", UtilMisc.toMap("billingAccountId", billingAccountId));
            if (ServiceUtil.isError(res)) {
                Debug.logError(ServiceUtil.getErrorMessage(res), module);
                return BigDecimal.ZERO;
            }
            BigDecimal availableBalance = (BigDecimal) res.get("accountBalance");
            if (availableBalance != null) {
                return availableBalance;
            }
        } catch (GenericServiceException e) {
            Debug.logError(e, module);
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal availableAccountBalance(String billingAccountId) {
        return availableAccountBalance(billingAccountId, dispatcher);
    }

    public Map<String, BigDecimal> makeBillingAccountMap(List<GenericValue> paymentPrefs) {
        Map<String, BigDecimal> accountMap = new HashMap<>();
        if (paymentPrefs != null) {
            for (GenericValue pp : paymentPrefs) {
                if (pp.get("billingAccountId") != null) {
                    accountMap.put(pp.getString("billingAccountId"), pp.getBigDecimal("maxAmount"));
                }
            }
        }
        return accountMap;
    }

    /**
     * Validate payment methods.
     * <p>
     * SCIPIO: WARN: The stock code in this method does not only check the validity of current pay methods in cart; it
     * also updates pay meth amounts for those that were previously left null.
     * <p>
     * SCIPIO: NOTE: Logic must reflect that of {@link ShoppingCart#isPaymentsAdequate()}.
     */
    public Map<String, Object> validatePaymentMethods() {
        String errMsg = null;
        String billingAccountId = cart.getBillingAccountId();
        BigDecimal billingAccountAmt = cart.getBillingAccountAmount();
        BigDecimal availableAmount = this.availableAccountBalance(billingAccountId);
        if (billingAccountAmt.compareTo(availableAmount) > 0) {
            Debug.logError("Billing account " + billingAccountId + " has [" + availableAmount + "] available but needs [" + billingAccountAmt + "] for this order", module);
            Map<String, String> messageMap = UtilMisc.toMap("billingAccountId", billingAccountId);
            errMsg = UtilProperties.getMessage(resource_error, "checkevents.not_enough_available_on_account", messageMap, cart.getLocale());
            return ServiceUtil.returnError(errMsg);
        }

        // payment by billing account only requires more checking
        List<String> paymentMethods = cart.getPaymentMethodIds();

        // SCIPIO: Patched: We want to consider the pay meths that have no paymentMethodIds!
        // WARN: we are changing the definition of paymentMethods by doing this; some code may need to use paymentMethodsWithPaymentMethodId instead
        //List<String> paymentMethodsWithPaymentMethodId = cart.getPaymentMethodIds();
        paymentMethods.addAll(cart.getPaymentMethodTypeIdsNoPaymentMethodIds()); // add offline, cod, etc, so they can auto calculate totals

        List<String> paymentTypes = cart.getPaymentMethodTypeIds();
        if (paymentTypes.contains("EXT_BILLACT") && paymentTypes.size() == 1 && (paymentMethods.size() == 0 || (paymentMethods.size() == 1 && paymentMethods.contains("EXT_BILLACT")))) { // SCIPIO: length 1 check added
            if (cart.getGrandTotal().compareTo(availableAmount) > 0) {
                errMsg = UtilProperties.getMessage(resource_error, "checkevents.insufficient_credit_available_on_account", cart.getLocale());
                return ServiceUtil.returnError(errMsg);
            }
        }

        // validate any gift card balances
        this.validateGiftCardAmounts();

        // update the selected payment methods amount with valid numbers
        if (paymentMethods != null) {
            List<String> nullPaymentIds = new ArrayList<>();
            for (String paymentMethodId : paymentMethods) {
                BigDecimal paymentAmount = cart.getPaymentAmount(paymentMethodId);
                if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) == 0) {
                    if (Debug.verboseOn()) {
                        Debug.logVerbose("Found null paymentMethodId - " + paymentMethodId, module);
                    }
                    nullPaymentIds.add(paymentMethodId);
                }
            }
            for (String paymentMethodId : nullPaymentIds) {
                BigDecimal selectedPaymentTotal = cart.getPaymentTotal();
                BigDecimal requiredAmount = cart.getGrandTotal();
                BigDecimal newAmount = requiredAmount.subtract(selectedPaymentTotal);
                boolean setOverflow = false;

                ShoppingCart.CartPaymentInfo info = cart.getPaymentInfo(paymentMethodId);

                if (Debug.verboseOn()) {
                    Debug.logVerbose("Remaining total is - " + newAmount, module);
                }
                if (newAmount.compareTo(BigDecimal.ZERO) > 0) {
                    info.amount = newAmount;
                    if (Debug.verboseOn()) {
                        Debug.logVerbose("Set null paymentMethodId - " + info.paymentMethodId + " / " + info.amount, module);
                    }
                } else {
                    info.amount = BigDecimal.ZERO;
                    if (Debug.verboseOn()) {
                        Debug.logVerbose("Set null paymentMethodId - " + info.paymentMethodId + " / " + info.amount, module);
                    }
                }
                if (!setOverflow) {
                    info.overflow = setOverflow = true;
                    if (Debug.verboseOn()) {
                        Debug.logVerbose("Set overflow flag on payment - " + info.paymentMethodId, module);
                    }
                }
            }
        }

        // verify the selected payment method amounts will cover the total
        BigDecimal reqAmtPreParse = cart.getGrandTotal().subtract(cart.getBillingAccountAmount());
        BigDecimal selectedPmnt = cart.getPaymentTotal();

        BigDecimal selectedPaymentTotal = selectedPmnt.setScale(scale, rounding);
        BigDecimal requiredAmount = reqAmtPreParse.setScale(scale, rounding);

        if (UtilValidate.isNotEmpty(paymentMethods) && requiredAmount.compareTo(selectedPaymentTotal) > 0) {
            Debug.logError("Required Amount : " + requiredAmount + " / Selected Amount : " + selectedPaymentTotal, module);
            errMsg = UtilProperties.getMessage(resource_error, "checkevents.payment_not_cover_this_order", cart.getLocale());
            return ServiceUtil.returnError(errMsg);
        }
        if (UtilValidate.isNotEmpty(paymentMethods) && requiredAmount.compareTo(selectedPaymentTotal) < 0) {
            BigDecimal changeAmount = selectedPaymentTotal.subtract(requiredAmount);
            if (!paymentTypes.contains("CASH")) {
                Debug.logError("Change Amount : " + changeAmount + " / No cash.", module);
                // SCIPIO: Use less cryptic message
                //errMsg = UtilProperties.getMessage(resource_error, "checkhelper.change_returned_cannot_be_greater_than_cash", cart.getLocale());
                errMsg = UtilProperties.getMessage(resource_error, "checkevents.payment_cannot_be_greater_than_order", cart.getLocale());
                return ServiceUtil.returnError(errMsg);
            }
            int cashIndex = paymentTypes.indexOf("CASH");
            String cashId = paymentTypes.get(cashIndex);
            BigDecimal cashAmount = cart.getPaymentAmount(cashId);
            if (cashAmount.compareTo(changeAmount) < 0) {
                Debug.logError("Change Amount : " + changeAmount + " / Cash Amount : " + cashAmount, module);
                // SCIPIO: Use less cryptic message
                //errMsg = UtilProperties.getMessage(resource_error, "checkhelper.change_returned_cannot_be_greater_than_cash", cart.getLocale());
                errMsg = UtilProperties.getMessage(resource_error, "checkevents.payment_cannot_be_greater_than_order", cart.getLocale());
                return ServiceUtil.returnError(errMsg);
            }
        }
        return ServiceUtil.returnSuccess();
    }

    /**
     * SCIPIO: Verifies if current payment methods in cart are adequate enough to cover the current order, or in
     * other words the cart payments in current state can effectively be used to pay for the order.
     * <p>
     * The definition of "adequate" is abstracted by this method. Currently, it requires that the payment method
     * total equals exactly the grand total. Due to stock code function, this is only true after a successful
     * call to {@link CheckOutHelper#validatePaymentMethods} (WARN: it is NOT necessarily true after a call to
     * {@link CheckOutHelper#setCheckOutPayment}!).
     *
     * @see CheckOutHelper#validatePaymentMethods()
     */
    public static boolean isPaymentsAdequate(ShoppingCart cart) {
        BigDecimal reqAmtPreParse = cart.getGrandTotal().subtract(cart.getBillingAccountAmount());
        BigDecimal selectedPmnt = cart.getPaymentTotal();

        BigDecimal selectedPaymentTotal = selectedPmnt.setScale(scale, rounding);
        BigDecimal requiredAmount = reqAmtPreParse.setScale(scale, rounding);

        return (requiredAmount.compareTo(selectedPaymentTotal) == 0);
    }

    /**
     * SCIPIO: Verifies if current payment methods in cart are adequate enough to cover the current order, or in
     * other words the cart payments in current state can effectively be used to pay for the order.
     */
    public boolean isPaymentsAdequate() {
        return isPaymentsAdequate(this.cart);
    }

    public void validateGiftCardAmounts() {
        // get the product store
        GenericValue productStore = ProductStoreWorker.getProductStore(cart.getProductStoreId(), delegator);
        if (productStore != null && !"Y".equalsIgnoreCase(productStore.getString("checkGcBalance"))) {
            return;
        }

        // get the payment config
        String paymentConfig = ProductStoreWorker.getProductStorePaymentProperties(delegator, cart.getProductStoreId(), "GIFT_CARD", null, true);
        String giftCardType = EntityUtilProperties.getPropertyValue(paymentConfig, "", "ofbiz", delegator);
        String balanceField = null;

        // get the gift card objects to check
        for (GenericValue gc : cart.getGiftCards()) {
            Map<String, Object> gcBalanceMap = null;
            BigDecimal gcBalance = BigDecimal.ZERO;
            try {
                Map<String, Object> ctx = UtilMisc.toMap("userLogin", cart.getUserLogin());
                ctx.put("currency", cart.getCurrency());
                if ("ofbiz".equalsIgnoreCase(giftCardType)) {
                    balanceField = "balance";
                    ctx.put("cardNumber", gc.getString("cardNumber"));
                    ctx.put("pinNumber", gc.getString("pinNumber"));
                    gcBalanceMap = dispatcher.runSync("checkGiftCertificateBalance", ctx);
                    if (ServiceUtil.isError(gcBalanceMap)) {
                        Debug.logError(ServiceUtil.getErrorMessage(gcBalanceMap), module);
                    }
                }
                if ("valuelink".equalsIgnoreCase(giftCardType)) {
                    balanceField = "balance";
                    ctx.put("paymentConfig", paymentConfig);
                    ctx.put("cardNumber", gc.getString("cardNumber"));
                    ctx.put("pin", gc.getString("pinNumber"));
                    gcBalanceMap = dispatcher.runSync("balanceInquireGiftCard", ctx);
                    if (ServiceUtil.isError(gcBalanceMap)) {
                        Debug.logError(ServiceUtil.getErrorMessage(gcBalanceMap), module);
                    }
                }
            } catch (GenericServiceException e) {
                Debug.logError(e, module);
            }
            if (gcBalanceMap != null) {
                BigDecimal bal = (BigDecimal) gcBalanceMap.get(balanceField);
                if (bal != null) {
                    gcBalance = bal;
                }
            }

            // get the bill-up to amount
            BigDecimal billUpTo = cart.getPaymentAmount(gc.getString("paymentMethodId"));

            // null bill-up to means use the full balance || update the bill-up to with the balance
            if (billUpTo == null || billUpTo.compareTo(BigDecimal.ZERO) == 0 || gcBalance.compareTo(billUpTo) < 0) {
                cart.addPaymentAmount(gc.getString("paymentMethodId"), gcBalance);
            }
        }
    }

    public String cleanVerifyEmailListToString(String emailListString, List<String> errorMessages) { // SCIPIO
        List<String> emailList = cleanVerifyEmailList(emailListString, errorMessages);
        if (emailList != null) {
            return StringUtils.join(emailList, ",");
        } else {
            return null;
        }
    }

    public List<String> cleanVerifyEmailList(String emailListString, List<String> errorMessages) { // SCIPIO
        List<String> res = null;
        if (emailListString != null) {
            res = new ArrayList<>();
            List<String> inEmailList = StringUtil.split(emailListString, ",");
            if (inEmailList != null) {
                for(String inEmail : inEmailList) {
                    String email = inEmail.trim();
                    if (email.length() > 0) {
                        if (UtilValidate.isEmail(email)) {
                            res.add(email);
                        } else {
                            errorMessages.add(UtilProperties.getMessage(resource_error, "checkhelper.value_not_valid_email", UtilMisc.toMap("email", email), (cart != null ? cart.getLocale() : Locale.getDefault())));
                        }
                    }
                }
            }
        }
        return res;
    }
}
