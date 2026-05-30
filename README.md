# Operaton JavaScript Polyglot

This repository builds the
`org.operaton.bpm.extension:operaton-javascript-polyglot` jar. It adds opt-in
Operaton script languages named `javascript-polyglot` and
`typescript-polyglot`.

The languages are backed by GraalJS, but they use custom Operaton bindings so
BPMN scripts can work with process variables as first-class JavaScript values:

```javascript
const path = uploadEvent.fileRef.path
uploadEvent.processed = true
execution.setVariable('uploadPath', path)
```

## Why This Exists

Operaton's regular `javascript` language exposes engine values through the normal
JSR-223/Spin model. That is useful for compatibility, but it means scripts often
need explicit Spin calls such as `prop(...)`, `hasProp(...)`, or manual
serialization/deserialization.

`javascript-polyglot` and `typescript-polyglot` are intended for processes that
want native JavaScript-style JSON access:

- JSON objects support dot and bracket notation.
- JSON arrays support normal index access.
- Java beans can be accessed through JavaScript-style properties and methods.
- Mutating a JSON process variable writes the updated value back to Operaton.
- Regular `javascript` and `ecmascript` behavior stays unchanged.

`typescript-polyglot` adds TypeScript syntax on top of the same runtime model. It
uses the official TypeScript compiler from the Maven classpath in transpile-only
mode, so it does not need Node.js and does not type-check against a project
`tsconfig.json` at runtime. Inline `typescript-polyglot` scripts are validated
with the same compiler diagnostics while BPMN resources are deployed, so invalid
TypeScript prevents the diagram from being deployed.

## Maven Usage

The jar is not published to Maven Central yet. Until it is available from a
public Maven repository, download the jar from the GitHub Releases page and add
it to the application classpath, or install it into a local or private Maven
repository.

Install a downloaded release jar into the local Maven repository with:

```sh
mvn install:install-file \
  -Dfile=operaton-javascript-polyglot-2.1.0.jar \
  -DgroupId=org.operaton.bpm.extension \
  -DartifactId=operaton-javascript-polyglot \
  -Dversion=2.1.0 \
  -Dpackaging=jar
```

After installing or publishing the jar to a Maven repository, add the dependency
to the Operaton application:

```xml
<dependency>
  <groupId>org.operaton.bpm.extension</groupId>
  <artifactId>operaton-javascript-polyglot</artifactId>
  <version>2.1.0</version>
</dependency>
```

Alternatively, build and install it locally from source:

```sh
mvn package
mvn install
```

## Spring Boot Usage

For Spring Boot based Operaton applications no extra code is needed after adding
the dependency. The jar contains:

- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `JavaScriptPolyglotAutoConfiguration`

That auto-configuration registers `JavaScriptPolyglotScriptEnvPlugin` as a
`ProcessEnginePlugin` bean.

## Plain Operaton Usage

Outside Spring Boot, register the plugin manually with the process engine
configuration:

```java
processEngineConfiguration.getProcessEnginePlugins()
    .add(new JavaScriptPolyglotScriptEnvPlugin());
```

The jar also registers the JSR-223 engine factory through:

```text
META-INF/services/javax.script.ScriptEngineFactory
```

## BPMN Usage

Use `scriptFormat="javascript-polyglot"` wherever a process should use the custom
bindings with JavaScript:

```xml
<bpmn:scriptTask id="CreateCaseFile"
                 name="Create case file"
                 scriptFormat="javascript-polyglot">
  <bpmn:script><![CDATA[
    const path = uploadEvent.fileRef.path
    uploadEvent.processed = true
    execution.setVariable('uploadPath', path)
  ]]></bpmn:script>
</bpmn:scriptTask>
```

Keep `scriptFormat="javascript"` for processes that should use Operaton's normal
JavaScript behavior.

Use `scriptFormat="typescript-polyglot"` when the script should be written in
TypeScript:

```xml
<bpmn:scriptTask id="CreateCaseFile"
                 name="Create case file"
                 scriptFormat="typescript-polyglot">
  <bpmn:script><![CDATA[
    interface UploadEvent {
      fileRef: { path: string }
      processed?: boolean
      caseDataId?: string
    }

    const event: UploadEvent = uploadEvent
    event.processed = true
    execution.setVariable('uploadPath', event.fileRef.path)
  ]]></bpmn:script>
</bpmn:scriptTask>
```

## Runtime Behavior

The plugin wraps Operaton's scripting setup in three places:

- `JavaScriptPolyglotScriptEngineFactory` exposes the `javascript-polyglot`
  JSR-223 language name and delegates execution to GraalJS.
- `TypeScriptPolyglotScriptEngineFactory` exposes the `typescript-polyglot`
  JSR-223 language name. It transpiles TypeScript to JavaScript before execution.
- `TypeScriptPolyglotBpmnParseListener` validates inline `typescript-polyglot`
  scripts during BPMN deployment.
- `JavaScriptPolyglotScriptingEngines` keeps default bindings for every language
  except the polyglot languages.
- `JavaScriptPolyglotValueMapper` converts between Spin JSON, Java host objects,
  GraalVM proxy objects and plain Java values.

Spin's JavaScript helper script is intentionally not injected for
`javascript-polyglot` or `typescript-polyglot`, because JSON values are exposed
directly as JavaScript objects.

## Compatibility

This plugin is deliberately opt-in:

- `javascript` is not changed.
- `ecmascript` is not changed.
- Existing Spin/JUEL/Groovy behavior is not changed.
- Only scripts using `javascript-polyglot` or `typescript-polyglot` receive the
  native JSON bindings.
- `typescript-polyglot` is transpile-only. Syntax errors are reported before
  deployment and execution, but semantic type errors are not checked.

## Tests

Run the plugin tests with:

```sh
mvn test
```

The tests cover:

- JSR-223 registration of `javascript-polyglot` and `typescript-polyglot`
- JSON process variable dot notation
- automatic write-back on JSON mutation
- compiled script evaluation
- TypeScript transpilation and diagnostics
- TypeScript deployment validation
- default `javascript` compatibility
- Spring Boot auto-configuration

## Tagged Builds

Pushing any Git tag triggers the `Build Tagged Artifact` GitHub Actions workflow.
The workflow runs `mvn -B package` on Java 17 and uploads the built jar from
`target/operaton-javascript-polyglot-*.jar` as a workflow artifact. Attach that
jar to the corresponding GitHub Release so users can download it without Maven
Central.
