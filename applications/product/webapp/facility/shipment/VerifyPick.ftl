<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<#if security.hasEntityPermission("FACILITY", "_VIEW", request)>
    <#assign showInput = "Y">

    <#assign isOrderStatusApproved = (isOrderStatusApproved?has_content && !isOrderStatusApproved)!false>
    <#if (shipments?has_content || isOrderStatusApproved)>
        <#assign showInput = "N">
    </#if>

    <#macro globalMenuContent menuArgs={}>
        <@menu args=menuArgs>
            <@menuitem type="link" href=makePageUrl("VerifyPick?facilityId=" + facility.facilityId + "&resetSearch=true") text=uiLabelMap.ProductVerifyNewPick class="+${styles.action_nav!} ${styles.action_update!}"/>
        </@menu>
    </#macro>

    <#macro menuContent menuArgs={}>
        <@menu args=menuArgs>
            <#if (picklistBinId?has_content || orderId?has_content)>
                <#if !shipments?has_content>
                <@menuitem type="generic">
                    <@modal id="addProductToPick" label="Verify product" linkClass="+${styles.menu_button_item_link!} ${styles.action_nav!} ${styles.action_add!}">
                        <#assign sectionTitle="${rawLabel('ProductProduct')} ${rawLabel('ProductToPick')}"/>
                        <@section title=sectionTitle>
                            <form name="singlePickForm" method="post" action="<@pageUrl>processVerifyPick</@pageUrl>">
                                <input type="hidden" name="orderId" value="${orderId!}"/>
                                <input type="hidden" name="shipGroupSeqId" value="${shipGroupSeqId!}"/>
                                <input type="hidden" name="facilityId" value="${facility.facilityId!}"/>
                                <@field type="lookup" formName="singlePickForm" name="productId"id="productId" size="20" maxlength="20" fieldFormName="LookupProduct" label=uiLabelMap.ProductProductId/>
                                <@field type="input"  name="quantity" size="6" maxlength="6" value="1" label=uiLabelMap.ProductQuantity/>
                                <input type="submit" value="${uiLabelMap.ProductVerify}&nbsp;${uiLabelMap.OrderItem}" class="${styles.link_run_sys!} ${styles.action_verify!}"/>
                            </form>
                        </@section>
                    </@modal>
                </@menuitem>
                <@menuitem type="generic">
                    <a href="javascript:document.multiPickForm.action='${makePageUrl('processBulkVerifyPick')}';document.multiPickForm.submit();" class="+${styles.link_run_sys!} ${styles.action_update!}">${uiLabelMap.ProductVerifyAllItems}</a>
                </@menuitem>
                <#else>
                    <a href="${makePageUrl('PackOrder?facilityId=' + (facility.facilityId!) + '&orderId=' + (orderId!) + '&shipGroupSeqId=' + (shipGroupSeqId!'00001'))}" class="+${styles.link_run_sys!} ${styles.action_update!}">${uiLabelMap.ProductPacking}</a>
                </#if>
            <#else>
            </#if>
        </@menu>
    </#macro>

    <@section menuContent=globalMenuContent>
        <#-- SCIPIO: 2.1.0: Rendering lookup form when nothing is present -->
        <#if !picklistBinId?has_content && !orderId?has_content>
            <@section>
                <form name="selectOrderForm" method="post" action="<@pageUrl>VerifyPick</@pageUrl>">
                    <input type="hidden" name="facilityId" value="${facility.facilityId!}"/>
                    <@field type="generic" label=uiLabelMap.ProductOrderId>
                        <@field type="lookup" formName="selectOrderForm" name="orderId" id="orderId" size="20" maxlength="20" fieldFormName="LookupOrderHeader" value=(orderId!)/>
                        /
                        <@field type="input" inline=true name="shipGroupSeqId" size="6" maxlength="6" value=shipGroupSeqId!"00001"/>
                    </@field>
                    <@field type="generic" label=uiLabelMap.FormFieldTitle_picklistBinId>
                        <@field type="input" label=uiLabelMap.FormFieldTitle_picklistBinId name="picklistBinId" size="29" maxlength="60" value=(picklistBinId!)/>
                    </@field>
                    <@field type="submit" text="${rawLabel('ProductVerify')} ${rawLabel('OrderOrder')}" class="+${styles.link_run_sys!} ${styles.action_verify!}"/>
                </form>
            </@section>
        </#if>

        <#if orderHeader?? && orderHeader?has_content && orderItemShipGroup?has_content>
            <#assign sectionTitle>${getLabel('ProductOrderId')} <a href="<@serverUrl>/ordermgr/control/orderview?orderId=${orderId}</@serverUrl>">${orderId}</a> / ${getLabel('ProductOrderShipGroupId')} #${shipGroupSeqId}</#assign>
            <@section title=wrapAsRaw(sectionTitle, 'htmlmarkup') menuContent=menuContent!>
                <#if (orderItemShipGroup.contactMechId)?has_content>
                    <#assign postalAddress = orderItemShipGroup.getRelatedOne("PostalAddress", false)>
                </#if>
                <#assign carrier = orderItemShipGroup.carrierPartyId!(uiLabelMap.CommonNA)>
                <@row>
                    <@cell columns=4>
                        <#if postalAddress?exists >
                            <@heading><strong>${uiLabelMap.ProductShipToAddress}</strong></@heading>
                            ${uiLabelMap.CommonTo}: ${postalAddress.toName!""}<br />
                            <#if postalAddress.attnName?has_content>
                                ${uiLabelMap.CommonAttn}: ${postalAddress.attnName}<br />
                            </#if>
                            ${postalAddress.address1}<br/>
                            <#if postalAddress.address2?has_content>
                                ${postalAddress.address2}<br/>
                            </#if>
                            ${postalAddress.city!}, ${postalAddress.stateProvinceGeoId!} ${postalAddress.postalCode!}<br />
                            ${postalAddress.countryGeoId!}<br/>
                        </#if>
                    </@cell>
                    <@cell columns=4>
                        <@heading><strong>${uiLabelMap.ProductCarrierShipmentMethod}</strong></@heading>
                        <#if carrier == "USPS">
                            <#assign color = "red">
                        <#elseif carrier == "UPS">
                            <#assign color = "green">
                        <#else>
                            <#assign color = "black">
                        </#if>
                        <#if carrier != "_NA_">
                            <font color="${color}">${carrier}</font>&nbsp;
                        </#if>
                        <#assign description = (delegator.findOne("ShipmentMethodType", {"shipmentMethodTypeId":orderItemShipGroup.shipmentMethodTypeId}, false)).description>
                        ${description!"??"}<br/>
                        ${uiLabelMap.ProductEstimatedShipCostForShipGroup}<br />
                        <#if shipmentCostEstimateForShipGroup?exists>
                            <@ofbizCurrency amount=shipmentCostEstimateForShipGroup isoCode=(orderReadHelper.getCurrency()!)/><br />
                        </#if>
                    </@cell>
                    <@cell columns=4>
                        <@heading><strong>${uiLabelMap.OrderInstructions}</strong></@heading>
                        ${orderItemShipGroup.shippingInstructions?default("(${uiLabelMap.CommonNone})")}
                    </@cell>
                </@row>
            </@section>
        </#if>
        <#if shipments?has_content>
            <@section title=wrapAsRaw(rawLabel('ProductPicked') + " " + rawLabel('FacilityShipments'), 'htmlmarkup')>
                <#list shipments as shipment>
                    <@row>
                        <@cell columns=4>
                            <@heading><strong>${uiLabelMap.ProductShipmentId}</strong></@heading>
                            <a href="<@pageUrl>EditShipment?shipmentId=${shipment.shipmentId}</@pageUrl>" class="${styles.link_nav_info_id!}">${shipment.shipmentId}</a>
                        </@cell>
                        <@cell columns=4 last=true>
                            <#if invoiceIdsPerShipment?has_content>
                                <@heading><strong>${uiLabelMap.AccountingInvoices}</strong></@heading>
                                <@menu type="button">
                                    <#list invoiceIdsPerShipment[shipment.shipmentId] as invoiceId>
                                        <@menuitem type="generic">
                                            ${uiLabelMap.CommonNbr}<a href="<@serverUrl>/accounting/control/invoiceOverview?invoiceId=${invoiceId}${raw(externalKeyParam)}</@serverUrl>" target="_blank" class="${styles.menu_button_item_link!} ${styles.action_nav!} ${styles.action_view!}">${invoiceId}</a>
                                                    (<a href="<@serverUrl>/accounting/control/invoice.pdf?invoiceId=${invoiceId}${raw(externalKeyParam)}</@serverUrl>" target="_blank" class="${styles.menu_button_item_link!} ${styles.action_run_sys!} ${styles.action_export!}">PDF</a>)
                                            </@menuitem>
                                    </#list>
                                </@menu>
                            </#if>
                        </@cell>
                    </@row>
                </#list>
            </@section>
        </#if>

        <#if showInput != "N">
            <#if orderItemShipGroupAssocs?has_content>
                <#assign sectionTitle="${rawLabel('ProductProduct')} ${rawLabel('ProductToPick')}"/>
                <@section title=sectionTitle>
                    <form name="multiPickForm" method="post" action="<@pageUrl>processVerifyPick</@pageUrl>">
                        <input type="hidden" name="productId" value=""/>
                        <input type="hidden" name="quantity" value=""/>
                        <input type="hidden" name="orderItemSeqId" value=""/>
                        <input type="hidden" name="facilityId" value="${facility.facilityId!}"/>
                        <input type="hidden" name="userLoginId" value="${userLoginId!}"/>
                        <input type="hidden" name="orderId" value="${orderId!}"/>
                        <input type="hidden" name="shipGroupSeqId" value="${shipGroupSeqId!}"/>
                        <@table type="data-list" autoAltRows=true scrollable=true responsive=true>
                            <@thead>
                                <@tr>
                                    <@th>${uiLabelMap.ProductItem} ${uiLabelMap.CommonNbr}</@th>
                                    <@th>${uiLabelMap.ProductProductId}</@th>
                                    <@th>${uiLabelMap.ProductInternalName}</@th>
                                    <@th>${uiLabelMap.ProductCountryOfOrigin}</@th>
                                    <@th>${uiLabelMap.ProductOrderedQuantity}</@th>
                                    <@th>${uiLabelMap.ProductVerified}&nbsp;${uiLabelMap.CommonQuantity}</@th>
                                    <@th>${uiLabelMap.CommonQty}&nbsp;${uiLabelMap.CommonTo}&nbsp;${uiLabelMap.ProductVerify}</@th>
                                    <@th>${uiLabelMap.ProductVerify}&nbsp;${uiLabelMap.OrderItems}</@th>
                                </@tr>
                            </@thead>
                            <@tbody>
                                <#assign rowKey = 0>
                                <#assign counter = 1>
                                <#-- <#assign isShowVerifyItemButton = "false"> -->
                                <#list orderItemShipGroupAssocs as orderItemShipGroupAssoc>
                                    <#assign orderItem = orderItemShipGroupAssoc.getRelatedOne("OrderItem")>
                                    <#if orderItem.statusId == "ITEM_APPROVED">
                                        <#assign orderItemSeqId = orderItemShipGroupAssoc.orderItemSeqId!>
                                        <#assign readyToVerify = verifyPickSession.getReadyToVerifyQuantity(orderItemSeqId)>
                                        <#assign orderItemQuantity = orderItemShipGroupAssoc.getBigDecimal("quantity")>
                                        <#assign verifiedQuantity = 0.000000>

                                        <#if (shipments?has_content)>
                                            <#list shipments as shipment>
                                                <#assign itemIssuances = delegator.findByAnd("ItemIssuance", {"shipmentId":shipment.getString("shipmentId"), "orderItemSeqId":orderItemSeqId}, null, false)/>
                                                <#if itemIssuances?has_content>
                                                    <#list itemIssuances as itemIssuance>
                                                        <#assign verifiedQuantity = verifiedQuantity + itemIssuance.getBigDecimal("quantity")>
                                                    </#list>
                                                </#if>
                                            </#list>
                                        </#if>
                                        <#if verifiedQuantity == orderItemQuantity>
                                            <#assign counter = counter +1>
                                        </#if>
                                        <#assign orderItemQuantity = orderItemQuantity.subtract(verifiedQuantity)>
                                        <#assign product = orderItem.getRelatedOne("Product", false)!/>
                                        <@tr>
                                            <#-- <#if (orderItemQuantity.compareTo(readyToVerify) > 0)>
                                                <#assign isShowVerifyItemButton = "true">
                                            <#else>
                                            </#if> -->
                                            <@td>${orderItemSeqId!}</@td>
                                            <@td>${product.productId!(uiLabelMap.CommonNA)}</@td>
                                            <@td>
                                                <a href="<@serverUrl>/catalog/control/ViewProduct?productId=${product.productId!}${raw(externalKeyParam)}</@serverUrl>" class="${styles.link_nav_info_name!}" target="_blank">${(product.internalName)!}</a>
                                            </@td>
                                            <@td>
                                                <@field type="select" name="geo_o_${orderItemShipGroupAssoc_index}">
                                                    <#if product.originGeoId?has_content>
                                                        <#assign originGeoId = product.originGeoId>
                                                        <#assign geo = delegator.findOne("Geo", {"geoId":originGeoId}, true)>
                                                        <option value="${originGeoId}">${geo.geoName!}</option>
                                                        <option value="${originGeoId}">---</option>
                                                    </#if>
                                                    <option value=""></option>
                                                    <@render resource="component://common/widget/CommonScreens.xml#countries" ctxVars={"countriesPreselect":!product.originGeoId??}/>
                                                </@field>
                                            </@td>
                                            <@td>${orderItemQuantity!}</@td>
                                            <@td>${readyToVerify!}</@td>
                                            <@td>
                                                <#assign qtyToVerify = 0>
                                                <#if (orderItemQuantity.compareTo(readyToVerify) > 0)>
                                                    <#assign qtyToVerify = orderItemQuantity.subtract(readyToVerify) >
                                                    <input type="text" size="7" name="qty_o_${orderItemShipGroupAssoc_index}" value="${qtyToVerify!}"/>
                                                <#else>
                                                    ${qtyToVerify}
                                                </#if>
                                            </@td>
                                            <@td>
                                                <#if (orderItemQuantity.compareTo(readyToVerify) > 0)>
                                                    <a href="javascript:$('input[name=productId]').val('${orderItem.productId}');$('input[name=quantity]').val('${qtyToVerify}');$('input[name=orderItemSeqId]').val('${orderItem.orderItemSeqId}');document.multiPickForm.submit();"
                                                        class="+${styles.link_run_sys!} ${styles.action_update!}">${uiLabelMap.ProductVerify}&nbsp;${uiLabelMap.OrderItem}</a>
                                                </#if>
                                            </@td>
                                            <input type="hidden" name="prd_o_${orderItemShipGroupAssoc_index}" value="${(orderItem.productId)!}"/>
                                            <input type="hidden" name="ite_o_${orderItemShipGroupAssoc_index}" value="${(orderItem.orderItemSeqId)!}"/>
                                        </@tr>
                                        <#assign workOrderItemFulfillments = orderItem.getRelated("WorkOrderItemFulfillment", null, null, false)/>
                                        <#if workOrderItemFulfillments?has_content>
                                            <#assign workOrderItemFulfillment = Static["org.ofbiz.entity.util.EntityUtil"].getFirst(workOrderItemFulfillments)/>
                                            <#if workOrderItemFulfillment?has_content>
                                                <#assign workEffort = workOrderItemFulfillment.getRelatedOne("WorkEffort", false)/>
                                                <#if workEffort?has_content>
                                                    <#assign workEffortTask = Static["org.ofbiz.entity.util.EntityUtil"].getFirst(delegator.findByAnd("WorkEffort", {"workEffortParentId":workEffort.workEffortId}, null, false))/>
                                                    <#if workEffortTask?has_content>
                                                        <#assign workEffortInventoryAssigns = workEffortTask.getRelated("WorkEffortInventoryAssign", null, null, false)/>
                                                        <#if workEffortInventoryAssigns?has_content>
                                                            <@tr>
                                                                <@td colspan="8">
                                                                    ${uiLabelMap.OrderMarketingPackageComposedBy}
                                                                </@td>
                                                            </@tr>
                                                            <#list workEffortInventoryAssigns as workEffortInventoryAssign>
                                                                <#assign inventoryItem = workEffortInventoryAssign.getRelatedOne("InventoryItem", false)/>
                                                                <#assign product = inventoryItem.getRelatedOne("Product", false)/>
                                                                <@tr>
                                                                    <@td colspan="2"></@td>
                                                                    <@td>${product.productId!(uiLabelMap.CommonNA)}</@td>
                                                                    <@td>${product.internalName!}</@td>
                                                                    <@td></@td>
                                                                    <@td>${workEffortInventoryAssign.quantity!}</@td>
                                                                </@tr>
                                                            </#list>
                                                        </#if>
                                                    </#if>
                                                </#if>
                                            </#if>
                                            <#assign rowKey = rowKey + 1>
                                        </#if>
                                    </#if>
                                </#list>
                            </@tbody>
                        </@table>
                    </form>
                </@section>
            </#if>

            <#assign orderId = orderId! >
            <#if verifyPickSession?has_content>
                <#assign pickRows = verifyPickSession.getPickRows(orderId)!>
                <form name="completePickForm" method="post" action="<@pageUrl>completeVerifiedPick</@pageUrl>">
                    <input type="hidden" name="orderId" value="${orderId!}"/>
                    <input type="hidden" name="shipGroupSeqId" value="${shipGroupSeqId!}"/>
                    <input type="hidden" name="facilityId" value="${facility.facilityId!}"/>
                    <input type="hidden" name="userLoginId" value="${userLoginId!}"/>
                    <#if pickRows?has_content>
                        <@section title="${rawLabel('ProductVerified')} ${rawLabel('OrderItems')} : ${pickRows.size()!}">
                            <@table type="data-list" autoAltRows=true scrollable=true responsive=true>
                                <@thead>
                                    <@tr class="header-row">
                                        <@th>${uiLabelMap.ProductItem} ${uiLabelMap.CommonNbr}</@th>
                                        <@th>${uiLabelMap.ProductProductId}</@th>
                                        <@th>${uiLabelMap.ProductInventoryItem} ${uiLabelMap.CommonNbr}</@th>
                                        <@th>${uiLabelMap.ProductVerified}&nbsp;${uiLabelMap.CommonQuantity}</@th>
                                        <@th>&nbsp;</@th>
                                    </@tr>
                                </@thead>
                                <@tbody>
                                    <#list pickRows as pickRow>
                                        <#if (pickRow.getOrderId()!).equals(orderId)>
                                            <@tr>
                                                <@td>${pickRow.getOrderItemSeqId()!}</@td>
                                                <@td>${pickRow.getProductId()!}</@td>
                                                <@td>${pickRow.getInventoryItemId()!}</@td>
                                                <@td>${pickRow.getReadyToVerifyQty()!}</@td>
                                            </@tr>
                                        </#if>
                                    </#list>
                                </@tbody>
                                <@tfoot>
                                    <@tr>
                                        <@td colspan="7">
                                            <#-- <#if isShowVerifyItemButton == "true">
                                                <input type="submit" value="${uiLabelMap.ProductVerify}&nbsp;${uiLabelMap.OrderItems}" class="${styles.link_run_sys!} ${styles.action_verify!}"/>
                                            </#if>
                                            &nbsp; -->
                                            <#if rowKey != counter>
                                                <input type="button" value="${uiLabelMap.CommonCancel}" onclick="javascript:document.clearPickForm.submit();"/>
                                            </#if>
                                            <a href="javascript:document.completePickForm.submit()" class="${styles.link_run_sys!} ${styles.action_complete!}">${uiLabelMap.ProductComplete}</a>
                                        </@td>
                                    </@tr>
                                </@tfoot>
                            </@table>
                        </@section>
                    </#if>
                </form>
                <@form name="clearPickForm" method="post" action=makePageUrl("cancelAllRows")>
                    <input type="hidden" name="orderId" value="${orderId!}"/>
                    <input type="hidden" name="shipGroupSeqId" value="${shipGroupSeqId!}"/>
                    <input type="hidden" name="facilityId" value="${facility.facilityId!}"/>
                </@form>
            </#if>
        </#if>
    </@section>
<#else>
  <@commonMsg type="error">${uiLabelMap.ProductFacilityViewPermissionError}</@commonMsg>
</#if>
