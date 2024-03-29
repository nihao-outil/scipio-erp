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
package org.ofbiz.webtools.labelmanager;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.parsers.ParserConfigurationException;

import org.ofbiz.base.component.ComponentConfig;
import org.ofbiz.base.component.ComponentConfig.ClasspathInfo;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.FileUtil;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.UtilCodec;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.UtilXml;
import org.ofbiz.base.util.cache.UtilCache;
import org.ofbiz.service.ServiceContext;
import org.ofbiz.service.ServiceUtil;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * LabelManagerFactory.
 * SCIPIO: Refactored for threading and various issues, see also LabelFile.
 */
public class LabelManagerFactory {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    public static final String resource = "WebtoolsUiLabels";
    public static final String keySeparator = "#";

    // SCIPIO: these are throw into a single-key cache so it can respond to cache clearing and fix synch
    private static class Static implements Serializable {
        private static final UtilCache<String, Static> STATICS = UtilCache.createUtilCache("label.manager.factory");
        protected final Set<String> componentNamesFound;
        protected final Map<String, LabelFile> filesFound;
        protected final Map<String, LabelFile> filesFoundNoExt;

        private Static(Set<String> componentNamesFound, Map<String, LabelFile> filesFound, Map<String, LabelFile> filesFoundNoExt) {
            this.componentNamesFound = componentNamesFound;
            this.filesFound = filesFound;
            this.filesFoundNoExt = filesFoundNoExt;
        }

        private static Static makeInstance() {
            Set<String> componentNamesFound = new TreeSet<>();
            Collection<ComponentConfig> componentConfigs = ComponentConfig.getAllComponents();
            for (ComponentConfig componentConfig : componentConfigs) {
                componentNamesFound.add(componentConfig.getComponentName());
            }

            Map<String, LabelFile> filesFound;
            try {
                filesFound = findLabelFiles(true);
            } catch (IOException e) {
                Debug.logError(e, module);
                filesFound = Collections.emptyMap();
            }

            Map<String, LabelFile> filesFoundNoExt = new TreeMap<>();
            for(Map.Entry<String, LabelFile> entry : filesFound.entrySet()) {
                String name = entry.getKey();
                if (name.lastIndexOf('.') > 0) { // SCIPIO
                    name = name.substring(0, name.lastIndexOf('.'));
                }
                filesFoundNoExt.put(name, entry.getValue());
            }

            return new Static(Collections.unmodifiableSet(componentNamesFound),
                    Collections.unmodifiableMap(filesFound),
                    Collections.unmodifiableMap(filesFoundNoExt));
        }

        public static Static getInstance() {
            Static entry = STATICS.get("default");
            if (entry == null) {
                entry = makeInstance();
                STATICS.putIfAbsent("default", entry);
            }
            return entry;
        }

        public Set<String> getComponentNamesFound() {
            return componentNamesFound;
        }

        public Map<String, LabelFile> getFilesFound() {
            return filesFound;
        }

        public Map<String, LabelFile> getFilesFoundNoExt() {
            return filesFoundNoExt;
        }
    }

    protected Map<String, LabelInfo> labels = new TreeMap<String, LabelInfo>();
    protected Set<String> localesFound = new TreeSet<String>();
    protected List<LabelInfo> duplicatedLocalesLabelsList = new LinkedList<LabelInfo>();

    public static synchronized LabelManagerFactory getInstance() {
        Static.getInstance();
        return new LabelManagerFactory();
    }

    protected LabelManagerFactory() {
    }

    public static Map<String, LabelFile> findLabelFiles(boolean includeExtension) throws IOException { // SCIPIO
        Map<String, LabelFile> filesFound = new TreeMap<String, LabelFile>();
        List<ClasspathInfo> cpInfos = ComponentConfig.getAllClasspathInfos();
        for (ClasspathInfo cpi : cpInfos) {
            if ("dir".equals(cpi.getType())) {
                String configRoot = cpi.getComponentConfig().getRootLocation();
                configRoot = configRoot.replace('\\', '/');
                if (!configRoot.endsWith("/")) {
                    configRoot = configRoot + "/";
                }
                String location = cpi.getLocation().replace('\\', '/');
                if (location.startsWith("/")) {
                    location = location.substring(1);
                }
                List<File> resourceFiles = FileUtil.findXmlFiles(configRoot + location, null, "resource", null);
                for (File resourceFile : resourceFiles) {
                    String fileName = resourceFile.getName();
                    if (!includeExtension && fileName.lastIndexOf('.') > 0) { // SCIPIO
                        fileName = fileName.substring(0, fileName.lastIndexOf('.'));
                    }
                    filesFound.put(fileName, new LabelFile(resourceFile, cpi.getComponentConfig().getComponentName()));
                }
            }
        }
        return filesFound;
    }

    public void findMatchingLabels(String component, String fileName, String key, String locale)
            throws MalformedURLException, SAXException, ParserConfigurationException, IOException, GeneralException {
        if (UtilValidate.isEmpty(component) && UtilValidate.isEmpty(fileName) && UtilValidate.isEmpty(key) && UtilValidate.isEmpty(locale)) {
            // Important! Don't allow unparameterized queries - doing so will result in loading the entire project into memory
            return;
        }
        for (LabelFile fileInfo : Static.getInstance().getFilesFound().values()) {
            if (UtilValidate.isNotEmpty(component) && !component.equals(fileInfo.componentName)) {
                continue;
            }
            if (UtilValidate.isNotEmpty(fileName) && !fileName.equals(fileInfo.getFileName())) {
                continue;
            }
            if (Debug.infoOn()) {
                Debug.logInfo("Current file : " + fileInfo.getFileName(), module);
            }
            Document resourceDocument = UtilXml.readXmlDocument(fileInfo.file.toURI().toURL(), false);
            Element resourceElem = resourceDocument.getDocumentElement();
            String labelKeyComment = "";
            for (Node propertyNode : UtilXml.childNodeList(resourceElem.getFirstChild())) {
                if (propertyNode instanceof Element) {
                    Element propertyElem = (Element) propertyNode;
                    String labelKey = UtilCodec.canonicalize(propertyElem.getAttribute("key"));
                    String labelComment = "";
                    for (Node valueNode : UtilXml.childNodeList(propertyElem.getFirstChild())) {
                        if (valueNode instanceof Element) {
                            Element valueElem = (Element) valueNode;
                            // Support old way of specifying xml:lang value.
                            // Old way: en_AU, new way: en-AU
                            String localeName = valueElem.getAttribute("xml:lang");
                            if( localeName.contains("_")) {
                                localeName = localeName.replace('_', '-');
                            }
                            String labelValue = UtilCodec.canonicalize(UtilXml.nodeValue(valueElem.getFirstChild()));
                            LabelInfo label = labels.get(labelKey + keySeparator + fileInfo.getFileName());

                            if (UtilValidate.isEmpty(label)) {
                                label = new LabelInfo(labelKey, labelKeyComment, fileInfo.getFileName(), localeName, labelValue, labelComment);
                                labels.put(labelKey + keySeparator + fileInfo.getFileName(), label);
                            } else {
                                if (label.setLabelValue(localeName, labelValue, labelComment, false)) {
                                    duplicatedLocalesLabelsList.add(label);
                                }
                            }
                            localesFound.add(localeName);
                            labelComment = "";
                        } else if (valueNode instanceof Comment) {
                            labelComment = labelComment + UtilCodec.canonicalize(valueNode.getNodeValue());
                        }
                    }
                    labelKeyComment = "";
                } else if (propertyNode instanceof Comment) {
                    labelKeyComment = labelKeyComment + UtilCodec.canonicalize(propertyNode.getNodeValue());
                }
            }
        }
    }

    public LabelFile getLabelFile(String fileName) {
        return Static.getInstance().getFilesFound().get(fileName);
    }

    public Map<String, LabelInfo> getLabels() {
        return labels;
    }

    public Set<String> getLocalesFound() {
        return new TreeSet<String>(localesFound);
    }

    public static Collection<LabelFile> getFilesFound() {
        return Static.getInstance().getFilesFound().values();
    }

    public static Set<String> getComponentNamesFound() {
        return Static.getInstance().getComponentNamesFound();
    }

    public Set<String> getLabelsList() {
        return labels.keySet();
    }

    public int getDuplicatedLocalesLabels() {
        return duplicatedLocalesLabelsList.size();
    }

    public List<LabelInfo> getDuplicatedLocalesLabelsList() {
        return duplicatedLocalesLabelsList;
    }

    public int updateLabelValue(List<String> localeNames, List<String> localeValues, List<String> localeComments, LabelInfo label, String key, String keyComment, String fileName) {
        int notEmptyLabels = 0;
        for (int i = 0; i < localeNames.size(); i++) {
            String localeName = localeNames.get(i);
            String localeValue = localeValues.get(i);
            String localeComment = null;
            if (UtilValidate.isNotEmpty(localeComments)) localeComment = localeComments.get(i);
            if (UtilValidate.isNotEmpty(localeValue) || UtilValidate.isNotEmpty(localeComment)) {
                if (label == null) {
                    try {
                        label = new LabelInfo(key, keyComment, fileName, localeName, localeValue, localeComment);
                        labels.put(key + keySeparator + fileName, label);
                    } catch (Exception e) {
                        Debug.logError(e, module);
                    }
                } else {
                    label.setLabelKeyComment(keyComment);
                }
                if (label != null) {
                    label.setLabelValue(localeName, localeValue, localeComment, true);
                    notEmptyLabels++;
                }
            }
        }
        return notEmptyLabels;
    }

    public static Map<String, LabelFile> getLabelFileMapStatic() { // SCIPIO
        return Static.getInstance().getFilesFound();
    }

    public static LabelFile getLabelFileStatic(String resource) { // SCIPIO
        return getLabelFileMapStatic().get(resource);
    }

    public static Map<String, LabelFile> getLabelFileNoExtMapStatic() { // SCIPIO
        return Static.getInstance().getFilesFoundNoExt();
    }

    public static LabelFile getLabelFileNoExtStatic(String resource) { // SCIPIO
        return getLabelFileNoExtMapStatic().get(resource);
    }
}
