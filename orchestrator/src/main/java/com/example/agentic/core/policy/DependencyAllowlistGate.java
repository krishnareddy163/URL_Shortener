package com.example.agentic.core.policy;

import com.example.agentic.core.gate.Gate;
import com.example.agentic.core.gate.GateContext;
import com.example.agentic.core.gate.GateResult;
import com.example.agentic.core.workspace.Diff;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.IOException;
import java.io.StringReader;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Security gate on {@code pom.xml} changes, evaluated before any build runs. Every project dependency a change adds
 * must be on {@code dependencyAllowlist}, and every build plugin, plugin dependency and parent POM it adds must be on
 * {@code buildAllowlist}, because plugins run code during {@code mvn compile}, before a human reviews the change.
 * Adding repositories or build extensions is refused outright: they change where code is fetched from. Only
 * additions are checked, so an existing POM is never re-flagged. The XML parser refuses DTDs and external entities.
 */
public final class DependencyAllowlistGate implements Gate {
    private static final String DEFAULT_PLUGIN_GROUP = "org.apache.maven.plugins";

    private final Set<String> dependencyAllowlist;
    private final Set<String> buildAllowlist;

    public DependencyAllowlistGate(PolicyConfig policy) {
        dependencyAllowlist = Set.copyOf(policy.dependencyAllowlist());
        buildAllowlist = Set.copyOf(policy.buildAllowlist());
    }

    @Override
    public String id() {
        return "dependency-allowlist";
    }

    @Override
    public GateResult evaluate(GateContext context) {
        for (Diff.FileChange change : context.diff().changes()) {
            if (!change.path().equals("pom.xml") && !change.path().endsWith("/pom.xml")) {
                continue;
            }
            BuildFile before;
            BuildFile after;
            try {
                before = BuildFile.parse(change.before());
                after = BuildFile.parse(change.after());
            } catch (IllegalArgumentException exception) {
                return GateResult.fail(id(), "cannot parse " + change.path() + ": " + exception.getMessage());
            }
            for (String dependency : added(after.dependencies(), before.dependencies())) {
                if (!dependencyAllowlist.contains(dependency)) {
                    return GateResult.fail(id(), "dependency not on allowlist: " + dependency);
                }
            }
            for (String buildCode : added(after.buildCode(), before.buildCode())) {
                if (!buildAllowlist.contains(buildCode)) {
                    return GateResult.fail(id(), "build plugin or parent POM not on allowlist: " + buildCode);
                }
            }
            Set<String> sources = added(after.sources(), before.sources());
            if (!sources.isEmpty()) {
                return GateResult.fail(id(), change.path() + " may not add repositories or build extensions: " + sources);
            }
        }
        return GateResult.pass();
    }

    private static Set<String> added(Set<String> after, Set<String> before) {
        Set<String> added = new TreeSet<>(after);
        added.removeAll(before);
        return added;
    }

    /**
     * What a POM pulls in.
     *
     * @param dependencies project dependencies as {@code groupId:artifactId}
     * @param buildCode    plugins, plugin dependencies and the parent POM as {@code groupId:artifactId}
     * @param sources      declared repositories, plugin repositories and build extensions
     */
    record BuildFile(Set<String> dependencies, Set<String> buildCode, Set<String> sources) {

        static BuildFile parse(String pom) {
            BuildFile file = new BuildFile(new TreeSet<>(), new TreeSet<>(), new TreeSet<>());
            if (pom == null || pom.isBlank()) {
                return file;
            }
            Document document = document(pom);
            NodeList dependencies = document.getElementsByTagName("dependency");
            for (int index = 0; index < dependencies.getLength(); index++) {
                Element dependency = (Element) dependencies.item(index);
                (insidePlugin(dependency) ? file.buildCode : file.dependencies).add(coordinates(dependency, ""));
            }
            NodeList plugins = document.getElementsByTagName("plugin");
            for (int index = 0; index < plugins.getLength(); index++) {
                Element plugin = (Element) plugins.item(index);
                if (!child(plugin, "artifactId").isEmpty()) {
                    file.buildCode.add(coordinates(plugin, DEFAULT_PLUGIN_GROUP));
                }
            }
            NodeList parents = document.getElementsByTagName("parent");
            for (int index = 0; index < parents.getLength(); index++) {
                file.buildCode.add(coordinates((Element) parents.item(index), ""));
            }
            for (String tag : new String[] {"repository", "pluginRepository", "extension"}) {
                NodeList declared = document.getElementsByTagName(tag);
                for (int index = 0; index < declared.getLength(); index++) {
                    file.sources.add(tag + " " + declared.item(index).getTextContent().replaceAll("\\s+", " ").strip());
                }
            }
            return file;
        }

        private static String coordinates(Element element, String defaultGroup) {
            String group = child(element, "groupId");
            return (group.isEmpty() ? defaultGroup : group) + ":" + child(element, "artifactId");
        }
    }

    private static Document document(String pom) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(RethrowingErrorHandler.INSTANCE);
            return builder.parse(new InputSource(new StringReader(pom)));
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    private static boolean insidePlugin(Node node) {
        for (Node parent = node.getParentNode(); parent != null; parent = parent.getParentNode()) {
            if ("plugin".equals(parent.getNodeName())) {
                return true;
            }
        }
        return false;
    }

    private static String child(Element element, String name) {
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (name.equals(children.item(index).getNodeName())) {
                return children.item(index).getTextContent().strip();
            }
        }
        return "";
    }

    /**
     * Reports parse problems only through the exception the gate turns into its failure reason. The parser's
     * default handler would also print {@code [Fatal Error]} lines to stderr.
     */
    private enum RethrowingErrorHandler implements ErrorHandler {
        INSTANCE;

        @Override
        public void warning(SAXParseException exception) {
            // Warnings do not affect which dependencies the POM declares.
        }

        @Override
        public void error(SAXParseException exception) throws SAXParseException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXParseException {
            throw exception;
        }
    }
}
