/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.scripting.resolver.internal;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.osgi.framework.Bundle;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
        service = BundledScriptFinder.class
)
public class BundledScriptFinder {

    private static final String NS_JAVAX_SCRIPT_CAPABILITY = "javax.script";
    private static final String SLASH = "/";
    private static final String DOT = ".";
    private static final Set<String> DEFAULT_METHODS = new HashSet<>(Arrays.asList("GET", "HEAD"));

    @Reference
    private ScriptEngineManager scriptEngineManager;

    ScriptEngineExecutable getScript(SlingHttpServletRequest request, Bundle bundle, boolean precompiledScripts) {
        return getScript(request, bundle, precompiledScripts,null);
    }

    ScriptEngineExecutable getScript(SlingHttpServletRequest request, Bundle bundle, boolean precompiledScripts, String delegatedResourceType) {
        List<String> scriptMatches;
        if (StringUtils.isEmpty(delegatedResourceType)) {
            scriptMatches = buildScriptMatches(request);
        } else {
            scriptMatches = buildScriptMatches(request, delegatedResourceType);
        }
        for (String extension : getScriptEngineExtensions()) {
            for (String match : scriptMatches) {
                URL bundledScriptURL;
                if (precompiledScripts) {
                    String className = fromScriptPathToClassName(match);
                    try {
                        Class clazz = bundle.loadClass(className);
                        return new PrecompiledScript(bundle, scriptEngineManager.getEngineByExtension(extension),
                                clazz.getDeclaredConstructor().newInstance());
                    } catch (ClassNotFoundException e) {
                        // do nothing here
                    } catch (Exception e) {
                        throw new RuntimeException("Cannot correctly instantiate class " + className + ".");
                    }
                } else {
                    bundledScriptURL = bundle.getEntry(NS_JAVAX_SCRIPT_CAPABILITY + SLASH + match + DOT + extension);
                    if (bundledScriptURL != null) {
                        return new Script(bundle, bundledScriptURL, scriptEngineManager.getEngineByExtension(extension));
                    }
                }
            }
        }
        return null;
    }

    private List<String> buildScriptMatches(SlingHttpServletRequest request) {
        return buildScriptMatches(request, null);
    }

    private List<String> buildScriptMatches(SlingHttpServletRequest request, String delegatedResourceType) {
        List<String> matches = new ArrayList<>();
        String resourceType = StringUtils.isEmpty(delegatedResourceType) ? request.getResource().getResourceType() : delegatedResourceType;
        String version = null;
        String method = request.getMethod();
        boolean defaultMethod = DEFAULT_METHODS.contains(method);
        if (resourceType.contains(SLASH) && StringUtils.countMatches(resourceType, SLASH) == 1) {
            version = resourceType.substring(resourceType.indexOf(SLASH) + 1);
            resourceType = resourceType.substring(0, resourceType.length() - version.length() - 1);
        }
        String extension = request.getRequestPathInfo().getExtension();
        String[] selectors = request.getRequestPathInfo().getSelectors();
        if (selectors.length > 0) {
            for (int i = selectors.length - 1; i >= 0; i--) {
                String scriptForMethod = resourceType + (StringUtils.isNotEmpty(version) ? SLASH + version + SLASH : SLASH) +
                        method + DOT + String.join(SLASH, Arrays.copyOf(selectors, i + 1));
                String scriptNoMethod = resourceType + (StringUtils.isNotEmpty(version) ? SLASH + version + SLASH : SLASH) + String.join
                        (SLASH, Arrays.copyOf(selectors, i + 1));
                if (StringUtils.isNotEmpty(extension)) {
                    if (defaultMethod) {
                        matches.add(scriptNoMethod + DOT + extension);
                    }
                    matches.add(scriptForMethod + DOT + extension);
                }
                if (defaultMethod) {
                    matches.add(scriptNoMethod);
                }
                matches.add(scriptForMethod);
            }
        }
        String scriptForMethod = resourceType + (StringUtils.isNotEmpty(version) ? SLASH + version + SLASH : SLASH) + method;
        String scriptNoMethod = resourceType + (StringUtils.isNotEmpty(version) ? SLASH + version + SLASH : SLASH) + resourceType
                .substring(resourceType.lastIndexOf(DOT) + 1);
        if (StringUtils.isNotEmpty(extension)) {
            if (defaultMethod) {
                matches.add(scriptNoMethod + DOT + extension);
            }
            matches.add(scriptForMethod + DOT + extension);

        }
        if (defaultMethod) {
            matches.add(scriptNoMethod);
        }
        matches.add(scriptForMethod);
        return Collections.unmodifiableList(matches);
    }

    private List<String> getScriptEngineExtensions() {
        List<String> _scriptEngineExtensions = new ArrayList<>();
        for (ScriptEngineFactory factory : scriptEngineManager.getEngineFactories()) {
            _scriptEngineExtensions.addAll(factory.getExtensions());
        }
        Collections.reverse(_scriptEngineExtensions);
        return Collections.unmodifiableList(_scriptEngineExtensions);
    }

    private String fromScriptPathToClassName(String scriptPath) {
        String[] parts = scriptPath.split("/");
        StringBuilder stringBuilder = new StringBuilder();
        for (String part : parts) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(".");
            }
            stringBuilder.append(JavaEscapeUtils.makeJavaIdentifier(part));
        }
        return stringBuilder.toString();
    }

}
