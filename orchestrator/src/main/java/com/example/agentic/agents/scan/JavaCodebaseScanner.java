package com.example.agentic.agents.scan;

import com.example.agentic.core.agent.WorkspaceView;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Real static analysis with JavaParser: declared types, imports, and Spring MVC routes
 * ({@code @RequestMapping} class prefixes combined with {@code @GetMapping}/{@code @PostMapping}/...).
 */
public final class JavaCodebaseScanner {
    private static final Map<String, String> METHOD_ANNOTATIONS = Map.of(
            "GetMapping", "GET", "PostMapping", "POST", "PutMapping", "PUT",
            "DeleteMapping", "DELETE", "PatchMapping", "PATCH", "RequestMapping", "ANY");

    private final JavaParser parser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_25));

    public CodebaseScan scan(WorkspaceView workspace) {
        List<String> files = new ArrayList<>();
        List<CodebaseScan.TypeInfo> types = new ArrayList<>();
        List<CodebaseScan.ImportInfo> imports = new ArrayList<>();
        List<CodebaseScan.Route> routes = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (String file : workspace.listFiles()) {
            Optional<String> source = file.endsWith(".java") ? workspace.read(file) : Optional.empty();
            source.map(parser::parse).ifPresent(result -> {
                Optional<CompilationUnit> unit = result.isSuccessful() ? result.getResult() : Optional.empty();
                unit.ifPresentOrElse(parsed -> {
                    files.add(file);
                    collect(parsed, file, types, imports, routes);
                }, () -> errors.add(file + ": " + firstProblem(result)));
            });
        }
        return new CodebaseScan(files, types, imports, routes, errors);
    }

    private static void collect(CompilationUnit unit, String file, List<CodebaseScan.TypeInfo> types,
                                List<CodebaseScan.ImportInfo> imports, List<CodebaseScan.Route> routes) {
        unit.getImports().forEach(item -> imports.add(new CodebaseScan.ImportInfo(file, item.getNameAsString())));
        String pkg = unit.getPackageDeclaration().map(declaration -> declaration.getNameAsString() + ".").orElse("");
        for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
            String name = type.getFullyQualifiedName().orElse(pkg + type.getNameAsString());
            types.add(new CodebaseScan.TypeInfo(name, kind(type), file));
            routes.addAll(routes(type, file));
        }
    }

    private static String firstProblem(ParseResult<CompilationUnit> result) {
        return result.getProblems().stream().findFirst().map(Object::toString).orElse("unknown");
    }

    private static List<CodebaseScan.Route> routes(TypeDeclaration<?> type, String file) {
        String prefix = type.getAnnotationByName("RequestMapping").map(JavaCodebaseScanner::path).orElse("");
        List<CodebaseScan.Route> routes = new ArrayList<>();
        for (MethodDeclaration method : type.getMethods()) {
            for (AnnotationExpr annotation : method.getAnnotations()) {
                String verb = METHOD_ANNOTATIONS.get(annotation.getNameAsString());
                if (verb != null) {
                    routes.add(new CodebaseScan.Route(verb.equals("ANY") ? requestMethod(annotation) : verb,
                            join(prefix, path(annotation)), type.getNameAsString() + "#" + method.getNameAsString(), file));
                }
            }
        }
        return routes;
    }

    private static String path(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            return firstString(single.getMemberValue());
        }
        if (annotation instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals("path") || pair.getNameAsString().equals("value")) {
                    return firstString(pair.getValue());
                }
            }
        }
        return "";
    }

    private static String requestMethod(AnnotationExpr annotation) {
        if (annotation instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals("method") && pair.getValue() instanceof FieldAccessExpr access) {
                    return access.getNameAsString();
                }
            }
        }
        return "ANY";
    }

    private static String firstString(Expression expression) {
        if (expression instanceof StringLiteralExpr literal) {
            return literal.getValue();
        }
        if (expression instanceof ArrayInitializerExpr array && !array.getValues().isEmpty()) {
            return firstString(array.getValues().get(0));
        }
        return "";
    }

    private static String join(String prefix, String path) {
        String combined = (prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix)
                + (path.isEmpty() || path.startsWith("/") ? path : "/" + path);
        return combined.isEmpty() ? "/" : combined;
    }

    private static String kind(TypeDeclaration<?> type) {
        if (type.isRecordDeclaration()) {
            return "record";
        }
        if (type.isEnumDeclaration()) {
            return "enum";
        }
        if (type.isClassOrInterfaceDeclaration()) {
            return type.asClassOrInterfaceDeclaration().isInterface() ? "interface" : "class";
        }
        return type.getClass().getSimpleName().replace("Declaration", "").toLowerCase(Locale.ROOT);
    }
}
