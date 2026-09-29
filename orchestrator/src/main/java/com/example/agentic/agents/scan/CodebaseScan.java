package com.example.agentic.agents.scan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structural facts extracted from Java sources.
 *
 * @param files       scanned Java files (workspace-relative)
 * @param classes     declared types
 * @param imports     import declarations per file
 * @param routes      HTTP routes from Spring mapping annotations
 * @param parseErrors files that could not be parsed, with the first problem
 */
public record CodebaseScan(List<String> files, List<TypeInfo> classes, List<ImportInfo> imports, List<Route> routes,
                           List<String> parseErrors) {

    public CodebaseScan {
        files = List.copyOf(files);
        classes = List.copyOf(classes);
        imports = List.copyOf(imports);
        routes = List.copyOf(routes);
        parseErrors = List.copyOf(parseErrors);
    }

    /**
     * A declared type.
     *
     * @param name fully qualified name
     * @param kind class, interface, record or enum
     * @param file declaring file
     */
    public record TypeInfo(String name, String kind, String file) {
    }

    /**
     * An import.
     *
     * @param file   importing file
     * @param target imported name
     */
    public record ImportInfo(String file, String target) {
    }

    /**
     * An HTTP route.
     *
     * @param method  HTTP method
     * @param path    full path (class prefix plus method mapping)
     * @param handler {@code Type#method}
     * @param file    declaring file
     */
    public record Route(String method, String path, String handler, String file) {
    }

    /** JSON-friendly form stored in {@code Proposal.data.scan}. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("files", files);
        map.put("classes", classes.stream().map(type -> Map.of("name", type.name(), "kind", type.kind(), "file", type.file())).toList());
        map.put("imports", imports.stream().map(item -> Map.of("file", item.file(), "import", item.target())).toList());
        map.put("routes", routes.stream().map(route -> Map.of("method", route.method(), "path", route.path(),
                "handler", route.handler(), "file", route.file())).toList());
        map.put("parseErrors", parseErrors);
        return map;
    }
}
