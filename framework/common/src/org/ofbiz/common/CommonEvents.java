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
package org.ofbiz.common;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.ilscipio.scipio.base.util.AttrHandler;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.ofbiz.base.lang.JSON;
import org.ofbiz.base.util.*;
import org.ofbiz.base.util.cache.UtilCache;
import org.ofbiz.common.image.storer.ImageStorers;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtilProperties;
import org.ofbiz.security.Security;
import org.ofbiz.webapp.control.RequestHandler;
import org.ofbiz.webapp.event.JsonEventUtil;
import org.ofbiz.webapp.website.WebSiteWorker;

/**
 * Common Services
 */
public class CommonEvents {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    // SCIPIO: NOTE: ignoreAttrs moved to JsonEventUtil.DEFAULT_IGNORE_ATTR
    //private static final String[] ignoreAttrs = new String[] { // Attributes removed for security reason; _ERROR_MESSAGE_ is kept

    private static final UtilCache<String, Map<String, String>> appletSessions = UtilCache.createUtilCache("AppletSessions", 0, 600000, true);

    public static String checkAppletRequest(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        String sessionId = request.getParameter("sessionId");
        String visitId = request.getParameter("visitId");
        sessionId = sessionId.trim();
        visitId = visitId.trim();

        String responseString = "";

        GenericValue visit = null;
        try {
            visit = EntityQuery.use(delegator).from("Visit").where("visitId", visitId).queryOne();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Cannot Visit Object", module);
        }

        if (visit != null && visit.getString("sessionId").equals(sessionId) && appletSessions.containsKey(sessionId)) {
            Map<String, String> sessionMap = appletSessions.get(sessionId);
            if (sessionMap != null && sessionMap.containsKey("followPage"))
                responseString = sessionMap.remove("followPage");
        }

        try {
            PrintWriter out = response.getWriter();
            response.setContentType("text/plain");
            out.println(responseString);
            out.close();
        } catch (IOException e) {
            Debug.logError(e, "Problems writing servlet output!", module);
        }

        return "success";
    }

    public static String receiveAppletRequest(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        String sessionId = request.getParameter("sessionId");
        String visitId = request.getParameter("visitId");
        sessionId = sessionId.trim();
        visitId = visitId.trim();

        String responseString = "ERROR";

        GenericValue visit = null;
        try {
            visit = EntityQuery.use(delegator).from("Visit").where("visitId", visitId).queryOne();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Cannot Visit Object", module);
        }

        if (visit.getString("sessionId").equals(sessionId)) {
            String currentPage = request.getParameter("currentPage");
            Map<String, String> sessionMap = appletSessions.get(sessionId);
            if (sessionMap != null) {
                String followers = sessionMap.get("followers");
                List<String> folList = StringUtil.split(followers, ",");
                for (String follower: folList) {
                    Map<String, String> folSesMap = UtilMisc.toMap("followPage", currentPage);
                    appletSessions.put(follower, folSesMap);
                }
            }
            responseString = "OK";
        }

        try {
            PrintWriter out = response.getWriter();
            response.setContentType("text/plain");
            out.println(responseString);
            out.close();
        } catch (IOException e) {
            Debug.logError(e, "Problems writing servlet output!", module);
        }

        return "success";
    }

    public static String setAppletFollower(HttpServletRequest request, HttpServletResponse response) {
        Security security = (Security) request.getAttribute("security");
        GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
        String visitId = request.getParameter("visitId");
        if (visitId != null) request.setAttribute("visitId", visitId);
        if (security.hasPermission("SEND_CONTROL_APPLET", userLogin)) {
            String followerSessionId = request.getParameter("followerSid");
            String followSessionId = request.getParameter("followSid");
            Map<String, String> follow = appletSessions.get(followSessionId);
            if (follow == null) follow = new LinkedHashMap<String, String>();
            String followerListStr = follow.get("followers");
            if (followerListStr == null) {
                followerListStr = followerSessionId;
            } else {
                followerListStr = followerListStr + "," + followerSessionId;
            }
            appletSessions.put(followSessionId, follow);
            appletSessions.put(followerSessionId, null);
        }
        return "success";
    }

    public static String setFollowerPage(HttpServletRequest request, HttpServletResponse response) {
        Security security = (Security) request.getAttribute("security");
        GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
        String visitId = request.getParameter("visitId");
        if (visitId != null) request.setAttribute("visitId", visitId);
        if (security.hasPermission("SEND_CONTROL_APPLET", userLogin)) {
            String followerSessionId = request.getParameter("followerSid");
            String pageUrl = request.getParameter("pageUrl");
            Map<String, String> follow = appletSessions.get(followerSessionId);
            if (follow == null) follow = new LinkedHashMap<String, String>();
            follow.put("followPage", pageUrl);
            appletSessions.put(followerSessionId, follow);
        }
        return "success";
    }

    /** Simple event to set the users per-session locale setting. The user's locale
     * setting should be passed as a "newLocale" request parameter. */
    public static String setSessionLocale(HttpServletRequest request, HttpServletResponse response) {
        String localeString = request.getParameter("newLocale");
        if (UtilValidate.isNotEmpty(localeString)) {
            // SCIPIO: 3.0.0: ProductStore.localeStrings restriction (indirectly invoked through ProductStoreAttrHandler)
            Locale locale = AttrHandler.resolver(request).filterLocale(localeString, true);
            if (locale == null) {
                request.setAttribute("_ERROR_MESSAGE_", UtilProperties.getMessage("CommonLanguageNotAvailable", UtilHttp.getLocale(request)));
                return "error";
            }
            localeString = locale.toString();

            UtilHttp.setLocale(request, locale);

            // update the UserLogin object
            GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
            if (userLogin == null) {
                userLogin = (GenericValue) request.getSession().getAttribute("autoUserLogin");
            }

            if (userLogin != null) {
                GenericValue ulUpdate = GenericValue.create(userLogin);
                ulUpdate.set("lastLocale", localeString);
                try {
                    ulUpdate.store();
                    userLogin.refreshFromCache();
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }
            }
        }
        return "success";
    }

    /** Simple event to set the user's per-session time zone setting. */
    public static String setSessionTimeZone(HttpServletRequest request, HttpServletResponse response) {
        String tzString = request.getParameter("tzId");
        if (UtilValidate.isNotEmpty(tzString)) {
            UtilHttp.setTimeZone(request, tzString);

            // update the UserLogin object
            GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
            if (userLogin == null) {
                userLogin = (GenericValue) request.getSession().getAttribute("autoUserLogin");
            }

            if (userLogin != null) {
                GenericValue ulUpdate = GenericValue.create(userLogin);
                ulUpdate.set("lastTimeZone", tzString);
                try {
                    ulUpdate.store();
                    userLogin.refreshFromCache();
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }
            }
        }
        return "success";
    }

    /** Simple event to set the users per-session currency uom value */
    public static String setSessionCurrencyUom(HttpServletRequest request, HttpServletResponse response) {
        String currencyUom = request.getParameter("currencyUom");
        if (UtilValidate.isNotEmpty(currencyUom)) {
            // update the session
            UtilHttp.setCurrencyUom(request.getSession(), currencyUom);

            // update the UserLogin object
            GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
            if (userLogin == null) {
                userLogin = (GenericValue) request.getSession().getAttribute("autoUserLogin");
            }

            if (userLogin != null) {
                GenericValue ulUpdate = GenericValue.create(userLogin);
                ulUpdate.set("lastCurrencyUom", currencyUom);
                try {
                    ulUpdate.store();
                    userLogin.refreshFromCache();
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }
            }
        }
        return "success";
    }

    public static String jsonResponseFromRequestAttributes(HttpServletRequest request, HttpServletResponse response) {
        // pull out the service response from the request attribute
        Map<String, Object> attrMap = JsonEventUtil.collectOutAttributes(request); // SCIPIO: Refactored
        try {
            JSON json = JSON.from(attrMap);
            writeJSONtoResponse(json, request, response);
        } catch (IOException e) {
            return "error";
        }
        return "success";
    }

    /**
     * SCIPIO: variant of jsonResponseFromRequestAttributes version of "json" that excludes more request attributes, more appropriate for frontend.
     * @deprecated SCIPIO: 2.1.0: use {@link #jsonResponseFromRequestAttributes}; configure in request-parameter-filter in controller.
     */
    @Deprecated
    public static String jsonResponseFromRequestAttributesRestricted(HttpServletRequest request, HttpServletResponse response) {
        return jsonResponseFromRequestAttributes(request, response);
    }

    /**
     * SCIPIO: variant of jsonResponseFromRequestAttributes version of "json" that will only output
     * attributes specifically requested in "scpOutParams" map or "scpOutAttrNames" list,
     * or the names otherwise listed in AjaxRequestUtil.
     * NOTE: this is important for security reasons.
     */
    public static String jsonResponseFromRequestAttributesExplicit(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> attrMap = JsonEventUtil.collectOutAttributesExplicit(request);
        try {
            JSON json = JSON.from(attrMap);
            writeJSONtoResponse(json, request, response);
        } catch (IOException e) {
            return "error";
        }
        return "success";
    }

    private static void writeJSONtoResponse(JSON json, HttpServletRequest request, HttpServletResponse response) throws UnsupportedEncodingException {
        String jsonStr = json.toString();
        String httpMethod = request.getMethod();

        // This was added for security reason (OFBIZ-5409), you might need to remove the "//" prefix when handling the JSON response
        // Though normally you simply have to access the data you want, so should not be annoyed by the "//" prefix
        if ("GET".equalsIgnoreCase(httpMethod)) {
            Debug.logWarning("for security reason (OFBIZ-5409) the '//' prefix was added handling the JSON response.  "
                    + "Normally you simply have to access the data you want, so should not be annoyed by the '//' prefix."
                    + "You might need to remove it if you use Ajax GET responses (not recommended)."
                    + "In case, the util.js scrpt is there to help you."
                    + "This can be customized in general.properties with the http.json.xssi.prefix property", module);
            Delegator delegator = (Delegator) request.getAttribute("delegator");
            String xssiPrefix =EntityUtilProperties.getPropertyValue("general", "http.json.xssi.prefix", delegator);
            jsonStr = xssiPrefix + jsonStr;
        }

        // set the JSON content type
        response.setContentType("application/json");
        // jsonStr.length is not reliable for unicode characters
        response.setContentLength(jsonStr.getBytes(UtilIO.getUtf8()).length); // SCIPIO: UtilIO.getUtf8()

        // return the JSON String
        Writer out;
        try {
            out = response.getWriter();
            out.write(jsonStr);
            out.flush();
        } catch (IOException e) {
            Debug.logError(e, module);
        }
    }

    public static String getJSONuiLabelArray(HttpServletRequest request, HttpServletResponse response)
            throws UnsupportedEncodingException, IOException {
        // Format - {resource1 : [key1, key2 ...], resource2 : [key1, key2, ...], ...}
        String jsonString = request.getParameter("requiredLabels");
        Map<String, List<String>> uiLabelObject = null;
        if (UtilValidate.isNotEmpty(jsonString)) {
            JSON json = JSON.from(jsonString);
            uiLabelObject = UtilGenerics.<Map<String, List<String>>> cast(json.toObject(Map.class));
        }
        if (UtilValidate.isEmpty(uiLabelObject)) {
            Debug.logWarning("No resource and labels found in JSON string: " + jsonString, module); // SCIPIO: Changed to warning
            return "error";
        }
        Locale locale = UtilHttp.getLocale(request);
        Map<String, List<String>> uiLabelMap = new HashMap<>();
        Set<Map.Entry<String, List<String>>> entrySet = uiLabelObject.entrySet();
        for (Map.Entry<String, List<String>> entry : entrySet) {
            String resource = entry.getKey();
            List<String> resourceKeys = entry.getValue();
            if (resourceKeys != null) {
                List<String> labels = new ArrayList<>(resourceKeys.size());
                for (String resourceKey : resourceKeys) {
                    String label = UtilProperties.getMessage(resource, resourceKey, locale);
                    labels.add(label);
                }
                uiLabelMap.put(resource, labels);
            }
        }
        writeJSONtoResponse(JSON.from(uiLabelMap), request, response);
        return "success";
    }

    public static String getJSONuiLabel(HttpServletRequest request, HttpServletResponse response) throws UnsupportedEncodingException, IOException {
        // Format - {resource : key}
        String jsonString = request.getParameter("requiredLabel");
        Map<String, String> uiLabelObject = null;
        if (UtilValidate.isNotEmpty(jsonString)) {
            JSON json = JSON.from(jsonString);
            uiLabelObject = UtilGenerics.<Map<String, String>>cast(json.toObject(Map.class));
        }
        if (UtilValidate.isEmpty(uiLabelObject)) {
            Debug.logWarning("No resource and labels found in JSON string: " + jsonString, module); // SCIPIO: Changed to warning // NOTE: one below this must remain error!
            return "error";
        } else if (uiLabelObject.size() > 1) {
            Debug.logError("More than one resource found, please use the method: getJSONuiLabelArray", module);
            return "error";
        }
        Locale locale = UtilHttp.getLocale(request);
        Map<String, String> uiLabelMap = new HashMap<>();
        Set<Map.Entry<String, String>> entrySet = uiLabelObject.entrySet();
        for (Map.Entry<String, String> entry : entrySet) {
            String resource = entry.getKey();
            String resourceKey = entry.getValue();
            if (resourceKey != null) {
                String label = UtilProperties.getMessage(resource, resourceKey, locale);
                uiLabelMap.put(resource, label);
            }
        }
        writeJSONtoResponse(JSON.from(uiLabelMap), request, response);
        return "success";
    }

    public static String getCaptcha(HttpServletRequest request, HttpServletResponse response) {
        try {
            Delegator delegator = (Delegator) request.getAttribute("delegator");
            final String captchaSizeConfigName = StringUtils.defaultIfEmpty(request.getParameter("captchaSize"), "default");
            final String captchaSizeConfig = EntityUtilProperties.getPropertyValue("captcha", "captcha." + captchaSizeConfigName, delegator);
            final String[] captchaSizeConfigs = captchaSizeConfig.split("\\|");
            final String captchaCodeId = StringUtils.defaultIfEmpty(request.getParameter("captchaCodeId"), ""); // this is used to uniquely identify in the user session the attribute where the captcha code for the last captcha for the form is stored

            final int fontSize = Integer.parseInt(captchaSizeConfigs[0]);
            final int height = Integer.parseInt(captchaSizeConfigs[1]);
            final int width = Integer.parseInt(captchaSizeConfigs[2]);
            final int charsToPrint = UtilProperties.getPropertyAsInteger("captcha", "captcha.code_length", 6);
            final char[] availableChars = EntityUtilProperties.getPropertyValue("captcha", "captcha.characters", delegator).toCharArray();

            //It is possible to pass the font size, image width and height with the request as well
            Color backgroundColor = Color.gray;
            Color borderColor = Color.DARK_GRAY;
            Color textColor = Color.ORANGE;
            Color circleColor = new Color(160, 160, 160);
            Font textFont = new Font("Arial", Font.PLAIN, fontSize);
            int circlesToDraw = 6;
            float horizMargin = 20.0f;
            double rotationRange = 0.7; // in radians
            BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            Graphics2D g = (Graphics2D) bufferedImage.getGraphics();

            g.setColor(backgroundColor);
            g.fillRect(0, 0, width, height);

            //Generating some circles for background noise
            g.setColor(circleColor);
            for (int i = 0; i < circlesToDraw; i++) {
                int circleRadius = (int) (Math.random() * height / 2.0);
                int circleX = (int) (Math.random() * width - circleRadius);
                int circleY = (int) (Math.random() * height - circleRadius);
                g.drawOval(circleX, circleY, circleRadius * 2, circleRadius * 2);
            }
            g.setColor(textColor);
            g.setFont(textFont);

            FontMetrics fontMetrics = g.getFontMetrics();
            int maxAdvance = fontMetrics.getMaxAdvance();
            int fontHeight = fontMetrics.getHeight();

            String captchaCode = RandomStringUtils.random(6, availableChars);

            float spaceForLetters = -horizMargin * 2 + width;
            float spacePerChar = spaceForLetters / (charsToPrint - 1.0f);

            for (int i = 0; i < captchaCode.length(); i++) {

                // this is a separate canvas used for the character so that
                // we can rotate it independently
                int charWidth = fontMetrics.charWidth(captchaCode.charAt(i));
                int charDim = Math.max(maxAdvance, fontHeight);
                int halfCharDim = (charDim / 2);

                BufferedImage charImage = new BufferedImage(charDim, charDim, BufferedImage.TYPE_INT_ARGB);
                Graphics2D charGraphics = charImage.createGraphics();
                charGraphics.translate(halfCharDim, halfCharDim);
                double angle = (Math.random() - 0.5) * rotationRange;
                charGraphics.transform(AffineTransform.getRotateInstance(angle));
                charGraphics.translate(-halfCharDim, -halfCharDim);
                charGraphics.setColor(textColor);
                charGraphics.setFont(textFont);

                int charX = (int) (0.5 * charDim - 0.5 * charWidth);
                charGraphics.drawString("" + captchaCode.charAt(i), charX,
                        ((charDim - fontMetrics.getAscent()) / 2 + fontMetrics.getAscent()));

                float x = horizMargin + spacePerChar * (i) - charDim / 2.0f;
                int y = ((height - charDim) / 2);

                g.drawImage(charImage, (int) x, y, charDim, charDim, null, null);

                charGraphics.dispose();
            }
            // Drawing the image border
            g.setColor(borderColor);
            g.drawRect(0, 0, width - 1, height - 1);
            g.dispose();
            response.setContentType("image/jpeg");
            ImageStorers.write(bufferedImage, "jpg", response.getOutputStream(), "IMAGE_CAPTCHA", null, delegator); // SCIPIO: ImageIO->ImageStorers
            HttpSession session = request.getSession();
            Map<String, String> captchaCodeMap = UtilGenerics.checkMap(session.getAttribute("_CAPTCHA_CODE_"));
            if (captchaCodeMap == null) {
                captchaCodeMap = new HashMap<>();
                session.setAttribute("_CAPTCHA_CODE_", captchaCodeMap);
            }
            captchaCodeMap.put(captchaCodeId, captchaCode);
        } catch (IOException | IllegalArgumentException | IllegalStateException ioe) {
            Debug.logError(ioe.getMessage(), module);
        }
        return "success";
    }

    /**
     * SCIPIO: Prepares a redirect or forward based on a targetPage request URI passed by the screens.
     * The targetPage is re-saved in request attributes as "targetPage". targetPage must be a controller
     * request URI within current controller that supports direct (public) requests.
     * <p>
     * Prepares a redirects to the controller URI named in the "targetPage"
     * request attribute or parameter, where parameter also supports "TARGET_PAGE". This is used if
     * targetPageResponse is "redirect-target". In this case the result is "redirect".
     * <p>
     * If targetPageResponse is "forward-target", then it does a forward to targetPage, and result is "forward".
     * <p>
     * if targetPageResponse is "forward-done" or "redirect-done", then it does the same thing
     * but with the alternate var name "donePage", as convenience for some cases where donePage
     * is reserved.
     * <p>
     * If targetPageResponse is not set, it simply returns success.
     * <p>
     * If targetPageResponse is set but targetPage is missing, returns an error. The screens should
     * be coded to always pass donePage if response expected.
     * <p>
     *  If there is another error such as security-related error,
     * returns error. The passed values must be valid public controller URIs in current webapp.
     * <p>
     * NOTE: This event requires the Scipio-enhanced RequestHandler to function properly.
     * <p>
     * NOTE: This does not set any error messages in request, because they are all internal errors.
     * The event response "error" can point to another event which sets an error.
     */
    public static String processTargetPage(HttpServletRequest request, HttpServletResponse response) {

        String targetPageResponse = (String) request.getAttribute("targetPageResponse");
        if (targetPageResponse == null) {
            targetPageResponse = request.getParameter("targetPageResponse");
        }
        if (UtilValidate.isNotEmpty(targetPageResponse)) {
            if ("redirect-done".equals(targetPageResponse) || "forward-done".equals(targetPageResponse)
                    || "forward-target".equals(targetPageResponse) || "redirect-target".equals(targetPageResponse)) {
                String targetPage;
                boolean isAlt = targetPageResponse.endsWith("-done");
                if (isAlt) {
                    targetPage = (String) request.getAttribute("donePage");
                    if (targetPage == null) {
                        targetPage = request.getParameter("donePage");
                        if (targetPage == null) {
                            targetPage = request.getParameter("DONE_PAGE");
                        }
                    }
                } else {
                    isAlt = true;
                    targetPage = (String) request.getAttribute("targetPage");
                    if (targetPage == null) {
                        targetPage = request.getParameter("targetPage");
                        if (targetPage == null) {
                            targetPage = request.getParameter("TARGET_PAGE");
                        }
                    }
                }

                if (UtilValidate.isEmpty(targetPage)) {
                    Debug.logError("Scipio: Missing target page for targetPageResponse " + targetPageResponse, module);
                    return "error";
                } else {
                    // SCIPIO: SECURITY CHECK: In case this is not checked anywhere else - at least make sure
                    // we link within our controller
                    if (RequestHandler.controllerHasRequestUriDirect(request, targetPage)) {
                        request.setAttribute("targetPage", targetPage);
                        return targetPageResponse.substring(0, targetPageResponse.indexOf('-'));
                    } else {
                        Debug.logError("Scipio: targetPage is not a valid direct controller request URI for redirect/forward "
                                + "('" + targetPage + "')", module);
                        return "error";
                    }
                }
            } else {
                Debug.logError("Scipio: Invalid targetPageResponse: " + targetPageResponse, module);
                return "error";
            }
        }
        return "success";
    }

    public static String getRobots(HttpServletRequest request, HttpServletResponse response) {
        try {
            String webSiteId = WebSiteWorker.getWebSiteId(request);
            Delegator delegator = (Delegator) request.getAttribute("delegator");
            GenericValue webSite = delegator.findOne("WebSite", UtilMisc.toMap("webSiteId", webSiteId), true);
            if (webSite == null) throw new GeneralException("website not found: " + webSiteId);
            String robots = webSite.getString("robots");

            response.setContentType("text/plain");
            response.setContentLength(robots.getBytes(UtilIO.getUtf8()).length); // SCIPIO: UtilIO.getUtf8()
            response.setHeader("Cache-Control", "public,max-age=86400");

            // return the content
            Writer out;
            try {
                out = response.getWriter();
                out.write(robots);
                out.flush();
            } catch (IOException e) {
                Debug.logError(e, module);
            }
        } catch (Exception e) {
            return "error";
        }
        return "success";
    }
}
