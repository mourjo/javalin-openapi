package io.javalin.openapi.processor.configuration

import groovy.lang.GroovyClassLoader
import groovy.transform.CompileStatic
import io.javalin.openapi.JsonSchema
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApis
import io.javalin.openapi.experimental.ExperimentalCompileOpenApiConfiguration
import io.javalin.openapi.experimental.OpenApiAnnotationProcessorConfigurer
import io.javalin.openapi.experimental.processor.shared.info
import io.javalin.openapi.processor.OpenApiAnnotationProcessor.Companion.context
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import java.io.File
import javax.annotation.processing.RoundEnvironment

class OpenApiPrecompileScriptingEngine {

    private val groovyClassLoader by lazy {
        GroovyClassLoader(
            OpenApiPrecompileScriptingEngine::class.java.classLoader,
            CompilerConfiguration().also {
                it.addCompilationCustomizers(
                    ASTTransformationCustomizer(CompileStatic::class.java)
                )
            }
        )
    }

    @OptIn(ExperimentalCompileOpenApiConfiguration::class)
    fun load(roundEnvironment: RoundEnvironment): OpenApiAnnotationProcessorConfigurer? =
        roundEnvironment.getElementsAnnotatedWithAny(setOf(OpenApis::class.java, OpenApi::class.java, JsonSchema::class.java))
            .firstOrNull()
            ?.let { context.trees?.getPath(it)?.compilationUnit?.sourceFile?.name }
            ?.let {
                when {
                    /* Default sources */
                    it.contains("src".toPathSegmentIdentifier()) -> it.findSourceTargetNameBy("src") to it.substringBeforeLast("src".toPathSegmentIdentifier())
                    /* Kapt stubs */
                    it.contains("stubs".toPathSegmentIdentifier()) -> it.findSourceTargetNameBy("stubs") to it.substringBeforeLast("build".toPathSegmentIdentifier())
                    else -> null
                }
            }
            ?.let { (sourceTargetName, compileSources) -> File(compileSources).resolve("src").resolve(sourceTargetName).resolve("compile").resolve("openapi.groovy") }
            ?.also { context.env.messager.info(it.absolutePath.toString()) }
            ?.takeIf { it.exists() }
            ?.let { scriptFile -> groovyClassLoader.parseClass(scriptFile).getConstructor().newInstance() as OpenApiAnnotationProcessorConfigurer }

    private fun String.findSourceTargetNameBy(segment: String): String =
        substringAfter(segment.toPathSegmentIdentifier()).substringBefore(File.separator)

    private fun String.toPathSegmentIdentifier(): String =
        File.separator + this + File.separator

}