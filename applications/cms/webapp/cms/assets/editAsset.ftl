<#-- Scipio Asset Editor  -->
<#include "component://cms/webapp/cms/common/common.ftl">

<#-- 
2016-12-02: TODO?: UI CURRENTLY DOES NOT SUPPORT VERSIONING ON ASSET TEMPLATES (BUT SCHEMA DOES)
- ASSUMING ACTIVE VERSION AS ONLY VERSION
-->

<#-- EDIT TEMPLATE -->
<#if assetTemplateModel?has_content>
    <#assign assetType = raw(assetTemplateModel.assetType!"TEMPLATE")>
    <#-- SCIPIO: 2.1.0: Decoupled assetType from editorType so that content assets can use template editor by default or optionally -->
    <#assign editorType = parameters.editorType!>
    <#if "CONTENT" == assetType>
        <#assign editAssetUri = "editContentAsset">
        <#assign listAssetsUri = "contentAssets">
        <#if !editorType?has_content>
            <#-- SCIPIO: 2.1.0: Now using template editor by default for content assets -->
            <#assign editorType = "TEMPLATE">
        </#if>
    <#else>
        <#if !editorType?has_content>
            <#assign editorType = "TEMPLATE">
        </#if>
        <#assign editAssetUri = "editAsset">
        <#assign listAssetsUri = "assets">
    </#if>
    <#assign editAssetUrl = makePageUrl(editAssetUri+"?assetTemplateId=${assetTemplateModel.id!}")>

    <#-- Javascript functions -->
    <@script>
        <@commonCmsScripts />

        <#if "CONTENT" == editorType>
            <@cmsContentEditorScript/>
        <#else>
            $(document).ready(function() {
              <#if "TEMPLATE" == editorType>
                CodeMirror.fromTextArea($('textarea#templateBody')[0], {
                    lineNumbers: true,
                    matchBrackets: true,
                    mode: "freemarker",
                    indentUnit: 4,
                    indentWithTabs: ${indentWithTabs?string},
                    foldGutter: true,
                    gutters: ["CodeMirror-linenumbers", "CodeMirror-foldgutter"],
                    extraKeys: {"Ctrl-Space": "autocomplete"}
                    }).on('change', function(cMirror){
                        $('textarea#templateBody')[0].value = cMirror.getValue();
                        setTemplateSource('Body');
                    }
                );
              </#if>
            });
        </#if>
        $(document).ready(function() {
            setupCmsDeleteActionHooks();
        });

        <#-- Template Source auto-select code -->
        var setTemplateSource = function(val) {
            jQuery('#editorForm input[name=templateSource]').val([val]);
        };
        jQuery(document).ready(function() {
            jQuery('#templateLocation').on('change keyup paste', function() {
                setTemplateSource('Location');
            });
        });
        
        function updateAssetInfo() {
            cmsCheckSubmitFieldOnlyIfChanged('settingsForm', 'description');
            updateCmsElement("<@pageUrl escapeAs='js'>updateAssetInfo</@pageUrl>", 'settingsForm', 
                function(eventMsgs) {
                    doCmsSuccessRedirect("${escapeFullUrl(editAssetUrl, 'js')}", eventMsgs);
                }
            );
        }
        
        function deleteAsset() {
            deleteCmsElement("<@pageUrl escapeAs='js'>deleteAsset</@pageUrl>", 
                { assetTemplateId : "${(assetTemplateModel.id)!}" }, 
                function(eventMsgs) {
                    doCmsSuccessRedirect("<@pageUrl escapeAs='js'>${listAssetsUri}</@pageUrl>", eventMsgs);
                }
            );
        }
    </@script>
    
    <@modal id="delete-dialog">
        <@heading>${uiLabelMap.CommonWarning}</@heading>
        ${uiLabelMap.CmsConfirmDeleteAction}
        <div class="modal-footer ${styles.text_right}">
           <a id="delete-button" class="${styles.button} btn-ok">${uiLabelMap.CommonContinue}</a>
        </div>
    </@modal>
    <#-- Content -->
    <#macro menuContent menuArgs={}>
        <@menu args=menuArgs>
            <@menuitem type="link" href=makePageUrl(editAssetUri) class="+${styles.action_nav!} ${styles.action_add!}" text=uiLabelMap.CommonNew/>
            <@cmsCopyMenuItem target="copyAsset" title=uiLabelMap.CmsCopyAsset>
                <@field type="hidden" name="envAssetType" value=(envAssetType!)/>
                <@field type="hidden" name="assetTemplateId" value=(assetTemplateModel.id!)/><#-- for browsing, on error -->
                <@field type="hidden" name="srcAssetTemplateId" value=(assetTemplateModel.id!)/>
                <@field type="input" name="templateName" value="" required=true label=uiLabelMap.CommonName/>
                <@field type="textarea" name="description" value=(assetTemplateModel.getDescription(locale)!) required=false label=uiLabelMap.CommonDescription/>
            </@cmsCopyMenuItem>
            <@menuitem type="generic">
                <@modal id="modal_new_attr" label=uiLabelMap.CmsAddAttribute linkClass="+${styles.menu_button_item_link!} ${styles.action_nav!} ${styles.action_add!}">
                    <@heading>${uiLabelMap.CmsAddAttribute}</@heading>
                    <form method="post" action="<@pageUrl>addAttributeToAsset</@pageUrl>" id="new-attribute-form">
                    <@fields type="default-compact">
                      <input type="hidden" name="assetTemplateId" value="${assetTemplateModel.id!}"/>
                        <@field type="hidden" name="envAssetType" value=(envAssetType!)/>
                        <@attributeFields/>
                      <@field type="submit" text=uiLabelMap.CommonAdd class="${styles.link_run_sys!} ${styles.action_add!}"/>
                    </@fields>
                    </form>
                 </@modal>
            </@menuitem>
            
            <@menuitem type="generic">
                <@modal id="modal_new_script" label=uiLabelMap.CmsAddScript linkClass="+${styles.menu_button_item_link!} ${styles.action_nav!} ${styles.action_add!}">
                    <@heading>${uiLabelMap.CmsAddScript}</@heading>
                    <@fields type="default-compact">
                        <@cmsScriptTemplateSelectForm formAction=makePageUrl("addScriptToAsset") webSiteId=((assetTemplateModel.webSiteId)!)>
                            <input type="hidden" name="assetTemplateId" value="${assetTemplateModel.id!}"/>
                            <@field type="hidden" name="envAssetType" value=(envAssetType!)/>
                        </@cmsScriptTemplateSelectForm>
                    </@fields>
                </@modal>
            </@menuitem>
            
            <@menuitem type="link" href="javascript:$('form#editorForm').submit(); void(0);" class="+${styles.action_run_sys!} ${styles.action_update!}" text=uiLabelMap.CommonSave/>
            <@menuitem type="link" href="javascript:deleteAsset(); void(0);" class="+${styles.action_run_sys!} ${styles.action_remove!} action_delete" text=uiLabelMap.CommonDelete/>
            
            <@cmsObjectExportFormMenuItem presetConfigName="CmsAssetTemplates" pkField="CmsAssetTemplate" pkValue=(assetTemplateModel.id!)/>
        </@menu>  
    </#macro>
    <@section menuContent=menuContent class="+cms-edit-elem cms-edit-asset"><#-- FIXME: get these classes on <body> instead... -->
        
            <@row>
                <@cell columns=12>
                    <@heading level=1>${assetTemplateModel.name!}</@heading></a>
                </@cell>
            </@row>
            <@row>
                <@cell columns=9>
                  <@form method="post" id="editorForm" action=makePageUrl("createUpdateAsset")>
                    <@field type="hidden" name="assetTemplateId" id="assetTemplateId" value=assetTemplateModel.id!""/>
                      <@field type="hidden" name="envAssetType" value=(envAssetType!)/>

                      <#-- General Content -->
                    <@section title=(editorType == "TEMPLATE")?then(uiLabelMap.CmsTemplate, uiLabelMap.CmsContent) class="+editorContent">
                      <@fields type="default-compact">
                        <#assign expandLangVisible = true>
                        <#assign labelDetail = "">
                        <#assign tooltip = "">
                        <#if editorType == "CONTENT">
                          <#assign expandLang = Static["com.ilscipio.scipio.cms.template.AttributeExpander$ExpandLang"].fromStringSafe("FTL")!>
                          <#if expandLang?has_content>
                              <#assign supportedLangStr = "${raw(uiLabelMap.CmsSupportedLang)}: ${raw(expandLang.getDescriptionWithExample(locale)!)}"/>
                              <#if expandLangVisible>
                                  <#if supportedLangStr?has_content>
                                      <#assign labelDetail><em class="label-detail" style="display: inline">${escapeVal(supportedLangStr, 'html')}</em></#assign>
                                  </#if>
                              <#else>
                                  <#assign tooltip = appendToSentence(tooltip, supportedLangStr + ".")>
                              </#if>
                          </#if>
                        </#if>
                        <@field label=(editorType == "TEMPLATE")?then(uiLabelMap.CmsTemplateBody, getLabel('OrderSendConfirmationEmailBody', 'OrderUiLabels')) type="textarea"
                            class="+editor" containerClass="+editor-field" name="templateBody" id="templateBody" value=(templateBody!"") rows=30 labelDetail=labelDetail tooltip=tooltip/>
                      </@fields>
                        <#-- Codemirrors default font-size is tiny for the hints, so resetting here -->
                        <style type="text/css">
                            .CodeMirror-hints {font-size:1em;}
                        </style>
                        
                        <@field type="input" name="templateLocation" value=(templateLocation!"") id="templateLocation" label=uiLabelMap.CmsTemplateLocation placeholder="component://cms/cms-templates/assets/xxx.ftl"/>
                        
                        <#-- NOTE: this explicit switch makes it clear to the user which is going to be submitted, so less likely to accidentally lose work 
                            It is auto-selected (see JS above) -->
                        <@field type="generic" label="${rawLabel('CmsUse')}:">
                            <#assign templateLocationSelected = templateLocation?has_content>
                            <@field type="radio" name="templateSource" value="Body" label=uiLabelMap.CmsTemplateBody inline=true checked=!templateLocationSelected/>
                            <@field type="radio" name="templateSource" value="Location" label=uiLabelMap.CmsTemplateLocation inline=true checked=templateLocationSelected/>
                        </@field>
                    </@section>
                  </@form>
                    
                    <#-- Attributes -->
                    <#if assetAttr?has_content>
                        <@section title=uiLabelMap.CmsTemplateAttributes class="+editorAttr">
                          <@table type="data-complex" autoAltRows=true responsive=true scrollable=true fixedColumnsRight=1>
                            <@thead>
                                <@tr>
                                    <@th>inputPos</@th>
                                    <@th width="200px">attributeName</@th>
                                    <@th>displayName</@th>
                                    <@th>inputType</@th>
                                    <@th>targetType</@th>
                                    <@th>expandLang</@th>
                                    <@th>expandPos</@th>
                                    <@th>required</@th>
                                    <@th>defaultValue</@th>
                                    <@th>inheritMode</@th>
                                    <@th>maxLength</@th>
                                    <@th>regularExpression</@th>
                                    <@th>inputHelp</@th>
                                    <@th width="32px"></@th>
                                </@tr>
                            </@thead>
                            <#list assetAttr as attrTmpl>
                                <@tr>
                                   <@td>${attrTmpl.inputPosition!}</@td>
                                   <@td>${attrTmpl.name!}</@td>
                                   <@td>${attrTmpl.displayName!}</@td>
                                   <@td>${attrTmpl.type!}</@td>
                                   <@td>${attrTmpl.targetType!}</@td>
                                   <@td>${attrTmpl.expandLang!}</@td>
                                   <@td>${attrTmpl.expandPosition!}</@td>
                                   <@td>${(attrTmpl.required!false)?string("Y", "N")}</@td>
                                   <@td>${attrTmpl.defaultValue!}</@td>
                                   <@td>${getLabel('CmsInheritMode_'+attrTmpl.inheritMode!"NEVER")}</@td>
                                   <@td>${attrTmpl.maxLength!}</@td>
                                   <@td>${attrTmpl.regularExpression!}</@td>
                                   <@td>${attrTmpl.help!}</@td>
                                   <@td>
                                        <a href="javascript:$('#remove_attr_${escapeVal(attrTmpl.id!, 'js-html')}').submit();" class="action_delete">
                                            <i class="${styles.text_color_alert} ${styles.icon!} ${styles.icon_prefix!}trash" style="font-size:16px;margin:4px;"></i>
                                        </a>
                                        <a href="javascript:$('#modal_link_edit_attr_${attrTmpl_index}').click(); void(0);">
                                            <i class="${styles.text_color_alert} ${styles.icon!} ${styles.icon_prefix!}edit" style="font-size:16px;margin:4px;"></i>
                                        </a>
                                    </@td>
                                </@tr>
                            </#list>
                          </@table>
                        </@section>
                        <#-- FIXME: this hast to be rendered outside table and section because of an issue
                            with styles (.row .row?) -->
                        <#list assetAttr as attrTmpl>
                          <@modal id="edit_attr_${attrTmpl_index}">
                            <@heading>${uiLabelMap.CmsEditAttribute}</@heading>
                            <form method="post" action="<@pageUrl>updateAssetAttribute</@pageUrl>" id="edit-attribute-form-${attrTmpl_index}">
                            <@fields type="default-compact">
                              <input type="hidden" name="assetTemplateId" value="${assetTemplateModel.id!}" />
                                <@field type="hidden" name="envAssetType" value=(envAssetType!)/>
                                <@attributeFields attrTmpl=attrTmpl/>
                              <@cmsUpdateAssocFormSubmitField/>
                            </@fields>
                            </form>
                          </@modal>
                        </#list>
                    </#if>
                    
                    <#-- Scripts -->
                    <@cmsScriptTemplateAssocTable scriptTemplates=scriptTemplates 
                        updateAction="updateAssetScript" updateFields={"assetTemplateId":assetTemplateModel.id!""}
                        deleteAction="deleteScriptAndAssetTemplateAssoc" envAssetType=(envAssetType!)/>

                </@cell>
              
                <@cell columns=3>
                  <@form method="post" id="settingsForm" action=makePageUrl('updateAssetInfo')>
                      <@fields type="default-compact">
                        <@field type="hidden" name="assetTemplateId" value=assetTemplateModel.id!""/>

                        <#-- Template Information -->
                        <@section title=uiLabelMap.CommonInformation class="+editorMeta">
                            <@field type="display" value=(assetTemplateModel.id!) label=uiLabelMap.CommonId/>
                            <@field type="input" name="templateName" value=(assetTemplateModel.name!) required=true label=uiLabelMap.CommonName/>
                            <@field type="display" value=uiLabelMap["CmsAssetType_"+raw(assetTemplateModel.assetType!"TEMPLATE")] label=uiLabelMap.CommonType/>
                            <@field type="select" name="contentTypeId" label=uiLabelMap.ContentType>
                                <#assign contentTypeId = assetTemplateModel.contentTypeId!"">
                                <option value=""<#if !contentTypeId?has_content> selected="selected"</#if>></option>
                                <@contentTypeOptions parentTypeId="SCP_TEMPLATE_PART" contentTypeId=contentTypeId/>
                            </@field>
                            <@field type="textarea" name="description_visible" value=(assetTemplateModel.getDescription(locale)!) required=false label=uiLabelMap.CommonDescription/>

                            <#-- NOTE: 2016: this WebSite field has NO rendering impact anymore; for organization purposes only -->
                            <@webSiteSelectField name="webSiteId" value=(assetTemplateModel.webSiteId!) required=false
                                tooltip="${rawLabel('CmsOnlyHookedWebSitesListed')} - ${rawLabel('CmsSettingNotUsedInRenderingNote')}"/>

                            <#-- This will extremely rarely be used for assets, but could have impact in the future...
                            <@field label=uiLabelMap.FormFieldTitle_txTimeout type="input" name="txTimeout" value=(assetTemplateModel.txTimeout!"") required=false tooltip=uiLabelMap.CmsPageTxTimeoutInfo/>
                            -->

                            <@menu type="button">
                                <@menuitem type="link" href="javascript:updateAssetInfo(); void(0);" class="+${styles.action_run_sys!} ${styles.action_update!}" text="${rawLabel('CmsSaveSettings')}" />
                            </@menu>
                        </@section>
                    </@fields>
                  </@form>
                </@cell>  
              
            </@row>
        
        <#-- Workaround for OFBIZ-2330 -->
        <#if assetAttr?has_content>
            <#list assetAttr as attrTmpl>
                <form id="remove_attr_${escapeVal(attrTmpl.id!, 'html')}" method="post" action="<@pageUrl>deleteAttributeFromAsset</@pageUrl>">
                    <input type="hidden" name="attributeTemplateId" value="${attrTmpl.id!}"/>
                    <input type="hidden" name="assetTemplateId" value="${assetTemplateModel.id!}"/>
                        <@field type="hidden" name="envAssetType" value=(envAssetType!)/>
                </form>
            </#list>
        </#if>
    </@section>
<#else>
    <@section>
        <#-- NEW Asset -->
        <@row>
            <@cell columns=6 last=true>
                <@form method="post" id="editorForm" action=makePageUrl("createUpdateAsset")>
                    <@section title=("CONTENT" == envAssetType!)?then(uiLabelMap.CmsNewContentAsset, uiLabelMap.CmsNewAsset)>
                      <@fields type="default-compact">
                          <@field type="hidden" name="envAssetType" value=(envAssetType!)/>

                        <input type="hidden" name="isCreate" value="Y"/>
                        <@field type="input" name="templateName" value=(parameters.templateName!) id="templateName" label=uiLabelMap.CmsTemplateName placeholder=uiLabelMap.CmsMyTemplateName required=true/>
                        <#if envAssetType?has_content>
                            <input type="hidden" name="assetType" value="${envAssetType}"/>
                        <#else>
                          <@field type="select" name="assetType" label=uiLabelMap.CommonType>
                              <#assign assetType = raw(parameters.assetType!)>
                              <option value="TEMPLATE"<#if !assetType?has_content || assetType == "TEMPLATE"> selected="selected"</#if>>${uiLabelMap.CmsAssetType_TEMPLATE}</option>
                              <option value="CONTENT"<#if assetType == "CONTENT"> selected="selected"</#if>>${uiLabelMap.CmsAssetType_CONTENT}</option>
                          </@field>
                        </#if>

                          <#-- NOTE: 2016: this WebSite field has NO rendering impact anymore; for organization purposes only -->
                        <@webSiteSelectField name="webSiteId" value=(parameters.webSiteId!) required=false
                            tooltip="${rawLabel('CmsOnlyHookedWebSitesListed')} - ${rawLabel('CmsSettingNotUsedInRenderingNote')}"/>
                        <@field type="select" name="contentTypeId" label=uiLabelMap.ContentType>
                            <#assign contentTypeId = parameters.contentTypeId!"">
                            <option value=""<#if !contentTypeId?has_content> selected="selected"</#if>></option>
                            <@contentTypeOptions parentTypeId="SCP_TEMPLATE_PART" contentTypeId=contentTypeId />
                        </@field>
                            
                        <@field type="textarea" name="description" value=(parameters.description!) label=uiLabelMap.CommonDescription required=false/>
                        <@field type="submit" text=uiLabelMap.CommonCreate class="+${styles.link_run_sys!} ${styles.action_add!}"/>
                      </@fields>
                    </@section>
                </@form>
            </@cell>
        </@row>
    </@section>
</#if>